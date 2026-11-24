package local.tt;

/**
 * The rev strip painted whole, once (tele8 probe, wiki/Telemetry.md): one picture of the dark
 * track and one of the lit strip, each a single continuous shape along TeleLayout's corner points
 * with round joins at the corners, soft edges, the red zone and 1000 rpm ticks. On the car the lit
 * picture is shown through a clip rectangle whose right edge is the current rpm, so a frame only
 * moves that edge (no pieces, no seams, nothing recoloured).
 *
 * The strip runs left to right everywhere (x grows with rpm along its centre line), so a vertical
 * cut at the centre line's x is exact there; on the slopes the cut is vertical, not square to the
 * slope.
 *
 * Pure Java 1.4 (the PC preview uses it too): no string +, no generics, no autoboxing.
 */
public final class TeleStrip {
    /** The pictures' top-left corner on the screen and their size. */
    public static final int X0 = 12;
    public static final int Y0 = 116;
    public static final int W = 1416;
    public static final int H = 176;

    /** ARGB, not premultiplied, W x H, row by row. */
    public final int[] track = new int[W * H];
    public final int[] lit = new int[W * H];
    /** The lit strip all red (from the shift point on, user 2026-11-24). */
    public final int[] litRed = new int[W * H];
    public long paintMs;

    private TeleStrip() {
    }

    /** Screen x of the strip's centre line at this rpm (where the clip edge goes). */
    public static int cutX(TeleLayout t, int rpm) {
        double[] o = new double[4];
        TeleLayout.tp(t.rpmToS(Math.max(t.barFromRpm, Math.min(t.barToRpm, rpm))), o);
        return (int) Math.round(o[0]);
    }

    public static TeleStrip paint(TeleLayout t) {
        long t0 = System.currentTimeMillis();
        TeleStrip p = new TeleStrip();
        int n = TeleLayout.TP_X.length - 1;
        double[] ax = new double[n];
        double[] ay = new double[n];
        double[] ux = new double[n];
        double[] uy = new double[n];
        double[] sl = new double[n];
        double[] s0 = new double[n];
        double acc = 0;
        for (int i = 0; i < n; i++) {
            ax[i] = TeleLayout.TP_X[i];
            ay[i] = TeleLayout.TP_Y[i];
            double dx = TeleLayout.TP_X[i + 1] - ax[i];
            double dy = TeleLayout.TP_Y[i + 1] - ay[i];
            sl[i] = Math.sqrt(dx * dx + dy * dy);
            ux[i] = dx / sl[i];
            uy[i] = dy / sl[i];
            s0[i] = acc;
            acc += sl[i];
        }
        double sRed = t.rpmToS(t.redlineRpm);
        double sShift = sRed; // the lit strip turns red at the red line (6800), where the red zone starts
        int kFrom = (t.barFromRpm + 999) / 1000;
        int kTo = t.barToRpm / 1000;
        double[] ticks = new double[Math.max(0, kTo - kFrom + 1)];
        double[] tickX = new double[ticks.length];
        double[] o = new double[4];
        int nt = 0;
        for (int k = Math.max(1, kFrom); k <= kTo; k++) {
            double s = t.rpmToS(k * 1000);
            // vertical ticks, so the corners are fine too (user 2026-11-24: 3000 was missing); not at the end
            if (s < acc - 4) {
                TeleLayout.tp(s, o);
                tickX[nt] = o[0];
                ticks[nt++] = s;
            }
        }
        for (int py = 0; py < H; py++) {
            double y = TeleStrip.Y0 + py + 0.5;
            for (int px = 0; px < W; px++) {
                double x = TeleStrip.X0 + px + 0.5;
                double best = 1e9;
                double bs = 0;
                double perp = 0;
                double over = 0;
                for (int i = 0; i < n; i++) {
                    double rx = x - ax[i];
                    double ry = y - ay[i];
                    double along = rx * ux[i] + ry * uy[i];
                    double tc = along < 0 ? 0 : along > sl[i] ? sl[i] : along;
                    double cx = rx - ux[i] * tc;
                    double cy = ry - uy[i] * tc;
                    double d = Math.sqrt(cx * cx + cy * cy);
                    if (d < best) {
                        best = d;
                        bs = s0[i] + tc;
                        perp = Math.abs(rx * uy[i] - ry * ux[i]);
                        over = i == 0 && along < 0 ? -along : i == n - 1 && along > sl[i] ? along - sl[i] : 0;
                        if (over == 0) {
                            perp = d; // round joins at the corners
                        }
                    }
                }
                double hw = TeleLayout.half(bs);
                double cov = clamp01(hw - perp + 0.5) * (over > 0 ? clamp01(0.5 - over) : 1);
                if (cov <= 0) {
                    continue;
                }
                int a = (int) Math.round(cov * 255);
                double inside = hw - perp; // px in from the edge
                double tick = 0;
                for (int k = 0; k < nt; k++) {
                    tick = Math.max(tick, clamp01(1.6 - Math.abs(x - tickX[k]))); // vertical ticks
                }
                // Track: an outlined band (after Audi's RS performance layout): a near-black body and
                // a 1.5 px hairline edge, red past the redline; faint ticks.
                // red zone = the right slope, cut vertically at its corner (the red line), with
                // hazard stripes (user 2026-11-24)
                boolean red = x >= TeleLayout.TP_X[2];
                boolean hazard = red && ((int) Math.floor((x + y) / 9.0) & 1) == 0;
                int tc = red ? (hazard ? 0x4A1210FF : 0x1A0706FF) : 0x0B0E12FF;
                int edge = red ? 0x8A2219FF : 0x56606BFF;
                tc = TeleLayout.mix(tc, edge, (float) clamp01(2.0 - inside));
                tc = TeleLayout.mix(tc, TeleLayout.LINE, (float) (tick * 0.6 * clamp01(inside - 1.5)));
                p.track[py * W + px] = (a << 24) | (tc >>> 8);
                // Lit: cool white, warming to amber over the last 1500 rpm before the shift point,
                // red past it; a soft tube shading across, darker ticks.
                int rpm = t.sToRpm(bs);
                int warm = t.shiftRpm - 1500;
                int lc = x >= TeleLayout.TP_X[2] ? TeleLayout.RED
                    : rpm <= warm ? TeleLayout.STRIP_LO
                    : TeleLayout.mix(TeleLayout.STRIP_LO, TeleLayout.AMBER, (rpm - warm) / 1500f);
                // inset 2.5 px so the hairline edge stays around the fill
                double litCov = clamp01(hw - 2.5 - perp + 0.5) * (over > 0 ? clamp01(0.5 - over - 2.5) : 1);
                if (litCov <= 0) {
                    continue;
                }
                double k = perp / hw;
                lc = TeleLayout.mix(lc, TeleLayout.BG, (float) (0.12 * k * k));
                lc = TeleLayout.mix(lc, TeleLayout.BG, (float) (tick * 0.5));
                p.lit[py * W + px] = ((int) Math.round(litCov * 255) << 24) | (lc >>> 8);
                int rc = TeleLayout.mix(TeleLayout.RED, TeleLayout.BG, (float) (0.12 * k * k + tick * 0.5));
                p.litRed[py * W + px] = ((int) Math.round(litCov * 255) << 24) | (rc >>> 8);
            }
        }
        p.paintMs = System.currentTimeMillis() - t0;
        return p;
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
