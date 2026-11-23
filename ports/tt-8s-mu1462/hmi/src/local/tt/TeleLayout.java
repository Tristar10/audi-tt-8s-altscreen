package local.tt;

/**
 * Layout of the OpenHaldex autocross view (wiki/Telemetry.md). Pure Java, no EAL.
 *
 * The autocross view replaces the map: a double press on the scroll wheel switches it on and off.
 * It fills the whole cockpit screen with an opaque background, so no map shows. The cluster's own
 * dials, tab bar, bottom bar and the frame over the top corners are drawn above our layer, so the
 * layout keeps clear of them.
 *  - Large (Classic) view, after the FL5 Civic Type R's +R screen: slanted shift lights under the
 *    tab bar, lighting from both ends to the middle (green, blue, red) and all flashing red at the
 *    shift point;
 *    a rev line in straight pieces following the cluster housing (up the left slope, across, down
 *    the right slope), numbers under it; a red line across with a notch in the middle holding a big
 *    gear; rpm left of the notch, Haldex lock right of it; the active preset in the pocket above the
 *    left slope, link status above the right slope, both tilted along the slope (the very top
 *    corners are covered by the cluster's frame); the five temperatures in a row at the bottom, one icon per value, in C.
 *    No speed: the stock speedometer shows it.
 *  - Small (Sport) view: only the map area left of the big rev counter is ours: gear and engine
 *    temperatures, one icon per value.
 *
 * build() turns the latest values into a list of slots: filled boxes, filled quads (rev line
 * pieces, notch), icons (TeleIcons) and texts. The same view and page always give the same slots
 * in the same order, so the HMI side can create one EAL node per slot when the view or page
 * changes, and per frame only change visibility, text, colour and box width (no texture upload
 * per frame, see Cockpit-drawing.md). Quads and icons are drawn into their own small pictures
 * once, one picture per colour they can take. The PC preview (gauges/tools/TelePreview.java)
 * draws the same slots with AWT.
 *
 * Coordinates are cockpit screen pixels (1440x540). A text slot's y is the top of the text; the
 * car places text by baseline at y + 0.82 * size. Colours are 0xRRGGBBAA, like EAL text colours.
 *
 * Java 1.4 library only (HMI-patching.md): no string +, no generics, no autoboxing.
 */
public final class TeleLayout {
    public static final int VIEW_LARGE = 0;
    public static final int VIEW_SMALL = 1;
    public static final int PAGE_LIVE = 0;
    public static final int PAGE_PRESETS = 1;
    public static final int BOX = 0;
    public static final int TEXT = 1;
    public static final int QUAD = 2;
    public static final int ICON = 3;
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int MAX = 400;
    public static final int CHANNELS = 64;
    public static final int MAXPRESET = 8;
    public static final int SCREEN_W = 1440;
    public static final int SCREEN_H = 540;

    // TTG1 channel ids (docs/MMI_LINK.md in OpenHaldex-S3), values are x1000.
    public static final int CH_RPM = 1;
    public static final int CH_SPEED = 2;
    public static final int CH_PEDAL = 3;
    public static final int CH_COOLANT = 5;
    public static final int CH_OIL = 6;
    public static final int CH_IAT = 7;
    public static final int CH_BOOST = 8;
    public static final int CH_HX_TARGET = 10;
    public static final int CH_HX_ACTUAL = 11;
    public static final int CH_GEAR = 13;
    public static final int CH_DSG = 16;
    public static final int CH_CLUTCH = 19;
    public static final int CH_PRESET = 22;

    public static final int BG = 0x000000FF;
    public static final int PANEL = 0x0B0F14FF;
    public static final int DIM = 0x262D35FF;
    public static final int LINE = 0x3A424BFF;
    public static final int WHITE = 0xF2F5F7FF;
    public static final int GREY = 0x8A949EFF;
    public static final int ICONC = 0xB8C0C8FF;
    public static final int GREEN = 0x34C759FF;
    public static final int AMBER = 0xFFB020FF;
    public static final int RED = 0xFF3B30FF;
    public static final int NOTCH = 0x2E0806FF;
    public static final int NOTCH_IN = 0x4A0E0AFF;
    public static final int NOTCH_HOT = 0x7A160EFF;
    public static final int NOTCH_HOT_IN = 0xB8241AFF;
    public static final int LED_OFF = 0x1E242BFF;
    public static final int BLUE = 0x2F7BFFFF;
    public static final int STRIP_LO = 0xDCE6EEFF;
    public static final int TRACK = 0x1A2027FF;
    public static final int EDGE = 0x2E363FFF;
    public static final int DARK_TEXT = 0x0B0F14FF;
    public static final int PEDAL_FILL = 0x34C759FF;
    public static final int BOOST_FILL = 0xFF5A36FF;
    public static final int VAC_FILL = 0x5A7A99FF;
    public static final int HALDEX = 0x2F9BFFFF;
    public static final int PRESET = 0x7FD3FFFF;
    public static final int COLD = 0x5AC8FAFF;
    public static final int CURSOR = 0x1C3B5AFF;
    public static final int ACTIVE = 0x34C759FF;

    private static final String[] MODES = {"STOCK", "FWD", "50:50", "60:40", "70:30", "80:20", "90:10",
        "SPEED", "THROTTLE", "MAP", "RPM"};

    // Temperatures, degrees C: channel, icon, amber and red limits. Haldex clutch limits are
    // guesses until we see real values.
    private static final int[] T_CH = {CH_COOLANT, CH_OIL, CH_IAT, CH_DSG, CH_CLUTCH};
    private static final int[] T_ICON = {TeleIcons.COOLANT, TeleIcons.OIL, TeleIcons.IAT, TeleIcons.DSG,
        TeleIcons.CLUTCH};
    private static final int[] T_AMBER = {108, 130, 55, 110, 90};
    private static final int[] T_RED = {115, 140, 70, 125, 110};
    private static final int OIL_COLD = 60;

    // Rev strip (large view): straight pieces following the cluster housing. It rises along the
    // frame's top-left edge, runs flat under the shift lights and drops along the top-right edge,
    // leaving a pocket above each slope. The flat middle is the fattest part and ends exactly at the
    // shift point; the slopes taper. The scale is in three parts: the left slope up to MID_RPM, the
    // middle up to the shift point, the right slope from the shift point to barToRpm.
    private static final int[] TP_X = {24, 325, 1057, 1416};
    private static final int[] TP_Y = {262, 148, 148, 262};
    private static final int HALF_MIN = 8;
    private static final int HALF_MAX = 22;
    private static final int TP_SEGS = 120;
    private static final int MID_RPM = 3000;
    // Shift lights: LEDS_SIDE per side under the tab bar, lighting from both ends towards the
    // middle, LED_STEP rpm apart up to the shift point.
    private static final int LEDS_SIDE = 6;
    private static final int LED_STEP = 200;
    // Red line and the notch holding the gear.
    private static final int LINE_Y = 200;
    private static final int NOTCH_X0 = 596;
    private static final int NOTCH_X1 = 844;
    private static final int NOTCH_SLOPE = 30;
    private static final int NOTCH_Y = 324;
    // Temperature row between the dials, under the notch.
    private static final int GRID_X0 = 330;
    private static final int GRID_X1 = 1090;
    private static final int GRID_Y = 362;

    // Settings
    public int shiftRpm = 6500;
    public int limitRpm = 6800;
    public int barFromRpm = 0;
    public int barToRpm = 7000;
    /** Temperatures in degrees F when true; the car uses C (user, 2026-11-23). */
    public boolean imperial = false;
    /** Boost in psi when true, bar when false. Channel 8 is taken as kPa above atmosphere. */
    public boolean boostPsi = false;

    // Input: latest values (x1000), whether each is fresh, link state, preset catalog.
    public final int[] val = new int[CHANNELS];
    public final boolean[] has = new boolean[CHANNELS];
    public boolean link;
    public long now;
    public int catN;
    public final int[] catId = new int[MAXPRESET];
    public final int[] catMode = new int[MAXPRESET];
    public final String[] catName = new String[MAXPRESET];
    public int cursor;
    public String status;
    public int statusRgba = GREY;
    /** Datagrams per second from OpenHaldex, -1 if unknown. */
    public int rateHz = -1;

    // Output
    public int count;
    public final int[] kind = new int[MAX];
    public final int[] x = new int[MAX];
    public final int[] y = new int[MAX];
    public final int[] w = new int[MAX];
    public final int[] h = new int[MAX];
    public final int[] rgba = new int[MAX];
    /** Text size in px; for an ICON slot the TeleIcons id. */
    public final int[] size = new int[MAX];
    public final int[] align = new int[MAX];
    public final boolean[] bold = new boolean[MAX];
    public final boolean[] vis = new boolean[MAX];
    public final String[] text = new String[MAX];
    /** Rotation in degrees, clockwise on screen, around the slot's (x, y). Texts and icons only. */
    public final float[] rot = new float[MAX];
    /** Quad corners, 4 per slot (qx[4 * i + k]); x/y/w/h hold the quad's bounding box. */
    public final int[] qx = new int[MAX * 4];
    public final int[] qy = new int[MAX * 4];

    private final double[] pa = new double[4];
    private final double[] pb = new double[4];

    public void build(int view, int page) {
        boolean large = view == VIEW_LARGE;
        count = 0;
        box(0, 0, SCREEN_W, SCREEN_H, BG, true);
        if (page == PAGE_PRESETS)
            presets(large);
        else if (large)
            liveLarge();
        else
            liveSmall();
    }

    private void liveLarge() {
        int rpm = ok(CH_RPM) ? r(val[CH_RPM]) : 0;
        boolean blink = (now / 100) % 2 == 0;
        boolean over = link && rpm >= shiftRpm;
        boolean limit = link && rpm >= limitRpm;
        int vc = link ? WHITE : GREY;
        double l0 = segLen(0);
        double l1 = segLen(1);
        double len = tpLen();
        double r0 = l0 + l1; // start of the right slope = the shift point

        // Glow behind the gear: layered translucent red, brighter while the shift cue flashes.
        boolean hot = over && blink;
        int[] gw = {300, 240, 180};
        int[] ga = {hot ? 0x22 : 0x10, hot ? 0x30 : 0x18, hot ? 0x48 : 0x22};
        for (int i = 0; i < gw.length; i++)
            quad(720 - gw[i], 176, 720 + gw[i], 176, 720 + gw[i] - 70, 352, 720 - gw[i] + 70, 352,
                (RED & 0xFFFFFF00) | ga[i], true);

        // Shift lights above the strip, as wide as its fat middle, slanted like the housing's slopes,
        // lighting from both ends towards the middle: green, blue, red over the last
        // LEDS_SIDE * LED_STEP rpm. From the shift point all flash red. Lit lights glow.
        int lx0 = TP_X[1];
        int lx1 = TP_X[2];
        int mid = 24;
        int lgap = 8;
        int lw = ((lx1 - lx0 - mid) / 2 - (LEDS_SIDE - 1) * lgap) / LEDS_SIDE;
        int lh = 22;
        int ly = 98;
        int sk = 8;
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < LEDS_SIDE; i++) {
                int on = shiftRpm - (LEDS_SIDE - i) * LED_STEP;
                boolean lit = link && (over ? blink : rpm >= on);
                int c = !lit ? LED_OFF : over ? RED : i < 2 ? GREEN : i < 4 ? BLUE : RED;
                int x0 = side == 0 ? lx0 + i * (lw + lgap) : lx1 - lw - i * (lw + lgap);
                int x1 = x0 + lw;
                int t = side == 0 ? sk : -sk; // top edge shifted: "/" on the left, "\" on the right
                int g = 6;
                quad(x0 + t - g, ly - g, x1 + t + g, ly - g, x1 + g, ly + lh + g, x0 - g, ly + lh + g,
                    (c & 0xFFFFFF00) | 0x50, lit);
                quad(x0 + t, ly, x1 + t, ly, x1, ly + lh, x0, ly + lh, c, true);
            }
        }

        // Rev strip: a dark track with hairline edges; lit from cool white, warming to amber over the
        // last 1500 rpm before the shift point, all red past it, the whole strip flashing at the
        // limiter. Numbers sit inside the strip, dark on the lit part.
        for (int k = 0; k < 3; k++) {
            double a = k == 0 ? 0 : k == 1 ? l0 : r0;
            double b = k == 0 ? l0 : k == 1 ? r0 : len;
            bandOff(a, b, half(a) + 1, half(a) + 3, half(b) + 1, half(b) + 3, EDGE, true);
            bandOff(a, b, -half(a) - 3, -half(a) - 1, -half(b) - 3, -half(b) - 1, EDGE, true);
        }
        for (int i = 0; i < TP_SEGS; i++) {
            double s0 = len * i / TP_SEGS;
            double s1 = len * (i + 1) / TP_SEGS - 1.0;
            int from = sToRpm(s0);
            int c;
            if (limit)
                c = blink ? RED : TRACK;
            else if (rpm <= from || !link)
                c = TRACK;
            else if (over)
                c = RED;
            else
                c = stripColour(from);
            bandOff(s0, s1, -half(s0), half(s0), -half(s1), half(s1), c, true);
        }
        // Shift point: a red cut across the strip where the fat middle ends.
        bandOff(r0 - 1.5, r0 + 1.5, -HALF_MAX - 7, HALF_MAX + 7, -HALF_MAX - 7, HALF_MAX + 7, RED, true);
        // Limiter range: red marks along the outer edge of the right slope.
        double sl = rpmToS(limitRpm);
        for (int i = 0; i < 5; i++) {
            double a = sl + (len - sl) * i / 5;
            double b = sl + (len - sl) * (i + 1) / 5 - 3;
            bandOff(a, b, half(a) + 5, half(a) + 10, half(b) + 5, half(b) + 10, RED, limitRpm < barToRpm);
        }
        for (int k = (barFromRpm + 999) / 1000; k * 1000 <= barToRpm; k++) {
            if (k == 0)
                continue;
            double sk0 = rpmToS(k * 1000);
            double th = 2 * half(sk0);
            boolean litK = link && rpm > k * 1000 && !(limit && !blink);
            int nc = litK ? DARK_TEXT : k * 1000 >= shiftRpm ? RED : GREY;
            if (th >= 30 && k * 1000 < barToRpm) {
                int sz = (int) Math.min(30, th - 12);
                slopeText(sk0 + 0.5, 0.46 * sz, sz, true, CENTER, nc, Integer.toString(k), true);
            } else {
                slopeText(sk0 - 6, -(half(sk0) + 6), 18, true, CENTER, k * 1000 >= shiftRpm ? RED : GREY,
                    Integer.toString(k), true);
            }
        }

        // Red line across with a soft glow, and the notch holding the gear: two layers for depth,
        // flashing with the shift lights from the shift point.
        quad(GRID_X0, LINE_Y - 6, NOTCH_X0 + 4, LINE_Y - 6, NOTCH_X0 + 4, LINE_Y + 6, GRID_X0, LINE_Y + 6,
            (RED & 0xFFFFFF00) | 0x30, true);
        quad(NOTCH_X1 - 4, LINE_Y - 6, GRID_X1, LINE_Y - 6, GRID_X1, LINE_Y + 6, NOTCH_X1 - 4, LINE_Y + 6,
            (RED & 0xFFFFFF00) | 0x30, true);
        int ni = 12;
        quad(NOTCH_X0, LINE_Y, NOTCH_X1, LINE_Y, NOTCH_X1 - NOTCH_SLOPE, NOTCH_Y, NOTCH_X0 + NOTCH_SLOPE, NOTCH_Y,
            hot ? NOTCH_HOT : NOTCH, true);
        quad(NOTCH_X0 + ni + 4, LINE_Y + 2, NOTCH_X1 - ni - 4, LINE_Y + 2, NOTCH_X1 - NOTCH_SLOPE - ni,
            NOTCH_Y - ni, NOTCH_X0 + NOTCH_SLOPE + ni, NOTCH_Y - ni, hot ? NOTCH_HOT_IN : NOTCH_IN, true);
        box(GRID_X0, LINE_Y - 1, NOTCH_X0 - GRID_X0 + 2, 3, RED, true);
        box(NOTCH_X1 - 2, LINE_Y - 1, GRID_X1 - NOTCH_X1 + 2, 3, RED, true);
        quad(NOTCH_X0 - 1, LINE_Y, NOTCH_X0 + 2, LINE_Y, NOTCH_X0 + NOTCH_SLOPE + 2, NOTCH_Y,
            NOTCH_X0 + NOTCH_SLOPE - 1, NOTCH_Y, RED, true);
        quad(NOTCH_X1 - 2, LINE_Y, NOTCH_X1 + 1, LINE_Y, NOTCH_X1 - NOTCH_SLOPE + 1, NOTCH_Y,
            NOTCH_X1 - NOTCH_SLOPE - 2, NOTCH_Y, RED, true);
        box(NOTCH_X0 + NOTCH_SLOPE - 1, NOTCH_Y - 1, NOTCH_X1 - NOTCH_X0 - 2 * NOTCH_SLOPE + 2, 3, RED, true);
        int gc = !link ? GREY : limit && !blink ? RED : WHITE;
        text(720, 184, 154, true, CENTER, gc, gear(), true);

        // Top-left pocket, along the rising slope: the active OpenHaldex preset, its mode above it,
        // and a Wi-Fi warning only while the link is slow or lost.
        boolean slow = link && rateHz >= 0 && rateHz < 10;
        double ps = l0 * 0.14;
        slopeText(ps, half(ps) + 12 + 0.82 * 32, 32, true, LEFT, link ? PRESET : GREY, presetName(), true);
        slopeText(ps + 18, half(ps) + 52 + 0.82 * 18, 18, true, LEFT, GREY, presetMode(), true);
        slopeIcon(l0 * 0.72, half(l0 * 0.72) + 14, 28, TeleIcons.WIFI, AMBER, !link || slow);

        // Top-right pocket, along the falling slope: Haldex lock. Icon and big percent, and under
        // them a bar along the slope with the target as a white tick.
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        double ba = r0 + 60;
        double bb = len - 30;
        double bf = ba + (bb - ba) * act / 100;
        double bt = ba + (bb - ba) * tgt / 100;
        bandOff(ba, bb, half(ba) + 12, half(ba) + 22, half(bb) + 12, half(bb) + 22, TRACK, true);
        bandOff(ba, bf, half(ba) + 12, half(ba) + 22, half(bf) + 12, half(bf) + 22, link ? HALDEX : GREY, act > 0);
        bandOff(bt - 2, bt + 2, half(bt) + 8, half(bt) + 26, half(bt) + 8, half(bt) + 26, WHITE, ok(CH_HX_TARGET));
        slopeIcon(ba + 4, half(ba) + 34, 32, TeleIcons.AWD, ICONC, true);
        slopeText(ba + 50, half(ba + 50) + 32 + 0.82 * 42, 42, true, LEFT, vc,
            ok(CH_HX_ACTUAL) ? percent(act) : "-", true);

        // Left of the notch: rpm, and the accelerator pedal under it.
        icon(GRID_X0 + 14, 212, 34, TeleIcons.TACH, ICONC, true);
        text(NOTCH_X0 - 8, 204, 46, true, RIGHT, over ? RED : vc, ok(CH_RPM) ? Integer.toString(rpm) : "-", link);
        text(NOTCH_X0 - 8, 210, 34, true, RIGHT, AMBER, "NO LINK", !link);
        int ped = ok(CH_PEDAL) ? clamp(r(val[CH_PEDAL]), 0, 100) : 0;
        icon(GRID_X0 + 16, 266, 30, TeleIcons.PEDAL, ICONC, true);
        bar(GRID_X0 + 58, 274, 120, 14, ped, 0, link ? PEDAL_FILL : GREY, -1);
        text(NOTCH_X0 - 8, 266, 28, true, RIGHT, vc, ok(CH_PEDAL) ? percent(ped) : "-", true);

        // Right of the notch: boost, value and a bar from vacuum to boost with a tick at zero.
        int kpa = ok(CH_BOOST) ? r(val[CH_BOOST]) : 0;
        int zero = 40; // percent of the bar: -100 kPa .. +150 kPa
        int pos = clamp((kpa + 100) * 100 / 250, 0, 100);
        icon(NOTCH_X1 + 12, 212, 34, TeleIcons.TURBO, ICONC, true);
        text(GRID_X1 - 46, 204, 46, true, RIGHT, kpa > 0 && link ? WHITE : vc, ok(CH_BOOST) ? boost(kpa) : "-", true);
        text(GRID_X1 - 4, 226, 16, true, RIGHT, GREY, boostPsi ? "PSI" : "BAR", true);
        bar(NOTCH_X1 + 12, 274, GRID_X1 - NOTCH_X1 - 16, 14, pos, zero,
            !link ? GREY : kpa >= 0 ? BOOST_FILL : VAC_FILL, zero);

        // Temperatures in one row, an icon and a value in each cell, short dividers between.
        int cw = (GRID_X1 - GRID_X0) / T_CH.length;
        for (int t = 0; t < T_CH.length; t++) {
            int cx0 = GRID_X0 + t * cw;
            if (t > 0)
                box(cx0 - 1, GRID_Y + 2, 2, 28, LINE, true);
            tempCell(t, cx0 + 12, cx0 + cw - 12, GRID_Y, 30, 32, rpm);
        }
    }

    /** Lit strip colour at rpm: cool white, warming to amber over the last 1500 rpm before shift. */
    private int stripColour(int rpmAt) {
        int from = shiftRpm - 1500;
        if (rpmAt <= from)
            return STRIP_LO;
        return mix(STRIP_LO, AMBER, Math.min(1f, (rpmAt - from) / 1500f));
    }

    private static int mix(int a, int b, float t) {
        int out = 0;
        for (int sh = 8; sh <= 24; sh += 8) {
            int ca = (a >>> sh) & 0xFF;
            int cb = (b >>> sh) & 0xFF;
            out |= (Math.round(ca + (cb - ca) * t) & 0xFF) << sh;
        }
        return out | 0xFF;
    }

    /** Horizontal bar: track, fill from the from-percent to pct, optional tick at tickPct. */
    private void bar(int x0, int y0, int bw, int bh, int pct, int fromPct, int c, int tickPct) {
        box(x0, y0, bw, bh, TRACK, true);
        int a = Math.min(fromPct, pct);
        int b = Math.max(fromPct, pct);
        box(x0 + bw * a / 100, y0, bw * (b - a) / 100, bh, c, b > a);
        box(x0 + bw * tickPct / 100 - 1, y0 - 4, 2, bh + 8, GREY, tickPct >= 0);
    }

    private String boost(int kpa) {
        int tenths = boostPsi ? Math.round(kpa * 1.45038f) : Math.round(kpa / 10f);
        StringBuffer sb = new StringBuffer();
        if (tenths < 0)
            sb.append('-');
        int a = Math.abs(tenths);
        sb.append(a / 10).append('.').append(a % 10);
        return sb.toString();
    }

    /** Half the strip's thickness at length s: tapering on the slopes, full on the middle. */
    private static double half(double s) {
        double l0 = segLen(0);
        double l1 = segLen(1);
        double l2 = segLen(2);
        if (s <= l0)
            return HALF_MIN + (HALF_MAX - HALF_MIN) * Math.max(0, s) / l0;
        if (s <= l0 + l1)
            return HALF_MAX;
        return HALF_MAX - (HALF_MAX - HALF_MIN) * Math.min(1, (s - l0 - l1) / l2);
    }

    private double rpmToS(int rpm) {
        double l0 = segLen(0);
        double l1 = segLen(1);
        double l2 = segLen(2);
        if (rpm <= MID_RPM)
            return l0 * Math.max(0, rpm - barFromRpm) / (double) (MID_RPM - barFromRpm);
        if (rpm <= shiftRpm)
            return l0 + l1 * (rpm - MID_RPM) / (double) (shiftRpm - MID_RPM);
        return l0 + l1 + l2 * Math.min(1.0, (rpm - shiftRpm) / (double) (barToRpm - shiftRpm));
    }

    private int sToRpm(double s) {
        double l0 = segLen(0);
        double l1 = segLen(1);
        double l2 = segLen(2);
        if (s <= l0)
            return barFromRpm + (int) ((MID_RPM - barFromRpm) * s / l0);
        if (s <= l0 + l1)
            return MID_RPM + (int) ((shiftRpm - MID_RPM) * (s - l0) / l1);
        return shiftRpm + (int) ((barToRpm - shiftRpm) * (s - l0 - l1) / l2);
    }

    /** Text along the strip at length s, its top edge offTop px out from the centre (negative: in). */
    private void slopeText(double s, double offTop, int sz, boolean b, int al, int c, String str, boolean v) {
        tp(s, pa);
        int i = add(TEXT, rd(pa[0] + pa[2] * offTop), rd(pa[1] + pa[3] * offTop), 0, sz, c, v && str != null);
        size[i] = sz;
        bold[i] = b;
        align[i] = al;
        text[i] = str;
        rot[i] = (float) Math.toDegrees(Math.atan2(pa[2], -pa[3]));
    }

    /** Icon standing on the strip at length s, its bottom edge off px out from the centre. */
    private void slopeIcon(double s, double off, int px, int id, int c, boolean v) {
        tp(s, pa);
        double out = off + px;
        int i = add(ICON, rd(pa[0] + pa[2] * out), rd(pa[1] + pa[3] * out), px, px, c, v);
        size[i] = id;
        rot[i] = (float) Math.toDegrees(Math.atan2(pa[2], -pa[3]));
    }

    private static double segLen(int k) {
        return Math.sqrt(sq(TP_X[k + 1] - TP_X[k]) + sq(TP_Y[k + 1] - TP_Y[k]));
    }

    private String linkText() {
        if (!link)
            return "NO LINK";
        if (rateHz < 0)
            return "OK";
        StringBuffer sb = new StringBuffer();
        sb.append(rateHz).append(" Hz");
        return sb.toString();
    }

    private void liveSmall() {
        // Only the map area left of the big rev counter shows: x 0..520, below a diagonal from
        // (0,110) to (200,50), above y 440. Gear and engine temperatures.
        int rpm = ok(CH_RPM) ? r(val[CH_RPM]) : 0;
        boolean blink = (now / 100) % 2 == 0;
        boolean over = link && rpm >= shiftRpm;
        boolean limit = link && rpm >= limitRpm;
        int gc = !link ? GREY : limit ? (blink ? RED : WHITE) : over ? RED : WHITE;
        text(130, 150, 230, true, CENTER, gc, gear(), true);
        text(130, 392, 26, true, CENTER, AMBER, "NO LINK", !link);
        box(262, 140, 2, 230, LINE, true);
        for (int t = 0; t < T_CH.length; t++)
            tempCell(t, 284, 490, 140 + t * 48, 34, 34, rpm);
    }

    /** One temperature: icon at x0, value right-aligned at x1, coloured by level. */
    private void tempCell(int t, int x0, int x1, int yy, int is, int vs, int rpm) {
        int ch = T_CH[t];
        String v = "-";
        int c = link ? WHITE : GREY;
        if (ok(ch)) {
            int deg = r(val[ch]);
            v = temp(deg);
            if (deg >= T_RED[t])
                c = RED;
            else if (deg >= T_AMBER[t])
                c = AMBER;
            else if (ch == CH_OIL && deg < OIL_COLD && rpm > 400)
                c = COLD;
        }
        icon(x0, yy, is, T_ICON[t], c == WHITE || c == GREY ? ICONC : c, true);
        text(x1, yy + (is - vs) / 2, vs, true, RIGHT, c, v, true);
    }

    /** Haldex lock bar with target tick at (x0, y0); actual percent right-aligned at (rx, ry). */
    private void hxBar(int x0, int y0, int bw, int bh, int rx, int ry, int ps) {
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        box(x0, y0, bw, bh, DIM, true);
        box(x0, y0, bw * act / 100, bh, link ? HALDEX : GREY, act > 0);
        box(x0 + bw * tgt / 100 - 2, y0 - 4, 4, bh + 8, WHITE, ok(CH_HX_TARGET));
        text(rx, ry, ps, true, RIGHT, link ? WHITE : GREY, ok(CH_HX_ACTUAL) ? percent(act) : "-", true);
    }

    private void presets(boolean large) {
        // A panel where the map was: large view between the dials, small view left of the rev counter.
        int ox = large ? 335 : 36;
        int oy = large ? 102 : 122;
        int bw = large ? 720 : 462;
        int bh = large ? 356 : 320;
        int pad = 16;
        box(ox, oy, bw, bh, PANEL, true);
        int top = oy + (large ? 14 : 10);
        int ts = large ? 18 : 14;
        text(ox + pad, top, ts, true, LEFT, GREY, large ? "OPENHALDEX PRESETS" : "PRESETS", true);
        String hint = status != null ? status : large ? "TURN TO CHOOSE, PRESS TO APPLY" : "PRESS TO APPLY";
        text(ox + bw - pad, top, ts, false, RIGHT, status != null ? statusRgba : GREY, hint, true);

        int active = ok(CH_PRESET) ? r(val[CH_PRESET]) : -1;
        int rowTop = oy + (large ? 44 : 34);
        int rowH = large ? 38 : 34;
        int ns = large ? 28 : 22;
        int ms = large ? 16 : 13;
        for (int i = 0; i < MAXPRESET; i++) {
            int y0 = rowTop + i * rowH;
            boolean on = link && i < catN;
            box(ox + pad - 6, y0, bw - 2 * pad + 12, rowH - 4, CURSOR, on && i == cursor);
            box(ox + pad, y0 + 8, 6, rowH - 20, ACTIVE, on && catId[i] == active);
            text(ox + pad + 18, y0 + (rowH - 4 - ns) / 2, ns, i == cursor, LEFT, WHITE,
                on ? upper(catName[i]) : null, on);
            text(ox + bw - pad - 8, y0 + (rowH - 4 - ms) / 2, ms, false, RIGHT,
                on && catId[i] == active ? ACTIVE : GREY, on ? mode(catMode[i]) : null, on);
        }
        text(ox + bw / 2, oy + bh / 2 - 16, 26, true, CENTER, link ? GREY : AMBER,
            link ? "NO PRESET LIST YET" : "NO LINK", !link || catN == 0);
    }

    // Rev line geometry: length along the corner points -> point (o[0], o[1]) and outward normal
    // (o[2], o[3]), pointing up, away from the centre of the screen.
    private static double tpLen() {
        double l = 0;
        for (int i = 1; i < TP_X.length; i++)
            l += Math.sqrt(sq(TP_X[i] - TP_X[i - 1]) + sq(TP_Y[i] - TP_Y[i - 1]));
        return l;
    }

    private static void tp(double s, double[] o) {
        for (int i = 1; i < TP_X.length; i++) {
            double dx = TP_X[i] - TP_X[i - 1];
            double dy = TP_Y[i] - TP_Y[i - 1];
            double l = Math.sqrt(dx * dx + dy * dy);
            if (s <= l || i == TP_X.length - 1) {
                o[0] = TP_X[i - 1] + dx * s / l;
                o[1] = TP_Y[i - 1] + dy * s / l;
                o[2] = dy / l;
                o[3] = -dx / l;
                return;
            }
            s -= l;
        }
    }

    private static double sq(double d) {
        return d * d;
    }

    /**
     * Piece of the strip between lengths s0..s1: at s0 from offset in0 to out0 along the outward
     * normal, at s1 from in1 to out1.
     */
    private void bandOff(double s0, double s1, double in0, double out0, double in1, double out1, int c,
                         boolean v) {
        tp(s0, pa);
        tp(s1, pb);
        quad(rd(pa[0] + pa[2] * out0), rd(pa[1] + pa[3] * out0), rd(pb[0] + pb[2] * out1), rd(pb[1] + pb[3] * out1),
            rd(pb[0] + pb[2] * in1), rd(pb[1] + pb[3] * in1), rd(pa[0] + pa[2] * in0), rd(pa[1] + pa[3] * in0), c, v);
    }

    private static int rd(double d) {
        return (int) Math.round(d);
    }

    private String gear() {
        if (!ok(CH_GEAR))
            return "-";
        int gv = r(val[CH_GEAR]);
        return gv <= 0 ? "N" : Integer.toString(gv);
    }

    private int presetIndex() {
        if (!ok(CH_PRESET))
            return -1;
        int id = r(val[CH_PRESET]);
        for (int i = 0; i < catN; i++)
            if (catId[i] == id)
                return i;
        return -1;
    }

    private String presetName() {
        if (!ok(CH_PRESET))
            return "-";
        int id = r(val[CH_PRESET]);
        if (id == 255)
            return "NONE";
        int i = presetIndex();
        if (i >= 0)
            return upper(catName[i]);
        StringBuffer sb = new StringBuffer("PRESET ");
        sb.append(id);
        return sb.toString();
    }

    private String presetMode() {
        int i = presetIndex();
        return i >= 0 ? mode(catMode[i]) : "";
    }

    private String temp(int c) {
        StringBuffer sb = new StringBuffer();
        sb.append(imperial ? c * 9 / 5 + 32 : c).append('°').append(imperial ? 'F' : 'C');
        return sb.toString();
    }

    private static String percent(int p) {
        StringBuffer sb = new StringBuffer();
        sb.append(p).append('%');
        return sb.toString();
    }

    private static String mode(int m) {
        return m >= 0 && m < MODES.length ? MODES[m] : "?";
    }

    private static String upper(String s) {
        return s == null ? "" : s.toUpperCase();
    }

    private boolean ok(int ch) {
        return link && has[ch];
    }

    private static int r(int v1000) {
        return v1000 >= 0 ? (v1000 + 500) / 1000 : (v1000 - 500) / 1000;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }

    private int add(int k, int x0, int y0, int w0, int h0, int c, boolean v) {
        int i = count++;
        kind[i] = k;
        x[i] = x0;
        y[i] = y0;
        w[i] = w0;
        h[i] = h0;
        rgba[i] = c;
        vis[i] = v;
        text[i] = null;
        size[i] = 0;
        bold[i] = false;
        align[i] = LEFT;
        rot[i] = 0;
        return i;
    }

    private void box(int x0, int y0, int w0, int h0, int c, boolean v) {
        add(BOX, x0, y0, w0, h0, c, v);
    }

    private void icon(int x0, int y0, int px, int id, int c, boolean v) {
        int i = add(ICON, x0, y0, px, px, c, v);
        size[i] = id;
    }

    private void quad(int x0, int y0, int x1, int y1, int x2, int y2, int x3, int y3, int c, boolean v) {
        int i = add(QUAD, 0, 0, 0, 0, c, v);
        int[] xs = {x0, x1, x2, x3};
        int[] ys = {y0, y1, y2, y3};
        int minX = xs[0], maxX = xs[0], minY = ys[0], maxY = ys[0];
        for (int k = 0; k < 4; k++) {
            qx[4 * i + k] = xs[k];
            qy[4 * i + k] = ys[k];
            minX = Math.min(minX, xs[k]);
            maxX = Math.max(maxX, xs[k]);
            minY = Math.min(minY, ys[k]);
            maxY = Math.max(maxY, ys[k]);
        }
        x[i] = minX;
        y[i] = minY;
        w[i] = maxX - minX + 1;
        h[i] = maxY - minY + 1;
    }

    private void text(int x0, int y0, int sz, boolean b, int al, int c, String s, boolean v) {
        int i = add(TEXT, x0, y0, 0, sz, c, v && s != null);
        size[i] = sz;
        bold[i] = b;
        align[i] = al;
        text[i] = s;
    }
}
