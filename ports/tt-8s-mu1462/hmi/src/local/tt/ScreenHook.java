package local.tt;

import java.io.File;

/**
 * v63: our settings in the stock MMI menus, prototype ("iPhone turn card" drop-down).
 *
 * PatchZoom adds three calls to framework classes shared by every HMI app:
 *   AbstractScreenFactory.getScreen(id, terminal):   return ScreenHook.created(screen, this, id, terminal)
 *   AbstractScreenFactory.getText(id):               s = ScreenHook.text(id); if (s != null) return s;
 *   HMIRegistryEvo.getOptionDrawers(terminal, app): return ScreenHook.drawers(drawers, app, terminal)
 *
 * Our rows go to two places (both for the first car test):
 *   - map options: the navigation app's options drawers are [destination options, map options]
 *     (NaviScreenFactory.getOptionDrawers); our row is appended to the second
 *   - MENU -> Settings -> MMI settings (screen 1100040, SettingsScreenBag2.sETTINGSMMIMAIN)
 * (The first idea, Options -> About Apple CarPlay, cannot be opened while CarPlay runs.)
 * Our texts use the top of the host app's own ID range (app = id / 100000): suffix 99900-99903,
 * e.g. 499900 in the navigation app (4), 1199900 in settings (11) - far above any string table,
 * so they cannot hide a stock text, and a lookup through the owning app still reaches this hook.
 * Off: /tmp/tt-settings-off (until restart) or /mnt/app/root/tt-settings-off.
 * Everything here runs on the HMI thread for every screen and every text: fast, never throws.
 */
public final class ScreenHook {
    /** MENU -> Settings -> MMI settings. */
    public static final int MMI_SETTINGS = 1100040;

    /** Navigation app number (its screens and texts are 4xxxxx). */
    public static final int NAVI_APP = 4;
    public static final int TEXT_SUFFIX = 99900;
    public static final int T_CARD = 0;
    public static final int T_OFF = 1;
    public static final int T_NEAR = 2;
    public static final int T_ALWAYS = 3;
    private static final String[] TEXTS = {
        "iPhone turn card", "Off", "Near turns", "Always",
    };
    /** Our row and model ID suffixes (internal only; never looked up in the stock data pool). */
    public static final int ROW_CARD = 99990;
    public static final int MODEL_CARD = 99991;

    private static final File OFF = new File("/tmp/tt-settings-off");
    private static final File OFF_FLASH = new File("/mnt/app/root/tt-settings-off");
    /** The map-options drawer we already extended (drawers may be handed out more than once). */
    private static Object doneDrawer;

    private ScreenHook() {
    }

    /** AbstractScreenFactory.getText(id): our text for our IDs, else null (stock lookup). */
    public static String text(int id) {
        int i = id % 100000 - TEXT_SUFFIX;
        if (i < 0 || i >= TEXTS.length || id < 100000) {
            return null;
        }
        return TEXTS[i];
    }

    /** AbstractScreenFactory.getScreen(id, terminal): the finished screen, possibly with our rows. */
    public static Object created(Object screen, Object factory, int id, int terminal) {
        if (id != MMI_SETTINGS || screen == null) {
            return screen;
        }
        try {
            if (off()) {
                log("settings page: off (tt-settings-off)");
                return screen;
            }
            TtSettingsPage.addRows(screen, "MMI settings", id / 100000);
        } catch (Throwable t) {
            log(new StringBuffer("settings page: MMI settings failed, left as stock: ").append(String.valueOf(t)).toString());
        }
        return screen;
    }

    /** HMIRegistryEvo.getOptionDrawers(terminal, app): app's options drawers (the same objects the app built). */
    public static Object drawers(Object drawers, int app, int terminal) {
        try {
            if (app != NAVI_APP || !(drawers instanceof Object[])) {
                return drawers;
            }
            Object[] d = (Object[]) drawers;
            if (d.length < 2 || d[1] == null || d[1] == doneDrawer) {
                return drawers;
            }
            doneDrawer = d[1];
            if (off()) {
                log("settings page: off (tt-settings-off)");
                return drawers;
            }
            log(new StringBuffer("settings page: navigation drawers ").append(d.length).append(" (")
                .append(d[0] == null ? "null" : d[0].getClass().getName()).append(", ")
                .append(d[1].getClass().getName()).append(", terminal ").append(terminal).append(')').toString());
            TtSettingsPage.addRows(d[1], "map options", NAVI_APP);
        } catch (Throwable t) {
            log(new StringBuffer("settings page: map options failed, left as stock: ").append(String.valueOf(t)).toString());
        }
        return drawers;
    }

    private static boolean off() {
        return OFF.exists() || OFF_FLASH.exists();
    }

    public static void log(String line) {
        RgdBus.note(line);
    }
}
