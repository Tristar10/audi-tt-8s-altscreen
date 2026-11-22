package de.audi.tghu.connectivity.hmi.evohighmmikombi;

import java.util.List;

import de.audi.atip.hmi.model.ChoiceListener;
import de.audi.atip.hmi.model.ChoiceModel;
import de.esolutions.hmi.widgets.audi.base.AbstractWidget;
import de.esolutions.hmi.widgets.audi.base.widgets.AbstractWidgetController;
import de.esolutions.hmi.widgets.audi.evo.high.widgets.CompositeRendererHigh;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MenuController;
import de.esolutions.hmi.widgets.audi.evo.widgets.menu.MultiLineMenuItemController;
import local.tt.ScreenHook;
import local.tt.TtSettingRow;

/**
 * v63 prototype: turns the stock "About Apple CarPlay" page (screen 2500140) into our settings
 * page. It lives in the connectivity package only for ConnectivityScreenFactory.getFonts, which
 * is package-private. The row is built exactly like the stock WLAN page's drop-down
 * (ConnectivityScreenBag4.cMOPTWLANMAIN): CompositeRendererHigh, bitmaps, fonts 4, insets 6,
 * type 64 - with our own texts (ScreenHook.text) and our own ChoiceModel instead of a stock model ID.
 * The new row is built completely before the About text is removed.
 */
public final class TtSettingsPage {
    private static final int[] BITMAPS = {
        127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127,
        127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127,
        127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127, 127,
        129, 332,
    };

    private TtSettingsPage() {
    }

    public static void build(Object screenObj, Object factoryObj, int terminal) {
        ConnectivityScreenFactory f = (ConnectivityScreenFactory) factoryObj;
        MenuController menu = findMenu(screenObj, 0);
        if (menu == null) {
            ScreenHook.log("settings page: no menu in the About screen, stock page kept");
            return;
        }
        List kids = menu.getChildren();
        AbstractWidget about = null;
        StringBuffer b = new StringBuffer("settings page: menu children");
        for (int i = 0; kids != null && i < kids.size(); i++) {
            Object c = kids.get(i);
            String n = c.getClass().getName();
            b.append(' ').append(n.substring(n.lastIndexOf('.') + 1));
            if (c instanceof MultiLineMenuItemController) {
                about = (AbstractWidget) c;
            }
        }
        ScreenHook.log(b.toString());

        TtSettingRow row = new TtSettingRow();
        CompositeRendererHigh r = new CompositeRendererHigh(row);
        row.setRenderer(r);
        row.setBitmaps(BITMAPS);
        r.setFonts(f.getFonts(4, terminal));
        row.setTextIds(new int[] {ScreenHook.T_OFF, ScreenHook.T_NEAR, ScreenHook.T_ALWAYS});
        row.setLabelId(ScreenHook.T_CARD);
        row.setGlassplateInsetsBottom(6);
        row.setGlassplateInsetsTop(6);
        row.setInternalID(ScreenHook.ROW_CARD);
        row.setType(64);
        ChoiceModel model = new ChoiceModel(ScreenHook.MODEL_CARD);
        model.setValue(ScreenHook.cardMode());
        model.setChoiceListener((ChoiceListener) ScreenHook.cardListener(model));
        row.setModel(model);
        row.ttModel = model;

        if (about != null) {
            menu.remove(about);
        }
        menu.add(row);
        ScreenHook.log(new StringBuffer("settings page: card row added (value ").append(model.getValue())
            .append(about != null ? ", About text removed)" : ", no About text found)").toString());
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
