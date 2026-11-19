package bunsik.common;

import java.util.List;

/** 논리 좌표. 화면은 이 좌표계를 비율에 맞춰 확대/축소한다. */
public final class StoreLayout {
    private StoreLayout() { }
    public record Position(int x, int y) { }
    public record CookStation(Menu menu, Position position) { }
    public static final int WIDTH = 640, HEIGHT = 480;
    public static final int PLAYER_RADIUS = 14;
    public static final Position KITCHEN = new Position(550, 230);
    public static final List<CookStation> COOK_STATIONS = List.of(
            new CookStation(Menu.TTEOKBOKKI, new Position(550, 140)),
            new CookStation(Menu.GIMBAP, new Position(550, 183)),
            new CookStation(Menu.SUNDAE, new Position(550, 226)),
            new CookStation(Menu.TWIGIM, new Position(550, 269)),
            new CookStation(Menu.EOMUK, new Position(550, 312)));
    public static final Position DISCARD = new Position(600, 350);
    public static final Position DISH_BIN = new Position(600, 405);
    public static final int KITCHEN_TARGET_REACH = 24;
    public static final int TABLE_REACH = 70;
    public static final Position SPAWN = new Position(430, 265);
    public static final List<Position> TABLES = List.of(new Position(230, 210),
            new Position(375, 210), new Position(230, 375), new Position(375, 375));
    public static final List<Position> QUEUE = List.of(new Position(55, 210),
            new Position(55, 265), new Position(55, 320));
    public static int clampX(int x) { return Math.max(PLAYER_RADIUS, Math.min(WIDTH - PLAYER_RADIUS, x)); }
    public static int clampY(int y) { return Math.max(120, Math.min(HEIGHT - PLAYER_RADIUS, y)); }
    public static double distance(Position position, int x, int y) {
        return Math.hypot(x - position.x(), y - position.y());
    }

    public static CookStation nearestStation(int x, int y) {
        CookStation nearest = null;
        double best = KITCHEN_TARGET_REACH;
        for (CookStation station : COOK_STATIONS) {
            double distance = distance(station.position(), x, y);
            if (distance < best) {
                nearest = station;
                best = distance;
            }
        }
        return nearest;
    }

    public static boolean nearKitchenTarget(Position target, int x, int y) {
        return distance(target, x, y) < KITCHEN_TARGET_REACH;
    }
}
