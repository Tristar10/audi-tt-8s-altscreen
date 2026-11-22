package local.tt;

import java.io.File;

/**
 * v63: our settings page in the stock MMI, prototype.
 *
 * PatchZoom adds two calls to the framework class AbstractScreenFactory (shared by every HMI
 * app's screens):
 *   getScreen(id, terminal):  return ScreenHook.created(screen, this, id, terminal)
 *   getText(id):              s = ScreenHook.text(id); if (s != null) return s;  (then stock)
 *
 * created() changes nothing except the connectivity screen "About Apple CarPlay" (2500140),
 * where TtSettingsPage replaces the About text with our setting rows. Our texts use IDs
 * 2599900-2599999 in the connectivity module, above its string table, so they cannot hide a
 * stock text. Off: /tmp/tt-settings-off (until restart) or /mnt/app/root/tt-settings-off.
 * Everything here is called on the HMI thread for every screen and every text: fast, no throws.
 */
public final class ScreenHook {
    public static final int ABOUT_CARPLAY = 2500140;

    public static final int TEXT_BASE = 2599900;
    public static final int T_CARD = TEXT_BASE;
    public static final int T_OFF = TEXT_BASE + 1;
    public static final int T_NEAR = TEXT_BASE + 2;
    public static final int T_ALWAYS = TEXT_BASE + 3;
    private static final String[] TEXTS = {
        "iPhone turn card", "Off", "Near turns", "Always",
    };
    /** Our row and model IDs (internal IDs only; never looked up in the stock data pool). */
    public static final int ROW_CARD = 2599990;
    public static final int MODEL_CARD = 2599991;

    private static final File OFF = new File("/tmp/tt-settings-off");
    private static final File OFF_FLASH = new File("/mnt/app/root/tt-settings-off");

    private ScreenHook() {
    }

    /** AbstractScreenFactory.getText(id): our text for our IDs, else null (stock lookup). */
    public static String text(int id) {
        int i = id - TEXT_BASE;
        if (i < 0 || i >= TEXTS.length) {
            return null;
        }
        return TEXTS[i];
    }

    /** AbstractScreenFactory.getScreen(id, terminal): the finished screen, possibly with our rows. */
    public static Object created(Object screen, Object factory, int id, int terminal) {
        if (id != ABOUT_CARPLAY || screen == null) {
            return screen;
        }
        try {
            if (OFF.exists() || OFF_FLASH.exists()) {
                log("settings page: off (tt-settings-off), stock About page");
                return screen;
            }
            log(new StringBuffer("settings page: About Apple CarPlay screen built (terminal ")
                .append(terminal).append(", ").append(screen.getClass().getName()).append(')').toString());
            de.audi.tghu.connectivity.hmi.evohighmmikombi.TtSettingsPage.build(screen, factory, terminal);
        } catch (Throwable t) {
            log(new StringBuffer("settings page: failed, stock page kept: ").append(String.valueOf(t)).toString());
        }
        return screen;
    }

    /** iPhone turn card: 0 off (map only), 1 near turns, 2 always. */
    public static int cardMode() {
        return Card.mode();
    }

    public static Object cardListener(Object model) {
        return new CardChoice((de.audi.atip.hmi.model.ChoiceModel) model);
    }

    public static void log(String line) {
        RgdBus.note(line);
    }
}
