package local.tt;

import de.esolutions.hmi.widgets.audi.base.eal.EALManager;

/** SKETCH. One gauge redraw on the HMI event thread (a plain class, like PanelJob: no synthetic accessors). */
final class GaugeJob implements Runnable {
    private final EALManager eal;

    GaugeJob(EALManager eal) {
        this.eal = eal;
    }

    public void run() {
        long t0 = System.currentTimeMillis();
        String r;
        try {
            r = Gauges.onHmiThread(eal);
        } catch (Throwable t) {
            r = new StringBuffer("error ").append(String.valueOf(t)).toString();
        }
        long ms = System.currentTimeMillis() - t0;
        Gauges.jobDone(ms);
        if (r != null || ms > 20) {
            RgdBus.note(new StringBuffer("gauge job ").append(ms).append(" ms ").append(r == null ? "" : r).toString());
        }
    }
}
