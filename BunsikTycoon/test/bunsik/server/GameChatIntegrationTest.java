package bunsik.server;

import bunsik.common.ChatMessages;
import bunsik.common.Message;
import bunsik.common.MessageType;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import javax.swing.SwingUtilities;

/** Real game sockets: lobby and playing-room chat must use verified membership. */
public final class GameChatIntegrationTest {
    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket free = new ServerSocket(0)) { port = free.getLocalPort(); }
        ServerFrame frame = onEdt(ServerFrame::new);
        GameServer server = new GameServer(frame, port);
        try {
            server.start();
            try (Peer a = new Peer(port); Peer b = new Peer(port); Peer solo = new Peer(port)) {
                a.send(new Message(MessageType.HELLO, "Alpha", "VERSUS"));
                int alphaId = a.until(MessageType.WELCOME, 3000).getInt(0);
                b.send(new Message(MessageType.HELLO, "Beta", "VERSUS"));
                b.until(MessageType.WELCOME, 3000);
                solo.send(new Message(MessageType.HELLO, "Solo", "SOLO"));
                solo.until(MessageType.WELCOME, 3000);

                a.send(ChatMessages.request("안녕|친구"));
                String expected = "CHAT|" + alphaId + "|Alpha|안녕 친구";
                check(expected.equals(a.until(MessageType.CHAT, 3000).encode()), "sender receives one server echo");
                check(expected.equals(b.until(MessageType.CHAT, 3000).encode()), "same lobby receives chat");
                solo.noChat(450);

                a.send(new Message(MessageType.CHAT, 999, "forged", "text"));
                check(a.until(MessageType.TOAST, 3000).get(0).contains("1~200"), "forged sender rejected");
                b.noChat(350);

                a.send(new Message(MessageType.READY, true));
                b.send(new Message(MessageType.READY, true));
                a.until(MessageType.START, 3000);
                b.until(MessageType.START, 3000);
                a.send(ChatMessages.request("경기 중"));
                expected = "CHAT|" + alphaId + "|Alpha|경기 중";
                check(expected.equals(a.until(MessageType.CHAT, 6000).encode()), "sender game echo");
                check(expected.equals(b.until(MessageType.CHAT, 6000).encode()), "same game receives chat");
                solo.noChat(350);

                b.send(new Message(MessageType.LEAVE));
                a.until(MessageType.GAME_OVER, 6000);
                a.until(MessageType.LOBBY, 3000);
                a.send(ChatMessages.request("다시 대기실"));
                check(a.until(MessageType.CHAT, 3000).get(2).equals("다시 대기실"), "lobby chat after game");
            }
            System.out.println("GameChatIntegrationTest: PASS (lobby, room, isolation, spoof, return)");
        } finally {
            server.shutdown();
            onEdt(() -> { frame.dispose(); return null; });
        }
    }

    private static final class Peer implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        Peer(int port) throws Exception {
            socket = new Socket("127.0.0.1", port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            Thread heartbeat = new Thread(() -> {
                while (!socket.isClosed()) {
                    try {
                        Thread.sleep(1000);
                        send(new Message(MessageType.PING));
                    } catch (Exception e) { return; }
                }
            }, "test-chat-heartbeat");
            heartbeat.setDaemon(true);
            heartbeat.start();
        }
        synchronized void send(Message message) throws Exception {
            socket.getOutputStream().write((message.encode() + "\n").getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        }
        Message until(MessageType type, int timeoutMs) throws Exception {
            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            while (System.nanoTime() < deadline) {
                socket.setSoTimeout(Math.max(1, (int) ((deadline - System.nanoTime()) / 1_000_000L)));
                Message message = Message.parse(in.readLine());
                if (message.type() == type) return message;
            }
            throw new AssertionError("No " + type);
        }
        void noChat(int timeoutMs) throws Exception {
            long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
            while (System.nanoTime() < deadline) {
                socket.setSoTimeout(Math.max(1, (int) ((deadline - System.nanoTime()) / 1_000_000L)));
                try {
                    Message message = Message.parse(in.readLine());
                    check(message.type() != MessageType.CHAT, "chat leaked to another recipient");
                } catch (SocketTimeoutException expected) { return; }
            }
        }
        @Override public void close() throws Exception { socket.close(); }
    }

    private static <T> T onEdt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        SwingUtilities.invokeAndWait(task);
        return task.get();
    }
    private static void check(boolean valid, String reason) {
        if (!valid) throw new AssertionError(reason);
    }
}
