package bunsik.server.game;

import bunsik.common.GameConfig;
import bunsik.common.Menu;
import bunsik.common.Message;
import bunsik.common.MessageType;
import bunsik.common.StoreLayout;
import bunsik.common.TableState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** 식당 하나의 게임 규칙 */
public final class Store {

    private final CustomerSchedule schedule;
    private final Chef chef = new Chef();
    private final Table[] tables = new Table[GameConfig.TABLE_COUNT];
    private final Deque<Customer> queue = new ArrayDeque<>();
    private final List<Message> events = new ArrayList<>();

    private long startAt = -1;
    private long endAt = -1;
    private long nextArrivalAt;
    private int nextCustomerId = 1;
    private int visited;
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

    public void update(long now) {
        if (!running(now)) {
            return;
        }
        arriveCustomers(now);
        dropImpatientCustomers(now);
        seatCustomers();
    }

    private void arriveCustomers(long now) {
        while (now >= nextArrivalAt && nextArrivalAt < endAt) {
            /** 수용 여부와 무관한 동일 순서 추첨 (두 식당 명단 일치용) **/
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
        toast("손님이 기다리다 지쳐서 화를 내며 떠났어요...");
    }

    /** 빈 테이블(EMPTY)에만 착석. 빈 그릇이 있는 테이블(DIRTY)은 제외 */
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

    public void onMove(int x, int y, boolean facingLeft) {
        chef.x = StoreLayout.clampX(x);
        chef.y = StoreLayout.clampY(y);
        chef.facingLeft = facingLeft;
    }

    private void toast(String text) {
        events.add(new Message(MessageType.TOAST, text));
    }

    /** 쌓인 개인 메시지 꺼내기 */
    public List<Message> drainEvents() {
        List<Message> out = new ArrayList<>(events);
        events.clear();
        return out;
    }

    public TableState tableState(int index) {
        return tables[index].state;
    }

    /** 테이블 손님의 남은 인내심 0~1000 (손님 없으면 -1) */
    public int tablePatience(int index, long now) {
        Customer c = tables[index].customer;
        return c == null ? -1 : (int) Math.round(c.patience(now) * 1000);
    }

    public int queueSize() {
        return queue.size();
    }

    public int visited() {
        return visited;
    }

    public int angryCount() {
        return angry;
    }
}