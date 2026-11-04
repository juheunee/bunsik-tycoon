package bunsik.server;

import bunsik.common.MessageType;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 서버 본체. 정해진 포트에서 접속을 기다리는 "문지기" 역할을 하고,
 * 접속이 오면 전담 직원({@link ClientHandler})을 붙여 준다.
 */
public final class GameServer {

    private final int port;
    private final VersusLobby lobby = new VersusLobby(this);
    private final AtomicInteger nextId = new AtomicInteger();

    public GameServer(int port) {
        this.port = port;
    }

    /** 접속을 무한히 기다린다(이 메서드는 서버가 꺼질 때까지 돌아오지 않음). */
    public void run() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            log("포트 " + port + "에서 손님(클라이언트)을 기다리는 중...");
            while (true) {
                Socket socket = serverSocket.accept();
                // 작은 메시지를 모아 보내느라 생기는 지연을 없앤다(실시간 게임에 유리).
                socket.setTcpNoDelay(true);
                int id = nextId.incrementAndGet();
                try {
                    ClientHandler handler = new ClientHandler(this, socket, id);
                    log("접속: #" + id + " " + handler.address());
                    Thread t = new Thread(handler, "client-" + id);
                    t.setDaemon(true);
                    t.start();
                } catch (IOException e) {
                    log("접속 처리 실패: " + e.getMessage());
                    socket.close();
                }
            }
        }
    }

    /** HELLO를 받으면: 혼자하기는 바로 게임방, 경쟁하기는 대기실로 */
    void onHello(ClientHandler h, boolean versus) {
        h.send(MessageType.WELCOME, h.id());
        if (versus) {
            lobby.join(h);
        } else {
            log("혼자하기 시작: " + h.nickname());
            new GameRoom(this, List.of(h), false).start();
        }
    }

    /**
     * 연결이 끊긴 사람 정리(MVP 수준: 명단에서 빼고 게임방에 알림).
     * 상대에게 알리기·승패 처리 같은 정식 끊김 처리는 README의 "앞으로 할 일" 참고.
     */
    void onDisconnect(ClientHandler h) {
        lobby.leave(h);
        GameRoom r = h.room();
        if (r != null) {
            r.onPlayerLeft(h);
        }
        log("연결 종료: #" + h.id() + " " + h.nickname());
    }

    VersusLobby lobby() {
        return lobby;
    }

    static void log(String text) {
        System.out.println("[서버] " + text);
    }
}
