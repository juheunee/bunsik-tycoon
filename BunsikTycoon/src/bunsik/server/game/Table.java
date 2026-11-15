package bunsik.server.game;

import bunsik.common.TableState;

final class Table {

    TableState state = TableState.EMPTY;
    Customer customer;
    int gold;

    void clear() {
        state = TableState.EMPTY;
        customer = null;
        gold = 0;
    }
}