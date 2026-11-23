package bunsik.server;

import bunsik.common.MessageType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** 접속 수락과 접속자 명단 관리 */
public final class GameServer {

    private static final int PING_INTERVAL_MS = 2000;

    private final ServerFrame frame;
    private final int port;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final VersusLobby lobby = new VersusLobby(this);
    private final AtomicInteger nextId = new AtomicInteger();
    // 대기실처럼 STATE가 오지 않는 동안에도 클라이언트가 서버 끊김을 6초 안에 알 수 있도록 PING 전송
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat");
        t.setDaemon(true);
        return t;
    });
    private ServerSocket serverSocket;

    public GameServer(ServerFrame frame, int port) {
        this.frame = frame;
        this.port = port;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        Thread acceptThread = new Thread(this::acceptLoop, "accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
        heartbeat.scheduleAtFixedRate(this::sendPing, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
        log("포트 " + port + "에서 접속 대기 중");
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ClientHandler handler = new ClientHandler(this, socket, nextId.incrementAndGet());
                clients.add(handler);
                frame.setClientCount(clients.size());
                log("접속: #" + handler.id() + " " + handler.address());
                Thread t = new Thread(handler, "client-" + handler.id());
                t.setDaemon(true);
                t.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    log("접속 처리 실패: " + e.getMessage());
                }
            }
        }
    }

    /** first: 이 연결의 첫 HELLO 여부 (다시 하기는 HELLO 재전송) */
    void onHello(ClientHandler h, boolean versus, boolean first) {
        if (first) {
            h.send(MessageType.WELCOME, h.id());
            log("입장: #" + h.id() + " " + h.nickname());
        }
        if (versus) {
            lobby.join(h);
            return;
        }
        log("혼자 하기: " + h.nickname());
        new GameRoom(this, List.of(h), false, null).start();
    }

    private void sendPing() {
        for (ClientHandler h : clients) {
            h.send(MessageType.PING);
        }
    }

    void onDisconnect(ClientHandler h) {
        // 같은 연결의 종료 처리는 명단에서 처음 빠질 때 한 번만
        if (!clients.remove(h)) {
            return;
        }
        lobby.leave(h);
        GameRoom r = h.room();
        if (r != null) {
            r.onPlayerLeft(h);
        }
        frame.setClientCount(clients.size());
        log("연결 종료: #" + h.id() + " " + h.nickname());
    }

    public void shutdown() {
        heartbeat.shutdownNow();
        for (ClientHandler h : clients) {
            h.send(MessageType.TOAST, "서버가 종료돼요");
        }
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }
        for (ClientHandler h : clients) {
            h.close();
        }
    }

    VersusLobby lobby() {
        return lobby;
    }

    void log(String text) {
        frame.log(text);
    }
}