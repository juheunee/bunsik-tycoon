package bunsik.server;

import bunsik.common.Message;
import bunsik.common.MessageType;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** 클라이언트 한 명 전담 수신 스레드 */
public final class ClientHandler implements Runnable {

    private static final int NICKNAME_MAX = 10;

    private final GameServer server;
    private final Socket socket;
    private final int id;
    private final BufferedReader in;
    private final BufferedWriter out;
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
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
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
                    server.log("잘못된 메시지 무시(" + nickname + "): " + line);
                }
            }
        } catch (IOException ignored) {
        } finally {
            close();
            server.onDisconnect(this);
        }
    }

    /** 메시지 처리 (false: 연결 종료) */
    private boolean handle(Message m) {
        switch (m.type()) {
            case HELLO -> {
                if (room != null) {
                    server.log("게임 중 HELLO 무시: " + nickname);
                    return true;
                }
                String name = sanitize(m.get(0));
                boolean versus = m.size() > 1 && "VERSUS".equals(m.get(1));
                nickname = name;
                boolean first = !greeted;
                greeted = true;
                server.onHello(this, versus, first);
            }
            case LEAVE -> {
                return false;
            }
            default -> {
                GameRoom r = room;
                if (r != null) {
                    r.enqueue(slot, m);
                } else {
                    server.log("수신(" + nickname + "): " + m.encode());
                }
            }
        }
        return true;
    }

    /** 구분자(| : ,) 제거와 길이 제한 */
    private String sanitize(String raw) {
        String s = raw.replaceAll("[|:,]", "").trim();
        if (s.isEmpty()) {
            s = "손님" + id;
        }
        return s.length() > NICKNAME_MAX ? s.substring(0, NICKNAME_MAX) : s;
    }

    public void send(Message m) {
        if (closed) {
            return;
        }
        try {
            synchronized (out) {
                out.write(m.encode());
                out.newLine();
                out.flush();
            }
        } catch (IOException e) {
            close();
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

    public GameRoom room() {
        return room;
    }

    public int id() {
        return id;
    }

    public String nickname() {
        return nickname;
    }

    public String address() {
        return String.valueOf(socket.getRemoteSocketAddress());
    }
}