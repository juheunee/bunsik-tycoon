package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;
import bunsik.common.Message;
import bunsik.common.MessageType;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot;
import bunsik.common.TableState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** 게임 규칙 (GameRoom 게임 루프 스레드 전용) */
public final class Store {

    private static final int KITCHEN_REACH_X = 85;
    private static final int KITCHEN_REACH_Y = 150;
    private static final int TABLE_REACH = 70;

    private final CustomerSchedule schedule;
    private final Chef chef = new Chef();
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
        if (chef.cooking != null && now >= chef.cookEndAt) {
            chef.held = chef.cooking;
            chef.cooking = null;
            events.add(new Message(MessageType.COOK_DONE, chef.held.name()));
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

        if (nearKitchen(chef.x, chef.y)) {
            interactKitchen();
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
                gold += t.gold;
                toast("청소 완료! +" + t.gold + " 골드");
                t.clear();
            }
            case EMPTY -> {
            }
        }
    }

    private void interactKitchen() {
        if (chef.cooking != null) {
            events.add(new Message(MessageType.OPEN_KITCHEN));
        } else if (chef.held != null) {
            toast("들고 있던 음식(" + chef.held.label + ")을 버렸어요");
            chef.held = null;
        } else {
            events.add(new Message(MessageType.OPEN_KITCHEN));
        }
    }

    private void serve(Table t, int no, long now) {
        Menu ordered = t.customer.menu;
        if (chef.held == null) {
            toast(no + "번 손님 주문: " + ordered.label + " (부엌에서 만들어 주세요)");
            return;
        }
        if (chef.held != ordered) {
            toast("주문 메뉴가 달라요 (주문: " + ordered.label + ")");
            return;
        }
        Customer c = t.customer;
        c.patienceAtServe = c.patience(now);
        c.served = true;
        c.eatEndAt = now + GameConfig.EAT_MS;
        t.state = TableState.EATING;
        chef.held = null;
        served++;
        satisfactionSum += c.patienceAtServe * 100.0;
        satisfactionCount++;
        toast(no + "번 테이블에 " + ordered.label + " 서빙 완료!");
    }

    private void onCook(Menu menu, long now) {
        if (!running(now) || menu == null) {
            return;
        }
        if (!nearKitchen(chef.x, chef.y)) {
            toast("부엌 가까이에서만 요리할 수 있어요");
        } else if (chef.cooking != null) {
            toast("이미 요리 중이에요");
        } else if (chef.held != null) {
            toast("이미 음식을 들고 있어요");
        } else {
            chef.cooking = menu;
            chef.cookEndAt = now + menu.cookTimeMs;
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

    public Menu heldMenu() {
        return chef.held;
    }

    public Menu cookingMenu() {
        return chef.cooking;
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

    /** 부엌 상호작용 범위 (StoreRenderer 안내 표시와 같은 범위) */
    private static boolean nearKitchen(int x, int y) {
        return Math.abs(x - StoreLayout.KITCHEN.x()) < KITCHEN_REACH_X
                && Math.abs(y - StoreLayout.KITCHEN.y()) < KITCHEN_REACH_Y;
    }

    /** 범위 안 가장 가까운 테이블 번호 (없으면 -1) */
    private static int nearestTable(int x, int y) {
        int best = -1;
        double bestDist = TABLE_REACH;
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
        long cookRemainMs = 0;
        long cookTotalMs = 0;
        if (chef.cooking != null) {
            cookTotalMs = chef.cooking.cookTimeMs;
            cookRemainMs = Math.min(cookTotalMs, Math.max(0, chef.cookEndAt - now));
        }

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
                chef.x, chef.y, chef.facingLeft, chef.held, chef.cooking, cookRemainMs, cookTotalMs,
                tableViews, queueViews);
    }
}