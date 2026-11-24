package local.tt;

import java.io.File;

import de.audi.atip.hmi.event.EventDispatcherAdmin;
import de.audi.atip.hmi.event.KeyEvent;
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
        int c = readCode(CODE_RAM);
        toggleCode = c >= 0 ? c : readCode(CODE_FLASH);
    }

    public void enqueue(Job job, int prio) {
        super.enqueue(job, prio); // the stock handling first, unchanged
        try {
            Object p = job == null ? null : job.getPayload();
            if (!(p instanceof KeyEvent)) {
                return;
            }
            KeyEvent k = (KeyEvent) p;
            int id = k.getID();
            if (trace) {
                RgdBus.note(new StringBuffer("tele key ").append(k.getKeyCode()).append(' ').append(kind(id))
                    .append(" terminal ").append(k.getTerminalID()).toString());
            }
            if (id == KeyEvent.KEY_PRESSED && toggleCode >= 0 && k.getKeyCode() == toggleCode) {
                TeleView.press();
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
