package bunsik.client.ui;

import bunsik.client.render.Sprites;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 경쟁 모드 대기실. 들어온 사람을 실시간으로 보여 주고, 두 사람이 모두 "준비"를 누르면 서버가 게임을 시작한다.
 */
final class LobbyPanel extends JPanel {

    /** 대기실 한 자리 */
    record Entry(int clientId, String nickname, boolean ready) {
    }

    private static final int SEAT_W = 300;
    private static final int SEAT_H = 250;

    private final GameFrame frame;
    private final UiKit.RoundButton readyButton = new UiKit.RoundButton("준비", UiKit.RED);
    private final UiKit.RoundButton leaveButton = new UiKit.RoundButton("나가기", UiKit.BROWN_SOFT);
    private List<Entry> entries = new ArrayList<>();
    private int myId = -1;
    private boolean myReady;

    LobbyPanel(GameFrame frame) {
        this.frame = frame;
        setLayout(null);
        add(readyButton);
        add(leaveButton);
        readyButton.addActionListener(e -> frame.sendReady(!myReady));
        leaveButton.addActionListener(e -> frame.leaveToTitle());
    }

    /** 새로 접속할 때 이전 정보 지우기 */
    void reset() {
        entries = new ArrayList<>();
        myReady = false;
        refreshButton();
        repaint();
    }

    void update(List<Entry> list, int myClientId) {
        this.entries = list;
        this.myId = myClientId;
        myReady = list.stream().anyMatch(e -> e.clientId() == myClientId && e.ready());
        refreshButton();
        repaint();
    }

    private void refreshButton() {
        readyButton.setText(myReady ? "준비 취소" : "준비");
        readyButton.setBaseColor(myReady ? UiKit.BROWN_SOFT : UiKit.RED);
    }

    @Override
    public void doLayout() {
        int cx = getWidth() / 2;
        int y = seatTop() + SEAT_H + 40;
        readyButton.setBounds(cx - 190, y, 220, 56);
        leaveButton.setBounds(cx + 46, y, 144, 56);
    }

    private int seatTop() {
        return 190;
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        UiKit.smooth(g);
        int w = getWidth();
        int h = getHeight();
        TitlePanel.paintShopBackdrop(g, w, h);

        g.setFont(UiKit.bold(38));
        UiKit.outlinedText(g, "경쟁 모드 대기실", w / 2.0, 132, UiKit.RED, Color.WHITE, 8f);
        g.setFont(UiKit.bold(15));
        g.setColor(UiKit.BROWN_SOFT);
        UiKit.centerText(g, statusText(), w / 2.0, 166);

        int top = seatTop();
        int gap = 90;
        int left = w / 2 - SEAT_W - gap / 2;
        for (int i = 0; i < 2; i++) {
            Entry e = i < entries.size() ? entries.get(i) : null;
            drawSeat(g, left + i * (SEAT_W + gap), top, e, i);
        }
        // VS
        double vx = w / 2.0;
        double vy = top + SEAT_H / 2.0;
        g.setColor(UiKit.RED_DARK);
        g.fill(new Ellipse2D.Double(vx - 34, vy - 34, 68, 68));
        g.setColor(UiKit.YELLOW);
        g.fill(new Ellipse2D.Double(vx - 29, vy - 29, 58, 58));
        g.setFont(UiKit.bold(24));
        g.setColor(UiKit.RED_DARK);
        UiKit.centerTextIn(g, "VS", vx - 29, vy - 29, 58, 58);
        g.dispose();
    }

    private String statusText() {
        if (entries.isEmpty()) {
            return "대기실에 들어가는 중...";
        }
        if (entries.size() < 2) {
            return "상대방이 들어오길 기다리는 중이에요. 상대는 서버 IP로 접속하면 돼요.";
        }
        if (entries.stream().allMatch(Entry::ready)) {
            return "곧 시작해요!";
        }
        return "두 사람 모두 '준비'를 누르면 게임이 시작돼요.";
    }

    private void drawSeat(Graphics2D g, int x, int y, Entry e, int index) {
        boolean me = e != null && e.clientId() == myId;
        g.setColor(new Color(0, 0, 0, 50));
        g.fill(new RoundRectangle2D.Double(x + 4, y + 6, SEAT_W, SEAT_H, 26, 26));
        g.setColor(e == null ? new Color(0xF3, 0xEC, 0xDF) : UiKit.CREAM);
        g.fill(new RoundRectangle2D.Double(x, y, SEAT_W, SEAT_H, 26, 26));
        g.setStroke(new BasicStroke(me ? 5f : 3f));
        g.setColor(me ? UiKit.RED : UiKit.CREAM_DARK.darker());
        g.draw(new RoundRectangle2D.Double(x, y, SEAT_W, SEAT_H, 26, 26));

        g.setFont(UiKit.bold(13));
        g.setColor(UiKit.BROWN_SOFT);
        g.drawString((index + 1) + "P" + (me ? "  (나)" : ""), x + 18, y + 28);

        if (e == null) {
            g.setFont(UiKit.bold(18));
            g.setColor(new Color(0xB9, 0xAD, 0x9B));
            UiKit.centerText(g, "빈 자리", x + SEAT_W / 2.0, y + SEAT_H / 2.0);
            g.setFont(UiKit.plain(13));
            UiKit.centerText(g, "기다리는 중...", x + SEAT_W / 2.0, y + SEAT_H / 2.0 + 24);
            return;
        }
        Graphics2D c = (Graphics2D) g.create();
        c.translate(x + SEAT_W / 2.0, y + 160);
        c.scale(1.6, 1.6);
        Sprites.chef(c, 0, 0, index == 1, null, 0, false);
        c.dispose();
        g.setFont(UiKit.bold(22));
        g.setColor(UiKit.BROWN);
        UiKit.centerText(g, e.nickname(), x + SEAT_W / 2.0, y + 188);

        double bw = 120;
        double bx = x + (SEAT_W - bw) / 2.0;
        double by = y + 202;
        g.setColor(e.ready() ? UiKit.GREEN : new Color(0xC9, 0xBE, 0xAE));
        g.fill(new RoundRectangle2D.Double(bx, by, bw, 32, 32, 32));
        g.setColor(Color.WHITE);
        g.setFont(UiKit.bold(15));
        UiKit.centerTextIn(g, e.ready() ? "준비 완료" : "준비 중", bx, by, bw, 32);
    }
}
