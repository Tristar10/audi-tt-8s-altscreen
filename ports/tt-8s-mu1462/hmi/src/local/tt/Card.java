package local.tt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

/**
 * The iPhone's own maneuver card on the cockpit CarPlay map.
 *
 * v44: per view (Classic with card, Sport map only). v46: the card shows only near a turn, the same rule as RgdBus.nearTurn (next turn or destination within 1 km,
 * motorway exits and forks 2 km), plus 10 s at route start and 5 s after a turn; otherwise map
 * only ("map" surface). v49 (user): never in the Sport skin's small view (ZoomGate.smallMap); every
 * other view, including the standard skin's two-dials view (v52), near turns. Its position comes from the hook's maneuverLayout setting (sh tt.sh card top).
 *
 * The hook reads /tmp/mibr-alt111-url-mode when CarPlay connects and on every showUI it sends;
 * touching /tmp/mibr-alt111-show-only makes it send showUI again with the current address, so the
 * iPhone switches while connected. At most one switch per 4 s. /tmp/tt-card-fixed (set by the
 * kit's manual card commands) turns this off.
 *
 * v56 (user, runs 48-49: the switching upset too much): map only by default; the automatic card
 * (v54 rules, 1 km) is an option, /tmp/tt-card-auto (sh tt.sh card auto).
 */
public final class Card {
    private static final File MODE = new File("/tmp/mibr-alt111-url-mode");
    private static final File SHOW = new File("/tmp/mibr-alt111-show-only");
    private static final File FIXED = new File("/tmp/tt-card-fixed");
    private static final File AUTO = new File("/tmp/tt-card-auto");
    private static final File STATUS = new File("/tmp/mibr-alt111-gen2.status");
    /** -1 not set yet, 0 card ("base"), 1 map only ("map"). */
    private static int last = -1;
    private static long lastSwitch;
    private static boolean routeOn;
    private static boolean nearNow;
    private static boolean lastNear;
    private static long startUntil;
    private static long holdUntil;

    private Card() {
    }

    /** RgdBus, on every iPhone update: whether a route runs and whether its next turn is near. */
    static void route(boolean on, boolean near) {
        long now = System.currentTimeMillis();
        synchronized (MODE) {
            if (on && !routeOn) {
                startUntil = now + 10000L;
            }
            if (lastNear && !near) {
                holdUntil = now + 5000L;
            }
            lastNear = near;
            routeOn = on;
            nearNow = near;
        }
        update(now);
    }

    /** Layers loop, every second: lets the start/after-turn holds run out while nothing is sent. */
    static void tick() {
        update(System.currentTimeMillis());
    }

    /** ZoomGate.stage: the cockpit view size changed (also once at MMI start). Same rule in both views. */
    static void view(boolean small) {
        update(System.currentTimeMillis());
    }

    private static void update(long now) {
        try {
            if (FIXED.exists()) {
                return;
            }
            int want;
            boolean live;
            synchronized (MODE) {
                boolean card = AUTO.exists() && routeOn && !ZoomGate.smallMap()
                    && (nearNow || now < startUntil || now < holdUntil);
                want = card ? 0 : 1;
                if (want == last) {
                    return;
                }
                if (last != -1 && now - lastSwitch < 4000L) {
                    return;
                }
                last = want;
                lastSwitch = now;
                write(MODE, want == 0 ? "base\n" : "map\n");
                live = sessionReady();
                if (live) {
                    write(SHOW, "1\n");
                    // v53 (run46): the iPhone rebuilds the map at its default zoom on every showUI.
                    ZoomGate.mapReset("card switch");
                }
            }
            RgdBus.note(new StringBuffer("card: ").append(want == 0 ? "shown (turn near)" : "map only")
                .append(live ? " (sent to the iPhone)" : " (from the next connect)").toString());
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("card: ").append(String.valueOf(t)).toString());
        }
    }

    /** v63 settings page: 0 off (map only), 1 near turns, 2 always - the same files as sh tt.sh card off|auto|on. */
    static int mode() {
        if (AUTO.exists()) {
            return 1;
        }
        if (FIXED.exists() && "base".equals(readMode())) {
            return 2;
        }
        return 0;
    }

    static void setMode(int m) throws java.io.IOException {
        synchronized (MODE) {
            if (m == 1) {
                FIXED.delete();
                write(AUTO, "");
                last = -1;
            } else {
                AUTO.delete();
                write(FIXED, "");
                int want = m == 2 ? 0 : 1;
                write(MODE, want == 0 ? "base\n" : "map\n");
                last = want;
                lastSwitch = System.currentTimeMillis();
                if (sessionReady()) {
                    write(SHOW, "1\n");
                    ZoomGate.mapReset("card setting");
                }
            }
        }
        if (m == 1) {
            update(System.currentTimeMillis());
        }
        RgdBus.note(new StringBuffer("card: setting ").append(m == 0 ? "off" : m == 1 ? "near turns" : "always").toString());
    }

    private static String readMode() {
        FileInputStream in = null;
        try {
            if (!MODE.exists()) {
                return "";
            }
            in = new FileInputStream(MODE);
            byte[] b = new byte[16];
            int n = in.read(b);
            return n > 0 ? new String(b, 0, n).trim() : "";
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static boolean sessionReady() {
        FileInputStream in = null;
        try {
            if (!STATUS.exists()) {
                return false;
            }
            in = new FileInputStream(STATUS);
            byte[] b = new byte[2048];
            int n = in.read(b);
            String s = n > 0 ? new String(b, 0, n) : "";
            return s.indexOf("control_session=1") >= 0 && s.indexOf("command_ready=1") >= 0;
        } catch (Throwable t) {
            return false;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void write(File f, String s) throws java.io.IOException {
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(s.getBytes());
        } finally {
            out.close();
        }
    }
}
