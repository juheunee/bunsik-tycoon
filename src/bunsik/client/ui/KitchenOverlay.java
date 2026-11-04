package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.Menu;
import bunsik.common.StoreSnapshot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 부엌 화면(요리 팝업). 가운데 큰 접시 + 모서리의 메뉴 카드. 메뉴 카드를 마우스로 끌어 접시에 놓으면 요리가 시작된다.
 *
 * <p>컴포넌트가 아니라 "상태 + 그리기 + 클릭 판정"만 가진 클래스다. 마우스 이벤트는 {@link GamePanel}이 받아
 * 식당 기준 좌표로 바꾼 뒤 이 클래스에 넘겨 준다. (별도 창(JDialog)을 쓰면 키보드 포커스가 꼬이기 쉬워서 이렇게 했다.)
 * 실제 조리 진행(남은 시간)은 서버가 보내 준 스냅샷 값을 그대로 보여 준다.
 */
final class KitchenOverlay {

    /** 팝업 판(식당 기준 좌표) */
    static final double PX = 40;
    static final double PY = 18;
    static final double PW = 720;
    static final double PH = 484;
    /** 큰 접시 */
    static final double PLATE_X = 400;
    static final double PLATE_Y = 228;
    static final double PLATE_R = 112;
    private static final double CARD_W = 124;
    private static final double CARD_H = 112;
    /** 결과 메시지를 보여 준 뒤 팝업이 닫히기까지 */
    static final long DONE_SHOW_MS = 1300;

    boolean open;
    Menu dragging;
    double dragX;
    double dragY;
    /** 요리 요청을 보낸 시각(서버 응답 전 중복 요청 방지). 0이면 없음 */
    long pendingSince;
    /** 요리 완료 메시지를 띄운 시각. 0이면 없음 */
    long doneAt;
    Menu doneMenu;

    void reset() {
        open = false;
        dragging = null;
        pendingSince = 0;
        doneAt = 0;
        doneMenu = null;
    }

    /** 메뉴 카드 위치: 네 모서리 + 아래 가운데 */
    static RoundRectangle2D.Double cardRect(int i) {
        double left = PX + 26;
        double right = PX + PW - 26 - CARD_W;
        double top = PY + 26;
        double bottom = PY + PH - 26 - CARD_H;
        double[][] xy = {{left, top}, {right, top}, {left, bottom}, {right, bottom}, {PLATE_X - CARD_W / 2, bottom}};
        return new RoundRectangle2D.Double(xy[i][0], xy[i][1], CARD_W, CARD_H, 18, 18);
    }

    static Menu menuAt(double x, double y) {
        Menu[] menus = Menu.values();
        for (int i = 0; i < menus.length; i++) {
            if (cardRect(i).contains(x, y)) {
                return menus[i];
            }
        }
        return null;
    }

    static boolean onPlate(double x, double y) {
        return Math.hypot(x - PLATE_X, y - PLATE_Y) <= PLATE_R + 10;
    }

    /** 지금 새 요리를 시작할 수 있는지 */
    boolean canStartCooking(StoreSnapshot s, long now) {
        boolean pending = pendingSince > 0 && now - pendingSince < 800;
        return s != null && s.cooking == null && s.held == null && !pending && doneAt == 0;
    }

    void draw(Graphics2D g, StoreSnapshot s, long now) {
        // 뒤 식당을 어둡게
        g.setColor(new Color(0x1A, 0x10, 0x0A, 150));
        g.fillRect(0, 0, 800, 520);

        // 스테인리스 조리대 판
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(PX + 4, PY + 6, PW, PH, 30, 30));
        g.setPaint(new GradientPaint(0, (float) PY, new Color(0xF1, 0xF4, 0xF6), 0, (float) (PY + PH),
                new Color(0xC6, 0xCE, 0xD4)));
        g.fill(new RoundRectangle2D.Double(PX, PY, PW, PH, 30, 30));
        g.setColor(new Color(255, 255, 255, 90));
        g.setStroke(new BasicStroke(1f));
        for (double y = PY + 14; y < PY + PH - 10; y += 7) {
            g.drawLine((int) PX + 20, (int) y, (int) (PX + PW - 20), (int) y);
        }
        g.setColor(UiKit.STEEL_DARK);
        g.setStroke(new BasicStroke(4f));
        g.draw(new RoundRectangle2D.Double(PX, PY, PW, PH, 30, 30));

        // 제목 리본
        double rw = 300;
        double rx = PLATE_X - rw / 2;
        g.setColor(UiKit.RED);
        g.fill(new RoundRectangle2D.Double(rx, PY + 22, rw, 34, 34, 34));
        g.setColor(Color.WHITE);
        g.setFont(UiKit.bold(15));
        UiKit.centerTextIn(g, "부엌 · 메뉴를 접시로 끌어다 놓으세요", rx, PY + 22, rw, 34);

        boolean canCook = canStartCooking(s, now);
        drawCards(g, canCook);
        drawPlate(g, s, now);
        drawStatus(g, s, now, canCook);

        // 끌고 있는 음식
        if (dragging != null) {
            if (onPlate(dragX, dragY)) {
                g.setColor(UiKit.alpha(UiKit.YELLOW, 200));
                g.setStroke(new BasicStroke(8f));
                g.draw(new Ellipse2D.Double(PLATE_X - PLATE_R - 8, PLATE_Y - PLATE_R - 8, (PLATE_R + 8) * 2,
                        (PLATE_R + 8) * 2));
            }
            Sprites.dish(g, dragging, dragX, dragY, 78, 1.0);
        }

        g.setFont(UiKit.bold(12));
        g.setColor(UiKit.BROWN_SOFT);
        UiKit.centerText(g, "ESC 닫기", PX + PW - 60, PY + PH - 8);
    }

    private void drawCards(Graphics2D g, boolean canCook) {
        Menu[] menus = Menu.values();
        for (int i = 0; i < menus.length; i++) {
            Menu m = menus[i];
            RoundRectangle2D.Double r = cardRect(i);
            boolean faded = !canCook || m == dragging;
            g.setColor(new Color(0, 0, 0, 45));
            g.fill(new RoundRectangle2D.Double(r.x + 3, r.y + 5, r.width, r.height, 18, 18));
            g.setColor(faded ? new Color(0xEE, 0xE8, 0xDE) : Color.WHITE);
            g.fill(r);
            g.setStroke(new BasicStroke(3f));
            g.setColor(faded ? new Color(0xC9, 0xBE, 0xAE) : UiKit.RED);
            g.draw(r);
            // 메뉴 사진 자리(지금은 코드로 그린 그림, 나중에 실제 이미지로 교체)
            Sprites.dish(g, m, r.x + r.width / 2, r.y + 42, 60, 1.0);
            if (faded) {
                g.setColor(new Color(255, 255, 255, 140));
                g.fill(r);
            }
            g.setColor(UiKit.BROWN);
            g.setFont(UiKit.bold(16));
            UiKit.centerText(g, m.label, r.x + r.width / 2, r.y + 90);
            g.setColor(UiKit.BROWN_SOFT);
            g.setFont(UiKit.plain(12));
            UiKit.centerText(g, (m.cookMs / 1000.0) + "초 · " + m.price + "G", r.x + r.width / 2, r.y + 105);
        }
    }

    private void drawPlate(Graphics2D g, StoreSnapshot s, long now) {
        Menu onPlate = null;
        double progress = 0;
        if (doneAt > 0) {
            onPlate = doneMenu;
            progress = 1;
        } else if (s != null && s.cooking != null) {
            onPlate = s.cooking;
            progress = s.cookTotalMs <= 0 ? 1 : 1.0 - s.cookRemainMs / (double) s.cookTotalMs;
        }
        // 접시 아래 그림자와 큰 접시
        Sprites.dish(g, onPlate, PLATE_X, PLATE_Y, PLATE_R * 2, 1.0);

        if (onPlate != null && doneAt == 0) {
            Sprites.steam(g, PLATE_X - 20, PLATE_Y - 50, now, 0);
            Sprites.steam(g, PLATE_X + 22, PLATE_Y - 46, now, 0.6);
            // 접시 둘레 진행 링
            g.setStroke(new BasicStroke(9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(new Color(0, 0, 0, 40));
            g.draw(new Ellipse2D.Double(PLATE_X - PLATE_R - 12, PLATE_Y - PLATE_R - 12, (PLATE_R + 12) * 2,
                    (PLATE_R + 12) * 2));
            g.setColor(UiKit.ORANGE);
            g.draw(new Arc2D.Double(PLATE_X - PLATE_R - 12, PLATE_Y - PLATE_R - 12, (PLATE_R + 12) * 2,
                    (PLATE_R + 12) * 2, 90, -360 * progress, Arc2D.OPEN));
        }
        if (onPlate == null) {
            g.setColor(UiKit.alpha(UiKit.BROWN_SOFT, 150));
            g.setFont(UiKit.bold(15));
            UiKit.centerText(g, "여기에 놓기", PLATE_X, PLATE_Y + 6);
        }
    }

    private void drawStatus(Graphics2D g, StoreSnapshot s, long now, boolean canCook) {
        // 상태 문구는 제목 리본과 접시 사이에 쓴다(아래쪽에는 메뉴 카드가 있음)
        double y = PLATE_Y - PLATE_R - 22;
        if (doneAt > 0) {
            double t = Math.min(1, (now - doneAt) / 200.0);
            double bw = 260 * (0.8 + 0.2 * t);
            double bh = 56;
            g.setColor(UiKit.YELLOW);
            g.fill(new RoundRectangle2D.Double(PLATE_X - bw / 2, PLATE_Y - bh / 2, bw, bh, 22, 22));
            g.setColor(UiKit.RED);
            g.setStroke(new BasicStroke(4f));
            g.draw(new RoundRectangle2D.Double(PLATE_X - bw / 2, PLATE_Y - bh / 2, bw, bh, 22, 22));
            g.setFont(UiKit.bold(28));
            UiKit.centerTextIn(g, "요리 완료!", PLATE_X - bw / 2, PLATE_Y - bh / 2, bw, bh);
            g.setFont(UiKit.bold(14));
            g.setColor(UiKit.BROWN);
            UiKit.centerText(g, doneMenu == null ? "" : doneMenu.withObjectParticle() + " 들고 손님에게 가져다 주세요",
                    PLATE_X, y);
        } else if (s != null && s.cooking != null) {
            g.setFont(UiKit.bold(15));
            g.setColor(UiKit.BROWN);
            UiKit.centerText(g, s.cooking.label + " 조리 중...  " + String.format("%.1f초", s.cookRemainMs / 1000.0),
                    PLATE_X, y);
        } else if (!canCook && s != null && s.held != null) {
            g.setFont(UiKit.bold(14));
            g.setColor(UiKit.RED_DARK);
            UiKit.centerText(g, "이미 음식을 들고 있어요", PLATE_X, y);
        }
    }
}
