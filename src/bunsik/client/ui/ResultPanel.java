package bunsik.client.ui;

import bunsik.common.Message;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * 결과 화면. 1P는 오늘의 영업 성적, 2P는 승자와 두 사람의 성적을 보여 준다.
 */
final class ResultPanel extends JPanel {

    private record Row(String nickname, int satisfaction, int served, int gold, int visited) {
    }

    private static final int TABLE_W = 640;

    private final UiKit.RoundButton lobbyButton = new UiKit.RoundButton("대기실로", UiKit.RED);
    private final UiKit.RoundButton titleButton = new UiKit.RoundButton("처음 화면으로", UiKit.BROWN_SOFT);
    private final List<Row> rows = new ArrayList<>();
    private int winner = -2;
    private int mySlot;

    ResultPanel(GameFrame frame) {
        setLayout(null);
        add(lobbyButton);
        add(titleButton);
        lobbyButton.addActionListener(e -> frame.backToLobby());
        titleButton.addActionListener(e -> frame.leaveToTitle());
    }

    /** GAME_OVER|승자|인원|닉네임:만족도:판매:골드:손님 ... */
    void showResult(Message m, int mySlot) {
        this.mySlot = mySlot;
        winner = m.getInt(0);
        int count = m.getInt(1);
        rows.clear();
        for (int i = 0; i < count; i++) {
            String[] f = m.get(2 + i).split(":");
            rows.add(new Row(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                    Integer.parseInt(f[4])));
        }
        lobbyButton.setVisible(winner != -2);
        doLayout();
        repaint();
    }

    @Override
    public void doLayout() {
        int cx = getWidth() / 2;
        int y = 470;
        if (lobbyButton.isVisible()) {
            lobbyButton.setBounds(cx - 190, y, 180, 56);
            titleButton.setBounds(cx + 10, y, 180, 56);
        } else {
            titleButton.setBounds(cx - 100, y, 200, 56);
        }
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        UiKit.smooth(g);
        int w = getWidth();
        int h = getHeight();
        TitlePanel.paintShopBackdrop(g, w, h);

        g.setFont(UiKit.bold(44));
        UiKit.outlinedText(g, "영업 종료!", w / 2.0, 140, UiKit.RED, Color.WHITE, 9f);

        String headline;
        if (winner == -2) {
            headline = "오늘의 영업 성적";
        } else if (winner == -1) {
            headline = "무승부! 두 가게 모두 대단했어요";
        } else {
            String name = winner < rows.size() ? rows.get(winner).nickname() : "?";
            headline = (winner == mySlot ? "승리! " : "아쉬운 패배... ") + name + " 사장님의 분식집이 이겼어요";
        }
        g.setFont(UiKit.bold(20));
        g.setColor(UiKit.BROWN);
        UiKit.centerText(g, headline, w / 2.0, 186);

        int x = (w - TABLE_W) / 2;
        int y = 214;
        int rowH = 64;
        String[] heads = {"사장님", "만족도", "판매", "골드", "손님"};
        double[] colX = {0.20, 0.44, 0.60, 0.75, 0.90};
        g.setColor(new Color(0, 0, 0, 50));
        g.fill(new RoundRectangle2D.Double(x + 4, y + 6, TABLE_W, 44 + rowH * rows.size(), 22, 22));
        g.setColor(UiKit.CREAM);
        g.fill(new RoundRectangle2D.Double(x, y, TABLE_W, 44 + rowH * rows.size(), 22, 22));
        g.setColor(UiKit.RED);
        g.fill(new RoundRectangle2D.Double(x, y, TABLE_W, 44, 22, 22));
        g.fillRect(x, y + 22, TABLE_W, 22);
        g.setFont(UiKit.bold(15));
        g.setColor(Color.WHITE);
        for (int c = 0; c < heads.length; c++) {
            UiKit.centerText(g, heads[c], x + TABLE_W * colX[c], y + 28);
        }
        for (int r = 0; r < rows.size(); r++) {
            Row row = rows.get(r);
            int ry = y + 44 + r * rowH;
            if (r == winner) {
                g.setColor(UiKit.alpha(UiKit.YELLOW, 150));
                g.fillRect(x + 3, ry, TABLE_W - 6, rowH - 2);
            }
            g.setColor(UiKit.BROWN);
            g.setFont(UiKit.bold(20));
            String nick = row.nickname() + (r == mySlot && rows.size() > 1 ? " (나)" : "");
            UiKit.centerText(g, nick, x + TABLE_W * colX[0], ry + 40);
            g.setColor(UiKit.RED_DARK);
            g.setFont(UiKit.bold(24));
            UiKit.centerText(g, row.satisfaction() < 0 ? "-" : row.satisfaction() + "%", x + TABLE_W * colX[1], ry + 41);
            g.setColor(UiKit.BROWN);
            g.setFont(UiKit.bold(20));
            UiKit.centerText(g, String.valueOf(row.served()), x + TABLE_W * colX[2], ry + 40);
            UiKit.centerText(g, String.valueOf(row.gold()), x + TABLE_W * colX[3], ry + 40);
            UiKit.centerText(g, String.valueOf(row.visited()), x + TABLE_W * colX[4], ry + 40);
        }
        g.setColor(UiKit.CREAM_DARK.darker());
        g.setStroke(new BasicStroke(3f));
        g.draw(new RoundRectangle2D.Double(x, y, TABLE_W, 44 + rowH * rows.size(), 22, 22));

        g.setFont(UiKit.plain(13));
        g.setColor(UiKit.BROWN_SOFT);
        UiKit.centerText(g, "승리 기준: 만족도가 높은 쪽 → 같으면 판매 수", w / 2.0, 450);
        g.dispose();
    }
}
