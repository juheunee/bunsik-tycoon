package bunsik.server.game;

import bunsik.common.Menu;
import bunsik.common.StoreLayout;

/** 플레이어 캐릭터(사장님)의 서버 쪽 상태. */
final class Chef {

    int x = StoreLayout.PLAYER_START_X;
    int y = StoreLayout.PLAYER_START_Y;
    boolean facingLeft;
    /** 들고 있는 음식. 한 번에 하나만 들 수 있다. */
    Menu held;
    /** 지금 조리 중인 메뉴 */
    Menu cooking;
    long cookEndAt;
}
