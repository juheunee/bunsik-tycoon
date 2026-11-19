package bunsik.client.ui;

import bunsik.client.render.Sprites;
import bunsik.common.StoreLayout;
import bunsik.common.StoreSnapshot;

import java.awt.Graphics2D;

/** 조리대별 상태와 주방 상호작용 위치를 표시한다. */
final class KitchenOverlay {
    private KitchenOverlay() { }

    static void paint(Graphics2D g, StoreSnapshot state) {
        Sprites.stationKitchen(g);
        for (var station : StoreLayout.COOK_STATIONS) {
            var menu = station.menu();
            var point = station.position();
            Long remaining = state.stations().get(menu);
            Sprites.cookStation(g, menu, point.x(), point.y(), remaining);
        }
        var discard = StoreLayout.DISCARD;
        Sprites.kitchenBin(g, discard.x(), discard.y(), "폐기");
        var dishBin = StoreLayout.DISH_BIN;
        Sprites.kitchenBin(g, dishBin.x(), dishBin.y(), "설거지통");
    }
}
