package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.AppConfig;
import bunsik.common.Menu;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 시작 화면: 닉네임·서버 IP·포트를 입력하고 "혼자 하기" 또는 "경쟁 하기"를 고른다(마우스 조작).
 */
final class TitlePanel extends JPanel {

    private static final int CARD_W = 460;
    private static final int CARD_H = 300;

    private final GameFrame frame;
    private final JTextField nickField = new JTextField();
    private final JTextField hostField = new JTextField();
    private final JTextField portField = new JTextField();
    private final UiKit.RoundButton soloButton = new UiKit.RoundButton("혼자 하기 (1P)", UiKit.ORANGE);
    private final UiKit.RoundButton versusButton = new UiKit.RoundButton("경쟁 하기 (2P)", UiKit.RED);
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private final JLabel[] labels = {new JLabel("닉네임"), new JLabel("서버 IP"), new JLabel("포트")};

    TitlePanel(GameFrame frame, AppConfig cfg) {
        this.frame = frame;
        setLayout(null);
        nickField.setText(cfg.get("client.nickname", ""));
        hostField.setText(cfg.get("client.host", "localhost"));
        portField.setText(String.valueOf(cfg.getInt("client.port", 5000)));
        for (JTextField f : new JTextField[]{nickField, hostField, portField}) {
            UiKit.styleField(f);
            add(f);
        }
        for (JLabel l : labels) {
            l.setFont(UiKit.bold(15));
            l.setForeground(UiKit.BROWN);
            add(l);
        }
        status.setFont(UiKit.bold(14));
        status.setForeground(UiKit.RED_DARK);
        add(status);
        add(soloButton);
        add(versusButton);

        soloButton.addActionListener(e -> start(false));
        versusButton.addActionListener(e -> start(true));
        nickField.addActionListener(e -> start(false));
    }

    private void start(boolean versus) {
        String nick = nickField.getText().trim();
        if (nick.isEmpty()) {
            status.setText("닉네임을 입력해 주세요");
            nickField.requestFocusInWindow();
            return;
        }
        String host = hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            status.setText("포트는 숫자로 입력해 주세요");
            return;
        }
        frame.connect(nick, host.isEmpty() ? "localhost" : host, port, versus);
    }

    void setBusy(boolean busy, String message) {
        soloButton.setEnabled(!busy);
        versusButton.setEnabled(!busy);
        status.setText(message);
    }

    @Override
    public void doLayout() {
        int cx = getWidth() / 2;
        int cardX = cx - CARD_W / 2;
        int cardY = cardTop();
        int labelW = 80;
        int fieldX = cardX + 40 + labelW;
        int fieldW = CARD_W - 80 - labelW;
        JTextField[] fields = {nickField, hostField, portField};
        for (int i = 0; i < 3; i++) {
            int y = cardY + 30 + i * 52;
            labels[i].setBounds(cardX + 40, y, labelW, 40);
            fields[i].setBounds(fieldX, y, fieldW, 40);
        }
        int by = cardY + 30 + 3 * 52 + 12;
        int bw = (CARD_W - 80 - 16) / 2;
        soloButton.setBounds(cardX + 40, by, bw, 54);
        versusButton.setBounds(cardX + 40 + bw + 16, by, bw, 54);
        status.setBounds(cardX, cardY + CARD_H + 6, CARD_W, 24);
    }

    private int cardTop() {
        return Math.max(250, getHeight() / 2 - 80);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        UiKit.smooth(g);
        int w = getWidth();
        int h = getHeight();
        paintShopBackdrop(g, w, h);

        // 큰 간판
        double sw = Math.min(720, w - 80);
        double sx = (w - sw) / 2;
        double sy = 92;
        double sh = 128;
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new RoundRectangle2D.Double(sx + 5, sy + 7, sw, sh, 26, 26));
        g.setColor(UiKit.RED_DARK);
        g.fill(new RoundRectangle2D.Double(sx, sy, sw, sh, 26, 26));
        g.setColor(UiKit.YELLOW);
        g.fill(new RoundRectangle2D.Double(sx + 8, sy + 8, sw - 16, sh - 16, 20, 20));
        g.setFont(UiKit.bold(62));
        UiKit.outlinedText(g, "도전! 분식왕", w / 2.0, sy + 80, UiKit.RED, Color.WHITE, 10f);
        g.setFont(UiKit.bold(16));
        g.setColor(UiKit.BROWN);
        UiKit.centerText(g, "주문 받고 · 요리하고 · 서빙하고 · 치우고!  오늘 영업도 힘내 볼까요?", w / 2.0, sy + sh - 18);

        // 입력 카드
        int cardX = w / 2 - CARD_W / 2;
        int cardY = cardTop();
        g.setColor(new Color(0, 0, 0, 50));
        g.fill(new RoundRectangle2D.Double(cardX + 4, cardY + 6, CARD_W, CARD_H, 24, 24));
        g.setColor(UiKit.CREAM);
        g.fill(new RoundRectangle2D.Double(cardX, cardY, CARD_W, CARD_H, 24, 24));
        g.setColor(UiKit.CREAM_DARK.darker());
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Double(cardX, cardY, CARD_W, CARD_H, 24, 24));
        g.dispose();
    }

    /** 시작·대기실·결과 화면이 함께 쓰는 가게 앞 배경(타일 벽 + 차양 + 조리대 위 음식들) */
    static void paintShopBackdrop(Graphics2D g, int w, int h) {
        g.setColor(new Color(0xFF, 0xF8, 0xEC));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0xEF, 0xE4, 0xD0));
        for (int row = 0, y = 0; y < h; row++, y += 28) {
            g.drawLine(0, y, w, y);
            int offset = (row % 2) * 28;
            for (int x = -offset; x < w; x += 56) {
                g.drawLine(x, y, x, y + 28);
            }
        }
        int stripes = Math.max(8, w / 70);
        Sprites.awning(g, 0, 0, w, 74, stripes);

        // 아래쪽 스테인리스 조리대와 음식들
        int counterY = h - 92;
        g.setColor(new Color(0xA9, 0xB4, 0xBB));
        g.fillRect(0, counterY + 18, w, h - counterY);
        g.setColor(new Color(0xE4, 0xE9, 0xEC));
        g.fillRect(0, counterY, w, 26);
        g.setColor(UiKit.STEEL_DARK);
        g.drawLine(0, counterY + 26, w, counterY + 26);
        Menu[] menus = Menu.values();
        double gap = w / (menus.length + 1.0);
        for (int i = 0; i < menus.length; i++) {
            Sprites.dish(g, menus[i], gap * (i + 1), counterY + 8, 84, 0.55);
        }
    }
}
