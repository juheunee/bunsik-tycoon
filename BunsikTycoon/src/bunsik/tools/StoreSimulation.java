package bunsik.tools;

import bunsik.common.GameConfig;
import bunsik.common.Message;
import bunsik.common.TableState;
import bunsik.server.game.Store;

/** GUI 없이 손님 흐름 확인용 시뮬레이션 (가상 시계, 행동 없음) */
public final class StoreSimulation {

    private static final long TICK_MS = 100;

    private StoreSimulation() {
    }

    public static void main(String[] args) {
        Store store = new Store(42);
        store.begin(0);

        for (long now = 0; now <= GameConfig.GAME_DURATION_MS; now += TICK_MS) {
            store.update(now);
            for (Message e : store.drainEvents()) {
                System.out.printf("[%5.1f초] %s%n", now / 1000.0, e.encode());
            }
            if (now % 5000 == 0) {
                System.out.printf("[%5.1f초] 대기줄 %d명 | %s%n", now / 1000.0, store.queueSize(), tables(store, now));
            }
        }
        System.out.println("----------------------------------------");
        System.out.println("다녀간 손님 " + store.visited() + " / 화내며 떠난 손님 " + store.angryCount());
    }

    private static String tables(Store store, long now) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < GameConfig.TABLE_COUNT; i++) {
            TableState st = store.tableState(i);
            sb.append(i + 1).append("번 ").append(st);
            if (st == TableState.WAITING_ORDER) {
                sb.append("(인내심 ").append(store.tablePatience(i, now) / 10).append("%)");
            }
            sb.append("  ");
        }
        return sb.toString();
    }
}