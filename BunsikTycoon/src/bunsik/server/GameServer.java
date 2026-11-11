package bunsik.server;

import bunsik.common.MessageType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** 접속 수락과 접속자 명단 관리 */
public final class GameServer {

    private final ServerFrame frame;
    private final int port;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final AtomicInteger nextId = new AtomicInteger();
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

    void onHello(ClientHandler h) {
        h.send(MessageType.WELCOME, h.id());
        log("입장: #" + h.id() + " " + h.nickname());
    }

    void onDisconnect(ClientHandler h) {
        clients.remove(h);
        frame.setClientCount(clients.size());
        log("연결 종료: #" + h.id() + " " + h.nickname());
    }

    public void shutdown() {
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

    void log(String text) {
        frame.log(text);
    }
}