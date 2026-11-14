package bunsik.client;

import bunsik.common.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** 접속·송신·수신은 EDT 밖에서 수행한다. 세션마다 새 객체를 사용한다. */
public final class NetworkClient implements AutoCloseable {
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "game-sender"); t.setDaemon(true); return t;
    });
    private final Consumer<Message> receiver;
    private final Consumer<String> disconnected;
    private volatile Socket socket;
    private volatile boolean closed;
    private BufferedWriter writer;

    public NetworkClient(Consumer<Message> receiver, Consumer<String> disconnected) {
        this.receiver = receiver; this.disconnected = disconnected;
    }
    public void connect(AppConfig config, String nickname) {
        connect(config.serverHost(), config.serverPort(), config.connectTimeoutMs(), nickname, "SOLO");
    }
    public void connect(String host, int port, int timeoutMs, String nickname, String mode) {
        sender.execute(() -> {
            try {
                Socket connection = new Socket();
                socket = connection;
                if (closed) { connection.close(); return; }
                connection.connect(new InetSocketAddress(host, port), timeoutMs);
                connection.setTcpNoDelay(true);
                writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8));
                write(new Message(MessageType.HELLO, nickname, mode));
                Thread reader = new Thread(() -> read(connection), "game-receiver");
                reader.setDaemon(true); reader.start();
            } catch (IOException e) { fail("접속 실패: " + e.getMessage()); }
        });
    }
    private void read(Socket connection) {
        try (var input = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while (!closed && (line = input.readLine()) != null) {
                try {
                    Message message = Message.parse(line);
                    // EOF보다 먼저 수신한 결과도 EDT 순서대로 전달한다. 화면은 세션 번호로 이전 접속을 거른다.
                    SwingUtilities.invokeLater(() -> receiver.accept(message));
                } catch (IllegalArgumentException e) { fail("잘못된 서버 메시지"); return; }
            }
            fail("서버 연결이 종료되었습니다.");
        } catch (IOException e) { fail("서버 연결이 끊겼습니다."); }
    }
    public void send(Message message) {
        if (closed) return;
        try { sender.execute(() -> {
            try { if (!closed && writer != null) write(message); }
            catch (IOException e) { fail("전송 실패"); }
        }); } catch (RejectedExecutionException ignored) { }
    }
    private void write(Message message) throws IOException {
        writer.write(message.encode()); writer.newLine(); writer.flush();
    }
    private synchronized void fail(String reason) {
        if (closed) return;
        close(); SwingUtilities.invokeLater(() -> disconnected.accept(reason));
    }
    @Override public synchronized void close() {
        closed = true;
        Socket connection = socket;
        if (connection != null) try { connection.close(); } catch (IOException ignored) { }
        sender.shutdownNow();
    }
}
