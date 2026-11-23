package local.tt;

import de.esolutions.hmi.widgets.audi.base.eal.EALManager;

/** One autocross-view update on the HMI event thread (a plain class, like PanelJob). */
final class TeleJob implements Runnable {
    private final EALManager eal;
    private final TeleFrame frame;

    TeleJob(EALManager eal, TeleFrame frame) {
        this.eal = eal;
        this.frame = frame;
    }

    public void run() {
        long t0 = System.currentTimeMillis();
        String r;
        try {
            r = TeleView.onHmiThread(eal, frame);
        } catch (Throwable t) {
            r = new StringBuffer("error ").append(String.valueOf(t)).toString();
        }
        long ms = System.currentTimeMillis() - t0;
        TeleView.jobDone(ms);
        if (r != null || ms > 30) {
            RgdBus.note(new StringBuffer("tele job ").append(ms).append(" ms ").append(r == null ? "" : r).toString());
        }
    }
}
