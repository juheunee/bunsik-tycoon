package bunsik.common;

/**
 * 식당 한 칸의 배치도(좌표). 서버는 "가까이 있는지" 판정에, 클라이언트는 그리기와 이동 제한에 같은 값을 쓴다.
 *
 * <p>좌표는 화면 크기와 상관없는 "식당 기준 좌표"(가로 800 × 세로 520)이며,
 * 클라이언트가 그릴 때 창 크기에 맞게 확대·축소한다.
 *
 * <pre>
 *  x:0      130                       615        800
 *    ┌──────┬─────────────────────────┬──────────┐ y:0
 *    │ 간판 │  벽 · 메뉴판             │  주방 후드│
 *    ├──────┼─────────────────────────┼──────────┤ y:96
 *    │ 입구 │   [T1]        [T2]      │  부엌     │
 *    │ 대기 │                         │ (조리대)  │
 *    │ 줄   │   [T3]        [T4]      │          │
 *    └──────┴─────────────────────────┴──────────┘ y:520
 * </pre>
 */
public final class StoreLayout {

    private StoreLayout() {
    }

    public static final int WIDTH = 800;
    public static final int HEIGHT = 520;
    public static final int WALL_HEIGHT = 96;
    public static final int ENTRANCE_RIGHT = 130;
    public static final int KITCHEN_LEFT = 615;

    /** 테이블 중심 좌표 (0~3번) */
    public static final int[] TABLE_X = {290, 470, 290, 470};
    public static final int[] TABLE_Y = {228, 228, 412, 412};

    /** 입구 대기줄 자리(손님 발 위치). 0번이 맨 앞 */
    public static final int[] QUEUE_X = {70, 70, 70};
    public static final int[] QUEUE_Y = {230, 330, 430};

    /** 캐릭터 시작 위치(발 위치) */
    public static final int PLAYER_START_X = 540;
    public static final int PLAYER_START_Y = 320;

    /** 캐릭터가 다닐 수 있는 범위(발 위치 기준) */
    public static final int MIN_X = 150;
    public static final int MAX_X = 588;
    public static final int MIN_Y = 140;
    public static final int MAX_Y = 505;

    /** 테이블과 상호작용할 수 있는 거리 */
    public static final int TABLE_REACH = 90;
    /** 이 x 이상이면 부엌 근처로 본다 */
    public static final int KITCHEN_REACH_X = 555;

    /** 캐릭터가 테이블을 뚫고 지나가지 못하게 막는 타원 크기 */
    private static final double TABLE_BLOCK_RX = 50;
    private static final double TABLE_BLOCK_RY = 24;

    public static int clampX(double x) {
        return (int) Math.round(Math.max(MIN_X, Math.min(MAX_X, x)));
    }

    public static int clampY(double y) {
        return (int) Math.round(Math.max(MIN_Y, Math.min(MAX_Y, y)));
    }

    /** 이 위치가 테이블에 막혀 있는지 */
    public static boolean blocked(double x, double y) {
        for (int i = 0; i < TABLE_X.length; i++) {
            double dx = (x - TABLE_X[i]) / TABLE_BLOCK_RX;
            double dy = (y - (TABLE_Y[i] + 6)) / TABLE_BLOCK_RY;
            if (dx * dx + dy * dy < 1.0) {
                return true;
            }
        }
        return false;
    }

    public static boolean nearKitchen(double x, double y) {
        return x >= KITCHEN_REACH_X;
    }

    /** 손이 닿는 가장 가까운 테이블 번호. 없으면 -1 */
    public static int nearestTable(double x, double y) {
        int best = -1;
        double bestDist = TABLE_REACH;
        for (int i = 0; i < TABLE_X.length; i++) {
            double d = Math.hypot(x - TABLE_X[i], y - TABLE_Y[i]);
            if (d <= bestDist) {
                bestDist = d;
                best = i;
            }
        }
        return best;
    }
}
