package bunsik.server;

import bunsik.common.GameConfig;
import bunsik.common.Menu;
import bunsik.common.Message;
import bunsik.common.MessageType;
import bunsik.server.game.Store;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 한 판의 게임을 진행하는 방. 1P면 식당 1개, 2P면 식당 2개(같은 손님 명단)를 가진다.
 *
 * <p><b>"한 줄 처리" 구조</b>: 직원 스레드들은 플레이어 행동을 {@link #commands} 대기줄에 넣기만 하고,
 * 게임 상태는 오직 이 방의 게임 루프 스레드 하나만 바꾼다. 여러 스레드가 같은 장부에 동시에 쓰는 일이
 * 생기지 않으므로 동시성 버그를 대부분 피할 수 있다.
 *
 * <p><b>"판단은 서버, 표시는 클라이언트"</b>: 점수·골드·시간·손님은 모두 여기서 계산하고,
 * 클라이언트에는 결과(STATE)만 보낸다.
 */
public final class GameRoom {

    private record Command(int slot, Message message) {
    }

    private final GameServer server;
    private final List<ClientHandler> players;
    private final Store[] stores;
    private final AtomicBoolean[] connected;
    private final boolean versus;
    private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService loop;

    private volatile boolean finished;
    private long endAt = Long.MAX_VALUE;

    GameRoom(GameServer server, List<ClientHandler> players, boolean versus) {
        this.server = server;
        this.players = players;
        this.versus = versus;
        long seed = System.nanoTime(); // 두 식당이 같은 seed → 같은 손님 명단
        this.stores = new Store[players.size()];
        this.connected = new AtomicBoolean[players.size()];
        for (int i = 0; i < players.size(); i++) {
            stores[i] = new Store(seed);
            connected[i] = new AtomicBoolean(true);
        }
        this.loop = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "game-room");
            t.setDaemon(true);
            return t;
        });
    }

    /** 게임 화면 전환 → 3,2,1 카운트다운 → 시작. 시작 시점은 서버 시계가 정한다(공정성). */
    void start() {
        String nick0 = players.get(0).nickname();
        String nick1 = players.size() > 1 ? players.get(1).nickname() : "-";
        for (int i = 0; i < players.size(); i++) {
            players.get(i).joinRoom(this, i);
            sendTo(i, new Message(MessageType.START, i, players.size(), nick0, nick1));
        }
        broadcastSnapshots(System.currentTimeMillis()); // 카운트다운 동안 보일 빈 식당

        for (int sec = GameConfig.COUNTDOWN_SEC; sec >= 1; sec--) {
            final int s = sec;
            loop.schedule(() -> broadcast(new Message(MessageType.COUNTDOWN, s)),
                    (GameConfig.COUNTDOWN_SEC - sec) * 1000L, TimeUnit.MILLISECONDS);
        }
        loop.schedule(this::begin, GameConfig.COUNTDOWN_SEC * 1000L, TimeUnit.MILLISECONDS);
    }

    private void begin() {
        if (finished) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Store s : stores) {
            s.begin(now);
        }
        endAt = now + GameConfig.GAME_DURATION_MS;
        broadcast(new Message(MessageType.COUNTDOWN, 0));
        loop.scheduleAtFixedRate(this::tick, 0, GameConfig.TICK_MS, TimeUnit.MILLISECONDS);
    }

    /** 직원 스레드가 호출: 행동을 대기줄에 넣기만 한다 */
    void enqueue(int slot, Message m) {
        if (slot >= 0 && slot < stores.length && !finished) {
            commands.add(new Command(slot, m));
        }
    }

    /** 게임 시계의 째깍 한 번(0.1초마다) */
    private void tick() {
        // scheduleAtFixedRate는 예외가 한 번이라도 밖으로 나가면 그 뒤로 조용히 멈춘다.
        // 그래서 반드시 전부 잡아서 로그만 남긴다.
        try {
            if (finished) {
                return;
            }
            long now = System.currentTimeMillis();
            Command c;
            while ((c = commands.poll()) != null) {
                apply(c, now);
            }
            for (int i = 0; i < stores.length; i++) {
                stores[i].update(now);
                for (Message e : stores[i].drainEvents()) {
                    sendTo(i, e);
                }
            }
            broadcastSnapshots(now);
            if (now >= endAt) {
                finish();
            }
        } catch (Exception e) {
            GameServer.log("게임 루프 오류(계속 진행): " + e);
            e.printStackTrace();
        }
    }

    private void apply(Command c, long now) {
        Store store = stores[c.slot()];
        Message m = c.message();
        try {
            switch (m.type()) {
                case MOVE -> store.onMove(m.getInt(0), m.getInt(1), m.getBool(2));
                case INTERACT -> store.onInteract(m.getInt(0), m.getInt(1), now);
                case COOK -> store.onCook(Menu.fromCode(m.get(0)), now);
                default -> {
                }
            }
        } catch (IllegalArgumentException e) {
            GameServer.log("잘못된 게임 요청 무시: " + m);
        }
    }

    private void broadcastSnapshots(long now) {
        for (int i = 0; i < stores.length; i++) {
            broadcast(stores[i].snapshot(i, now).toMessage());
        }
    }

    /** 시간 종료: 결과 계산 → GAME_OVER → 방 정리 */
    private void finish() {
        if (finished) {
            return;
        }
        finished = true;

        int winner = -2; // 혼자하기
        if (versus && stores.length == 2) {
            winner = compare(stores[0], stores[1]);
        }
        Object[] args = new Object[2 + players.size()];
        args[0] = winner;
        args[1] = players.size();
        for (int i = 0; i < players.size(); i++) {
            Store s = stores[i];
            args[2 + i] = players.get(i).nickname() + ":" + s.satisfaction() + ":" + s.served() + ":"
                    + s.gold() + ":" + s.visited();
        }
        broadcast(new Message(MessageType.GAME_OVER, args));
        GameServer.log("게임 종료" + (versus ? " (승자 슬롯: " + winner + ")" : ""));

        for (ClientHandler p : players) {
            p.leaveRoom(this);
        }
        loop.shutdown();
        if (versus) {
            server.lobby().onGameFinished();
        }
    }

    /** 승패: 만족도가 높은 쪽 → 같으면 판매 수 → 그래도 같으면 무승부(-1) */
    private static int compare(Store a, Store b) {
        int sa = Math.max(0, a.satisfaction());
        int sb = Math.max(0, b.satisfaction());
        if (sa != sb) {
            return sa > sb ? 0 : 1;
        }
        if (a.served() != b.served()) {
            return a.served() > b.served() ? 0 : 1;
        }
        return -1;
    }

    /**
     * 게임 중 누군가 나감(MVP 수준). 그 사람에게는 더 보내지 않고, 남은 사람은 시간이 끝날 때까지 계속한다.
     * 아무도 남지 않으면 방을 정리한다.
     */
    void onPlayerLeft(ClientHandler h) {
        int idx = players.indexOf(h);
        if (idx >= 0) {
            connected[idx].set(false);
        }
        boolean anyone = false;
        for (AtomicBoolean b : connected) {
            anyone |= b.get();
        }
        if (!anyone) {
            try {
                loop.execute(this::finish);
            } catch (RejectedExecutionException ignored) {
                // 이미 끝난 방
            }
        }
    }

    private void sendTo(int slot, Message m) {
        if (connected[slot].get()) {
            players.get(slot).send(m);
        }
    }

    private void broadcast(Message m) {
        for (int i = 0; i < players.size(); i++) {
            sendTo(i, m);
        }
    }
}
