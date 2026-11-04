package bunsik.common;

/**
 * 게임 밸런스 숫자들을 한곳에 모은 클래스.
 *
 * <p>"10초"처럼 숫자를 코드 곳곳에 흩어 쓰면 밸런스를 바꿀 때 전부 찾아야 하므로, 여기만 고치면 되도록 모았다.
 */
public final class GameConfig {

    private GameConfig() {
    }

    /** 서버 게임 시계가 째깍거리는 간격(밀리초). 이 간격마다 상태를 계산하고 STATE를 보낸다. */
    public static final int TICK_MS = 100;
    /** 한 판의 제한 시간 */
    public static final int GAME_DURATION_MS = 180_000;
    /** 시작 전 카운트다운(초) */
    public static final int COUNTDOWN_SEC = 3;

    /** 테이블 수 */
    public static final int TABLE_COUNT = 4;
    /** 입구에서 줄 설 수 있는 최대 손님 수. 꽉 차면 새 손님은 그냥 지나간다. */
    public static final int QUEUE_CAPACITY = 3;

    /** 게임 시작 후 첫 손님이 오기까지 */
    public static final int FIRST_ARRIVAL_MS = 1_500;
    /** 손님 등장 간격(최소~최대 사이 무작위) */
    public static final int ARRIVAL_GAP_MIN_MS = 4_000;
    public static final int ARRIVAL_GAP_MAX_MS = 7_000;

    /** 손님 인내심: 입구 도착부터 서빙까지 하나의 바가 이 시간 동안 줄어든다. */
    public static final int PATIENCE_MS = 45_000;
    /** 음식을 받은 뒤 먹는 시간. 끝나면 빈 그릇과 골드를 두고 떠난다. */
    public static final int EAT_MS = 5_000;
    /** 팁 비율: 골드 = 가격 + 가격 × TIP_RATE × (서빙 시점 남은 인내심 비율) */
    public static final double TIP_RATE = 0.5;

    /** 캐릭터 이동 속도(초당 픽셀, 식당 기준 좌표) */
    public static final double PLAYER_SPEED = 230;
    /** 클라이언트가 위치를 서버에 알리는 최소 간격 */
    public static final int MOVE_SEND_INTERVAL_MS = 100;

    /** 대기실 최대 인원 */
    public static final int VERSUS_PLAYERS = 2;
    /** 닉네임 최대 글자 수 */
    public static final int NICKNAME_MAX = 10;
}
