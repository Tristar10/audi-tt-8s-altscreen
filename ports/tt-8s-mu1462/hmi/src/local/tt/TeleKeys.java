package local.tt;

import java.io.File;

import de.audi.atip.hmi.event.EventDispatcherAdmin;
import de.audi.atip.hmi.event.KeyEvent;
import de.audi.atip.hmi.event.WheelButtonEvent;
import de.esolutions.fw.util.commons.job.BaseJobFilter;
import de.esolutions.fw.util.commons.job.Job;
import de.esolutions.hmi.widgets.audi.base.eal.EALManager;

/**
 * Steering-wheel keys for the autocross view (tele12, wiki/Telemetry.md). An event filter on the
 * HMI's dispatcher, the stock hook the driving-school display uses
 * (EventDispatcherAdmin.addEventFilter): every queued event goes on to the stock handling first
 * and unchanged; key events are only looked at, never consumed.
 *
 * - /tmp/tt-tele-keys exists: every key event is logged (code, pressed/released/turned/long,
 *   terminal), to find which code the wheel's buttons send on the TT.
 * - Key map (/mnt/app/root/tt/tele-keymap, or /tmp/tt-tele-keymap): lines "CODE EVENT ACTION",
 *   EVENT pressed | released | turned | long, ACTION open | up | down | press | back | close: the
 *   Haldex control page (TeleUi, tele17).
 * - The toggle key (/mnt/app/root/tt/tele-key-code, or /tmp/tt-tele-key-code, a number): pressed
 *   twice within 450 ms switches the view on or off (TeleView.press). Until a code is set, nothing
 *   toggles.
 *
 * Java 1.4 library only.
 */
final class TeleKeys extends BaseJobFilter {
    private static final File TRACE = new File("/tmp/tt-tele-keys");
    private static final File CODE_RAM = new File("/tmp/tt-tele-key-code");
    private static final File CODE_FLASH = new File("/mnt/app/root/tt/tele-key-code");
    private static boolean installed;
    private static volatile int toggleCode = -1;
    private static volatile boolean trace;
    /** /tmp/tt-tele-keys-off: no default wheel map (only an explicit key map, if any). */
    private static final File NO_DEFAULTS = new File("/tmp/tt-tele-keys-off");
    private static volatile boolean noDefaults;
    /** /tmp/tt-tele-keys-pass: the car gets every key again, as before tele30. */
    private static final File PASS = new File("/tmp/tt-tele-keys-pass");
    private static volatile boolean pass;
    /** Per key (0: 17, 1: 15): the press was kept from the car, so its release is too. */
    private static final boolean[] held = new boolean[2];
    private static final File MAP_RAM = new File("/tmp/tt-tele-keymap");
    private static final File MAP_FLASH = new File("/mnt/app/root/tt/tele-keymap");
    private static volatile int[][] keymap = new int[0][];
    /** tele29: holding the wheel press this long opens the pop-up (on release). */
    private static final long HOLD_MS = 700L;
    private static long downAt;
    private static boolean longDone;
    private static boolean openAtDown;

    private TeleKeys() {
    }

    /** Installs the filter once the HMI is up; safe to call every second. */
    static void install() {
        if (installed) {
            return;
        }
        try {
            EALManager m = TtPanel.ealManager();
            if (m == null) {
                return;
            }
            EventDispatcherAdmin a = m.getTerminal().getFramework().getHMIService().getEventDispatcherAdmin();
            if (a == null) {
                return;
            }
            a.addEventFilter(new TeleKeys());
            installed = true;
            RgdBus.note("tele keys: event filter installed");
        } catch (Throwable t) {
            installed = true; // do not retry every second
            RgdBus.note(new StringBuffer("tele keys: install failed ").append(String.valueOf(t)).toString());
        }
    }

    /** Re-read the switches (TeleView, every second). */
    static void readSwitches() {
        trace = TRACE.exists();
        noDefaults = NO_DEFAULTS.exists();
        pass = PASS.exists();
        int c = readCode(CODE_RAM);
        toggleCode = c >= 0 ? c : readCode(CODE_FLASH);
        int[][] km = readMap(MAP_RAM);
        keymap = km.length > 0 ? km : readMap(MAP_FLASH);
    }

    public void enqueue(Job job, int prio) {
        // tele30 (user 2026-11-25: the wheel also clicked things on the hidden map): while our view
        // is on screen, the wheel's press (17) and roll (20) are ours and the car does not get
        // them; Back (15) is ours only while the pop-up is open. Decided at the press and kept
        // until the release, so the car never sees half a click. Off with /tmp/tt-tele-keys-pass.
        boolean mine = false;
        try {
            Object p0 = job == null ? null : job.getPayload();
            if (p0 instanceof KeyEvent && !pass) {
                KeyEvent k0 = (KeyEvent) p0;
                int code = k0.getKeyCode();
                int id = k0.getID();
                if (code == 17 || code == 15) {
                    int slot = code == 17 ? 0 : 1;
                    if (id == KeyEvent.KEY_PRESSED) {
                        held[slot] = code == 17 ? TeleView.showing() : TeleView.showing() && TeleView.uiOpen();
                    }
                    mine = held[slot];
                    if (id == KeyEvent.KEY_RELEASED) {
                        held[slot] = false;
                    }
                } else if (code == 20) {
                    mine = TeleView.showing();
                }
            }
        } catch (Throwable t) {
            mine = false;
        }
        if (!mine) {
            super.enqueue(job, prio); // the stock handling, unchanged
        }
        try {
            Object p = job == null ? null : job.getPayload();
            if (!(p instanceof KeyEvent)) {
                return;
            }
            KeyEvent k = (KeyEvent) p;
            int id = k.getID();
            int dir = -1;
            if (k instanceof WheelButtonEvent) {
                dir = ((WheelButtonEvent) k).getDirection();
            }
            if (trace) {
                RgdBus.note(new StringBuffer("tele key ").append(k.getKeyCode()).append(' ').append(kind(id))
                    .append(dir >= 0 ? (dir == WheelButtonEvent.TURN_UP ? " up" : " down") : "")
                    .append(" terminal ").append(k.getTerminalID()).toString());
            }
            // Default wheel map (TT, selftest 2026-11-24): 17 the scroll-wheel press, 20 the roll
            // (direction in the event), 15 Back. Double press of 17 switches the view, a long
            // press opens the Haldex pop-up; roll changes the page or moves in the pop-up; Back
            // closes it. The stock action of every key still happens. A key map file replaces this.
            if (keymap.length == 0 && !noDefaults) {
                int code = k.getKeyCode();
                if (code == 17 && id == KeyEvent.KEY_PRESSED) {
                    // tele29: remember when, so a hold can open the pop-up on release (the car did
                    // not send KEY_LONG_PRESSED on 2026-11-25, only pressed/released)
                    downAt = System.currentTimeMillis();
                    longDone = false;
                    openAtDown = TeleView.uiOpen();
                    TeleView.press();
                } else if (code == 17 && id == KeyEvent.KEY_LONG_PRESSED) {
                    if (!longDone && !openAtDown) {
                        longDone = true;
                        TeleUi.post(TeleUi.OPEN);
                    }
                } else if (code == 17 && id == KeyEvent.KEY_RELEASED) {
                    long held = downAt > 0 ? System.currentTimeMillis() - downAt : 0;
                    if (held >= HOLD_MS && !longDone && !openAtDown) {
                        TeleUi.post(TeleUi.OPEN);
                        if (trace) {
                            RgdBus.note(new StringBuffer("tele key 17 held ").append(held).append(" ms: pop-up").toString());
                        }
                    }
                    downAt = 0;
                    longDone = false;
                } else if (code == 20 && id == KeyEvent.KEY_TURNED && TeleView.isOn()) {
                    TeleUi.post(dir == WheelButtonEvent.TURN_UP ? TeleUi.UP : TeleUi.DOWN);
                } else if (code == 15 && id == KeyEvent.KEY_PRESSED && TeleView.uiOpen()) {
                    TeleUi.post(TeleUi.BACK);
                }
                return;
            }
            if (id == KeyEvent.KEY_PRESSED && toggleCode >= 0 && k.getKeyCode() == toggleCode) {
                TeleView.press();
            }
            // tele18: a long press of the same key opens or closes the Haldex pop-up
            if (id == KeyEvent.KEY_LONG_PRESSED && toggleCode >= 0 && k.getKeyCode() == toggleCode) {
                TeleUi.post(TeleUi.OPEN);
            }
            int[][] km = keymap;
            for (int i = 0; i < km.length; i++) {
                if (km[i][0] == k.getKeyCode() && km[i][1] == id) {
                    TeleUi.post(km[i][2]);
                }
            }
        } catch (Throwable t) {
            // never disturb the stock key handling
        }
    }

    private static String kind(int id) {
        switch (id) {
            case KeyEvent.KEY_PRESSED:
                return "pressed";
            case KeyEvent.KEY_RELEASED:
                return "released";
            case KeyEvent.KEY_TURNED:
                return "turned";
            case KeyEvent.KEY_MOVED:
                return "moved";
            case KeyEvent.KEY_LONG_PRESSED:
                return "long";
            default:
                return new StringBuffer("event ").append(id).toString();
        }
    }

    /** "CODE EVENT ACTION" lines -> {code, event id, action}. */
    private static int[][] readMap(File f) {
        if (!f.exists()) {
            return new int[0][];
        }
        int[][] out = new int[16][];
        int n = 0;
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            try {
                for (String l = r.readLine(); l != null && n < out.length; l = r.readLine()) {
                    java.util.StringTokenizer t = new java.util.StringTokenizer(l);
                    if (t.countTokens() < 3) {
                        continue;
                    }
                    try {
                        int code = Integer.parseInt(t.nextToken());
                        String ev = t.nextToken();
                        int id = ev.equals("released") ? KeyEvent.KEY_RELEASED : ev.equals("turned") ? KeyEvent.KEY_TURNED
                            : ev.equals("long") ? KeyEvent.KEY_LONG_PRESSED : KeyEvent.KEY_PRESSED;
                        int act = TeleUi.action(t.nextToken());
                        if (act != 0) {
                            out[n++] = new int[] {code, id, act};
                        }
                    } catch (NumberFormatException e) {
                        // skip the line
                    }
                }
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return new int[0][];
        }
        int[][] m = new int[n][];
        System.arraycopy(out, 0, m, 0, n);
        return m;
    }

    private static int readCode(File f) {
        if (!f.exists()) {
            return -1;
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            try {
                String l = r.readLine();
                return l == null ? -1 : Integer.parseInt(l.trim());
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return -1;
        }
    }
}
