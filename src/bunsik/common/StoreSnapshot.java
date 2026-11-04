package bunsik.common;

import java.util.ArrayList;
import java.util.List;

/**
 * 식당 하나의 "지금 이 순간" 상태를 통째로 담은 사진(스냅샷).
 *
 * <p>서버는 0.1초마다 모든 식당의 스냅샷을 STATE 메시지로 보내고, 클라이언트는 받은 그대로 그리기만 한다.
 * 사건(손님 등장 등)만 따로 보내면 메시지 하나가 어긋났을 때 계속 틀어진 채로 가지만,
 * 매번 전체 상태를 보내면 다음 스냅샷이 자동으로 바로잡아 준다.
 *
 * <p>STATE 메시지 값 순서:
 * <pre>
 * 0 슬롯 | 1 남은시간ms | 2 골드 | 3 다녀간손님 | 4 판매수 | 5 만족도(0~100, 아직 없으면 -1)
 * 6 캐릭터x | 7 캐릭터y | 8 왼쪽보기 | 9 들고있는메뉴 | 10 조리중메뉴 | 11 조리남은ms | 12 조리전체ms
 * 13 테이블들  "상태:메뉴:인내심(0~1000, 없으면 -1):골드:생김새" 를 쉼표로 이음
 * 14 대기줄    "인내심:생김새" 를 쉼표로 이음 (비었으면 -)
 * </pre>
 */
public final class StoreSnapshot {

    /** 테이블 하나의 상태 */
    public static final class TableView {
        public TableState state = TableState.EMPTY;
        /** 주문을 받은 뒤에만 보인다(주문 전에는 null) */
        public Menu menu;
        /** 0~1000. 인내심 바가 없으면 -1 */
        public int patience = -1;
        /** 빈 그릇과 함께 놓인 골드 */
        public int gold;
        /** 손님 생김새 번호(옷·머리 색) */
        public int look;
    }

    /** 입구에 줄 선 손님 */
    public static final class CustomerView {
        public int patience;
        public int look;
    }

    public int slot;
    public long timeLeftMs;
    public int gold;
    public int visited;
    public int served;
    /** 0~100, 아직 계산할 손님이 없으면 -1 */
    public int satisfaction = -1;
    public int playerX = StoreLayout.PLAYER_START_X;
    public int playerY = StoreLayout.PLAYER_START_Y;
    public boolean facingLeft;
    public Menu held;
    public Menu cooking;
    public int cookRemainMs;
    public int cookTotalMs;
    public TableView[] tables = new TableView[0];
    public CustomerView[] queue = new CustomerView[0];

    public Message toMessage() {
        StringBuilder t = new StringBuilder();
        for (int i = 0; i < tables.length; i++) {
            TableView v = tables[i];
            if (i > 0) {
                t.append(',');
            }
            t.append(v.state.name()).append(':').append(Menu.code(v.menu)).append(':')
                    .append(v.patience).append(':').append(v.gold).append(':').append(v.look);
        }
        StringBuilder q = new StringBuilder();
        for (int i = 0; i < queue.length; i++) {
            if (i > 0) {
                q.append(',');
            }
            q.append(queue[i].patience).append(':').append(queue[i].look);
        }
        return new Message(MessageType.STATE, slot, timeLeftMs, gold, visited, served, satisfaction,
                playerX, playerY, facingLeft, Menu.code(held), Menu.code(cooking), cookRemainMs, cookTotalMs,
                tables.length == 0 ? "-" : t.toString(), queue.length == 0 ? "-" : q.toString());
    }

    public static StoreSnapshot fromMessage(Message m) {
        StoreSnapshot s = new StoreSnapshot();
        s.slot = m.getInt(0);
        s.timeLeftMs = m.getLong(1);
        s.gold = m.getInt(2);
        s.visited = m.getInt(3);
        s.served = m.getInt(4);
        s.satisfaction = m.getInt(5);
        s.playerX = m.getInt(6);
        s.playerY = m.getInt(7);
        s.facingLeft = m.getBool(8);
        s.held = Menu.fromCode(m.get(9));
        s.cooking = Menu.fromCode(m.get(10));
        s.cookRemainMs = m.getInt(11);
        s.cookTotalMs = m.getInt(12);

        List<TableView> tables = new ArrayList<>();
        if (!m.get(13).equals("-")) {
            for (String part : m.get(13).split(",")) {
                String[] f = part.split(":");
                TableView v = new TableView();
                v.state = TableState.valueOf(f[0]);
                v.menu = Menu.fromCode(f[1]);
                v.patience = Integer.parseInt(f[2]);
                v.gold = Integer.parseInt(f[3]);
                v.look = Integer.parseInt(f[4]);
                tables.add(v);
            }
        }
        s.tables = tables.toArray(new TableView[0]);

        List<CustomerView> queue = new ArrayList<>();
        if (!m.get(14).equals("-")) {
            for (String part : m.get(14).split(",")) {
                String[] f = part.split(":");
                CustomerView c = new CustomerView();
                c.patience = Integer.parseInt(f[0]);
                c.look = Integer.parseInt(f[1]);
                queue.add(c);
            }
        }
        s.queue = queue.toArray(new CustomerView[0]);
        return s;
    }
}
