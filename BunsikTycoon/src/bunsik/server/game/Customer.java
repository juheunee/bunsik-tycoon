package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;

final class Customer {

    final int id;
    final Menu menu;
    final int look;
    final long arrivedAt;

    boolean served;
    double patienceAtServe;
    long eatEndAt;

    Customer(int id, Menu menu, int look, long arrivedAt) {
        this.id = id;
        this.menu = menu;
        this.look = look;
        this.arrivedAt = arrivedAt;
    }

    /** 남은 인내심 (0.0~1.0, 서빙 후 고정) */
    double patience(long now) {
        if (served) {
            return patienceAtServe;
        }
        double p = 1.0 - (now - arrivedAt) / (double) GameConfig.PATIENCE_MS;
        return Math.max(0.0, Math.min(1.0, p));
    }
}