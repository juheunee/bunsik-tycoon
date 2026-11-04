package bunsik.common;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 접속 정보(IP·포트) 같은 설정을 파일에서 읽는다. 코드에 IP를 박아 두지 않기 위한 장치다.
 *
 * <p>읽는 순서: {@code config.properties}(저장소에 올리는 공용 기본값) →
 * {@code config.local.properties}(각자 노트북 전용, .gitignore로 제외) 순서로 읽고, 뒤에 읽은 값이 앞의 값을 덮어쓴다.
 * 파일은 프로그램을 실행한 폴더(보통 저장소 최상위)에서 찾는다.
 */
public final class AppConfig {

    private final Properties props = new Properties();

    private AppConfig() {
    }

    public static AppConfig load() {
        AppConfig cfg = new AppConfig();
        cfg.loadFile("config.properties");
        cfg.loadFile("config.local.properties");
        return cfg;
    }

    private void loadFile(String name) {
        Path path = Path.of(name);
        if (!Files.isRegularFile(path)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            Properties tmp = new Properties();
            tmp.load(reader);
            props.putAll(tmp);
        } catch (IOException e) {
            System.err.println("[설정] " + name + " 을(를) 읽지 못했어요: " + e.getMessage());
        }
    }

    public String get(String key, String defaultValue) {
        String v = props.getProperty(key);
        return (v == null || v.isBlank()) ? defaultValue : v.trim();
    }

    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(get(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
