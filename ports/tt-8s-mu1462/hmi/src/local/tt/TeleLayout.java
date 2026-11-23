package local.tt;

/**
 * Layout of the OpenHaldex autocross view (wiki/Telemetry.md). Pure Java, no EAL.
 *
 * The autocross view replaces the map: a long press on the scroll wheel switches it on and off.
 * It fills the whole cockpit screen with an opaque background, so no map shows; the cluster's own
 * dials, tab bar and bottom bar are drawn above our layer, so the layout keeps clear of them.
 *  - Large (Classic) view: a big rev arc in the centre with the gear inside it, a shift light row
 *    under the tab bar, engine temperatures top left, Haldex top right.
 *  - Small (Sport) view: only the map area left of the big rev counter is ours: gear and engine
 *    temperatures.
 *
 * build() turns the latest values into a list of slots: filled boxes, filled quads (arc segments)
 * and texts. The same view and page always give the same slots in the same order, so the HMI side
 * can create one EAL node per slot when the view or page changes, and per frame only change
 * visibility, text, colour and box width (no texture upload per frame, see Cockpit-drawing.md).
 * A quad is drawn into its own small picture once, one picture per colour it can take. The PC
 * preview (gauges/tools/TelePreview.java) draws the same slots with AWT.
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
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int MAX = 160;
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
    public static final int WHITE = 0xF2F5F7FF;
    public static final int GREY = 0x8A949EFF;
    public static final int GREEN = 0x34C759FF;
    public static final int AMBER = 0xFFB020FF;
    public static final int RED = 0xFF3B30FF;
    public static final int HALDEX = 0x2F9BFFFF;
    public static final int PRESET = 0x7FD3FFFF;
    public static final int COLD = 0x5AC8FAFF;
    public static final int CURSOR = 0x1C3B5AFF;
    public static final int ACTIVE = 0x34C759FF;

    private static final String[] MODES = {"STOCK", "FWD", "50:50", "60:40", "70:30", "80:20", "90:10",
        "SPEED", "THROTTLE", "MAP", "RPM"};

    // Engine temperatures, degrees C: amber and red limits. Haldex clutch limits are guesses until
    // we see real values.
    private static final int[] T_CH = {CH_COOLANT, CH_OIL, CH_IAT, CH_DSG, CH_CLUTCH};
    private static final String[] T_NAME = {"COOLANT", "OIL", "INTAKE", "DSG", "CLUTCH"};
    private static final int[] T_AMBER = {108, 130, 55, 110, 90};
    private static final int[] T_RED = {115, 140, 70, 125, 110};
    private static final int OIL_COLD = 60;

    // Rev arc (large view): centre, radii, start and end angle (degrees, 0 = right, counter-clockwise).
    private static final int ARC_CX = 720;
    private static final int ARC_CY = 470;
    private static final int ARC_OUT = 330;
    private static final int ARC_IN = 268;
    private static final double ARC_FROM = 162;
    private static final double ARC_TO = 18;
    private static final int ARC_SEGS = 36;
    private static final int LEDS = 15;
    private static final int LED_STEP = 100; // rpm per shift light before the shift point

    // Settings
    public int shiftRpm = 6300;
    public int limitRpm = 6700;
    public int barFromRpm = 1000;
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

    // Output
    public int count;
    public final int[] kind = new int[MAX];
    public final int[] x = new int[MAX];
    public final int[] y = new int[MAX];
    public final int[] w = new int[MAX];
    public final int[] h = new int[MAX];
    public final int[] rgba = new int[MAX];
    public final int[] size = new int[MAX];
    public final int[] align = new int[MAX];
    public final boolean[] bold = new boolean[MAX];
    public final boolean[] vis = new boolean[MAX];
    public final String[] text = new String[MAX];
    /** Quad corners, 4 per slot (qx[4 * i + k]); x/y/w/h hold the quad's bounding box. */
    public final int[] qx = new int[MAX * 4];
    public final int[] qy = new int[MAX * 4];

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

        // Shift lights under the tab bar: green, amber, red in the last 1500 rpm, all red and
        // flashing from the shift point.
        int lw = 30;
        int lgap = 6;
        int lx = 720 - (LEDS * (lw + lgap) - lgap) / 2;
        for (int i = 0; i < LEDS; i++) {
            int on = shiftRpm - (LEDS - i) * LED_STEP;
            int c;
            if (over)
                c = blink ? RED : DIM;
            else if (link && rpm >= on)
                c = i < 5 ? GREEN : i < 10 ? AMBER : RED;
            else
                c = DIM;
            box(lx + i * (lw + lgap), 102, lw, 26, c, true);
        }

        // Rev arc: white, amber in the last 1000 rpm before the shift point, all red from it, the
        // whole arc flashing at the limiter. Red tick at the shift point, 1..7 inside.
        int span = barToRpm - barFromRpm;
        double sweep = ARC_FROM - ARC_TO;
        double gapDeg = 1.4;
        for (int i = 0; i < ARC_SEGS; i++) {
            double a0 = ARC_FROM - sweep * i / ARC_SEGS;
            double a1 = ARC_FROM - sweep * (i + 1) / ARC_SEGS + gapDeg;
            int from = barFromRpm + span * i / ARC_SEGS;
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
            ring(a0, a1, ARC_IN, ARC_OUT, c, true);
        }
        if (shiftRpm > barFromRpm && shiftRpm < barToRpm) {
            double a = ARC_FROM - sweep * (shiftRpm - barFromRpm) / span;
            ring(a + 0.5, a - 0.5, ARC_IN - 10, ARC_OUT + 10, RED, true);
        } else {
            ring(0, 0, 0, 0, RED, false);
        }
        for (int k = (barFromRpm + 999) / 1000; k * 1000 <= barToRpm; k++) {
            double a = Math.toRadians(ARC_FROM - sweep * (k * 1000 - barFromRpm) / span);
            int px = ARC_CX + (int) Math.round(Math.cos(a) * 240);
            int py = ARC_CY - (int) Math.round(Math.sin(a) * 240);
            text(px, py - 12, 24, true, CENTER, k * 1000 >= shiftRpm ? RED : GREY, Integer.toString(k), true);
        }

        // Gear inside the arc, red from the shift point, flashing at the limiter; rpm under it.
        int gc = !link ? GREY : limit ? (blink ? RED : WHITE) : over ? RED : WHITE;
        text(ARC_CX, 222, 230, true, CENTER, gc, gear(), true);
        text(ARC_CX, 420, 34, true, CENTER, over ? RED : WHITE, Integer.toString(rpm), link && ok(CH_RPM));
        text(ARC_CX, 422, 30, true, CENTER, AMBER, "NO LINK", !link);

        // Engine temperatures, top left (above the small rev counter).
        text(40, 100, 18, true, LEFT, GREY, "ENGINE", true);
        temps(40, 404, 126, 38, 4, 20, 32, rpm);

        // Haldex, top right (above the speedometer): preset, lock bar with target tick, clutch temp.
        int hx = 1036;
        int hr = 1404;
        text(hx, 100, 18, true, LEFT, GREY, "HALDEX", true);
        text(hr, 96, 30, true, RIGHT, link ? PRESET : GREY, presetName(), true);
        hxBar(hx, 150, 270, 26, hr, 30);
        temps(hx, hr, 204, 38, 1, 20, 32, rpm, 4);
    }

    private void liveSmall() {
        // Only the map area left of the big rev counter shows: x 0..520, below a diagonal from
        // (0,110) to (200,50), above y 440. Gear and engine temperatures.
        int rpm = ok(CH_RPM) ? r(val[CH_RPM]) : 0;
        boolean blink = (now / 100) % 2 == 0;
        boolean over = link && rpm >= shiftRpm;
        boolean limit = link && rpm >= limitRpm;
        int gc = !link ? GREY : limit ? (blink ? RED : WHITE) : over ? RED : WHITE;
        text(130, 124, 18, true, CENTER, GREY, "GEAR", true);
        text(130, 150, 230, true, CENTER, gc, gear(), true);
        text(130, 392, 26, true, CENTER, AMBER, "NO LINK", !link);
        text(250, 124, 18, true, LEFT, GREY, "ENGINE", true);
        temps(250, 490, 150, 46, 5, 20, 34, rpm);
    }

    /** Rows of label (left) and value (right), coloured by level. First temp index optional. */
    private void temps(int lx, int rx, int top, int step, int rows, int ls, int vs, int rpm) {
        temps(lx, rx, top, step, rows, ls, vs, rpm, 0);
    }

    private void temps(int lx, int rx, int top, int step, int rows, int ls, int vs, int rpm, int first) {
        for (int i = 0; i < rows; i++) {
            int t = first + i;
            int ch = T_CH[t];
            String name = T_NAME[t];
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
            int yy = top + i * step;
            text(lx, yy + (vs - ls) / 2 + 2, ls, false, LEFT, c == WHITE ? GREY : c, name, true);
            text(rx, yy, vs, true, RIGHT, c, v, true);
        }
    }

    private void hxBar(int x0, int y0, int bw, int bh, int rx, int ps) {
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        box(x0, y0, bw, bh, DIM, true);
        box(x0, y0, bw * act / 100, bh, link ? HALDEX : GREY, act > 0);
        box(x0 + bw * tgt / 100 - 2, y0 - 4, 4, bh + 8, WHITE, ok(CH_HX_TARGET));
        text(rx, y0 + (bh - ps) / 2, ps, true, RIGHT, link ? WHITE : GREY,
            ok(CH_HX_ACTUAL) ? percent(act) : "-", true);
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

    private String gear() {
        if (!ok(CH_GEAR))
            return "-";
        int gv = r(val[CH_GEAR]);
        return gv <= 0 ? "N" : Integer.toString(gv);
    }

    private String presetName() {
        if (!ok(CH_PRESET))
            return "-";
        int id = r(val[CH_PRESET]);
        if (id == 255)
            return "NONE";
        for (int i = 0; i < catN; i++)
            if (catId[i] == id)
                return upper(catName[i]);
        StringBuffer sb = new StringBuffer("PRESET ");
        sb.append(id);
        return sb.toString();
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

    /** Ring segment between angles a0 and a1 (degrees) and radii rIn..rOut around the arc centre. */
    private void ring(double a0, double a1, int rIn, int rOut, int c, boolean v) {
        int i = add(QUAD, 0, 0, 0, 0, c, v);
        double r0 = Math.toRadians(a0);
        double r1 = Math.toRadians(a1);
        double[] ang = {r0, r1, r1, r0};
        int[] rad = {rOut, rOut, rIn, rIn};
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (int k = 0; k < 4; k++) {
            int px = ARC_CX + (int) Math.round(Math.cos(ang[k]) * rad[k]);
            int py = ARC_CY - (int) Math.round(Math.sin(ang[k]) * rad[k]);
            qx[4 * i + k] = px;
            qy[4 * i + k] = py;
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
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
