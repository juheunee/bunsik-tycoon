package bunsik.client.render;

import bunsik.client.ui.UiKit;
import bunsik.common.Menu;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot;
import bunsik.common.TableState;

import java.awt.Color;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 스냅샷 하나를 받아 식당 하나를 통째로 그린다(내 식당·상대 식당 공통).
 *
 * <p>그리기만 한다. 계산·네트워크 전송은 절대 하지 않는다(화면이 버벅이는 원인이 되므로).
 */
public final class StoreRenderer {

    private StoreRenderer() {
    }

    /**
     * @param s          서버가 보낸 최신 상태(아직 없으면 null → 빈 식당)
     * @param px         캐릭터 x (내 식당은 내 컴퓨터의 위치, 상대 식당은 스냅샷 위치)
     * @param mine       내 식당이면 true → 가까운 테이블 강조와 조작 안내를 그린다
     */
    public static void draw(Graphics2D g, StoreSnapshot s, double px, double py, boolean facingLeft,
                            boolean moving, long now, boolean mine) {
        Sprites.floor(g);
        Sprites.wall(g);
        Sprites.menuBoard(g);
        Sprites.entrance(g, now);

        boolean nearKitchen = mine && StoreLayout.nearKitchen(px, py);
        Sprites.kitchen(g, now, nearKitchen);
        int nearTable = (mine && !nearKitchen) ? StoreLayout.nearestTable(px, py) : -1;

        StoreSnapshot.TableView[] tables = s == null ? new StoreSnapshot.TableView[0] : s.tables;
        StoreSnapshot.CustomerView[] queue = s == null ? new StoreSnapshot.CustomerView[0] : s.queue;

        // 입구 대기줄(뒤쪽 손님부터 그려야 앞 손님이 위에 겹친다)
        for (int i = queue.length - 1; i >= 0; i--) {
            if (i >= StoreLayout.QUEUE_X.length) {
                continue;
            }
            double qx = StoreLayout.QUEUE_X[i];
            double qy = StoreLayout.QUEUE_Y[i];
            double headTop = Sprites.customer(g, qx, qy, queue[i].look, Sprites.moodFor(queue[i].patience), false, now);
            Sprites.patienceBar(g, qx, headTop - 12, queue[i].patience, now);
        }

        // 테이블과 캐릭터는 아래쪽(y가 큰 것)일수록 나중에 그려야 자연스럽게 겹친다.
        record Drawable(double y, int table) {
        }
        List<Drawable> order = new ArrayList<>();
        for (int i = 0; i < Math.min(tables.length, StoreLayout.TABLE_X.length); i++) {
            order.add(new Drawable(StoreLayout.TABLE_Y[i] + 26, i));
        }
        order.add(new Drawable(py, -1));
        order.sort(Comparator.comparingDouble(Drawable::y));

        Menu held = s == null ? null : s.held;
        for (Drawable d : order) {
            if (d.table() >= 0) {
                drawTable(g, d.table(), tables[d.table()], d.table() == nearTable, now);
            } else {
                Sprites.chef(g, px, py, facingLeft, held, now, moving);
                if (!mine && s != null && s.cooking != null) {
                    Sprites.hintLabel(g, s.cooking.label + " 요리 중", px, py - 104);
                }
            }
        }

        if (mine && s != null) {
            String hint = hintFor(s, nearKitchen, nearTable);
            if (hint != null) {
                Sprites.hintLabel(g, hint, px, py - 104);
            }
        }
    }

    private static void drawTable(Graphics2D g, int i, StoreSnapshot.TableView v, boolean highlight, long now) {
        double tx = StoreLayout.TABLE_X[i];
        double ty = StoreLayout.TABLE_Y[i];
        boolean seated = v.state == TableState.WAITING_ORDER || v.state == TableState.WAITING_FOOD
                || v.state == TableState.EATING;

        Sprites.stool(g, tx, ty - 36);
        double headTop = 0;
        if (seated) {
            Sprites.Mood mood = v.state == TableState.EATING ? Sprites.Mood.EATING : Sprites.moodFor(v.patience);
            headTop = Sprites.customer(g, tx, ty - 14, v.look, mood, true, now);
        }
        Sprites.tableTop(g, tx, ty, i + 1, highlight, now);
        Sprites.stool(g, tx, ty + 40);

        switch (v.state) {
            case WAITING_ORDER -> {
                Sprites.patienceBar(g, tx, headTop - 12, v.patience, now);
                Sprites.bubble(g, tx + 28, headTop - 24, 46, 24);
                g.setColor(UiKit.RED);
                g.setFont(UiKit.bold(12.5f));
                UiKit.centerTextIn(g, "주문!", tx + 28, headTop - 24, 46, 24);
            }
            case WAITING_FOOD -> {
                Sprites.patienceBar(g, tx, headTop - 12, v.patience, now);
                Sprites.bubble(g, tx + 28, headTop - 32, 40, 32);
                Sprites.dish(g, v.menu, tx + 48, headTop - 16, 28, 0.8);
            }
            case EATING -> {
                Sprites.dish(g, v.menu, tx, ty - 6, 40, 0.6);
                double bobY = Math.sin(now / 200.0) * 2;
                g.setFont(UiKit.bold(12));
                UiKit.outlinedText(g, "냠냠", tx + 26, headTop - 2 + bobY, Color.WHITE, UiKit.BROWN, 3f);
            }
            case DIRTY -> {
                Sprites.emptyDish(g, tx - 14, ty - 4, 40, 0.6);
                Sprites.coins(g, tx + 22, ty, 20);
                g.setFont(UiKit.bold(14));
                UiKit.outlinedText(g, "+" + v.gold, tx + 22, ty - 16, UiKit.YELLOW, UiKit.BROWN, 3.5f);
            }
            case EMPTY -> {
            }
        }
    }

    /** 내 캐릭터 머리 위에 띄울 "지금 스페이스를 누르면 무슨 일이 생기는지" 안내 */
    private static String hintFor(StoreSnapshot s, boolean nearKitchen, int nearTable) {
        if (nearKitchen) {
            if (s.cooking != null) {
                return "SPACE  요리 확인";
            }
            return s.held != null ? "SPACE  " + s.held.label + " 버리기" : "SPACE  요리하기";
        }
        if (nearTable < 0 || nearTable >= s.tables.length) {
            return null;
        }
        StoreSnapshot.TableView v = s.tables[nearTable];
        return switch (v.state) {
            case WAITING_ORDER -> "SPACE  주문 받기";
            case WAITING_FOOD -> s.held == v.menu ? "SPACE  서빙하기" : "주문: " + v.menu.label;
            case DIRTY -> "SPACE  치우기";
            default -> null;
        };
    }
}
