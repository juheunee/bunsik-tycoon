package bunsik.client.render;

import bunsik.client.ui.UiKit;
import bunsik.common.Menu;
import bunsik.common.StoreLayout;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * 게임에 나오는 모든 그림을 코드로 그리는 곳(MVP용 임시 그래픽).
 *
 * <p>나중에 외부 이미지로 바꿀 때는 이 파일의 메서드 안쪽만 {@code g.drawImage(...)}로 바꾸면 되도록,
 * 그림 그리는 코드를 전부 여기 모아 두었다. 좌표는 모두 "식당 기준 좌표"(800 × 520)다.
 */
public final class Sprites {

    private Sprites() {
    }

    /** 손님 표정 */
    public enum Mood { HAPPY, NEUTRAL, ANGRY, EATING }

    private static final Color SKIN = new Color(0xFF, 0xDB, 0xB8);
    private static final Color SKIN_LINE = new Color(0xDE, 0xA2, 0x78);
    private static final Color INK = new Color(0x2A, 0x1A, 0x12);
    private static final Color[] SHIRTS = {
            new Color(0x3D, 0x7D, 0xD8), new Color(0x3A, 0xA5, 0x69), new Color(0x8A, 0x5B, 0xC9),
            new Color(0xF0, 0x8A, 0x24), new Color(0x1F, 0xA3, 0xA3), new Color(0xE5, 0x6B, 0x9A)
    };
    private static final Color[] HAIRS = {
            new Color(0x2B, 0x21, 0x1C), new Color(0x6B, 0x42, 0x26), new Color(0x4A, 0x35, 0x28),
            new Color(0x8E, 0x8A, 0x86)
    };
    private static final Color PLATE_RIM = new Color(0x7C, 0xC4, 0x7E);
    private static final Color PLATE_RIM_DARK = new Color(0x4E, 0x98, 0x55);
    private static final Color PLATE_IN = new Color(0xF5, 0xFB, 0xF2);
    private static final Color SAUCE = new Color(0xE0, 0x41, 0x2C);
    private static final Color SAUCE_DARK = new Color(0xB8, 0x2C, 0x1C);
    private static final Color RICE_CAKE = new Color(0xFF, 0xF2, 0xDE);
    private static final Color GOLD = new Color(0xF7, 0xC5, 0x31);
    private static final Color GOLD_DARK = new Color(0xB5, 0x82, 0x0E);

    // =====================================================================
    // 배경
    // =====================================================================

    /** 홀 바닥(체크무늬 타일)과 벽 아래 그림자 */
    public static void floor(Graphics2D g) {
        int tile = 40;
        Color a = new Color(0xFF, 0xF4, 0xDE);
        Color b = new Color(0xF2, 0xDC, 0xB4);
        for (int y = StoreLayout.WALL_HEIGHT; y < StoreLayout.HEIGHT; y += tile) {
            for (int x = StoreLayout.ENTRANCE_RIGHT; x < StoreLayout.KITCHEN_LEFT; x += tile) {
                boolean even = ((x - StoreLayout.ENTRANCE_RIGHT) / tile + (y - StoreLayout.WALL_HEIGHT) / tile) % 2 == 0;
                g.setColor(even ? a : b);
                g.fillRect(x, y, tile, tile);
            }
        }
        g.setPaint(new GradientPaint(0, StoreLayout.WALL_HEIGHT, new Color(0, 0, 0, 45),
                0, StoreLayout.WALL_HEIGHT + 14, new Color(0, 0, 0, 0)));
        g.fillRect(0, StoreLayout.WALL_HEIGHT, StoreLayout.WIDTH, 14);
    }

    /** 위쪽 벽: 흰 타일 + 빨간 띠 */
    public static void wall(Graphics2D g) {
        g.setColor(new Color(0xFF, 0xFA, 0xF0));
        g.fillRect(0, 0, StoreLayout.WIDTH, StoreLayout.WALL_HEIGHT);
        g.setColor(new Color(0xEC, 0xE1, 0xCE));
        g.setStroke(new BasicStroke(1f));
        for (int row = 0, y = 0; y < 80; row++, y += 16) {
            g.drawLine(0, y, StoreLayout.WIDTH, y);
            int offset = (row % 2) * 16;
            for (int x = -offset; x < StoreLayout.WIDTH; x += 32) {
                g.drawLine(x, y, x, Math.min(y + 16, 80));
            }
        }
        g.setColor(UiKit.RED);
        g.fillRect(0, 80, StoreLayout.WIDTH, 16);
        g.setColor(UiKit.YELLOW);
        g.fillRect(0, 80, StoreLayout.WIDTH, 3);
    }

    /** 벽에 붙은 노란 메뉴판 */
    public static void menuBoard(Graphics2D g) {
        double x = 160;
        double y = 6;
        double w = 440;
        double h = 70;
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new RoundRectangle2D.Double(x + 3, y + 4, w, h, 12, 12));
        g.setColor(new Color(0x8B, 0x5A, 0x2B));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 12, 12));
        g.setColor(new Color(0xFF, 0xD8, 0x4D));
        g.fill(new RoundRectangle2D.Double(x + 5, y + 5, w - 10, h - 10, 8, 8));

        g.setColor(UiKit.RED);
        g.fill(new RoundRectangle2D.Double(x + 10, y + 10, 50, h - 20, 8, 8));
        g.setColor(Color.WHITE);
        g.setFont(UiKit.bold(17));
        UiKit.centerTextIn(g, "메뉴", x + 10, y + 10, 50, h - 20);

        Menu[] menus = Menu.values();
        double startX = x + 100;
        double gap = (w - 110) / menus.length;
        for (int i = 0; i < menus.length; i++) {
            double cx = startX + i * gap;
            dish(g, menus[i], cx, y + 23, 28, 0.85);
            g.setColor(UiKit.BROWN);
            g.setFont(UiKit.bold(11));
            UiKit.centerText(g, menus[i].label, cx, y + 52);
            g.setColor(UiKit.RED_DARK);
            g.setFont(UiKit.bold(10.5f));
            UiKit.centerText(g, menus[i].price + "G", cx, y + 63.5);
        }
    }

    /** 왼쪽 입구: 간판, 차양, 출입문, 대기줄 자리 */
    public static void entrance(Graphics2D g, long now) {
        int right = StoreLayout.ENTRANCE_RIGHT;
        int top = StoreLayout.WALL_HEIGHT;
        int h = StoreLayout.HEIGHT - top;
        // 테라조 바닥
        g.setColor(new Color(0xE6, 0xDE, 0xD0));
        g.fillRect(0, top, right, h);
        for (int i = 0; i < 140; i++) {
            int sx = 18 + (i * 53) % (right - 20);
            int sy = top + (i * 97) % h;
            g.setColor(i % 3 == 0 ? new Color(0xC9, 0xBD, 0xAA) : new Color(0xF6, 0xF2, 0xEA));
            g.fill(new Ellipse2D.Double(sx, sy, 3, 3));
        }
        // 바깥 인도
        g.setColor(new Color(0xB9, 0xBE, 0xC2));
        g.fillRect(0, top, 14, h);
        g.setColor(new Color(0x9C, 0xA2, 0xA7));
        for (int y = top; y < StoreLayout.HEIGHT; y += 26) {
            g.drawLine(0, y, 14, y);
        }
        // 출입문 틀(가운데가 열린 문)
        g.setColor(new Color(0x6E, 0x45, 0x26));
        g.fillRect(14, top, 7, 64);
        g.fillRect(14, StoreLayout.HEIGHT - 50, 7, 50);
        g.setColor(new Color(0xBF, 0xE3, 0xF0, 170));
        g.fillRect(21, top, 4, 64);
        g.fillRect(21, StoreLayout.HEIGHT - 50, 4, 50);
        // 홀과의 경계 몰딩
        g.setColor(new Color(0xC9, 0xA7, 0x7C));
        g.fillRect(right - 3, top, 3, h);

        // 줄 서는 곳 표시
        g.setColor(UiKit.BROWN_SOFT);
        g.setFont(UiKit.bold(11));
        UiKit.centerText(g, "줄 서는 곳", 72, top + 32);
        for (int i = 0; i < StoreLayout.QUEUE_X.length; i++) {
            double qx = StoreLayout.QUEUE_X[i];
            double qy = StoreLayout.QUEUE_Y[i];
            g.setColor(new Color(0xFF, 0xC9, 0x3C, 200));
            g.fill(new RoundRectangle2D.Double(qx - 20, qy - 5, 40, 10, 10, 10));
            g.setColor(new Color(0xC7, 0x8F, 0x10));
            g.draw(new RoundRectangle2D.Double(qx - 20, qy - 5, 40, 10, 10, 10));
        }
        // 발판
        g.setColor(UiKit.RED_DARK);
        g.fill(new RoundRectangle2D.Double(28, StoreLayout.HEIGHT - 40, 88, 30, 8, 8));
        g.setColor(UiKit.YELLOW_SOFT);
        g.setFont(UiKit.bold(11));
        UiKit.centerTextIn(g, "어서오세요", 28, StoreLayout.HEIGHT - 40, 88, 30);

        // 벽 위: 가게 간판과 차양
        g.setColor(UiKit.RED_DARK);
        g.fill(new RoundRectangle2D.Double(8, 6, right - 16, 40, 10, 10));
        g.setColor(UiKit.RED);
        g.fill(new RoundRectangle2D.Double(10, 8, right - 20, 36, 8, 8));
        g.setFont(UiKit.bold(22));
        UiKit.outlinedText(g, "분식왕", right / 2.0, 34, UiKit.YELLOW, UiKit.RED_DARK, 3f);
        awning(g, 0, 50, right, 34, 6);
    }

    /** 빨강·하양 줄무늬 차양(아래가 물결 모양) */
    public static void awning(Graphics2D g, double x, double y, double w, double h, int stripes) {
        double sw = w / stripes;
        for (int i = 0; i < stripes; i++) {
            double sx = x + i * sw;
            Color c = i % 2 == 0 ? UiKit.RED : Color.WHITE;
            g.setColor(c);
            g.fill(new Rectangle2D.Double(sx, y, sw + 0.5, h - sw / 2));
            g.fill(new Ellipse2D.Double(sx, y + h - sw, sw, sw));
        }
        g.setColor(new Color(0, 0, 0, 35));
        g.fill(new Rectangle2D.Double(x, y, w, 4));
    }

    /** 오른쪽 부엌: 후드, 조리대, 조리 기구들 */
    public static void kitchen(Graphics2D g, long now, boolean highlight) {
        int left = StoreLayout.KITCHEN_LEFT;
        int top = StoreLayout.WALL_HEIGHT;
        int w = StoreLayout.WIDTH - left;
        // 회색 타일 바닥
        for (int y = top; y < StoreLayout.HEIGHT; y += 30) {
            for (int x = left; x < StoreLayout.WIDTH; x += 30) {
                g.setColor(((x - left) / 30 + (y - top) / 30) % 2 == 0
                        ? new Color(0xDD, 0xE3, 0xE7) : new Color(0xCE, 0xD6, 0xDB));
                g.fillRect(x, y, 30, 30);
            }
        }
        // 벽 위 후드
        Path2D hood = new Path2D.Double();
        hood.moveTo(left + 18, 6);
        hood.lineTo(StoreLayout.WIDTH - 8, 6);
        hood.lineTo(StoreLayout.WIDTH, 70);
        hood.lineTo(left + 8, 70);
        hood.closePath();
        g.setPaint(new GradientPaint(0, 6, new Color(0xF0, 0xF3, 0xF5), 0, 70, new Color(0xA9, 0xB4, 0xBB)));
        g.fill(hood);
        g.setColor(UiKit.STEEL_DARK);
        g.setStroke(new BasicStroke(2f));
        g.draw(hood);
        g.setColor(UiKit.RED);
        g.fill(new RoundRectangle2D.Double(left + 62, 24, 70, 28, 8, 8));
        g.setColor(Color.WHITE);
        g.setFont(UiKit.bold(16));
        UiKit.centerTextIn(g, "주 방", left + 62, 24, 70, 28);

        // 조리기구
        double sx = left + 58;
        double sw = 122;
        tteokbokkiPan(g, sx, top + 10, sw, 68, now);
        eomukPot(g, sx, top + 92, sw, 68, now);
        fryer(g, sx, top + 174, sw, 68, now);
        steamer(g, sx, top + 256, sw, 74, now);
        cuttingBoard(g, sx, top + 344, sw, 72);

        // 손님 쪽 스테인리스 조리대(여기로 다가가서 스페이스)
        double cx = left;
        double cy = top + 4;
        double ch = StoreLayout.HEIGHT - top - 10;
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new RoundRectangle2D.Double(cx - 4, cy + 4, 46, ch, 10, 10));
        g.setPaint(new GradientPaint((float) cx, 0, new Color(0xF3, 0xF6, 0xF8), (float) (cx + 40), 0,
                new Color(0xB8, 0xC2, 0xC9)));
        g.fill(new RoundRectangle2D.Double(cx, cy, 40, ch, 10, 10));
        g.setColor(UiKit.STEEL_DARK);
        g.setStroke(new BasicStroke(2f));
        g.draw(new RoundRectangle2D.Double(cx, cy, 40, ch, 10, 10));
        g.setColor(new Color(255, 255, 255, 140));
        g.draw(new Line2D.Double(cx + 8, cy + 10, cx + 8, cy + ch - 10));
        if (highlight) {
            float pulse = (float) (0.55 + 0.45 * Math.sin(now / 160.0));
            g.setColor(UiKit.alpha(UiKit.YELLOW, (int) (120 + 135 * pulse)));
            g.setStroke(new BasicStroke(5f));
            g.draw(new RoundRectangle2D.Double(cx - 4, cy - 2, 48, ch + 4, 14, 14));
        }
    }

    private static void tteokbokkiPan(Graphics2D g, double x, double y, double w, double h, long now) {
        g.setColor(new Color(0x34, 0x34, 0x36));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
        g.setColor(SAUCE_DARK);
        g.fill(new RoundRectangle2D.Double(x + 6, y + 6, w - 12, h - 12, 10, 10));
        g.setColor(SAUCE);
        g.fill(new RoundRectangle2D.Double(x + 9, y + 9, w - 18, h - 18, 8, 8));
        for (int i = 0; i < 9; i++) {
            double rx = x + 18 + (i * 37) % (w - 40);
            double ry = y + 16 + (i * 23) % (h - 30);
            Graphics2D t = (Graphics2D) g.create();
            t.translate(rx + 9, ry + 3);
            t.rotate((i * 47 % 180) * Math.PI / 180);
            t.setColor(RICE_CAKE);
            t.fill(new RoundRectangle2D.Double(-10, -3.5, 20, 7, 7, 7));
            t.setColor(new Color(0xE3, 0xA6, 0x86));
            t.draw(new RoundRectangle2D.Double(-10, -3.5, 20, 7, 7, 7));
            t.dispose();
        }
        steam(g, x + w * 0.35, y + 6, now, 0);
        steam(g, x + w * 0.7, y + 6, now, 1.7);
    }

    private static void eomukPot(Graphics2D g, double x, double y, double w, double h, long now) {
        g.setColor(UiKit.STEEL_DARK);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 10, 10));
        g.setColor(new Color(0xD4, 0xB0, 0x72));
        g.fill(new RoundRectangle2D.Double(x + 6, y + 6, w - 12, h - 12, 6, 6));
        g.setStroke(new BasicStroke(2.5f));
        for (int i = 0; i < 6; i++) {
            double bx = x + 16 + i * 18;
            g.setColor(new Color(0x9C, 0x6B, 0x3A));
            g.draw(new Line2D.Double(bx, y + 10, bx + 10, y + h - 8));
            for (int k = 0; k < 3; k++) {
                g.setColor(k % 2 == 0 ? new Color(0xF2, 0xD2, 0x99) : new Color(0xE6, 0xBA, 0x70));
                g.fill(new RoundRectangle2D.Double(bx + k * 3 - 2, y + 18 + k * 12, 14, 10, 6, 6));
            }
        }
        steam(g, x + w * 0.5, y + 4, now, 0.8);
    }

    private static void fryer(Graphics2D g, double x, double y, double w, double h, long now) {
        g.setColor(new Color(0x9A, 0xA6, 0xAE));
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 10, 10));
        g.setColor(new Color(0xE9, 0xB8, 0x45));
        g.fill(new RoundRectangle2D.Double(x + 7, y + 7, w - 14, h - 14, 6, 6));
        for (int i = 0; i < 6; i++) {
            double px = x + 16 + (i * 29) % (w - 36);
            double py = y + 14 + (i * 17) % (h - 30);
            g.setColor(new Color(0xD8, 0x8E, 0x2A));
            g.fill(new RoundRectangle2D.Double(px, py, 22, 13, 9, 9));
            g.setColor(new Color(0xF7, 0xD4, 0x7A));
            g.fill(new Ellipse2D.Double(px + 4, py + 3, 4, 3));
            g.fill(new Ellipse2D.Double(px + 12, py + 6, 4, 3));
        }
        // 기름 방울
        double t = (now % 900) / 900.0;
        g.setColor(new Color(255, 255, 255, (int) (180 * (1 - t))));
        g.draw(new Ellipse2D.Double(x + 30 + 10 * t, y + 40, 4 + 4 * t, 4 + 4 * t));
    }

    private static void steamer(Graphics2D g, double x, double y, double w, double h, long now) {
        double d = Math.min(w, h) - 6;
        double cx = x + w / 2;
        g.setColor(UiKit.STEEL_DARK);
        g.fill(new Ellipse2D.Double(cx - d / 2 - 3, y, d + 6, d + 6));
        g.setPaint(new GradientPaint(0, (float) y, new Color(0xF2, 0xF5, 0xF7), 0, (float) (y + d),
                new Color(0xB3, 0xBD, 0xC4)));
        g.fill(new Ellipse2D.Double(cx - d / 2, y + 3, d, d));
        g.setColor(UiKit.STEEL_DARK);
        g.fill(new Ellipse2D.Double(cx - 8, y + d / 2 - 5, 16, 10));
        // 옆에 놓인 순대 한 줄
        g.setColor(new Color(0x4B, 0x25, 0x30));
        g.fill(new RoundRectangle2D.Double(x - 2, y + h - 16, 40, 12, 12, 12));
        steam(g, cx, y + 2, now, 2.4);
    }

    private static void cuttingBoard(Graphics2D g, double x, double y, double w, double h) {
        g.setColor(new Color(0xA8, 0x77, 0x45));
        g.fill(new RoundRectangle2D.Double(x, y + 4, w, h - 4, 10, 10));
        g.setColor(new Color(0xD1, 0xA3, 0x6A));
        g.fill(new RoundRectangle2D.Double(x, y, w, h - 6, 10, 10));
        g.setColor(new Color(0x1F, 0x2A, 0x1F));
        g.fill(new RoundRectangle2D.Double(x + 10, y + 14, 66, 18, 18, 18));
        for (int i = 0; i < 3; i++) {
            double cx = x + 30 + i * 26;
            double cy = y + 48;
            kimbapSlice(g, cx, cy, 10);
        }
    }

    private static void kimbapSlice(Graphics2D g, double cx, double cy, double r) {
        g.setColor(new Color(0x1F, 0x2A, 0x1F));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        g.setColor(new Color(0xFB, 0xFB, 0xF6));
        g.fill(new Ellipse2D.Double(cx - r * 0.8, cy - r * 0.8, r * 1.6, r * 1.6));
        double f = r * 0.22;
        g.setColor(new Color(0xF6, 0xD2, 0x32));
        g.fill(new Ellipse2D.Double(cx - f * 1.3, cy - f * 1.3, f * 2.6, f * 2.6));
        g.setColor(new Color(0xF0, 0x7A, 0x28));
        g.fill(new Ellipse2D.Double(cx - r * 0.45 - f, cy - r * 0.35 - f, f * 2, f * 2));
        g.setColor(new Color(0x3E, 0x9B, 0x3A));
        g.fill(new Ellipse2D.Double(cx + r * 0.35 - f, cy - r * 0.3 - f, f * 2, f * 2));
        g.setColor(new Color(0xF0, 0x8F, 0xA0));
        g.fill(new Ellipse2D.Double(cx - r * 0.2 - f, cy + r * 0.4 - f, f * 2, f * 2));
        g.setColor(new Color(0x8A, 0x5A, 0x2E));
        g.fill(new Ellipse2D.Double(cx + r * 0.35 - f, cy + r * 0.3 - f, f * 1.8, f * 1.8));
    }

    /** 모락모락 김 */
    public static void steam(Graphics2D g, double x, double y, long now, double phase) {
        Graphics2D t = (Graphics2D) g.create();
        t.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 2; i++) {
            double p = ((now / 1400.0) + phase + i * 0.5) % 1.0;
            double yy = y - p * 26;
            int alpha = (int) (150 * Math.sin(p * Math.PI));
            t.setColor(new Color(255, 255, 255, Math.max(0, alpha)));
            double sway = Math.sin((now / 300.0) + phase + i) * 4;
            Path2D s = new Path2D.Double();
            s.moveTo(x + i * 8 - 4, yy);
            s.curveTo(x + i * 8 + sway, yy - 6, x + i * 8 - 8 - sway, yy - 12, x + i * 8 - 4, yy - 18);
            t.draw(s);
        }
        t.dispose();
    }

    // =====================================================================
    // 테이블
    // =====================================================================

    /** 빨간 플라스틱 의자 */
    public static void stool(Graphics2D g, double cx, double cy) {
        g.setColor(new Color(0x8E, 0x1F, 0x16));
        g.fill(new Rectangle2D.Double(cx - 11, cy, 4, 12));
        g.fill(new Rectangle2D.Double(cx + 7, cy, 4, 12));
        g.setColor(new Color(0xB8, 0x2A, 0x1E));
        g.fill(new Ellipse2D.Double(cx - 16, cy - 7, 32, 15));
        g.setColor(new Color(0xE5, 0x45, 0x30));
        g.fill(new Ellipse2D.Double(cx - 15, cy - 8, 30, 12));
        g.setColor(new Color(255, 255, 255, 90));
        g.draw(new Arc2D.Double(cx - 10, cy - 6, 20, 7, 30, 120, Arc2D.OPEN));
    }

    /** 스테인리스 원형 테이블 */
    public static void tableTop(Graphics2D g, double cx, double cy, int number, boolean highlight, long now) {
        g.setColor(new Color(0, 0, 0, 45));
        g.fill(new Ellipse2D.Double(cx - 58, cy - 4, 116, 36));
        g.setColor(new Color(0x5E, 0x67, 0x6D));
        g.fill(new Rectangle2D.Double(cx - 6, cy + 4, 12, 20));
        if (highlight) {
            float pulse = (float) (0.55 + 0.45 * Math.sin(now / 160.0));
            g.setColor(UiKit.alpha(UiKit.YELLOW, (int) (120 + 135 * pulse)));
            g.setStroke(new BasicStroke(6f));
            g.draw(new Ellipse2D.Double(cx - 61, cy - 31, 122, 60));
        }
        g.setPaint(new GradientPaint((float) cx, (float) (cy - 26), new Color(0xF4, 0xF7, 0xF9),
                (float) cx, (float) (cy + 26), new Color(0xA7, 0xB2, 0xBA)));
        g.fill(new Ellipse2D.Double(cx - 56, cy - 26, 112, 52));
        g.setStroke(new BasicStroke(2.2f));
        g.setColor(UiKit.STEEL_DARK);
        g.draw(new Ellipse2D.Double(cx - 56, cy - 26, 112, 52));
        g.setColor(new Color(255, 255, 255, 150));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Arc2D.Double(cx - 46, cy - 20, 92, 40, 100, 70, Arc2D.OPEN));
        // 테이블 번호표
        g.setColor(UiKit.RED);
        g.fill(new Ellipse2D.Double(cx + 36, cy + 4, 18, 18));
        g.setColor(Color.WHITE);
        g.setFont(UiKit.bold(11));
        UiKit.centerTextIn(g, String.valueOf(number), cx + 36, cy + 4, 18, 18);
    }

    // =====================================================================
    // 음식
    // =====================================================================

    /**
     * 초록 멜라민 접시에 담긴 음식. 접시 지름 = size, flatten(1.0=위에서 본 원, 0.6=비스듬히 본 타원).
     * menu가 null이면 빈 접시.
     */
    public static void dish(Graphics2D g0, Menu menu, double cx, double cy, double size, double flatten) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);
        g.scale(size / 100.0, size / 100.0 * flatten);
        plate(g);
        if (menu != null) {
            switch (menu) {
                case TTEOKBOKKI -> tteokbokki(g);
                case GIMBAP -> gimbap(g);
                case SUNDAE -> sundae(g);
                case TWIGIM -> twigim(g);
                case EOMUK -> eomuk(g);
            }
        }
        g.dispose();
    }

    /** 다 먹고 남은 빈 그릇(양념 자국 + 젓가락) */
    public static void emptyDish(Graphics2D g0, double cx, double cy, double size, double flatten) {
        Graphics2D g = (Graphics2D) g0.create();
        g.translate(cx, cy);
        g.scale(size / 100.0, size / 100.0 * flatten);
        plate(g);
        g.setColor(UiKit.alpha(SAUCE, 150));
        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(-22, -18, 40, 34, 200, 110, Arc2D.OPEN));
        g.fill(new Ellipse2D.Double(8, 6, 9, 7));
        g.fill(new Ellipse2D.Double(-6, 14, 6, 5));
        g.setColor(new Color(0xD9, 0xB0, 0x7A));
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(-34, 30, 40, -22));
        g.draw(new Line2D.Double(-28, 36, 44, -14));
        g.dispose();
    }

    private static void plate(Graphics2D g) {
        g.setColor(new Color(0, 0, 0, 45));
        g.fill(new Ellipse2D.Double(-47, -42, 100, 100));
        g.setColor(PLATE_RIM);
        g.fill(new Ellipse2D.Double(-48, -48, 96, 96));
        g.setStroke(new BasicStroke(2.5f));
        g.setColor(PLATE_RIM_DARK);
        g.draw(new Ellipse2D.Double(-48, -48, 96, 96));
        g.setColor(PLATE_IN);
        g.fill(new Ellipse2D.Double(-37, -37, 74, 74));
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(0xCF, 0xE6, 0xCC));
        g.draw(new Ellipse2D.Double(-37, -37, 74, 74));
    }

    private static void tteokbokki(Graphics2D g) {
        g.setColor(SAUCE_DARK);
        g.fill(new Ellipse2D.Double(-33, -31, 66, 62));
        g.setColor(SAUCE);
        g.fill(new Ellipse2D.Double(-30, -29, 60, 56));
        double[][] cakes = {{-13, -12, 25}, {9, -15, -30}, {-3, 3, 10}, {14, 6, 60}, {-17, 11, -50},
                {2, -24, 80}, {19, -5, -15}, {3, 18, 30}};
        for (double[] c : cakes) {
            Graphics2D t = (Graphics2D) g.create();
            t.translate(c[0], c[1]);
            t.rotate(Math.toRadians(c[2]));
            t.setColor(RICE_CAKE);
            t.fill(new RoundRectangle2D.Double(-11, -4, 22, 8, 8, 8));
            t.setStroke(new BasicStroke(1.2f));
            t.setColor(new Color(0xE3, 0x9C, 0x7E));
            t.draw(new RoundRectangle2D.Double(-11, -4, 22, 8, 8, 8));
            t.setColor(new Color(255, 255, 255, 170));
            t.draw(new Line2D.Double(-7, -2, 5, -2));
            t.dispose();
        }
        g.setColor(new Color(0xF3, 0xC4, 0x7A));
        Path2D tri = new Path2D.Double();
        tri.moveTo(-24, -2);
        tri.lineTo(-12, -6);
        tri.lineTo(-16, 4);
        tri.closePath();
        g.fill(tri);
        Path2D tri2 = new Path2D.Double();
        tri2.moveTo(10, 14);
        tri2.lineTo(22, 16);
        tri2.lineTo(14, 24);
        tri2.closePath();
        g.fill(tri2);
        g.setColor(new Color(0x5D, 0xAA, 0x3C));
        g.fill(new Rectangle2D.Double(-6, -16, 5, 3));
        g.fill(new Rectangle2D.Double(16, -18, 4, 3));
        g.fill(new Rectangle2D.Double(-22, 18, 5, 3));
        g.setColor(new Color(0x8B, 0xCB, 0x5E));
        g.fill(new Rectangle2D.Double(6, 2, 4, 3));
        g.setColor(new Color(255, 255, 255, 110));
        g.setStroke(new BasicStroke(2f));
        g.draw(new Arc2D.Double(-24, -24, 48, 44, 110, 40, Arc2D.OPEN));
    }

    private static void gimbap(Graphics2D g) {
        double[][] pos = {{-17, -14}, {3, -21}, {20, -6}, {-21, 7}, {-1, 2}, {15, 15}, {-6, 22}};
        for (double[] p : pos) {
            kimbapSlice(g, p[0], p[1], 11);
        }
        g.setColor(new Color(0xF6, 0xD2, 0x32));
        g.fill(new RoundRectangle2D.Double(22, -26, 10, 7, 3, 3));
    }

    private static void sundae(Graphics2D g) {
        double[][] pos = {{-16, -15}, {4, -20}, {20, -4}, {-20, 5}, {0, 1}, {14, 16}, {-8, 20}};
        for (double[] p : pos) {
            g.setColor(new Color(0x2E, 0x14, 0x1C));
            g.fill(new Ellipse2D.Double(p[0] - 10, p[1] - 8, 20, 16));
            g.setColor(new Color(0x56, 0x2A, 0x36));
            g.fill(new Ellipse2D.Double(p[0] - 8, p[1] - 6.5, 16, 13));
            g.setColor(new Color(0x8A, 0x55, 0x63));
            g.fill(new Ellipse2D.Double(p[0] - 4, p[1] - 3, 3, 2.5));
            g.fill(new Ellipse2D.Double(p[0] + 2, p[1] + 1, 2.5, 2));
            g.fill(new Ellipse2D.Double(p[0] - 1, p[1] + 3, 2, 2));
        }
        g.setColor(new Color(0xEE, 0xEC, 0xE8));
        g.fill(new Ellipse2D.Double(18, -30, 13, 10));
        g.setColor(new Color(0x55, 0x55, 0x55));
        g.fill(new Ellipse2D.Double(23, -27, 2, 2));
        g.fill(new Ellipse2D.Double(26, -24, 2, 2));
    }

    private static void twigim(Graphics2D g) {
        double[][] pieces = {{-13, -11, 30}, {11, -14, -20}, {-7, 11, 70}, {16, 8, 10}, {-22, 2, -10}};
        for (int i = 0; i < pieces.length; i++) {
            double[] p = pieces[i];
            Graphics2D t = (Graphics2D) g.create();
            t.translate(p[0], p[1]);
            t.rotate(Math.toRadians(p[2]));
            t.setColor(new Color(0xB8, 0x75, 0x24));
            t.fill(new RoundRectangle2D.Double(-15, -8.5, 30, 17, 12, 12));
            t.setColor(new Color(0xE3, 0xA1, 0x3A));
            t.fill(new RoundRectangle2D.Double(-14, -7.5, 28, 15, 11, 11));
            t.setColor(new Color(0xF7, 0xD2, 0x7A));
            for (int k = 0; k < 6; k++) {
                t.fill(new Ellipse2D.Double(-11 + k * 4, (k % 2 == 0 ? -4 : 2), 3, 2.5));
            }
            if (i == 1) { // 새우튀김 꼬리
                t.setColor(new Color(0xE0, 0x4A, 0x2E));
                Path2D tail = new Path2D.Double();
                tail.moveTo(14, 0);
                tail.lineTo(22, -6);
                tail.lineTo(21, 6);
                tail.closePath();
                t.fill(tail);
            }
            if (i == 3) { // 고구마튀김 단면
                t.setColor(new Color(0xF6, 0xA8, 0x3A));
                t.fill(new Ellipse2D.Double(-6, -4, 12, 8));
            }
            t.dispose();
        }
    }

    private static void eomuk(Graphics2D g) {
        g.setColor(new Color(0xE6, 0xC5, 0x8E, 200));
        g.fill(new Ellipse2D.Double(-30, -26, 60, 54));
        for (int s = 0; s < 2; s++) {
            double ox = s * 12 - 6;
            double oy = s * 10 - 5;
            g.setColor(new Color(0x9C, 0x6B, 0x3A));
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(-30 + ox, 28 + oy, 26 + ox, -28 + oy));
            for (int k = 0; k < 4; k++) {
                double t = 0.25 + k * 0.15;
                double x = -30 + ox + 56 * t;
                double y = 28 + oy - 56 * t;
                Graphics2D r = (Graphics2D) g.create();
                r.translate(x, y);
                r.rotate(Math.toRadians(-45));
                r.setColor(k % 2 == 0 ? new Color(0xF2, 0xD2, 0x99) : new Color(0xE7, 0xBD, 0x72));
                r.fill(new RoundRectangle2D.Double(-8, -10, 14, 20, 8, 8));
                r.setStroke(new BasicStroke(1.2f));
                r.setColor(new Color(0xB9, 0x8A, 0x45));
                r.draw(new RoundRectangle2D.Double(-8, -10, 14, 20, 8, 8));
                r.dispose();
            }
        }
    }

    /** 테이블 위 골드 동전 더미 */
    public static void coins(Graphics2D g, double cx, double cy, double size) {
        double w = size;
        double h = size * 0.45;
        for (int i = 0; i < 3; i++) {
            double y = cy - i * h * 0.45;
            g.setColor(GOLD_DARK);
            g.fill(new Ellipse2D.Double(cx - w / 2, y - h / 2 + 2, w, h));
            g.setColor(GOLD);
            g.fill(new Ellipse2D.Double(cx - w / 2, y - h / 2, w, h));
        }
        g.setColor(new Color(255, 255, 255, 160));
        g.setStroke(new BasicStroke(1.5f));
        double top = cy - 2 * h * 0.45;
        g.draw(new Arc2D.Double(cx - w / 2 + 3, top - h / 2 + 2, w - 6, h - 4, 30, 100, Arc2D.OPEN));
    }

    // =====================================================================
    // 사람
    // =====================================================================

    /**
     * 플레이어 캐릭터(빨간 두건 + 빨간 앞치마 사장님). (x, y) = 발 위치.
     */
    public static void chef(Graphics2D g, double x, double y, boolean facingLeft, Menu held, long now,
                            boolean moving) {
        double bob = moving ? Math.abs(Math.sin(now / 90.0)) * 3 : 0;
        double step = moving ? Math.sin(now / 90.0) * 3 : 0;
        int dir = facingLeft ? -1 : 1;

        g.setColor(new Color(0, 0, 0, 55));
        g.fill(new Ellipse2D.Double(x - 18, y - 5, 36, 10));
        // 다리
        g.setColor(new Color(0x36, 0x36, 0x4A));
        g.fill(new RoundRectangle2D.Double(x - 10, y - 16 - bob, 8, 15 + step, 4, 4));
        g.fill(new RoundRectangle2D.Double(x + 2, y - 16 - bob, 8, 15 - step, 4, 4));
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(x - 12, y - 4 + step - bob * 0.2, 11, 6));
        g.fill(new Ellipse2D.Double(x + 1, y - 4 - step - bob * 0.2, 11, 6));

        double top = y - 48 - bob;
        // 몸통(흰 티)
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(x - 16, top, 32, 34, 14, 14));
        g.setColor(new Color(0xC9, 0xBF, 0xB2));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(x - 16, top, 32, 34, 14, 14));
        // 앞치마
        g.setColor(UiKit.RED);
        g.fill(new RoundRectangle2D.Double(x - 12, top + 8, 24, 27, 8, 8));
        g.setColor(UiKit.RED_DARK);
        g.draw(new Line2D.Double(x - 9, top + 8, x - 6, top));
        g.draw(new Line2D.Double(x + 9, top + 8, x + 6, top));
        g.setColor(UiKit.YELLOW);
        g.fill(new RoundRectangle2D.Double(x - 6, top + 19, 12, 8, 3, 3));

        // 팔
        if (held != null) {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(x - 20, top + 10, 10, 14));
            g.fill(new Ellipse2D.Double(x + 10, top + 10, 10, 14));
        } else {
            double swing = moving ? Math.sin(now / 90.0) * 3 : 0;
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(x - 22, top + 6 + swing, 10, 20));
            g.fill(new Ellipse2D.Double(x + 12, top + 6 - swing, 10, 20));
            g.setColor(SKIN);
            g.fill(new Ellipse2D.Double(x - 21, top + 22 + swing, 8, 8));
            g.fill(new Ellipse2D.Double(x + 13, top + 22 - swing, 8, 8));
        }

        // 머리
        double hy = top - 14;
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(x - 16, hy - 15, 32, 30));
        g.setColor(SKIN);
        g.fill(new Ellipse2D.Double(x - 15, hy - 13, 30, 29));
        g.setColor(SKIN_LINE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(x - 15, hy - 13, 30, 29));
        // 빨간 두건
        g.setColor(UiKit.RED);
        g.fill(new Arc2D.Double(x - 17, hy - 17, 34, 28, 0, 180, Arc2D.CHORD));
        g.fill(new Rectangle2D.Double(x - 17, hy - 4, 34, 4));
        g.setColor(Color.WHITE);
        for (int i = 0; i < 4; i++) {
            g.fill(new Ellipse2D.Double(x - 11 + i * 6, hy - 11 + (i % 2) * 4, 3, 3));
        }
        // 두건 매듭(뒤쪽)
        g.setColor(UiKit.RED_DARK);
        g.fill(new Ellipse2D.Double(x - dir * 20 - 4, hy - 8, 8, 7));
        g.fill(new Ellipse2D.Double(x - dir * 24 - 4, hy - 4, 7, 6));
        // 얼굴
        double ex = x + dir * 3;
        g.setColor(INK);
        g.fill(new Ellipse2D.Double(ex - 7, hy + 3, 3.5, 4.5));
        g.fill(new Ellipse2D.Double(ex + 3.5, hy + 3, 3.5, 4.5));
        g.setColor(new Color(0xFF, 0x9E, 0x9E, 160));
        g.fill(new Ellipse2D.Double(ex - 12, hy + 8, 6, 4));
        g.fill(new Ellipse2D.Double(ex + 6, hy + 8, 6, 4));
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(ex - 4, hy + 6, 8, 6, 200, 140, Arc2D.OPEN));

        // 들고 있는 음식(가슴 앞)
        if (held != null) {
            dish(g, held, x + dir * 2, top + 12, 34, 0.7);
            g.setColor(SKIN);
            g.fill(new Ellipse2D.Double(x - 19, top + 14, 8, 8));
            g.fill(new Ellipse2D.Double(x + 11, top + 14, 8, 8));
        }
    }

    /**
     * 손님. 서 있을 때는 (x, y) = 발 위치, 앉아 있을 때는 (x, y) = 엉덩이(몸통 아래) 위치.
     *
     * @return 머리 꼭대기 y(인내심 바·말풍선 위치 계산용)
     */
    public static double customer(Graphics2D g, double x, double y, int look, Mood mood, boolean seated,
                                  long now) {
        Color shirt = SHIRTS[Math.floorMod(look, SHIRTS.length)];
        Color hair = HAIRS[Math.floorMod(look / SHIRTS.length, HAIRS.length)];
        int style = Math.floorMod(look, 3);
        double bodyBottom = seated ? y : y - 13;
        if (!seated) {
            g.setColor(new Color(0, 0, 0, 50));
            g.fill(new Ellipse2D.Double(x - 16, y - 5, 32, 9));
            g.setColor(new Color(0x45, 0x4B, 0x63));
            g.fill(new RoundRectangle2D.Double(x - 10, y - 15, 8, 14, 4, 4));
            g.fill(new RoundRectangle2D.Double(x + 2, y - 15, 8, 14, 4, 4));
            g.setColor(INK);
            g.fill(new Ellipse2D.Double(x - 11, y - 4, 10, 5));
            g.fill(new Ellipse2D.Double(x + 1, y - 4, 10, 5));
        }
        double top = bodyBottom - 30;
        // 몸통과 팔
        g.setColor(shirt.darker());
        g.fill(new Ellipse2D.Double(x - 21, top + 5, 10, 20));
        g.fill(new Ellipse2D.Double(x + 11, top + 5, 10, 20));
        g.setColor(shirt);
        g.fill(new RoundRectangle2D.Double(x - 15, top, 30, 32, 14, 14));
        g.setColor(shirt.darker());
        g.setStroke(new BasicStroke(1.3f));
        g.draw(new RoundRectangle2D.Double(x - 15, top, 30, 32, 14, 14));
        g.setColor(UiKit.alpha(Color.WHITE, 120));
        g.fill(new Arc2D.Double(x - 7, top - 4, 14, 10, 180, 180, Arc2D.CHORD));

        // 머리
        double hy = top - 13;
        if (style == 1) { // 긴 머리
            g.setColor(hair);
            g.fill(new RoundRectangle2D.Double(x - 16, hy - 12, 32, 34, 14, 14));
        }
        g.setColor(SKIN);
        g.fill(new Ellipse2D.Double(x - 14, hy - 13, 28, 27));
        g.setColor(SKIN_LINE);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new Ellipse2D.Double(x - 14, hy - 13, 28, 27));
        g.setColor(hair);
        g.fill(new Arc2D.Double(x - 15, hy - 15, 30, 24, 0, 180, Arc2D.CHORD));
        if (style == 2) { // 똥머리
            g.fill(new Ellipse2D.Double(x - 6, hy - 22, 12, 11));
        }
        if (mood == Mood.ANGRY) {
            g.setColor(new Color(0xFF, 0x60, 0x50, 70));
            g.fill(new Ellipse2D.Double(x - 14, hy - 4, 28, 18));
        }

        // 얼굴 표정
        g.setColor(INK);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (mood) {
            case EATING -> {
                g.draw(new Arc2D.Double(x - 8, hy, 6, 5, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(x + 2, hy, 6, 5, 0, 180, Arc2D.OPEN));
                double chew = 2 + Math.abs(Math.sin(now / 120.0)) * 3;
                g.fill(new Ellipse2D.Double(x - 3, hy + 6, 6, chew));
            }
            case HAPPY -> {
                g.fill(new Ellipse2D.Double(x - 7, hy + 1, 3.5, 4));
                g.fill(new Ellipse2D.Double(x + 3.5, hy + 1, 3.5, 4));
                g.draw(new Arc2D.Double(x - 4, hy + 4, 8, 6, 200, 140, Arc2D.OPEN));
            }
            case NEUTRAL -> {
                g.fill(new Ellipse2D.Double(x - 7, hy + 1, 3.5, 4));
                g.fill(new Ellipse2D.Double(x + 3.5, hy + 1, 3.5, 4));
                g.draw(new Line2D.Double(x - 3, hy + 9, x + 3, hy + 9));
            }
            case ANGRY -> {
                g.draw(new Line2D.Double(x - 8, hy - 2, x - 3, hy));
                g.draw(new Line2D.Double(x + 8, hy - 2, x + 3, hy));
                g.fill(new Ellipse2D.Double(x - 7, hy + 1, 3.5, 3.5));
                g.fill(new Ellipse2D.Double(x + 3.5, hy + 1, 3.5, 3.5));
                g.draw(new Arc2D.Double(x - 4, hy + 8, 8, 6, 20, 140, Arc2D.OPEN));
                // 화난 표시
                g.setColor(new Color(0xE5, 0x2B, 0x20));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double ax = x + 13;
                double ay = hy - 12;
                g.draw(new QuadCurve2D.Double(ax - 4, ay - 1, ax, ay, ax - 1, ay - 5));
                g.draw(new QuadCurve2D.Double(ax + 4, ay - 1, ax, ay, ax + 1, ay - 5));
                g.draw(new QuadCurve2D.Double(ax - 4, ay + 1, ax, ay, ax - 1, ay + 5));
                g.draw(new QuadCurve2D.Double(ax + 4, ay + 1, ax, ay, ax + 1, ay + 5));
            }
        }
        return hy - 16;
    }

    public static Mood moodFor(int patience1000) {
        if (patience1000 > 600) {
            return Mood.HAPPY;
        }
        if (patience1000 > 300) {
            return Mood.NEUTRAL;
        }
        return Mood.ANGRY;
    }

    /** 머리 위 인내심 바(초록 → 노랑 → 빨강) */
    public static void patienceBar(Graphics2D g, double cx, double y, int patience1000, long now) {
        double ratio = Math.max(0, Math.min(1, patience1000 / 1000.0));
        double w = 46;
        g.setColor(new Color(0x3B, 0x24, 0x16, 210));
        g.fill(new RoundRectangle2D.Double(cx - w / 2 - 2, y - 2, w + 4, 10, 8, 8));
        Color c = ratio > 0.6 ? new Color(0x4C, 0xB9, 0x44)
                : ratio > 0.3 ? new Color(0xF5, 0xB8, 0x2E) : new Color(0xE5, 0x41, 0x2D);
        if (ratio <= 0.3 && (now / 250) % 2 == 0) {
            c = c.brighter();
        }
        g.setColor(c);
        g.fill(new RoundRectangle2D.Double(cx - w / 2, y, w * ratio, 6, 6, 6));
        g.setColor(new Color(255, 255, 255, 90));
        g.fill(new RoundRectangle2D.Double(cx - w / 2, y, w * ratio, 2.5, 3, 3));
    }

    /** 말풍선 몸통(꼬리는 왼쪽 아래) */
    public static void bubble(Graphics2D g, double x, double y, double w, double h) {
        Path2D tail = new Path2D.Double();
        tail.moveTo(x + 6, y + h - 2);
        tail.lineTo(x + 2, y + h + 9);
        tail.lineTo(x + 16, y + h - 2);
        tail.closePath();
        g.setColor(new Color(0, 0, 0, 40));
        g.fill(new RoundRectangle2D.Double(x + 2, y + 3, w, h, 12, 12));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 12, 12));
        g.fill(tail);
        g.setColor(UiKit.BROWN);
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new RoundRectangle2D.Double(x, y, w, h, 12, 12));
    }

    /** 캐릭터 머리 위에 뜨는 작은 안내 라벨(예: "SPACE 서빙하기") */
    public static void hintLabel(Graphics2D g, String text, double cx, double y) {
        g.setFont(UiKit.bold(12));
        double w = g.getFontMetrics().stringWidth(text) + 18;
        double h = 22;
        g.setColor(new Color(0x2A, 0x1A, 0x12, 215));
        g.fill(new RoundRectangle2D.Double(cx - w / 2, y, w, h, h, h));
        g.setColor(UiKit.YELLOW);
        UiKit.centerTextIn(g, text, cx - w / 2, y, w, h);
    }
}
