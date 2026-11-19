package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;
import bunsik.common.Message;
import bunsik.common.MessageType;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot;
import bunsik.common.StoreSnapshot.Hand;
import bunsik.common.TableState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;

/** 게임 규칙 (GameRoom 게임 루프 스레드 전용) */
public final class Store {

    private final CustomerSchedule schedule;
    private final Chef chef = new Chef();
    // An absent station is idle. A zero deadline means the food is ready to pick up.
    private final EnumMap<Menu, Long> cookEndsAt = new EnumMap<>(Menu.class);
    private final Table[] tables = new Table[GameConfig.TABLE_COUNT];
    private final Deque<Customer> queue = new ArrayDeque<>();
    private final List<Message> events = new ArrayList<>();

    private long startAt = -1;
    private long endAt = -1;
    private long nextArrivalAt;
    private int nextCustomerId = 1;

    private int gold;
    private int visited;
    private int served;
    private double satisfactionSum;
    private int satisfactionCount;
    private int angry;

    public Store(long seed) {
        this.schedule = new CustomerSchedule(seed);
        for (int i = 0; i < tables.length; i++) {
            tables[i] = new Table();
        }
    }

    public void begin(long now) {
        startAt = now;
        endAt = now + GameConfig.GAME_DURATION_MS;
        nextArrivalAt = now + GameConfig.ARRIVAL_GAP_MIN_MS;
    }

    private boolean running(long now) {
        return startAt >= 0 && now < endAt;
    }

    // ===== 시간 흐름 =====

    public void update(long now) {
        if (!running(now)) {
            return;
        }
        arriveCustomers(now);
        dropImpatientCustomers(now);
        seatCustomers();
        finishMeals(now);
        finishCooking(now);
    }

    private void arriveCustomers(long now) {
        while (now >= nextArrivalAt && nextArrivalAt < endAt) {
            // 수용 여부와 무관한 동일 순서 추첨 (두 식당 명단 일치용)
            Menu menu = schedule.nextMenu();
            int look = schedule.nextLook();
            long gap = schedule.nextGap();
            if (queue.size() < GameConfig.QUEUE_CAPACITY) {
                queue.addLast(new Customer(nextCustomerId++, menu, look, nextArrivalAt));
            } else {
                toast("줄이 너무 길어서 손님이 그냥 지나갔어요");
            }
            nextArrivalAt += gap;
        }
    }

    private void dropImpatientCustomers(long now) {
        Iterator<Customer> it = queue.iterator();
        while (it.hasNext()) {
            if (it.next().patience(now) <= 0) {
                it.remove();
                leaveAngry();
            }
        }
        for (Table t : tables) {
            boolean waiting = t.state == TableState.WAITING_ORDER || t.state == TableState.WAITING_FOOD;
            if (waiting && t.customer.patience(now) <= 0) {
                t.clear();
                leaveAngry();
            }
        }
    }

    private void leaveAngry() {
        visited++;
        angry++;
        satisfactionCount++;
        toast("손님이 기다리다 지쳐서 화를 내며 떠났어요...");
    }

    private void seatCustomers() {
        for (Table t : tables) {
            if (queue.isEmpty()) {
                return;
            }
            if (t.state == TableState.EMPTY) {
                t.customer = queue.pollFirst();
                t.state = TableState.WAITING_ORDER;
            }
        }
    }

    private void finishMeals(long now) {
        for (Table t : tables) {
            if (t.state == TableState.EATING && now >= t.customer.eatEndAt) {
                Customer c = t.customer;
                int price = c.menu.price;
                t.gold = price + (int) Math.round(price * GameConfig.TIP_RATE * c.patienceAtServe);
                t.customer = null;
                t.state = TableState.DIRTY;
                visited++;
            }
        }
    }

    private void finishCooking(long now) {
        for (var station : cookEndsAt.entrySet()) {
            if (station.getValue() != 0 && now >= station.getValue()) {
                station.setValue(0L);
                events.add(new Message(MessageType.COOK_DONE, station.getKey()));
            }
        }
    }

    // ===== 플레이어 행동 =====

    /** 행동 메시지 처리 (값 오류 시 IllegalArgumentException) */
    public void handle(Message m, long now) {
        switch (m.type()) {
            case MOVE -> onMove(m.getInt(0), m.getInt(1), m.getBool(2));
            case INTERACT -> onInteract(m.getInt(0), m.getInt(1), now);
            case COOK -> onCook(Menu.valueOf(m.get(0)), now);
            default -> {
            }
        }
    }

    private void onMove(int x, int y, boolean facingLeft) {
        chef.x = StoreLayout.clampX(x);
        chef.y = StoreLayout.clampY(y);
        chef.facingLeft = facingLeft;
    }

    private void onInteract(int x, int y, long now) {
        if (!running(now)) {
            return;
        }
        onMove(x, y, chef.facingLeft);

     // 인내심 0 손님 먼저 퇴장 (같은 틱의 update보다 행동이 먼저 처리되므로)
        dropImpatientCustomers(now);
        
        if (StoreLayout.nearKitchenTarget(StoreLayout.DISH_BIN, chef.x, chef.y)) {
            unloadDishes();
            return;
        }
        if (StoreLayout.nearKitchenTarget(StoreLayout.DISCARD, chef.x, chef.y)) {
            discardFood();
            return;
        }
        var station = StoreLayout.nearestStation(chef.x, chef.y);
        if (station != null) {
            pickUpFood(station.menu());
            return;
        }
        int index = nearestTable(chef.x, chef.y);
        if (index < 0) {
            return;
        }
        Table t = tables[index];
        int no = index + 1;
        switch (t.state) {
            case WAITING_ORDER -> {
                t.state = TableState.WAITING_FOOD;
                toast(no + "번 테이블 주문: " + t.customer.menu.label);
            }
            case WAITING_FOOD -> serve(t, no, now);
            case EATING -> toast("맛있게 먹는 중이에요");
            case DIRTY -> {
                int hand = chef.freeHand();
                if (hand < 0) {
                    toast("그릇을 들려면 한 손을 비워 주세요.");
                    return;
                }
                chef.hands[hand] = Hand.DISH;
                gold += t.gold;
                toast("그릇을 들었어요! +" + t.gold + " 골드");
                t.clear();
                seatCustomers();
            }
            case EMPTY -> {
            }
        }
    }

    private void pickUpFood(Menu menu) {
        if (cookEndsAt.getOrDefault(menu, -1L) != 0) return;
        int hand = chef.freeHand();
        if (hand < 0) {
            toast("음식을 들려면 한 손을 비워 주세요.");
            return;
        }
        chef.hands[hand] = new Hand(menu, false);
        cookEndsAt.remove(menu);
        toast(menu.label + "을(를) 집었어요.");
    }

    private void unloadDishes() {
        int count = 0;
        for (int i = 0; i < chef.hands.length; i++) {
            if (chef.hands[i].dish()) {
                chef.hands[i] = Hand.EMPTY;
                count++;
            }
        }
        if (count > 0) toast("설거지통에 그릇 " + count + "개를 넣었어요.");
    }

    private void discardFood() {
        for (int i = 0; i < chef.hands.length; i++) {
            Menu menu = chef.hands[i].menu();
            if (menu != null) {
                chef.hands[i] = Hand.EMPTY;
                toast(menu.label + "을(를) 버렸어요.");
                return;
            }
        }
    }

    private void serve(Table t, int no, long now) {
        Menu ordered = t.customer.menu;
        int hand = chef.handHolding(ordered);
        if (hand < 0) {
            toast("주문 메뉴가 달라요 (주문: " + ordered.label + ")");
            return;
        }
        Customer c = t.customer;
        c.patienceAtServe = c.patience(now);
        c.served = true;
        c.eatEndAt = now + GameConfig.EAT_MS;
        t.state = TableState.EATING;
        chef.hands[hand] = Hand.EMPTY;
        served++;
        satisfactionSum += c.patienceAtServe * 100.0;
        satisfactionCount++;
        toast(no + "번 테이블에 " + ordered.label + " 서빙 완료!");
    }

    private void onCook(Menu menu, long now) {
        if (!running(now) || menu == null) {
            return;
        }
        var station = StoreLayout.nearestStation(chef.x, chef.y);
        if (station == null || station.menu() != menu) {
            toast("해당 메뉴의 조리대 앞에서만 요리할 수 있어요.");
        } else if (cookEndsAt.containsKey(menu)) {
            toast("조리대에 음식이 있어요.");
        } else {
            cookEndsAt.put(menu, now + menu.cookTimeMs);
        }
    }

    // ===== 결과 조회 =====

    private void toast(String text) {
        events.add(new Message(MessageType.TOAST, text));
    }

    /** 쌓인 개인 메시지 꺼내기 */
    public List<Message> drainEvents() {
        List<Message> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    /** 만족도 0~100 (떠난 손님 없으면 -1) */
    public int satisfaction() {
        if (satisfactionCount == 0) {
            return -1;
        }
        return (int) Math.round(satisfactionSum / satisfactionCount);
    }

    public int gold() {
        return gold;
    }

    public int served() {
        return served;
    }

    public int visited() {
        return visited;
    }

    public TableState tableState(int index) {
        return tables[index].state;
    }

    /** 테이블 손님의 주문 메뉴 (손님 없으면 null) */
    public Menu orderedMenu(int index) {
        Customer c = tables[index].customer;
        return c == null ? null : c.menu;
    }

    /** 테이블 손님의 남은 인내심 0~1000 (손님 없으면 -1) */
    public int tablePatience(int index, long now) {
        Customer c = tables[index].customer;
        return c == null ? -1 : (int) Math.round(c.patience(now) * 1000);
    }

    public int angryCount() {
        return angry;
    }

    public int queueSize() {
        return queue.size();
    }

    public int playerX() {
        return chef.x;
    }

    public int playerY() {
        return chef.y;
    }

    /** 범위 안 가장 가까운 테이블 번호 (없으면 -1) */
    private static int nearestTable(int x, int y) {
        int best = -1;
        double bestDist = StoreLayout.TABLE_REACH;
        for (int i = 0; i < StoreLayout.TABLES.size(); i++) {
            StoreLayout.Position p = StoreLayout.TABLES.get(i);
            double d = Math.hypot(x - p.x(), y - p.y());
            if (d < bestDist) {
                best = i;
                bestDist = d;
            }
        }
        return best;
    }

    public StoreSnapshot snapshot(int slot, long now) {
        long timeLeftMs = startAt < 0 ? GameConfig.GAME_DURATION_MS : Math.max(0, endAt - now);
        var stations = new EnumMap<Menu, Long>(Menu.class);
        cookEndsAt.forEach((menu, endsAt) -> stations.put(menu, endsAt == 0 ? 0
                : Math.clamp(endsAt - now, 1L, (long) menu.cookTimeMs)));

        List<StoreSnapshot.TableView> tableViews = new ArrayList<>();
        for (Table t : tables) {
            Menu menu = null;
            int patience = -1;
            int appearance = 0;
            if (t.customer != null) {
                appearance = t.customer.look;
                // 주문 접수 후에만 메뉴 공개
                if (t.state == TableState.WAITING_FOOD || t.state == TableState.EATING) {
                    menu = t.customer.menu;
                }
                if (t.state == TableState.WAITING_ORDER || t.state == TableState.WAITING_FOOD) {
                    patience = (int) Math.round(t.customer.patience(now) * 1000);
                }
            }
            tableViews.add(new StoreSnapshot.TableView(t.state, menu, patience, t.gold, appearance));
        }

        List<StoreSnapshot.CustomerView> queueViews = new ArrayList<>();
        for (Customer c : queue) {
            queueViews.add(new StoreSnapshot.CustomerView((int) Math.round(c.patience(now) * 1000), c.look));
        }

        // STATE 순서: 다녀간 손님(visited) → 판매 수(served)
        return new StoreSnapshot(slot, timeLeftMs, gold, visited, served, satisfaction(),
                chef.x, chef.y, chef.facingLeft, List.of(chef.hands), stations,
                tableViews, queueViews);
    }
}
