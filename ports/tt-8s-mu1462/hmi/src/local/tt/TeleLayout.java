package local.tt;

/**
 * Layout of the OpenHaldex autocross view (wiki/Telemetry.md). Pure Java, no EAL.
 *
 * The autocross view replaces the map: a double press on the scroll wheel switches it on and off.
 * It fills the whole cockpit screen with an opaque background, so no map shows. The cluster's own
 * dials, tab bar, bottom bar and the frame over the top corners are drawn above our layer, so the
 * layout keeps clear of them.
 *  - Large (Classic) view, after the FL5 Civic Type R's +R screen: shift lights under the tab bar;
 *    a rev line that rises from the lower left and runs across the top, numbers under it; a red
 *    line across with a notch in the middle holding a big gear; rpm left of the notch, Haldex lock
 *    right of it; the active preset in the pocket inside the rev line's bend (top left), speed top
 *    right (the very top corners are covered by the cluster's frame); temperatures and link status
 *    in a grid at the bottom, one icon per value.
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
    public static final int MAX = 200;
    public static final int CHANNELS = 64;
    public static final int MAXPRESET = 8;
    public static final int SCREEN_W = 1440;
    public static final int SCREEN_H = 540;

    // TTG1 channel ids (docs/MMI_LINK.md in OpenHaldex-S3), values are x1000.
    public static final int CH_RPM = 1;
    public static final int CH_SPEED = 2;
    public static final int CH_COOLANT = 5;
    public static final int CH_OIL = 6;
    public static final int CH_IAT = 7;
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
    public static final int NOTCH = 0x3A0A07FF;
    public static final int NOTCH_HOT = 0x8A1A10FF;
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

    // Rev line (large view): a quarter circle from the lower left up into a straight line under the
    // shift lights. Centre of the curve, its radius, the right end, half the line thickness.
    private static final int TP_CX = 230;
    private static final int TP_CY = 252;
    private static final int TP_R = 110;
    private static final int TP_X1 = 1404;
    private static final int TP_HALF = 12;
    private static final int TP_SEGS = 84;
    // Shift lights: LEDS in a row under the tab bar, LED_STEP rpm apart up to the shift point.
    private static final int LEDS = 13;
    private static final int LED_STEP = 125;
    // Red line and the notch holding the gear.
    private static final int LINE_Y = 200;
    private static final int NOTCH_X0 = 596;
    private static final int NOTCH_X1 = 844;
    private static final int NOTCH_IN = 30;
    private static final int NOTCH_Y = 324;
    // Telemetry grid between the dials, under the notch.
    private static final int GRID_X0 = 330;
    private static final int GRID_X1 = 1090;
    private static final int GRID_Y = 340;
    private static final int GRID_ROW = 58;

    // Settings
    public int shiftRpm = 6500;
    public int limitRpm = 6800;
    public int barFromRpm = 0;
    public int barToRpm = 7000;
    public boolean imperial = true;

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

        // Shift lights under the tab bar: green, amber, red over the last LEDS * LED_STEP rpm, all
        // red and flashing from the shift point.
        int lw = 64;
        int lgap = 12;
        int lx = 720 - (LEDS * (lw + lgap) - lgap) / 2;
        for (int i = 0; i < LEDS; i++) {
            int on = shiftRpm - (LEDS - i) * LED_STEP;
            int c;
            if (over)
                c = blink ? RED : DIM;
            else if (link && rpm >= on)
                c = i < 4 ? GREEN : i < 9 ? AMBER : RED;
            else
                c = DIM;
            box(lx + i * (lw + lgap), 100, lw, 22, c, true);
        }

        // Rev line: white, amber in the last 1000 rpm before the shift point, all red from it, the
        // whole line flashing at the limiter. Red marks over the limiter range, a red tick at the
        // shift point, 1..7 under the line.
        double len = tpLen();
        int span = barToRpm - barFromRpm;
        for (int i = 0; i < TP_SEGS; i++) {
            double s0 = len * i / TP_SEGS;
            double s1 = len * (i + 1) / TP_SEGS - 2;
            int from = barFromRpm + span * i / TP_SEGS;
            int c;
            if (limit)
                c = blink ? RED : DIM;
            else if (rpm <= from)
                c = DIM;
            else if (over)
                c = RED;
            else if (from >= shiftRpm - 1000)
                c = AMBER;
            else
                c = WHITE;
            band(s0, s1, -TP_HALF, TP_HALF, c, true);
        }
        double sl = len * (limitRpm - barFromRpm) / span;
        for (int i = 0; i < 4; i++)
            band(sl + (len - sl) * i / 4, sl + (len - sl) * (i + 1) / 4 - 3, TP_HALF + 4, TP_HALF + 9, RED,
                limitRpm < barToRpm);
        double ss = len * (shiftRpm - barFromRpm) / span;
        band(ss - 1.5, ss + 1.5, -TP_HALF - 7, TP_HALF + 7, RED, shiftRpm > barFromRpm && shiftRpm < barToRpm);
        for (int k = (barFromRpm + 999) / 1000; k * 1000 <= barToRpm; k++) {
            if (k == 0)
                continue;
            tp(len * (k * 1000 - barFromRpm) / span, pa);
            text((int) Math.round(pa[0] - pa[2] * 36), (int) Math.round(pa[1] - pa[3] * 36) - 11, 22, true, CENTER,
                k * 1000 >= shiftRpm ? RED : GREY, Integer.toString(k), true);
        }

        // Speed, top right under the line.
        text(1404, 198, 56, true, RIGHT, vc, speed(), true);
        text(1404, 256, 16, false, RIGHT, GREY, imperial ? "MPH" : "KM/H", true);

        // Red line across with the notch; the notch glows brighter from the shift point. Gear in it.
        quad(NOTCH_X0, LINE_Y, NOTCH_X1, LINE_Y, NOTCH_X1 - NOTCH_IN, NOTCH_Y, NOTCH_X0 + NOTCH_IN, NOTCH_Y,
            over ? NOTCH_HOT : NOTCH, true);
        box(GRID_X0, LINE_Y - 1, NOTCH_X0 - GRID_X0 + 2, 3, RED, true);
        box(NOTCH_X1 - 2, LINE_Y - 1, GRID_X1 - NOTCH_X1 + 2, 3, RED, true);
        quad(NOTCH_X0 - 1, LINE_Y, NOTCH_X0 + 2, LINE_Y, NOTCH_X0 + NOTCH_IN + 2, NOTCH_Y,
            NOTCH_X0 + NOTCH_IN - 1, NOTCH_Y, RED, true);
        quad(NOTCH_X1 - 2, LINE_Y, NOTCH_X1 + 1, LINE_Y, NOTCH_X1 - NOTCH_IN + 1, NOTCH_Y,
            NOTCH_X1 - NOTCH_IN - 2, NOTCH_Y, RED, true);
        box(NOTCH_X0 + NOTCH_IN - 1, NOTCH_Y - 1, NOTCH_X1 - NOTCH_X0 - 2 * NOTCH_IN + 2, 3, RED, true);
        int gc = !link ? GREY : limit && !blink ? RED : WHITE; // the notch carries the shift cue
        text(720, 186, 150, true, CENTER, gc, gear(), true);

        // Top-left pocket, inside the bend of the rev line: the active preset and its mode.
        text(150, 208, 28, true, LEFT, link ? PRESET : GREY, presetName(), true);
        text(154, 240, 16, false, LEFT, GREY, presetMode(), true);

        // Left of the notch: rpm. Right of it: Haldex lock, big percent over a bar with target tick.
        icon(GRID_X0 + 14, 226, 36, TeleIcons.TACH, ICONC, true);
        text(NOTCH_X0 - 6, 216, 48, true, RIGHT, over ? RED : vc, ok(CH_RPM) ? Integer.toString(rpm) : "-", link);
        text(NOTCH_X0 - 6, 216, 34, true, RIGHT, AMBER, "NO LINK", !link);
        icon(NOTCH_X1 + 10, 226, 36, TeleIcons.AWD, ICONC, true);
        hxBar(NOTCH_X1 + 10, 284, GRID_X1 - NOTCH_X1 - 16, 20, GRID_X1 - 6, 216, 48);

        // Telemetry grid: 3 columns x 2 rows, an icon and a value in each cell.
        int cw = (GRID_X1 - GRID_X0) / 3;
        box(GRID_X0 + cw - 1, GRID_Y, 2, 2 * GRID_ROW - 10, LINE, true);
        box(GRID_X0 + 2 * cw - 1, GRID_Y, 2, 2 * GRID_ROW - 10, LINE, true);
        int[] order = {0, 1, 2, 3, 4};
        for (int n = 0; n < order.length; n++) {
            int col = n / 2;
            int row = n % 2;
            int cx0 = GRID_X0 + col * cw;
            int yy = GRID_Y + row * GRID_ROW;
            tempCell(order[n], cx0 + 16, cx0 + cw - 16, yy, 36, 36, rpm);
        }
        // Last cell: the link to OpenHaldex, datagrams per second.
        int cx0 = GRID_X0 + 2 * cw;
        int yy = GRID_Y + GRID_ROW;
        boolean slow = link && rateHz >= 0 && rateHz < 10;
        icon(cx0 + 16, yy, 36, TeleIcons.WIFI, link && !slow ? ICONC : AMBER, true);
        text(cx0 + cw - 16, yy, 36, true, RIGHT, link && !slow ? WHITE : AMBER, linkText(), true);
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

    // Rev line geometry: arc length -> point (o[0], o[1]) and outward normal (o[2], o[3]).
    private static double tpLen() {
        return TP_R * Math.PI / 2 + (TP_X1 - TP_CX);
    }

    private static void tp(double s, double[] o) {
        double arc = TP_R * Math.PI / 2;
        if (s < arc) {
            double th = Math.PI - s / TP_R;
            o[0] = TP_CX + TP_R * Math.cos(th);
            o[1] = TP_CY - TP_R * Math.sin(th);
            o[2] = Math.cos(th);
            o[3] = -Math.sin(th);
        } else {
            o[0] = TP_CX + (s - arc);
            o[1] = TP_CY - TP_R;
            o[2] = 0;
            o[3] = -1;
        }
    }

    /** Piece of the rev line between arc lengths s0..s1, offsets d0..d1 along the outward normal. */
    private void band(double s0, double s1, double d0, double d1, int c, boolean v) {
        tp(s0, pa);
        tp(s1, pb);
        quad(rd(pa[0] + pa[2] * d1), rd(pa[1] + pa[3] * d1), rd(pb[0] + pb[2] * d1), rd(pb[1] + pb[3] * d1),
            rd(pb[0] + pb[2] * d0), rd(pb[1] + pb[3] * d0), rd(pa[0] + pa[2] * d0), rd(pa[1] + pa[3] * d0), c, v);
    }

    private static int rd(double d) {
        return (int) Math.round(d);
    }

    private String speed() {
        if (!ok(CH_SPEED))
            return "-";
        int kmh1000 = val[CH_SPEED];
        return Integer.toString(r(imperial ? kmh1000 / 1000 * 621 + kmh1000 % 1000 * 621 / 1000 : kmh1000));
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
