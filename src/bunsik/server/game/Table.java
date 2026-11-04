package bunsik.server.game;

import bunsik.common.TableState;

/** 테이블 한 개. 상태 흐름은 {@link TableState} 참고. */
final class Table {

    TableState state = TableState.EMPTY;
    Customer customer;
    /** DIRTY 상태일 때 테이블 위에 놓인 골드 */
    int gold;

    void clear() {
        state = TableState.EMPTY;
        customer = null;
        gold = 0;
    }
}
