package local.tt;

/**
 * Glide (tele17, wiki/Telemetry.md): the gauges move smoothly between data updates instead of
 * stepping at the link's 20 Hz. For each moving channel it keeps the last two samples and, at every
 * tick, shows the value on the line between them, one sample late (about 50 ms at 20 Hz). A new
 * sample is noticed when any of the channels changes. Shared by the head unit and the simulator.
 * Pure Java 1.4.
 */
public final class TeleGlide {
    /** The channels that move continuously; gear, preset, temperatures are left alone. */
    public static final int[] CH = {TeleLayout.CH_RPM, TeleLayout.CH_SPEED, TeleLayout.CH_PEDAL, TeleLayout.CH_BOOST,
        TeleLayout.CH_BRAKE, TeleLayout.CH_HX_TARGET, TeleLayout.CH_HX_ACTUAL};

    private final int[] last = new int[TeleLayout.CHANNELS];
    private final double[] prev = new double[TeleLayout.CHANNELS];
    private final double[] cur = new double[TeleLayout.CHANNELS];
    private long prevAt;
    private long curAt;

    /**
     * Call once per tick after the layout's values were filled in. sample: true when the caller
     * knows a new sample arrived (demo); false lets the glide detect it by a changed value.
     */
    public void apply(TeleLayout t, long now, boolean sample) {
        boolean changed = sample;
        if (!changed) {
            for (int k = 0; k < CH.length; k++) {
                if (t.val[CH[k]] != last[CH[k]]) {
                    changed = true;
                }
            }
        }
        if (changed) {
            prevAt = curAt;
            curAt = now;
            for (int k = 0; k < CH.length; k++) {
                int ch = CH[k];
                prev[ch] = cur[ch];
                cur[ch] = t.val[ch];
                last[ch] = t.val[ch];
            }
        }
        if (prevAt <= 0 || curAt <= prevAt || curAt - prevAt > 500) {
            return; // no rhythm yet, or a gap: show the values as they are
        }
        double f = Math.min(1.0, (now - curAt) / (double) (curAt - prevAt));
        for (int k = 0; k < CH.length; k++) {
            int ch = CH[k];
            if (t.has[ch]) {
                t.val[ch] = (int) Math.round(prev[ch] + (cur[ch] - prev[ch]) * f);
            }
        }
    }
}
