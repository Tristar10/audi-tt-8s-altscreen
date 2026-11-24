package local.tt;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import de.audi.tghu.waveplayer.RingTonePlayer;
import de.audi.tghu.waveplayer.WavePlayer;
import de.audi.tghu.waveplayer.WavePlayerListener;
import de.esolutions.hmi.widgets.audi.base.eal.EALManager;

/**
 * Shift tone probe (tele10, wiki/Telemetry.md): plays one of the head unit's own tones through
 * the stock wave player service (de.audi.tghu.waveplayer.WavePlayer, the one the message chime and
 * the ringtone preview use). Each caller gets its own player client, so the stock users keep
 * theirs. Every state the player reports is logged with the milliseconds since the request, to
 * measure the delay. Java 1.4 library only.
 */
final class TeleBeep implements WavePlayerListener {
    private static final TeleBeep LISTENER = new TeleBeep();
    private static final Object LOCK = new Object();
    private static RingTonePlayer system;
    private static RingTonePlayer ring;
    private static long t0;
    private static String what = "-";

    private TeleBeep() {
    }

    /**
     * which: "system" (default) or "ring"; id: tone id, or -1 for the player's default tone.
     * Returns a line for the log.
     */
    static String play(String which, int id) {
        try {
            RingTonePlayer p = player("ring".equals(which));
            if (p == null) {
                return "tele beep: no wave player service";
            }
            synchronized (LOCK) {
                t0 = System.currentTimeMillis();
                what = new StringBuffer(which).append(' ').append(id < 0 ? "default" : String.valueOf(id)).toString();
            }
            if (id < 0) {
                p.playDefault(RingTonePlayer.PLAY_ONCE);
            } else {
                p.playTone(RingTonePlayer.PLAY_ONCE, id);
            }
            return new StringBuffer("tele beep: requested ").append(what).toString();
        } catch (Throwable t) {
            return new StringBuffer("tele beep failed ").append(String.valueOf(t)).toString();
        }
    }

    static String stop() {
        try {
            if (system != null) {
                system.abort();
            }
            if (ring != null) {
                ring.abort();
            }
            return "tele beep: aborted";
        } catch (Throwable t) {
            return new StringBuffer("tele beep abort failed ").append(String.valueOf(t)).toString();
        }
    }

    private static RingTonePlayer player(boolean wantRing) {
        synchronized (LOCK) {
            if (wantRing ? ring != null : system != null) {
                return wantRing ? ring : system;
            }
        }
        EALManager m = TtPanel.ealManager();
        if (m == null) {
            return null;
        }
        BundleContext bc = m.getTerminal().getFramework().getBundleCxt();
        ServiceReference r = bc == null ? null : bc.getServiceReference("de.audi.tghu.waveplayer.WavePlayer");
        WavePlayer wp = r == null ? null : (WavePlayer) bc.getService(r);
        if (wp == null) {
            return null;
        }
        RingTonePlayer p = wantRing ? wp.getRingTonePlayer() : (RingTonePlayer) wp.getSystemTonePlayer();
        p.setListener(LISTENER);
        synchronized (LOCK) {
            if (wantRing) {
                ring = p;
            } else {
                system = p;
            }
        }
        return p;
    }

    public void state(int s) {
        RgdBus.note(new StringBuffer("tele beep ").append(what).append(": ").append(name(s)).append(" after ")
            .append(System.currentTimeMillis() - t0).append(" ms").toString());
    }

    public void playToneInfo(int info) {
        RgdBus.note(new StringBuffer("tele beep ").append(what).append(": tone info ").append(info).append(" after ")
            .append(System.currentTimeMillis() - t0).append(" ms").toString());
    }

    private static String name(int s) {
        switch (s) {
            case STATE_PLAYING:
                return "playing";
            case STATE_ABORTED:
                return "aborted";
            case STATE_FINISHED:
                return "finished";
            case STATE_INTERRUPTED:
                return "interrupted";
            default:
                return new StringBuffer("state ").append(s).toString();
        }
    }
}
