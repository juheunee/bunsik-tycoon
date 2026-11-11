package bunsik.chat;

import bunsik.common.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

public final class ChatIntegrationTest {
    public static void main(String[] args) throws Exception {
        int port;
        try (ServerSocket reservation = new ServerSocket(0)) { port = reservation.getLocalPort(); }
        Path directory = Files.createTempDirectory("bunsik-chat-test-");
        Files.writeString(directory.resolve("config.properties"), "server.port=" + port);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process server = new ProcessBuilder(java, "-cp", Path.of("out").toAbsolutePath().toString(),
                "bunsik.chat.ChatServerMain").directory(directory.toFile()).redirectErrorStream(true).start();
        try (Socket a = connect(port); Socket b = connect(port)) {
            a.setSoTimeout(3000); b.setSoTimeout(3000);
            BufferedReader ar = reader(a), br = reader(b);
            send(a, new Message(MessageType.HELLO, "주흔", "VERSUS"));
            Message welcomeA = Message.parse(ar.readLine());
            check(welcomeA.type() == MessageType.WELCOME, "A welcome");
            send(b, new Message(MessageType.HELLO, "차민", "VERSUS"));
            Message welcomeB = Message.parse(br.readLine());
            check(welcomeB.type() == MessageType.WELCOME, "B welcome");
            send(a, ChatMessages.request("안녕하세요"));
            String expected = "CHAT|" + welcomeA.getInt(0) + "|주흔|안녕하세요";
            check(expected.equals(ar.readLine()) && expected.equals(br.readLine()), "A broadcast");
            send(b, ChatMessages.request("반갑습니다"));
            expected = "CHAT|" + welcomeB.getInt(0) + "|차민|반갑습니다";
            check(expected.equals(ar.readLine()) && expected.equals(br.readLine()), "B broadcast");
            send(a, new Message(MessageType.CHAT, 999, "위조", "본문"));
            check(Message.parse(ar.readLine()).type() == MessageType.TOAST, "reject spoof");
            send(a, ChatMessages.request("계속 연결"));
            check(ar.readLine().endsWith("|계속 연결") && br.readLine().endsWith("|계속 연결"), "survive bad input");
            invalid(() -> ChatMessages.request(" "));
            invalid(() -> ChatMessages.request("a".repeat(201)));
            invalid(() -> ChatMessages.display(new Message(MessageType.CHAT, "body")));
            check(ChatMessages.request("a|b\nc").get(0).equals("a b c"), "sanitize");
            check(ChatMessages.request("a".repeat(200)).get(0).length() == 200, "boundary");
            send(b, new Message(MessageType.LEAVE));
            check(br.readLine() == null, "leave closes connection");
            System.out.println("ChatIntegrationTest: PASS");
        } finally {
            server.destroyForcibly(); server.waitFor();
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) { Files.delete(path); }
            }
        }
    }

    private static Socket connect(int port) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (true) {
            try { return new Socket("127.0.0.1", port); }
            catch (ConnectException e) {
                if (System.nanoTime() > deadline) { throw e; }
                Thread.sleep(50);
            }
        }
    }

    private static BufferedReader reader(Socket socket) throws IOException {
        return new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }
    private static void send(Socket socket, Message message) throws IOException {
        socket.getOutputStream().write((message.encode() + "\n").getBytes(StandardCharsets.UTF_8));
        socket.getOutputStream().flush();
    }
    private static void check(boolean value, String label) {
        if (!value) { throw new AssertionError(label); }
    }
    private static void invalid(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected invalid CHAT");
    }
}
