package bunsik.client;

import bunsik.common.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** 접속·송신·수신은 EDT 밖에서 수행함. 세션마다 새 객체를 사용 */
public final class NetworkClient implements AutoCloseable {
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "game-sender"); t.setDaemon(true); return t;
    });
    // 2초마다 PING. 서버는 6초 무응답 접속을 끊으므로 이동이 없는 대기실, 정지 중에도 연결 유지
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "client-heartbeat"); t.setDaemon(true); return t;
    });
    private final Consumer<Message> receiver;
    private final Consumer<String> disconnected;
    private volatile Socket socket;
    private volatile boolean closed, leaving;
    private boolean started;
    private BufferedWriter writer;

    public NetworkClient(Consumer<Message> receiver, Consumer<String> disconnected) {
        this.receiver = receiver; this.disconnected = disconnected;
    }
    public void connect(AppConfig config, String nickname) {
        connect(config.serverHost(), config.serverPort(), config.connectTimeoutMs(), nickname, "SOLO");
    }
    public synchronized void connect(String host, int port, int timeoutMs, String nickname, String mode) {
        if (closed || started) throw new IllegalStateException("Connection already started or closed");
        if (host == null || host.isBlank() || port < 1 || port > 65535 || timeoutMs < 1
                || nickname == null || nickname.isBlank() || nickname.length() > 20
                || !("SOLO".equals(mode) || "VERSUS".equals(mode))) {
            throw new IllegalArgumentException("Invalid connection settings");
        }
        started = true;
        sender.execute(() -> {
            try {
                Socket connection = new Socket();
                socket = connection;
                if (closed) { connection.close(); return; }
                connection.connect(new InetSocketAddress(host, port), timeoutMs);
                if (leaving) { close(); return; }
                connection.setTcpNoDelay(true);
                // 서버도 2초마다 PING을 보내므로 6초 동안 수신이 없으면 끊긴 것으로 판단
                connection.setSoTimeout(6000);
                writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
                write(new Message(MessageType.HELLO, nickname, mode));
                Thread reader = new Thread(() -> read(connection), "game-receiver");
                reader.setDaemon(true); reader.start();
                // 접속 직후 close()가 먼저 실행된 경우 예약 거절은 무시
                try { heartbeat.scheduleAtFixedRate(() -> send(new Message(MessageType.PING)), 2, 2, TimeUnit.SECONDS); }
                catch (RejectedExecutionException ignored) { }
            } catch (IOException | IllegalArgumentException e) { fail("접속 실패: " + e.getMessage()); }
        });
    }
    private void read(Socket connection) {
        try (var input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = input.readLine()) != null) {
                try {
                    Message message = Message.parse(line);
                    // EOF보다 먼저 수신한 결과도 EDT 순서대로 전달함. 화면은 세션 번호로 이전 접속을 거름.
                    SwingUtilities.invokeLater(() -> receiver.accept(message));
                } catch (IllegalArgumentException e) { fail("잘못된 서버 메시지"); return; }
            }
            fail("서버 연결이 종료되었습니다.");
        } catch (IOException e) { fail("서버 연결이 끊겼습니다."); }
    }
    public void send(Message message) {
        if (closed || leaving) return;
        try { sender.execute(() -> {
            try { if (!closed && !leaving && writer != null) write(message); }
            catch (IOException e) { fail("전송 실패"); }
        }); } catch (RejectedExecutionException ignored) { }
    }
    /** Stop gameplay sends, then deliver LEAVE on the same writer before closing. */
    public synchronized void leave() {
        if (closed || leaving) return;
        leaving = true;
        try {
            sender.execute(() -> {
                try {
                    if (!closed && writer != null) write(new Message(MessageType.LEAVE));
                } catch (IOException ignored) {
                    // EOF also removes the session on the server.
                } finally {
                    close();
                }
            });
        } catch (RejectedExecutionException e) {
            close();
        }
    }

    private void write(Message message) throws IOException {
        writer.write(message.encode()); writer.newLine(); writer.flush();
    }
    private synchronized void fail(String reason) {
        if (closed) return;
        boolean report = !leaving;
        close();
        if (report) SwingUtilities.invokeLater(() -> disconnected.accept(reason));
    }
    @Override public synchronized void close() {
        closed = true;
        heartbeat.shutdownNow();
        Socket connection = socket;
        if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        // Socket.close unblocks I/O; queued sends observe closed and do no work.
        // Do not interrupt the sender itself before it posts the EDT failure callback.
        sender.shutdown();
    }
}
