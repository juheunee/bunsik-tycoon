package bunsik.common;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/** 실행 디렉터리(또는 지정 디렉터리)의 공유 설정 위에 개인 설정을 덮어쓴다. */
public final class AppConfig {
    private final String host;
    private final int port;
    private final int connectTimeoutMs;

    private AppConfig(Properties properties) {
        host = properties.getProperty("server.host").trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("server.host must not be empty");
        }
        port = readInt(properties, "server.port", 1, 65535);
        connectTimeoutMs = readInt(properties, "client.connectTimeoutMs", 1, Integer.MAX_VALUE);
    }

    public static AppConfig load() throws IOException {
        return load(Paths.get("."));
    }

    public static AppConfig load(Path directory) throws IOException {
        if (directory == null) {
            throw new IllegalArgumentException("Configuration directory must not be null");
        }
        Properties properties = new Properties();
        properties.setProperty("server.host", "127.0.0.1");
        properties.setProperty("server.port", "9999");
        properties.setProperty("client.connectTimeoutMs", "5000");
        loadFile(properties, directory.resolve("config.properties"));
        loadFile(properties, directory.resolve("config.local.properties"));
        return new AppConfig(properties);
    }

    private static void loadFile(Properties properties, Path file) throws IOException {
        if (Files.notExists(file)) { return; }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
    }

    private static int readInt(Properties properties, String key, int min, int max) {
        final int value;
        try {
            value = Integer.parseInt(properties.getProperty(key).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid integer setting: " + key, e);
        }
        if (value < min || value > max) {
            throw new IllegalArgumentException("Setting out of range: " + key);
        }
        return value;
    }

    public String serverHost() { return host; }
    public int serverPort() { return port; }
    public int connectTimeoutMs() { return connectTimeoutMs; }
}
