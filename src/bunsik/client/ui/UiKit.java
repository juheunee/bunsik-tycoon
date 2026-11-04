package bunsik.client.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JTextField;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.util.HashSet;
import java.util.Set;

/**
 * 화면 공통 도구: 분식집 색상표, 한글 글꼴, 둥근 버튼, 입력창 꾸미기, 글자 그리기.
 */
public final class UiKit {

    private UiKit() {
    }

    // ---------------- 분식집 색상표 ----------------
    /** 떡볶이 국물 빨강 */
    public static final Color RED = new Color(0xD8, 0x3A, 0x2B);
    public static final Color RED_DARK = new Color(0xA8, 0x26, 0x1C);
    /** 간판 노랑 */
    public static final Color YELLOW = new Color(0xFF, 0xC9, 0x3C);
    public static final Color YELLOW_SOFT = new Color(0xFF, 0xE3, 0x8A);
    /** 단무지·튀김 주황 */
    public static final Color ORANGE = new Color(0xF0, 0x8A, 0x24);
    /** 벽·종이 크림색 */
    public static final Color CREAM = new Color(0xFF, 0xF6, 0xE4);
    public static final Color CREAM_DARK = new Color(0xF1, 0xE0, 0xC0);
    /** 진한 갈색(글자) */
    public static final Color BROWN = new Color(0x3B, 0x24, 0x16);
    public static final Color BROWN_SOFT = new Color(0x7A, 0x56, 0x3C);
    /** 파 초록 */
    public static final Color GREEN = new Color(0x4C, 0x9A, 0x2A);
    /** 스테인리스 */
    public static final Color STEEL = new Color(0xC9, 0xD1, 0xD6);
    public static final Color STEEL_DARK = new Color(0x7D, 0x8A, 0x93);
    public static final Color WHITE = Color.WHITE;

    // ---------------- 글꼴 ----------------
    private static final String[] KOREAN_FONTS = {
            "NanumSquareRound", "나눔스퀘어라운드", "Malgun Gothic", "맑은 고딕", "Apple SD Gothic Neo",
            "AppleGothic", "NanumGothic", "나눔고딕", "Noto Sans CJK KR", "Noto Sans KR", "Gulim", "굴림"
    };
    private static final String FAMILY = pickFamily();

    private static String pickFamily() {
        try {
            Set<String> available = new HashSet<>();
            for (String name : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                available.add(name);
            }
            for (String candidate : KOREAN_FONTS) {
                if (available.contains(candidate)) {
                    return candidate;
                }
            }
        } catch (Exception ignored) {
            // 글꼴 목록을 못 읽으면 기본 글꼴
        }
        return Font.DIALOG;
    }

    public static Font font(int style, float size) {
        return new Font(FAMILY, style, 12).deriveFont(size);
    }

    public static Font bold(float size) {
        return font(Font.BOLD, size);
    }

    public static Font plain(float size) {
        return font(Font.PLAIN, size);
    }

    // ---------------- 그리기 도우미 ----------------

    public static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** (cx, baselineY)를 가운데로 글자 쓰기 */
    public static void centerText(Graphics2D g, String text, double cx, double baselineY) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) baselineY);
    }

    /** 사각형 정중앙에 글자 쓰기 */
    public static void centerTextIn(Graphics2D g, String text, double x, double y, double w, double h) {
        FontMetrics fm = g.getFontMetrics();
        double baseline = y + (h - fm.getHeight()) / 2.0 + fm.getAscent();
        g.drawString(text, (float) (x + (w - fm.stringWidth(text)) / 2.0), (float) baseline);
    }

    /** 테두리 있는 글자(간판 글씨처럼). 가운데 정렬. */
    public static void outlinedText(Graphics2D g, String text, double cx, double baselineY,
                                    Color fill, Color outline, float outlineWidth) {
        FontRenderContext frc = g.getFontRenderContext();
        TextLayout layout = new TextLayout(text, g.getFont(), frc);
        double w = layout.getAdvance();
        Shape shape = layout.getOutline(AffineTransform.getTranslateInstance(cx - w / 2.0, baselineY));
        g.setStroke(new BasicStroke(outlineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(outline);
        g.draw(shape);
        g.setColor(fill);
        g.fill(shape);
    }

    public static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    // ---------------- 컴포넌트 ----------------

    /** 둥근 분식집 버튼 */
    public static final class RoundButton extends JButton {

        private Color base;

        public RoundButton(String text, Color base) {
            super(text);
            this.base = base;
            setFont(bold(18));
            setForeground(WHITE);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        public void setBaseColor(Color c) {
            this.base = c;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            smooth(g);
            int w = getWidth();
            int h = getHeight();
            Color fill = base;
            if (!isEnabled()) {
                fill = new Color(0xB8, 0xAE, 0xA2);
            } else if (getModel().isPressed()) {
                fill = base.darker();
            } else if (getModel().isRollover()) {
                fill = brighter(base);
            }
            int arc = h;
            // 아래 그림자(눌리는 느낌)
            g.setColor(fill.darker().darker());
            g.fill(new RoundRectangle2D.Double(0, 4, w - 1, h - 5, arc, arc));
            int offset = getModel().isPressed() ? 3 : 0;
            g.setColor(fill);
            g.fill(new RoundRectangle2D.Double(0, offset, w - 1, h - 5, arc, arc));
            g.setColor(alpha(WHITE, 60));
            g.setStroke(new BasicStroke(2f));
            g.draw(new RoundRectangle2D.Double(3, offset + 3, w - 7, h - 11, arc - 6, arc - 6));
            g.setFont(getFont());
            g.setColor(getForeground());
            centerTextIn(g, getText(), 0, offset, w, h - 5);
            g.dispose();
        }

        private static Color brighter(Color c) {
            return new Color(Math.min(255, c.getRed() + 22), Math.min(255, c.getGreen() + 22),
                    Math.min(255, c.getBlue() + 22));
        }
    }

    /** 입력창을 분식집 메모지처럼 꾸민다 */
    public static void styleField(JTextField field) {
        field.setFont(plain(17));
        field.setForeground(BROWN);
        field.setBackground(WHITE);
        field.setCaretColor(RED);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CREAM_DARK.darker(), 2, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
    }
}
