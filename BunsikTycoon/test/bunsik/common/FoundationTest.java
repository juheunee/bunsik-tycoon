package bunsik.common;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** 외부 라이브러리 없이 실행하는 공통 계약 회귀 검사. */
public final class FoundationTest {
    public static void main(String[] args) throws Exception {
        Message hello = Message.parse(new Message(MessageType.HELLO, "주흔", "VERSUS").encode());
        check(hello.type() == MessageType.HELLO && hello.size() == 2, "HELLO header");
        check(hello.get(0).equals("주흔"), "UTF-8 nickname");
        check(new Message(MessageType.LEAVE).encode().equals("LEAVE"), "no fields");
        check(Message.parse("TOAST|").size() == 1, "trailing empty field");
        check(Message.parse("START|0|2||").size() == 4, "multiple empty fields");
        check(new Message(MessageType.TOAST, "a|b\r\nc").encode().equals("TOAST|a b  c"), "sanitize");
        check(Message.parse("MOVE|-3|4|true").getInt(0) == -3, "signed int");
        check(Message.parse("STATE|2147483648").getLong(0) == 2147483648L, "long");
        check(Message.parse("READY|true").getBool(0), "true");
        check(!Message.parse("READY|false").getBool(0), "false");
        invalid(() -> Message.parse(null));
        invalid(() -> Message.parse(" "));
        invalid(() -> Message.parse("UNKNOWN|1"));
        invalid(() -> Message.parse("LEAVE\nREADY|true"));
        invalid(() -> Message.parse("LEAVE").get(0));
        invalid(() -> hello.get(-1));
        invalid(() -> Message.parse("WELCOME|abc").getInt(0));
        invalid(() -> Message.parse("WELCOME|2147483648").getInt(0));
        invalid(() -> Message.parse("READY|yes").getBool(0));
        invalid(() -> new Message(null));
        invalid(() -> new Message(MessageType.TOAST, (Object) null));

        Path directory = Files.createTempDirectory("bunsik-config-test-");
        try {
            AppConfig defaults = AppConfig.load(directory);
            check(defaults.serverHost().equals("127.0.0.1") && defaults.serverPort() == 9999
                    && defaults.connectTimeoutMs() == 5000, "defaults");
            Files.writeString(directory.resolve("config.properties"),
                    "server.host=공유호스트\nserver.port=5550\n", StandardCharsets.UTF_8);
            Files.writeString(directory.resolve("config.local.properties"),
                    "server.host=192.168.0.10\nclient.connectTimeoutMs=2500\n", StandardCharsets.UTF_8);
            AppConfig local = AppConfig.load(directory);
            check(local.serverHost().equals("192.168.0.10") && local.serverPort() == 5550
                    && local.connectTimeoutMs() == 2500, "local overrides only specified keys");
            Files.writeString(directory.resolve("config.local.properties"), "server.port=abc\n");
            invalid(() -> AppConfig.load(directory));
            Files.writeString(directory.resolve("config.local.properties"), "server.port=65536\n");
            invalid(() -> AppConfig.load(directory));
            Files.writeString(directory.resolve("config.local.properties"), "server.host=   \n");
            invalid(() -> AppConfig.load(directory));
            Files.writeString(directory.resolve("config.local.properties"), "client.connectTimeoutMs=0\n");
            invalid(() -> AppConfig.load(directory));
        } finally {
            try (Stream<Path> paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) {
                    Files.delete(path);
                }
            }
        }
        System.out.println("FoundationTest: PASS");
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }

    private static void invalid(Action action) throws Exception {
        try { action.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Expected IllegalArgumentException");
    }

    private interface Action { void run() throws Exception; }
}
