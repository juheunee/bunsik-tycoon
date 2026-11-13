package bunsik.common;

import java.util.List;

/** 논리 좌표. 화면은 이 좌표계를 비율에 맞춰 확대/축소한다. */
public final class StoreLayout {
    private StoreLayout() { }
    public record Position(int x, int y) { }
    public static final int WIDTH = 640, HEIGHT = 480;
    public static final int PLAYER_RADIUS = 14;
    public static final Position KITCHEN = new Position(320, 70);
    public static final Position SPAWN = new Position(320, 360);
    public static final List<Position> TABLES = List.of(new Position(200, 210),
            new Position(440, 210), new Position(200, 330), new Position(440, 330));
    public static final List<Position> QUEUE = List.of(new Position(55, 210),
            new Position(55, 265), new Position(55, 320));
    public static int clampX(int x) { return Math.max(PLAYER_RADIUS, Math.min(WIDTH - PLAYER_RADIUS, x)); }
    public static int clampY(int y) { return Math.max(120, Math.min(HEIGHT - PLAYER_RADIUS, y)); }
}
