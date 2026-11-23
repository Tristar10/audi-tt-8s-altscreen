package local.tt;

import java.util.List;

import de.audi.atip.hmi.model.ChoiceModel;
import de.esolutions.hmi.widgets.audi.base.widgets.AbstractWidgetController;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.CompositeRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuController;

/**
 * v63: appends our setting rows to a stock menu (ScreenHook decides where: the navigation app's
 * map options drawer, and MENU -> Settings -> MMI settings).
 *
 * The row is built like the stock WLAN page's drop-down (ConnectivityScreenBag4.cMOPTWLANMAIN):
 * MenuItemController + CompositeRendererHigh, bitmaps, insets 6, type 64 (TYPE_COMBO_BOX) - with
 * our own texts (ScreenHook.text) and our own ChoiceModel instead of a stock model ID. Like the
 * settings and map-options rows around it, it sets no fonts (theme defaults).
 */
final class TtSettingsPage {
    private static final int[] BITMAPS = {
        127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127,
        127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127,
        127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127,
        129, 332,
    };

    private TtSettingsPage() {
    }

    /** Adds our rows to the first menu found under root. where: for the log; app: the host app number. */
    static void addRows(Object root, String where, int app) {
        int base = app * 100000;
        int t = base + ScreenHook.TEXT_SUFFIX;
        MenuController menu = findMenu(root, 0);
        if (menu == null) {
            RgdBus.note(new StringBuffer("settings page: no menu in ").append(where).append(", left as stock").toString());
            return;
        }
        List kids = menu.getChildren();
        RgdBus.note(new StringBuffer("settings page: ").append(where).append(" menu has ")
            .append(kids == null ? 0 : kids.size()).append(" children").toString());

        TtSettingRow row = new TtSettingRow();
        CompositeRendererHigh r = new CompositeRendererHigh(row);
        row.setRenderer(r);
        row.setBitmaps(BITMAPS);
        row.setTextIds(new int[] {t + ScreenHook.T_OFF, t + ScreenHook.T_NEAR, t + ScreenHook.T_ALWAYS});
        row.setLabelId(t + ScreenHook.T_CARD);
        row.setGlassplateInsetsBottom(6);
        row.setGlassplateInsetsTop(6);
        row.setInternalID(base + ScreenHook.ROW_CARD);
        row.setType(64);
        ChoiceModel model = new ChoiceModel(base + ScreenHook.MODEL_CARD);
        model.setValue(Card.mode());
        model.setChoiceListener(new CardChoice(model));
        row.setModel(model);
        row.ttModel = model;
        row.where = where;

        menu.add(row);
        RgdBus.note(new StringBuffer("settings page: card row added to ").append(where)
            .append(" (value ").append(model.getValue()).append(')').toString());
    }

    private static MenuController findMenu(Object w, int depth) {
        if (w instanceof MenuController) {
            return (MenuController) w;
        }
        if (!(w instanceof AbstractWidgetController) || depth > 8) {
            return null;
        }
        List kids = ((AbstractWidgetController) w).getChildren();
        for (int i = 0; kids != null && i < kids.size(); i++) {
            MenuController m = findMenu(kids.get(i), depth + 1);
            if (m != null) {
                return m;
            }
        }
        return null;
    }
}
