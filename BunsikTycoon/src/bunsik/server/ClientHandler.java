package bunsik.server;

import bunsik.common.Message;
import bunsik.common.MessageType;
import bunsik.common.ChatMessages;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/** 클라이언트 한 명의 수신 스레드 */
public final class ClientHandler implements Runnable {

    private static final int NICKNAME_MAX = 10;
    /** 이 시간 동안 아무 메시지도 없으면 연결 끊김으로 판단 (클라이언트는 2초마다 PING) */
    private static final int HEARTBEAT_TIMEOUT_MS = 6000;

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
        socket.setSoTimeout(HEARTBEAT_TIMEOUT_MS);
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
        } catch (SocketTimeoutException e) {
            server.log("응답 없음(하트비트 시간 초과): " + nickname);
        } catch (IOException ignored) {
        } finally {
            close();
            server.onDisconnect(this);
        }
    }

    /** 메시지 처리 (false: 연결 종료) */
    private boolean handle(Message m) {
        // PING은 연결 유지 확인용이라 수신 자체로 하트비트 시간이 초기화되고 처리할 내용은 없음
        if (m.type() == MessageType.PING) {
            return true;
        }
        if (!greeted && m.type() != MessageType.HELLO && m.type() != MessageType.LEAVE) {
            server.log("HELLO 전 메시지 무시: " + m.type());
            return true;
        }
        switch (m.type()) {
            case HELLO -> {
                // 1P 다시 하기는 결과 후(방과 대기실 밖)의 HELLO라 여기에 걸리지 않음
                if (room != null || server.lobby().contains(this)) {
                    server.log("중복 HELLO 무시: " + nickname);
                    return true;
                }
                String name = sanitize(m.get(0));
                boolean versus = m.size() > 1 && "VERSUS".equals(m.get(1));
                nickname = name;
                boolean first = !greeted;
                greeted = true;
                server.onHello(this, versus, first);
            }
            case READY -> server.lobby().setReady(this, m.getBool(0));
            case CHAT -> {
                try {
                    ChatMessages.requestText(m);
                    GameRoom r = room;
                    if (r != null) r.enqueue(slot, m);
                    else server.lobby().broadcastChat(this, m);
                } catch (IllegalArgumentException e) {
                    send(MessageType.TOAST, "채팅은 1~200자로 입력해 주세요.");
                }
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
