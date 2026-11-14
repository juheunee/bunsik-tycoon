package bunsik.common;

import java.util.List;
import java.util.stream.Collectors;

/** README 7-4 STATE 계약. 서버의 가변 상태를 화면과 공유하지 않는다. */
public record StoreSnapshot(int slot, long timeLeftMs, int gold, int servedCustomers,
        int sales, int satisfaction, int chefX, int chefY, boolean facingLeft,
        Menu holding, Menu cooking, long cookRemainMs, long cookTotalMs,
        List<TableView> tables, List<CustomerView> queue) {
    public record TableView(TableState state, Menu menu, int patience, int gold, int appearance) {
        public TableView {
            if (state == null || patience < -1 || patience > 1000 || gold < 0 || appearance < 0)
                throw new IllegalArgumentException("Invalid table");
        }
    }
    public record CustomerView(int patience, int appearance) {
        public CustomerView {
            if (patience < 0 || patience > 1000 || appearance < 0)
                throw new IllegalArgumentException("Invalid customer");
        }
    }
    public StoreSnapshot {
        tables = List.copyOf(tables);
        queue = List.copyOf(queue);
        if (slot < 0 || slot > 1 || timeLeftMs < 0 || gold < 0 || servedCustomers < 0
                || sales < 0 || satisfaction < -1 || satisfaction > 100 || cookRemainMs < 0
                || cookTotalMs < cookRemainMs || tables.size() != GameConfig.TABLE_COUNT
                || queue.size() > GameConfig.QUEUE_CAPACITY)
            throw new IllegalArgumentException("Invalid store snapshot");
    }
    private static String code(Menu menu) { return menu == null ? "-" : menu.name(); }
    private static Menu menu(String code) { return "-".equals(code) ? null : Menu.valueOf(code); }
    public Message toMessage() {
        String tableText = tables.stream().map(t -> t.state() + ":" + code(t.menu()) + ":"
                + t.patience() + ":" + t.gold() + ":" + t.appearance()).collect(Collectors.joining(","));
        String queueText = queue.isEmpty() ? "-" : queue.stream().map(c -> c.patience() + ":"
                + c.appearance()).collect(Collectors.joining(","));
        return new Message(MessageType.STATE, slot, timeLeftMs, gold, servedCustomers, sales,
                satisfaction, chefX, chefY, facingLeft, code(holding), code(cooking), cookRemainMs,
                cookTotalMs, tableText, queueText);
    }
    public static StoreSnapshot fromMessage(Message message) {
        if (message.type() != MessageType.STATE || message.size() != 15)
            throw new IllegalArgumentException("Expected STATE with 15 fields");
        try {
            var tables = new java.util.ArrayList<TableView>();
            for (String item : message.get(13).split(",", -1)) {
                String[] p = item.split(":", -1);
                if (p.length != 5) throw new IllegalArgumentException("Invalid table fields");
                tables.add(new TableView(TableState.valueOf(p[0]), menu(p[1]),
                        Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));
            }
            var queue = new java.util.ArrayList<CustomerView>();
            if (!"-".equals(message.get(14))) for (String item : message.get(14).split(",", -1)) {
                String[] p = item.split(":", -1);
                if (p.length != 2) throw new IllegalArgumentException("Invalid queue fields");
                queue.add(new CustomerView(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
            }
            return new StoreSnapshot(message.getInt(0), message.getLong(1), message.getInt(2),
                    message.getInt(3), message.getInt(4), message.getInt(5), message.getInt(6),
                    message.getInt(7), message.getBool(8), menu(message.get(9)), menu(message.get(10)),
                    message.getLong(11), message.getLong(12), tables, queue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid STATE: " + e.getMessage(), e);
        }
    }
    public static StoreSnapshot empty() {
        return new StoreSnapshot(0, GameConfig.GAME_DURATION_MS, 0, 0, 0, -1,
                StoreLayout.SPAWN.x(), StoreLayout.SPAWN.y(), false, null, null, 0, 0,
                java.util.Collections.nCopies(GameConfig.TABLE_COUNT,
                        new TableView(TableState.EMPTY, null, -1, 0, 0)), List.of());
    }
}
