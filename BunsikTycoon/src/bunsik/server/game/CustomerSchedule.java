package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;

import java.util.Random;

/** 손님 등장 순서, 메뉴, 간격 (같은 seed → 같은 손님 명단) */
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