package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot;

import java.awt.Graphics2D;

/** 조리대와 B3에서 수신한 조리 상태를 식당 위에 표시한다. */
final class KitchenOverlay {
    private KitchenOverlay() { }

    static StoreLayout.CookStation nearestStation(int x, int y) {
        StoreLayout.CookStation nearest = null;
        double best = StoreLayout.KITCHEN_TARGET_REACH;
        for (var station : StoreLayout.COOK_STATIONS) {
            double distance = StoreLayout.distance(station.position(), x, y);
            if (distance < best) {
                nearest = station;
                best = distance;
            }
        }
        return nearest;
    }

    static boolean nearDiscard(int x, int y) {
        return StoreLayout.distance(StoreLayout.DISCARD, x, y) < StoreLayout.KITCHEN_TARGET_REACH;
    }

    static void paint(Graphics2D g, StoreSnapshot state) {
        Sprites.stationKitchen(g);
        for (var station : StoreLayout.COOK_STATIONS) {
            var menu = station.menu();
            var point = station.position();
            boolean cooking = menu == state.cooking();
            String status = cooking ? "%s %.1f초".formatted(menu.label, state.cookRemainMs()/1000.0) : menu.label;
            Sprites.cookStation(g, menu, point.x(), point.y(), status, cooking);
        }
        var discard = StoreLayout.DISCARD;
        Sprites.discardStation(g, discard.x(), discard.y());
    }
}
