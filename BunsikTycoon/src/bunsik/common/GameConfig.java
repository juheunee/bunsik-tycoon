package bunsik.common;

public final class GameConfig {
    private GameConfig() { }
    public static final int GAME_DURATION_MS = 180000;
    public static final int TABLE_COUNT = 4;
    public static final int QUEUE_CAPACITY = 3;
    public static final int ARRIVAL_GAP_MIN_MS = 4000;
    public static final int ARRIVAL_GAP_MAX_MS = 7000;
    public static final int PATIENCE_MS = 45000;
    public static final int EAT_MS = 5000;
    public static final double TIP_RATE = 0.5;
    public static final int PLAYER_SPEED = 230;
    public static final int INPUT_TICK_MS = 40;
}
