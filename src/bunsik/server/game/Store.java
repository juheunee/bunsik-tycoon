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

/**
 * 식당 하나의 게임 규칙 전부(손님 등장, 착석, 주문, 조리, 서빙, 식사, 청소, 점수).
 *
 * <p>화면(Swing)도 소켓도 모르는 순수한 자바 코드다. 그래서 서버가 그대로 쓸 수 있고,
 * 규칙만 따로 떼어 확인하기도 쉽다. 이 클래스는 반드시 {@code GameRoom}의 게임 루프 스레드 하나에서만
 * 호출된다(그래서 synchronized 없이도 안전하다).
 */
public final class Store {

    private final CustomerSchedule schedule;
    private final Chef chef = new Chef();
    private final Table[] tables = new Table[GameConfig.TABLE_COUNT];
    private final Deque<Customer> queue = new ArrayDeque<>();
    /** 이 식당 주인에게만 보낼 메시지(안내 문구, 부엌 열기, 요리 완료) */
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

    public Store(long seed) {
        this.schedule = new CustomerSchedule(seed);
        for (int i = 0; i < tables.length; i++) {
            tables[i] = new Table();
        }
    }

    /** 카운트다운이 끝난 순간 호출. 이때부터 시간이 흐른다. */
    public void begin(long now) {
        startAt = now;
        endAt = now + GameConfig.GAME_DURATION_MS;
        nextArrivalAt = now + GameConfig.FIRST_ARRIVAL_MS;
    }

    private boolean running(long now) {
        return startAt >= 0 && now < endAt;
    }

    // ------------------------------------------------------------------
    // 시간의 흐름 (게임 시계가 0.1초마다 호출)
    // ------------------------------------------------------------------

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

    /** 1. 예약 명단에 따라 손님이 입구에 도착 */
    private void arriveCustomers(long now) {
        while (now >= nextArrivalAt && nextArrivalAt < endAt) {
            // 두 식당의 명단이 어긋나지 않도록 손님을 받든 못 받든 항상 같은 순서로 뽑는다.
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

    /** 2. 인내심이 바닥난 손님은 화를 내며 떠난다(대기줄·테이블 모두) */
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
        satisfactionCount++; // 만족도 0점으로 기록
        toast("손님이 기다리다 지쳐서 화를 내며 떠났어요...");
    }

    /** 3. 빈 테이블(EMPTY)이 있으면 줄 맨 앞 손님이 앉는다. 빈 그릇이 있는 테이블(DIRTY)에는 앉지 않는다. */
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

    /** 4. 다 먹은 손님은 빈 그릇과 골드를 두고 떠난다 */
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

    /** 5. 조리 시간이 지나면 음식이 완성되어 캐릭터가 들게 된다 */
    private void finishCooking(long now) {
        if (chef.cooking != null && now >= chef.cookEndAt) {
            chef.held = chef.cooking;
            chef.cooking = null;
            events.add(new Message(MessageType.COOK_DONE, Menu.code(chef.held)));
        }
    }

    // ------------------------------------------------------------------
    // 플레이어 행동 (클라이언트 요청을 서버가 확인한 뒤 적용)
    // ------------------------------------------------------------------

    public void onMove(int x, int y, boolean facingLeft) {
        chef.x = StoreLayout.clampX(x);
        chef.y = StoreLayout.clampY(y);
        chef.facingLeft = facingLeft;
    }

    /** 스페이스 키. 어디 근처인지, 테이블이 어떤 상태인지에 따라 하는 일이 달라진다. */
    public void onInteract(int x, int y, long now) {
        if (!running(now)) {
            return;
        }
        onMove(x, y, chef.facingLeft);

        if (StoreLayout.nearKitchen(chef.x, chef.y)) {
            interactKitchen();
            return;
        }
        int index = StoreLayout.nearestTable(chef.x, chef.y);
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
                // 빈 테이블: 할 일 없음
            }
        }
    }

    private void interactKitchen() {
        if (chef.cooking != null) {
            events.add(new Message(MessageType.OPEN_KITCHEN)); // 조리 진행 상황 다시 보기
        } else if (chef.held != null) {
            toast("들고 있던 " + chef.held.withObjectParticle() + " 버렸어요");
            chef.held = null;
        } else {
            events.add(new Message(MessageType.OPEN_KITCHEN));
        }
    }

    private void serve(Table t, int no, long now) {
        Menu ordered = t.customer.menu;
        if (chef.held == null) {
            toast(no + "번 손님은 " + ordered.withObjectParticle() + " 기다리고 있어요. 부엌에서 만들어 주세요");
            return;
        }
        if (chef.held != ordered) {
            toast("이 손님은 " + ordered.withObjectParticle() + " 주문했어요");
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

    /** 접시에 메뉴를 놓음 = 요리 시작 */
    public void onCook(Menu menu, long now) {
        if (!running(now) || menu == null) {
            return;
        }
        if (!StoreLayout.nearKitchen(chef.x, chef.y)) {
            toast("부엌 가까이에서만 요리할 수 있어요");
        } else if (chef.cooking != null) {
            toast("이미 요리 중이에요");
        } else if (chef.held != null) {
            toast("이미 음식을 들고 있어요");
        } else {
            chef.cooking = menu;
            chef.cookEndAt = now + menu.cookMs;
        }
    }

    // ------------------------------------------------------------------
    // 결과와 스냅샷
    // ------------------------------------------------------------------

    private void toast(String text) {
        events.add(new Message(MessageType.TOAST, text));
    }

    /** 쌓인 개인 메시지를 꺼내고 비운다 */
    public List<Message> drainEvents() {
        List<Message> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    /** 0~100. 아직 떠난 손님이 없으면 -1 */
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

    public StoreSnapshot snapshot(int slot, long now) {
        StoreSnapshot s = new StoreSnapshot();
        s.slot = slot;
        s.timeLeftMs = startAt < 0 ? GameConfig.GAME_DURATION_MS : Math.max(0, endAt - now);
        s.gold = gold;
        s.visited = visited;
        s.served = served;
        s.satisfaction = satisfaction();
        s.playerX = chef.x;
        s.playerY = chef.y;
        s.facingLeft = chef.facingLeft;
        s.held = chef.held;
        s.cooking = chef.cooking;
        if (chef.cooking != null) {
            s.cookRemainMs = (int) Math.max(0, chef.cookEndAt - now);
            s.cookTotalMs = chef.cooking.cookMs;
        }

        s.tables = new StoreSnapshot.TableView[tables.length];
        for (int i = 0; i < tables.length; i++) {
            Table t = tables[i];
            StoreSnapshot.TableView v = new StoreSnapshot.TableView();
            v.state = t.state;
            v.gold = t.gold;
            if (t.customer != null) {
                v.look = t.customer.look;
                if (t.state == TableState.WAITING_FOOD || t.state == TableState.EATING) {
                    v.menu = t.customer.menu; // 주문을 받은 뒤에만 공개
                }
                if (t.state != TableState.EATING) {
                    v.patience = (int) Math.round(t.customer.patience(now) * 1000);
                }
            }
            s.tables[i] = v;
        }

        s.queue = new StoreSnapshot.CustomerView[queue.size()];
        int i = 0;
        for (Customer c : queue) {
            StoreSnapshot.CustomerView v = new StoreSnapshot.CustomerView();
            v.patience = (int) Math.round(c.patience(now) * 1000);
            v.look = c.look;
            s.queue[i++] = v;
        }
        return s;
    }
}
