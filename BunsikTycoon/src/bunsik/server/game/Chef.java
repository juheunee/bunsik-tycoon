package bunsik.server.game;

import bunsik.common.Menu;
import bunsik.common.StoreLayout;

/** 플레이어의 서버 쪽 상태 */
final class Chef {

    int x = StoreLayout.SPAWN.x();
    int y = StoreLayout.SPAWN.y();
    boolean facingLeft;
    Menu held;
    Menu cooking;
    long cookEndAt;
}