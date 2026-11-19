package bunsik.server.game;

import bunsik.common.Menu;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot.Hand;

/** 플레이어의 서버 쪽 상태 */
final class Chef {

    int x = StoreLayout.SPAWN.x();
    int y = StoreLayout.SPAWN.y();
    boolean facingLeft;
    final Hand[] hands = {Hand.EMPTY, Hand.EMPTY};

    int freeHand() {
        for (int i = 0; i < hands.length; i++) {
            if (hands[i].empty()) return i;
        }
        return -1;
    }

    int handHolding(Menu menu) {
        for (int i = 0; i < hands.length; i++) {
            if (hands[i].menu() == menu) return i;
        }
        return -1;
    }
}
