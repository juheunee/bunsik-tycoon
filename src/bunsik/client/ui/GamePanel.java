package bunsik.client.ui;

import bunsik.client.NetworkClient;
import bunsik.client.render.StoreRenderer;
import bunsik.common.GameConfig;
import bunsik.common.Menu;
import bunsik.common.MessageType;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 게임 화면. 위쪽 상태 표시줄 + 내 식당(+ 2P면 오른쪽에 상대 식당) + 아래 조작 안내.
 *
 * <p>역할 나눔:
 * <ul>
 *   <li>캐릭터 이동은 반응 속도가 중요하므로 내 화면에서 바로 움직이고, 위치만 0.1초 간격으로 서버에 알린다.</li>
 *   <li>주문·서빙·청소·요리처럼 결과가 걸린 행동은 서버에 "요청"만 보내고, 결과는 서버 스냅샷으로 받는다.</li>
 *   <li>그리기는 초당 약 60번, 받은 스냅샷을 그대로 그린다.</li>
 * </ul>
 */
final class GamePanel extends JPanel {

    private static final int HUD_H = 64;
    private static final int HELP_H = 30;
    private static final int STORE_HEADER_H = 28;
    private static final long TOAST_MS = 2400;

    private record Toast(String text, long at) {
    }

    private final GameFrame frame;
    private NetworkClient net;
    private int mySlot;
    private int playerCount = 1;
    private String[] nicks = {"", ""};
    private final StoreSnapshot[] snaps = new StoreSnapshot[2];

    // 내 캐릭터(내 컴퓨터가 직접 움직임)
    private double px = StoreLayout.PLAYER_START_X;
    private double py = StoreLayout.PLAYER_START_Y;
    private boolean facingLeft;
    private boolean moving;
    private boolean up;
    private boolean down;
    private boolean left;
    private boolean right;
    private boolean spaceHeld;

    private boolean running;
    private int countdown = -1;
    private long countdownAt;
    private boolean showOpponent = true;
    private final KitchenOverlay kitchen = new KitchenOverlay();
    private final List<Toast> toasts = new ArrayList<>();

    private final Timer frameTimer = new Timer(16, e -> onFrame());
    private long lastFrameAt;
    private long lastMoveSentAt;
    private int lastSentX = -1;
    private int lastSentY = -1;
    private boolean lastSentLeft;

    // 마우스 좌표 → 식당 좌표 변환용(내 식당이 그려진 위치와 배율)
    private double myOriginX;
    private double myOriginY;
    private double myScale = 1;

    GamePanel(GameFrame frame) {
        this.frame = frame;
        setFocusable(true);
        setBackground(new Color(0x2A, 0x1A, 0x12));
        installKeyBindings();
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                onMousePressed(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                onMouseDragged(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                onMouseReleased(e);
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // ------------------------------------------------------------------
    // 키보드: KeyListener 대신 Key Bindings
    //  KeyListener는 다른 컴포넌트를 클릭하면 키 입력을 못 받는 "포커스 문제"가 잦다.
    //  WHEN_IN_FOCUSED_WINDOW 로 등록하면 창이 활성화되어 있는 한 키를 받는다.
    // ------------------------------------------------------------------

    private void installKeyBindings() {
        bindHold(KeyEvent.VK_UP, v -> up = v);
        bindHold(KeyEvent.VK_W, v -> up = v);
        bindHold(KeyEvent.VK_DOWN, v -> down = v);
        bindHold(KeyEvent.VK_S, v -> down = v);
        bindHold(KeyEvent.VK_LEFT, v -> left = v);
        bindHold(KeyEvent.VK_A, v -> left = v);
        bindHold(KeyEvent.VK_RIGHT, v -> right = v);
        bindHold(KeyEvent.VK_D, v -> right = v);
        bindHold(KeyEvent.VK_SPACE, v -> {
            // 키를 누르고 있으면 자동 반복 입력이 들어오므로, 처음 눌린 순간에만 한 번 실행한다.
            if (v && !spaceHeld) {
                interact();
            }
            spaceHeld = v;
        });
        bindPress(KeyEvent.VK_ESCAPE, this::onEscape);
        bindPress(KeyEvent.VK_H, () -> {
            showOpponent = !showOpponent;
            repaint();
        });
    }

    private void bindHold(int key, Consumer<Boolean> setter) {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();
        String name = "key" + key;
        im.put(KeyStroke.getKeyStroke(key, 0, false), name + "+");
        im.put(KeyStroke.getKeyStroke(key, 0, true), name + "-");
        am.put(name + "+", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setter.accept(true);
            }
        });
        am.put(name + "-", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                setter.accept(false);
            }
        });
    }

    private void bindPress(int key, Runnable action) {
        String name = "press" + key;
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0, false), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ------------------------------------------------------------------
    // GameFrame이 서버 메시지를 받아 호출
    // ------------------------------------------------------------------

    void prepare(NetworkClient net, int mySlot, int playerCount, String[] nicks) {
        this.net = net;
        this.mySlot = mySlot;
        this.playerCount = playerCount;
        this.nicks = nicks;
        snaps[0] = null;
        snaps[1] = null;
        px = StoreLayout.PLAYER_START_X;
        py = StoreLayout.PLAYER_START_Y;
        facingLeft = false;
        up = down = left = right = spaceHeld = false;
        running = false;
        countdown = -1;
        showOpponent = true;
        kitchen.reset();
        toasts.clear();
        lastSentX = -1;
        lastFrameAt = System.currentTimeMillis();
        frameTimer.start();
    }

    void setCountdown(int sec) {
        countdown = sec;
        countdownAt = System.currentTimeMillis();
        if (sec == 0) {
            running = true;
        }
    }

    void applySnapshot(StoreSnapshot s) {
        if (s.slot >= 0 && s.slot < snaps.length) {
            snaps[s.slot] = s;
        }
    }

    void openKitchen() {
        kitchen.open = true;
        kitchen.dragging = null;
        up = down = left = right = false;
    }

    void onCookDone(Menu menu) {
        if (kitchen.open) {
            kitchen.doneAt = System.currentTimeMillis();
            kitchen.doneMenu = menu;
        }
        toast((menu == null ? "음식" : menu.label) + " 완성! 주문한 테이블로 가져가세요");
    }

    void toast(String text) {
        toasts.add(new Toast(text, System.currentTimeMillis()));
        while (toasts.size() > 3) {
            toasts.remove(0);
        }
    }

    void stop() {
        running = false;
        frameTimer.stop();
        kitchen.reset();
    }

    int mySlot() {
        return mySlot;
    }

    // ------------------------------------------------------------------
    // 매 프레임(약 60fps): 이동 → 위치 전송 → 다시 그리기
    // ------------------------------------------------------------------

    private void onFrame() {
        long now = System.currentTimeMillis();
        double dt = Math.min(0.05, (now - lastFrameAt) / 1000.0);
        lastFrameAt = now;

        moving = false;
        if (running && !kitchen.open) {
            double dx = (right ? 1 : 0) - (left ? 1 : 0);
            double dy = (down ? 1 : 0) - (up ? 1 : 0);
            if (dx != 0 || dy != 0) {
                double len = Math.hypot(dx, dy);
                double step = GameConfig.PLAYER_SPEED * dt / len;
                // x와 y를 따로 움직여 보면 테이블에 막혀도 벽을 타고 미끄러지듯 움직일 수 있다.
                double nx = StoreLayout.clampX(px + dx * step);
                if (!StoreLayout.blocked(nx, py)) {
                    px = nx;
                }
                double ny = StoreLayout.clampY(py + dy * step);
                if (!StoreLayout.blocked(px, ny)) {
                    py = ny;
                }
                if (dx != 0) {
                    facingLeft = dx < 0;
                }
                moving = true;
            }
            sendPositionIfNeeded(now);
        }

        if (kitchen.doneAt > 0 && now - kitchen.doneAt > KitchenOverlay.DONE_SHOW_MS) {
            kitchen.reset();
        }
        toasts.removeIf(t -> now - t.at() > TOAST_MS);
        repaint();
    }

    /** 위치가 바뀌었을 때만, 그리고 0.1초에 한 번까지만 보낸다(매 프레임 보내면 네트워크가 막힘). */
    private void sendPositionIfNeeded(long now) {
        int x = (int) Math.round(px);
        int y = (int) Math.round(py);
        boolean changed = x != lastSentX || y != lastSentY || facingLeft != lastSentLeft;
        if (changed && now - lastMoveSentAt >= GameConfig.MOVE_SEND_INTERVAL_MS && net != null) {
            net.send(MessageType.MOVE, x, y, facingLeft);
            lastSentX = x;
            lastSentY = y;
            lastSentLeft = facingLeft;
            lastMoveSentAt = now;
        }
    }

    private void interact() {
        if (!running || kitchen.open || net == null) {
            return;
        }
        net.send(MessageType.INTERACT, (int) Math.round(px), (int) Math.round(py));
    }

    private void onEscape() {
        if (!kitchen.open) {
            return;
        }
        StoreSnapshot s = snaps[mySlot];
        if (s != null && s.cooking != null) {
            toast("요리가 끝날 때까지 기다려 주세요");
            return;
        }
        kitchen.reset();
    }

    // ------------------------------------------------------------------
    // 마우스: 부엌 화면에서 메뉴 끌어다 놓기
    // ------------------------------------------------------------------

    private double toStoreX(MouseEvent e) {
        return (e.getX() - myOriginX) / myScale;
    }

    private double toStoreY(MouseEvent e) {
        return (e.getY() - myOriginY) / myScale;
    }

    private void onMousePressed(MouseEvent e) {
        requestFocusInWindow();
        if (!kitchen.open || !running) {
            return;
        }
        double x = toStoreX(e);
        double y = toStoreY(e);
        if (kitchen.canStartCooking(snaps[mySlot], System.currentTimeMillis())) {
            kitchen.dragging = KitchenOverlay.menuAt(x, y);
            kitchen.dragX = x;
            kitchen.dragY = y;
        }
    }

    private void onMouseDragged(MouseEvent e) {
        if (kitchen.dragging != null) {
            kitchen.dragX = toStoreX(e);
            kitchen.dragY = toStoreY(e);
        }
    }

    private void onMouseReleased(MouseEvent e) {
        Menu menu = kitchen.dragging;
        kitchen.dragging = null;
        if (menu == null || net == null) {
            return;
        }
        if (KitchenOverlay.onPlate(toStoreX(e), toStoreY(e))) {
            net.send(MessageType.COOK, menu.name());
            kitchen.pendingSince = System.currentTimeMillis();
        }
    }

    // ------------------------------------------------------------------
    // 그리기
    // ------------------------------------------------------------------

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        UiKit.smooth(g);
        long now = System.currentTimeMillis();
        int w = getWidth();
        int h = getHeight();

        // 바닥: 어두운 나무판
        g.setColor(new Color(0x33, 0x21, 0x16));
        for (int x = 0; x < w; x += 48) {
            g.drawLine(x, HUD_H, x, h - HELP_H);
        }

        StoreSnapshot mine = snaps[mySlot];
        drawHud(g, w, mine, now);

        boolean versus = playerCount >= 2;
        boolean split = versus && showOpponent;
        int top = HUD_H;
        int areaH = h - HUD_H - HELP_H;
        if (split) {
            int half = w / 2;
            drawStoreArea(g, new Rectangle(0, top, half, areaH), mySlot, true, now);
            drawStoreArea(g, new Rectangle(half, top, w - half, areaH), 1 - mySlot, false, now);
            g.setColor(new Color(0x14, 0x0C, 0x08));
            g.fillRect(half - 2, top, 4, areaH);
        } else {
            drawStoreArea(g, new Rectangle(0, top, w, areaH), mySlot, true, now);
        }
        drawHelp(g, w, h, versus);
        g.dispose();
    }

    private void drawHud(Graphics2D g, int w, StoreSnapshot s, long now) {
        g.setColor(UiKit.RED);
        g.fillRect(0, 0, w, HUD_H);
        g.setColor(UiKit.RED_DARK);
        g.fillRect(0, HUD_H - 5, w, 5);
        g.setFont(UiKit.bold(24));
        UiKit.outlinedText(g, "도전! 분식왕", 92, 41, UiKit.YELLOW, UiKit.RED_DARK, 5f);

        int gold = s == null ? 0 : s.gold;
        int visited = s == null ? 0 : s.visited;
        int served = s == null ? 0 : s.served;
        String satis = (s == null || s.satisfaction < 0) ? "-" : s.satisfaction + "%";
        String[][] chips = {{"골드", String.valueOf(gold)}, {"다녀간 손님", String.valueOf(visited)},
                {"판매 메뉴", String.valueOf(served)}, {"만족도", satis}};
        double x = 196;
        for (String[] c : chips) {
            x += chip(g, x, 10, c[0], c[1]) + 10;
        }

        // 2P에서 상대 식당을 숨겼을 때는 상대 점수만 간단히
        if (playerCount >= 2 && !showOpponent) {
            StoreSnapshot o = snaps[1 - mySlot];
            String text = "상대 " + nicks[1 - mySlot] + " · 만족도 "
                    + ((o == null || o.satisfaction < 0) ? "-" : o.satisfaction + "%")
                    + " · 판매 " + (o == null ? 0 : o.served);
            g.setFont(UiKit.bold(13));
            g.setColor(UiKit.YELLOW_SOFT);
            g.drawString(text, (float) x + 6, 38);
        }

        // 남은 시간
        long left = s == null ? GameConfig.GAME_DURATION_MS : s.timeLeftMs;
        long sec = (left + 999) / 1000;
        String time = String.format("%d:%02d", sec / 60, sec % 60);
        double tw = 128;
        double tx = w - tw - 14;
        boolean hurry = running && sec <= 30;
        g.setColor(new Color(0x2A, 0x1A, 0x12));
        g.fill(new RoundRectangle2D.Double(tx, 9, tw, 44, 14, 14));
        g.setFont(UiKit.bold(11));
        g.setColor(UiKit.CREAM_DARK);
        g.drawString("남은 시간", (float) tx + 10, 26);
        g.setFont(UiKit.bold(24));
        g.setColor(hurry && (now / 400) % 2 == 0 ? new Color(0xFF, 0x6B, 0x5A) : UiKit.YELLOW);
        g.drawString(time, (float) tx + 52, 44);
    }

    /** 상단 숫자 칩. 그린 너비를 돌려준다. */
    private double chip(Graphics2D g, double x, double y, String label, String value) {
        g.setFont(UiKit.bold(20));
        double vw = g.getFontMetrics().stringWidth(value);
        g.setFont(UiKit.bold(11));
        double lw = g.getFontMetrics().stringWidth(label);
        double w = Math.max(vw, lw) + 26;
        g.setColor(UiKit.CREAM);
        g.fill(new RoundRectangle2D.Double(x, y, w, 44, 14, 14));
        g.setColor(UiKit.BROWN_SOFT);
        g.setFont(UiKit.bold(11));
        UiKit.centerText(g, label, x + w / 2, y + 15);
        g.setColor(UiKit.RED_DARK);
        g.setFont(UiKit.bold(20));
        UiKit.centerText(g, value, x + w / 2, y + 38);
        return w;
    }

    private void drawStoreArea(Graphics2D g0, Rectangle area, int slot, boolean mine, long now) {
        StoreSnapshot s = slot >= 0 && slot < snaps.length ? snaps[slot] : null;
        Graphics2D g = (Graphics2D) g0.create();

        // 머리글
        g.setColor(new Color(0x3B, 0x24, 0x16));
        g.fillRect(area.x, area.y, area.width, STORE_HEADER_H);
        g.setFont(UiKit.bold(14));
        g.setColor(mine ? UiKit.YELLOW : UiKit.CREAM_DARK);
        String who = slot >= 0 && slot < nicks.length ? nicks[slot] : "";
        String title = mine ? "내 식당 · " + who : "상대 식당 · " + who;
        if (!mine && s != null) {
            title += "    만족도 " + (s.satisfaction < 0 ? "-" : s.satisfaction + "%") + " · 판매 " + s.served
                    + " · 골드 " + s.gold;
        }
        g.drawString(title, area.x + 14, area.y + 19);

        // 식당을 영역에 맞게 확대·축소
        int availW = area.width - 20;
        int availH = area.height - STORE_HEADER_H - 12;
        double scale = Math.min(availW / (double) StoreLayout.WIDTH, availH / (double) StoreLayout.HEIGHT);
        double ox = area.x + (area.width - StoreLayout.WIDTH * scale) / 2.0;
        double oy = area.y + STORE_HEADER_H + (area.height - STORE_HEADER_H - StoreLayout.HEIGHT * scale) / 2.0;
        if (mine) {
            myOriginX = ox;
            myOriginY = oy;
            myScale = scale;
        }

        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(ox - 4, oy - 2, StoreLayout.WIDTH * scale + 8,
                StoreLayout.HEIGHT * scale + 8, 14, 14));
        g.translate(ox, oy);
        g.scale(scale, scale);
        g.clip(new Rectangle2D.Double(0, 0, StoreLayout.WIDTH, StoreLayout.HEIGHT));

        if (mine) {
            StoreRenderer.draw(g, s, px, py, facingLeft, moving, now, true);
            drawToasts(g, now);
            if (kitchen.open) {
                kitchen.draw(g, s, now);
            }
            drawCountdown(g, now);
        } else {
            double ox2 = s == null ? StoreLayout.PLAYER_START_X : s.playerX;
            double oy2 = s == null ? StoreLayout.PLAYER_START_Y : s.playerY;
            StoreRenderer.draw(g, s, ox2, oy2, s != null && s.facingLeft, false, now, false);
        }
        g.dispose();
    }

    /** 안내 문구는 벽(메뉴판) 위에 잠깐 띄운다. 바닥 위에 띄우면 손님·인내심 바를 가리기 때문. */
    private void drawToasts(Graphics2D g, long now) {
        double y = 8;
        g.setFont(UiKit.bold(15));
        for (Toast t : toasts) {
            long age = now - t.at();
            int alpha = age > TOAST_MS - 400 ? (int) (255 * (TOAST_MS - age) / 400.0) : 255;
            double tw = g.getFontMetrics().stringWidth(t.text()) + 36;
            double tx = (StoreLayout.WIDTH - tw) / 2.0;
            g.setColor(new Color(0x2A, 0x1A, 0x12, Math.max(0, Math.min(225, alpha))));
            g.fill(new RoundRectangle2D.Double(tx, y, tw, 32, 32, 32));
            g.setColor(UiKit.alpha(Color.WHITE, alpha));
            UiKit.centerTextIn(g, t.text(), tx, y, tw, 32);
            y += 36;
        }
    }

    private void drawCountdown(Graphics2D g, long now) {
        String text;
        if (countdown > 0) {
            text = String.valueOf(countdown);
        } else if (countdown == 0 && now - countdownAt < 900) {
            text = "영업 시작!";
        } else {
            return;
        }
        g.setColor(new Color(0x1A, 0x10, 0x0A, countdown > 0 ? 140 : 70));
        g.fillRect(0, 0, StoreLayout.WIDTH, StoreLayout.HEIGHT);
        double t = Math.min(1, (now - countdownAt) / 250.0);
        g.setFont(UiKit.bold((float) ((countdown > 0 ? 120 : 76) * (0.7 + 0.3 * t))));
        UiKit.outlinedText(g, text, StoreLayout.WIDTH / 2.0, StoreLayout.HEIGHT / 2.0 + 36, UiKit.YELLOW,
                UiKit.RED_DARK, 12f);
        if (countdown > 0) {
            g.setFont(UiKit.bold(18));
            g.setColor(Color.WHITE);
            UiKit.centerText(g, "손님이 곧 들어와요! 방향키로 움직이고 스페이스로 일해요", StoreLayout.WIDTH / 2.0,
                    StoreLayout.HEIGHT / 2.0 + 90);
        }
    }

    private void drawHelp(Graphics2D g, int w, int h, boolean versus) {
        g.setColor(new Color(0x2A, 0x1A, 0x12));
        g.fillRect(0, h - HELP_H, w, HELP_H);
        g.setColor(UiKit.CREAM_DARK);
        g.setFont(UiKit.bold(13));
        String help = "방향키/WASD 이동    SPACE 주문받기·서빙·치우기·부엌    마우스 메뉴를 접시로 끌기    ESC 부엌 닫기";
        if (versus) {
            help += "    H 상대 식당 " + (showOpponent ? "숨기기" : "보기");
        }
        UiKit.centerText(g, help, w / 2.0, h - 10);
        g.setStroke(new BasicStroke(1f));
    }
}
