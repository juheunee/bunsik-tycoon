package bunsik.server;

import bunsik.common.GameConfig;
import bunsik.common.Message;
import bunsik.common.MessageType;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 접속한 클라이언트 한 명을 전담하는 직원(스레드 하나).
 *
 * <p>메시지를 읽는 {@code readLine()}은 메시지가 올 때까지 멈춰서 기다린다.
 * 직원이 한 명뿐이면 A를 기다리는 동안 B의 말을 들을 수 없으므로, 접속자마다 이 객체를 스레드로 하나씩 띄운다.
 *
 * <p>이 직원은 "듣고 전달"만 한다. 게임 행동(MOVE·INTERACT·COOK)은 직접 처리하지 않고
 * 게임방의 대기줄에 넣어 게임 루프 스레드가 순서대로 처리하게 한다.
 */
public final class ClientHandler implements Runnable {

    private final GameServer server;
    private final Socket socket;
    private final int id;
    private final BufferedReader in;
    private final PrintWriter out;

    private volatile String nickname;
    private volatile GameRoom room;
    private volatile int slot = -1;
    private volatile boolean closed;
    private boolean greeted;

    ClientHandler(GameServer server, Socket socket, int id) throws IOException {
        this.server = server;
        this.socket = socket;
        this.id = id;
        this.nickname = "손님" + id;
        // 한글이 깨지지 않도록 UTF-8을 명시한다(노트북마다 기본 인코딩이 다를 수 있음).
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    @Override
    public void run() {
        try {
            String line;
            while (!closed && (line = in.readLine()) != null) {
                try {
                    if (!handle(Message.parse(line))) {
                        break;
                    }
                } catch (IllegalArgumentException e) {
                    // 형식이 이상한 메시지 하나 때문에 이 직원(스레드)이 죽지 않도록 무시한다.
                    GameServer.log("이상한 메시지 무시(" + nickname + "): " + line);
                }
            }
        } catch (IOException e) {
            // 상대가 갑자기 끊으면 여기로 온다. 아래 finally에서 정리한다.
        } finally {
            close();
            server.onDisconnect(this);
        }
    }

    /** @return false면 연결을 끝낸다 */
    private boolean handle(Message m) {
        switch (m.type()) {
            case HELLO -> {
                if (greeted) {
                    return true;
                }
                greeted = true;
                nickname = sanitizeNickname(m.get(0));
                server.onHello(this, "VERSUS".equals(m.get(1)));
            }
            case READY -> server.lobby().setReady(this, m.getBool(0));
            case MOVE, INTERACT, COOK -> {
                GameRoom r = room;
                if (r != null) {
                    r.enqueue(slot, m);
                }
            }
            case LEAVE -> {
                return false;
            }
            default -> GameServer.log("클라이언트가 보낼 수 없는 메시지 무시: " + m.type());
        }
        return true;
    }

    private String sanitizeNickname(String raw) {
        String s = raw.replaceAll("[|:,]", "").trim();
        if (s.isEmpty()) {
            s = "손님" + id;
        }
        return s.length() > GameConfig.NICKNAME_MAX ? s.substring(0, GameConfig.NICKNAME_MAX) : s;
    }

    /**
     * 메시지 보내기. 게임 루프 스레드와 대기실 처리가 동시에 이 메서드를 부를 수 있으므로
     * 한 번에 하나씩만 쓰도록 잠근다(안 그러면 두 메시지가 한 줄에 섞여 깨질 수 있다).
     */
    public void send(Message m) {
        if (closed) {
            return;
        }
        synchronized (out) {
            out.println(m.encode());
        }
    }

    public void send(MessageType type, Object... args) {
        send(new Message(type, args));
    }

    public void close() {
        closed = true;
        try {
            socket.close();
        } catch (IOException ignored) {
            // 이미 닫힘
        }
    }

    void joinRoom(GameRoom room, int slot) {
        this.room = room;
        this.slot = slot;
    }

    void leaveRoom(GameRoom room) {
        if (this.room == room) {
            this.room = null;
            this.slot = -1;
        }
    }

    public int id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public GameRoom room() {
        return room;
    }

    public String address() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }
}
