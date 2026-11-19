package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;
import bunsik.common.Message;
import bunsik.common.MessageType;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot.Hand;
import bunsik.common.TableState;
import java.util.List;

public final class StoreKitchenTest {
    public static void main(String[] args) {
        testCookingAndServing();
        testMixedHandsAtDishBin();
        System.out.println("StoreKitchenTest: PASS");
    }

    private static void testCookingAndServing() {
        Store store = new Store(0);
        store.begin(0);
        long now = 35000;
        for (long tick = 1000; tick <= now; tick += 1000) store.update(tick);
        Menu first = store.orderedMenu(0);
        Menu second = store.orderedMenu(1);
        check(first != second && store.queueSize() >= 2, "seed provides different orders and a waiting line");
        interact(store, StoreLayout.TABLES.get(0), now);
        interact(store, StoreLayout.TABLES.get(1), now);
        cook(store, first, now);
        cook(store, first, now + 10);
        cook(store, second, now + 20);
        var cooking = store.snapshot(0, now + 20).stations();
        check(cooking.get(first) == first.cookTimeMs - 20 && cooking.get(second) == second.cookTimeMs,
                "duplicate does not restart the first timer, second menu cooks independently");

        now += Math.max(first.cookTimeMs, second.cookTimeMs) + 20;
        store.update(now);
        check(store.snapshot(0, now).hands().equals(List.of(Hand.EMPTY, Hand.EMPTY)), "cooking does not fill hands");
        cook(store, first, now);
        check(store.snapshot(0, now).stations().get(first) == 0, "ready food occupies the station");
        interact(store, station(first), now);
        interact(store, station(second), now);
        check(store.snapshot(0, now).hands().equals(List.of(new Hand(first, false), new Hand(second, false))),
                "two independent pickups");

        interact(store, StoreLayout.TABLES.get(1), now);
        check(store.snapshot(0, now).hands().equals(List.of(new Hand(first, false), Hand.EMPTY)),
                "orders select the matching hand");
        interact(store, StoreLayout.TABLES.get(0), now);
        check(store.served() == 2, "both meals served");
        now += GameConfig.EAT_MS;
        store.update(now);
        check(store.tableState(0) == TableState.DIRTY && store.tableState(1) == TableState.DIRTY,
                "customers cannot sit at dirty tables");
        interact(store, StoreLayout.TABLES.get(0), now);
        interact(store, StoreLayout.TABLES.get(1), now);
        check(store.tableState(0) == TableState.WAITING_ORDER && store.tableState(1) == TableState.WAITING_ORDER,
                "waiting customers can sit immediately after lifting dishes");
        check(store.gold() >= first.price + second.price, "cleaning pays each table");
        check(store.snapshot(0, now).hands().equals(List.of(Hand.DISH, Hand.DISH)), "both hands carry dishes");

        cook(store, first, now);
        now += first.cookTimeMs;
        store.update(now);
        interact(store, station(first), now);
        check(store.snapshot(0, now).stations().get(first) == 0, "full hands leave ready food on the station");
        interact(store, StoreLayout.DISH_BIN, now);
        check(store.snapshot(0, now).hands().equals(List.of(Hand.EMPTY, Hand.EMPTY)), "dish bin empties both hands");
    }

    private static void testMixedHandsAtDishBin() {
        Store store = new Store(42);
        store.begin(0);
        long now = GameConfig.ARRIVAL_GAP_MIN_MS;
        store.update(now);
        Menu order = store.orderedMenu(0);
        Menu extra = order == Menu.GIMBAP ? Menu.SUNDAE : Menu.GIMBAP;
        interact(store, StoreLayout.TABLES.getFirst(), now);
        cook(store, order, now);
        cook(store, extra, now);
        now += Math.max(order.cookTimeMs, extra.cookTimeMs);
        store.update(now);
        interact(store, station(order), now);
        interact(store, station(extra), now);
        interact(store, StoreLayout.TABLES.getFirst(), now);
        now += GameConfig.EAT_MS;
        store.update(now);
        interact(store, StoreLayout.TABLES.getFirst(), now);
        check(store.snapshot(0, now).hands().equals(List.of(Hand.DISH, new Hand(extra, false))), "mixed carry");
        interact(store, StoreLayout.DISH_BIN, now);
        check(store.snapshot(0, now).hands().equals(List.of(Hand.EMPTY, new Hand(extra, false))),
                "dish bin leaves food in the same hand");
    }

    private static void cook(Store store, Menu menu, long now) {
        var point = station(menu);
        store.handle(new Message(MessageType.MOVE, point.x(), point.y(), false), now);
        store.handle(new Message(MessageType.COOK, menu), now);
    }

    private static StoreLayout.Position station(Menu menu) {
        return StoreLayout.COOK_STATIONS.stream().filter(station -> station.menu() == menu)
                .findFirst().orElseThrow().position();
    }

    private static void interact(Store store, StoreLayout.Position point, long now) {
        store.handle(new Message(MessageType.INTERACT, point.x(), point.y()), now);
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
