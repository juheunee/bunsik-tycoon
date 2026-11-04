package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;

/** 손님 한 명. 무엇을 먹을지는 도착할 때 정해지지만, 주문을 받기 전까지 화면에는 보이지 않는다. */
final class Customer {

    final int id;
    final Menu menu;
    final int look;
    /** 입구에 도착한 시각. 인내심은 이때부터 줄어든다. */
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

    /**
     * 남은 인내심(0.0~1.0). 입구 도착부터 서빙까지 하나의 바가 계속 줄어들고,
     * 서빙된 뒤에는 그 순간의 값에서 멈춘다(화면에서는 바가 사라짐).
     */
    double patience(long now) {
        if (served) {
            return patienceAtServe;
        }
        double p = 1.0 - (now - arrivedAt) / (double) GameConfig.PATIENCE_MS;
        return Math.max(0.0, Math.min(1.0, p));
    }
}
