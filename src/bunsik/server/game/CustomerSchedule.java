package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;

import java.util.Random;

/**
 * 손님이 언제, 무엇을 먹으러 올지 정하는 "예약 명단".
 *
 * <p>경쟁 모드에서는 두 식당에 같은 seed(씨앗값)를 주어 똑같은 순서·시간·메뉴의 손님이 오게 한다.
 * 그래야 운이 아니라 실력으로 승부가 난다. 같은 순서를 유지하려면 손님을 받든 못 받든
 * 매번 menu → look → gap 순서로 똑같이 뽑아야 한다.
 */
final class CustomerSchedule {

    private static final int LOOK_COUNT = 24;
    private final Random random;

    CustomerSchedule(long seed) {
        this.random = new Random(seed);
    }

    Menu nextMenu() {
        Menu[] all = Menu.values();
        return all[random.nextInt(all.length)];
    }

    int nextLook() {
        return random.nextInt(LOOK_COUNT);
    }

    long nextGap() {
        return GameConfig.ARRIVAL_GAP_MIN_MS
                + random.nextInt(GameConfig.ARRIVAL_GAP_MAX_MS - GameConfig.ARRIVAL_GAP_MIN_MS + 1);
    }
}
