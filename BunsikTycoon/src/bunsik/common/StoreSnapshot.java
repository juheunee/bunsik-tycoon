package bunsik.common;

import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

/** README 7-4 STATE 계약. 서버의 가변 상태를 화면과 공유하지 않는다. */
public record StoreSnapshot(int slot, long timeLeftMs, int gold, int servedCustomers,
        int sales, int satisfaction, int chefX, int chefY, boolean facingLeft,
        List<Hand> hands, Map<Menu, Long> stations,
        List<TableView> tables, List<CustomerView> queue) {
    public record Hand(Menu menu, boolean dish) {
        public static final Hand EMPTY = new Hand(null, false);
        public static final Hand DISH = new Hand(null, true);

        public Hand {
            if (menu != null && dish) throw new IllegalArgumentException("Invalid hand");
        }

        public boolean empty() { return menu == null && !dish; }
        public String label() { return dish ? "빈 그릇" : menu == null ? "비어 있음" : menu.label; }

        private static Hand parse(String value) {
            return "DISH".equals(value) ? DISH : new Hand(StoreSnapshot.menu(value), false);
        }

        private String encode() { return dish ? "DISH" : code(menu); }
    }

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
        hands = List.copyOf(hands);
        stations = Map.copyOf(stations);
        tables = List.copyOf(tables);
        queue = List.copyOf(queue);
        if (slot < 0 || slot > 1 || timeLeftMs < 0 || gold < 0 || servedCustomers < 0
                || sales < 0 || satisfaction < -1 || satisfaction > 100 || hands.size() != 2
                || tables.size() != GameConfig.TABLE_COUNT
                || queue.size() > GameConfig.QUEUE_CAPACITY)
            throw new IllegalArgumentException("Invalid store snapshot");
        for (var station : stations.entrySet()) {
            if (station.getValue() < 0 || station.getValue() > station.getKey().cookTimeMs) {
                throw new IllegalArgumentException("Invalid cook time");
            }
        }
    }

    public StoreSnapshot(int slot, long timeLeftMs, int gold, int servedCustomers,
            int sales, int satisfaction, int chefX, int chefY, boolean facingLeft,
            Menu holding, Menu cooking, long cookRemainMs, long cookTotalMs,
            List<TableView> tables, List<CustomerView> queue) {
        this(slot, timeLeftMs, gold, servedCustomers, sales, satisfaction, chefX, chefY, facingLeft,
                List.of(new Hand(holding, false), Hand.EMPTY),
                cooking == null ? Map.of() : Map.of(cooking, cookRemainMs), tables, queue);
        if (cookRemainMs < 0 || cookTotalMs < cookRemainMs) {
            throw new IllegalArgumentException("Invalid legacy cook time");
        }
    }

    public boolean hasFreeHand() { return hands.stream().anyMatch(Hand::empty); }
    private static String code(Menu menu) { return menu == null ? "-" : menu.name(); }
    private static Menu menu(String code) { return "-".equals(code) ? null : Menu.valueOf(code); }
    public Message toMessage() {
        String tableText = tables.stream().map(t -> t.state() + ":" + code(t.menu()) + ":"
                + t.patience() + ":" + t.gold() + ":" + t.appearance()).collect(Collectors.joining(","));
        String queueText = queue.isEmpty() ? "-" : queue.stream().map(c -> c.patience() + ":"
                + c.appearance()).collect(Collectors.joining(","));
        String stationText = stations.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(s -> s.getKey().name() + ":" + s.getValue()).collect(Collectors.joining(","));
        return new Message(MessageType.STATE, slot, timeLeftMs, gold, servedCustomers, sales,
                satisfaction, chefX, chefY, facingLeft, hands.get(0).encode(), hands.get(1).encode(),
                stationText.isEmpty() ? "-" : stationText, tableText, queueText);
    }
    public static StoreSnapshot fromMessage(Message message) {
        if (message.type() != MessageType.STATE || (message.size() != 14 && message.size() != 15))
            throw new IllegalArgumentException("Invalid STATE field count");
        boolean legacy = message.size() == 15;
        int tablesIndex = legacy ? 13 : 12;
        try {
            var tables = new java.util.ArrayList<TableView>();
            for (String item : message.get(tablesIndex).split(",", -1)) {
                String[] p = item.split(":", -1);
                if (p.length != 5) throw new IllegalArgumentException("Invalid table fields");
                tables.add(new TableView(TableState.valueOf(p[0]), menu(p[1]),
                        Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])));
            }
            var queue = new java.util.ArrayList<CustomerView>();
            String queueText = message.get(tablesIndex + 1);
            if (!"-".equals(queueText)) for (String item : queueText.split(",", -1)) {
                String[] p = item.split(":", -1);
                if (p.length != 2) throw new IllegalArgumentException("Invalid queue fields");
                queue.add(new CustomerView(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
            }
            if (legacy) {
                return new StoreSnapshot(message.getInt(0), message.getLong(1), message.getInt(2),
                        message.getInt(3), message.getInt(4), message.getInt(5), message.getInt(6),
                        message.getInt(7), message.getBool(8), menu(message.get(9)), menu(message.get(10)),
                        message.getLong(11), message.getLong(12), tables, queue);
            }
            return new StoreSnapshot(message.getInt(0), message.getLong(1), message.getInt(2),
                    message.getInt(3), message.getInt(4), message.getInt(5), message.getInt(6),
                    message.getInt(7), message.getBool(8),
                    List.of(Hand.parse(message.get(9)), Hand.parse(message.get(10))),
                    parseStations(message.get(11)), tables, queue);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid STATE: " + e.getMessage(), e);
        }
    }

    private static Map<Menu, Long> parseStations(String text) {
        var stations = new EnumMap<Menu, Long>(Menu.class);
        if ("-".equals(text)) return stations;
        for (String entry : text.split(",", -1)) {
            String[] fields = entry.split(":", -1);
            if (fields.length != 2 || stations.putIfAbsent(Menu.valueOf(fields[0]),
                    Long.parseLong(fields[1])) != null) {
                throw new IllegalArgumentException("Invalid station fields");
            }
        }
        return stations;
    }
    public static StoreSnapshot empty() {
        return new StoreSnapshot(0, GameConfig.GAME_DURATION_MS, 0, 0, 0, -1,
                StoreLayout.SPAWN.x(), StoreLayout.SPAWN.y(), false, null, null, 0, 0,
                java.util.Collections.nCopies(GameConfig.TABLE_COUNT,
                        new TableView(TableState.EMPTY, null, -1, 0, 0)), List.of());
    }
}
