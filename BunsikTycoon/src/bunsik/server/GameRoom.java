package bunsik.server;

import bunsik.common.GameConfig;
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

/** 한 판 진행 (게임 상태 변경은 게임 루프 스레드 하나에서만) */
public final class GameRoom {

    private static final int TICK_MS = 100;
    private static final int COUNTDOWN_SEC = 3;

    private record Command(int slot, Message message) {
    }

    private final GameServer server;
    private final List<ClientHandler> players;
    private final Store[] stores;
    private final AtomicBoolean[] connected;
    private final boolean versus;
    private final Runnable onFinished;
    private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService loop;

    private volatile boolean finished;
    private long endAt = Long.MAX_VALUE;

    /** onFinished: 게임 종료 후 실행할 작업 (없으면 null) */
    GameRoom(GameServer server, List<ClientHandler> players, boolean versus, Runnable onFinished) {
        this.server = server;
        this.players = players;
        this.versus = versus;
        this.onFinished = onFinished;
        long seed = System.nanoTime();
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

    /** START 전송 -> 카운트다운 -> 시작 */
    void start() {
        String nick0 = players.get(0).nickname();
        String nick1 = players.size() > 1 ? players.get(1).nickname() : "-";
        for (int i = 0; i < players.size(); i++) {
            players.get(i).joinRoom(this, i);
            sendTo(i, new Message(MessageType.START, i, players.size(), nick0, nick1));
        }
        broadcastSnapshots(System.currentTimeMillis());

        for (int sec = COUNTDOWN_SEC; sec >= 1; sec--) {
            final int s = sec;
            loop.schedule(() -> broadcast(new Message(MessageType.COUNTDOWN, s)),
                    (COUNTDOWN_SEC - sec) * 1000L, TimeUnit.MILLISECONDS);
        }
        loop.schedule(this::begin, COUNTDOWN_SEC * 1000L, TimeUnit.MILLISECONDS);
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
        server.log("게임 시작 (" + (versus ? "경쟁 모드" : "혼자 하기") + ")");
        loop.scheduleAtFixedRate(this::tick, 0, TICK_MS, TimeUnit.MILLISECONDS);
    }

    /** 행동 대기줄 추가 (수신 스레드 호출용) */
    void enqueue(int slot, Message m) {
        if (slot >= 0 && slot < stores.length && !finished) {
            commands.add(new Command(slot, m));
        }
    }

    private void tick() {
        // 반복 실행 중단 방지용 전체 예외 처리
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
            server.log("게임 루프 오류: " + e);
        }
    }

    private void apply(Command c, long now) {
        try {
            stores[c.slot()].handle(c.message(), now);
        } catch (IllegalArgumentException e) {
            server.log("잘못된 게임 요청 무시: " + c.message().encode());
        }
    }

    private void broadcastSnapshots(long now) {
        for (int i = 0; i < stores.length; i++) {
            broadcast(stores[i].snapshot(i, now).toMessage());
        }
    }

    private void finish() {
        endGame(versus ? compare(stores[0], stores[1]) : -2);
    }

    /** GAME_OVER|승자|인원|닉네임:만족도:판매:골드:손님 ... */
    private void endGame(int winner) {
        if (finished) {
            return;
        }
        finished = true;
        broadcast(gameOverMessage(winner));
        server.log("게임 종료" + (versus ? " (승자 슬롯: " + winner + ")" : ""));
        for (ClientHandler p : players) {
            p.leaveRoom(this);
        }
        loop.shutdownNow();
        if (onFinished != null) {
            onFinished.run();
        }
    }

    /** 만족도 -> 판매 수 순서 비교 (무승부 -1) */
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

    private Message gameOverMessage(int winner) {
        Object[] args = new Object[2 + players.size()];
        args[0] = winner;
        args[1] = players.size();
        for (int i = 0; i < players.size(); i++) {
            Store s = stores[i];
            args[2 + i] = players.get(i).nickname() + ":" + s.satisfaction() + ":" + s.served() + ":"
                    + s.gold() + ":" + s.visited();
        }
        return new Message(MessageType.GAME_OVER, args);
    }

    /** 게임 중 퇴장 (전원 퇴장 시 방 정리) */
    void onPlayerLeft(ClientHandler h) {
        int idx = players.indexOf(h);
        if (idx < 0) {
            return;
        }
        connected[idx].set(false);
        for (AtomicBoolean b : connected) {
            if (b.get()) {
                return;
            }
        }
        try {
            loop.execute(() -> endGame(versus ? -1 : -2));
        } catch (RejectedExecutionException ignored) {
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