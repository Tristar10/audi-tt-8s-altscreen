package local.tt;

/**
 * Layout of the OpenHaldex telemetry pages (wiki/Telemetry.md). Pure Java, no EAL.
 *
 * build() turns the latest values into a list of slots: filled boxes and texts. The same view and
 * page always give the same slots in the same order, so the HMI side can create one EAL node per
 * slot when the view or page changes, and per frame only change visibility, text, colour and box
 * width (no texture upload per frame, see Cockpit-drawing.md). The PC preview
 * (gauges/tools/TelePreview.java) draws the same slots with AWT.
 *
 * Slot coordinates are relative to the page box (boxX, boxY on the 1440x540 cockpit screen). A
 * text slot's y is the top of the text; the car places text by baseline at y + 0.82 * size.
 * Colours are 0xRRGGBBAA, like EAL text colours.
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
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;
    public static final int MAX = 128;
    public static final int CHANNELS = 64;
    public static final int MAXPRESET = 8;

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

    public static final int BG = 0x0B0F14F2;
    public static final int DIM = 0x262D35FF;
    public static final int WHITE = 0xF2F5F7FF;
    public static final int GREY = 0x8A949EFF;
    public static final int AMBER = 0xFFB020FF;
    public static final int RED = 0xFF3B30FF;
    public static final int HALDEX = 0x2F9BFFFF;
    public static final int PRESET = 0x7FD3FFFF;
    public static final int COLD = 0x5AC8FAFF;
    public static final int CURSOR = 0x1C3B5AFF;
    public static final int ACTIVE = 0x34C759FF;

    private static final String[] MODES = {"STOCK", "FWD", "50:50", "60:40", "70:30", "80:20", "90:10",
        "SPEED", "THROTTLE", "MAP", "RPM"};

    // Temperature warnings, degrees C. Shown on the Live page only when out of range.
    // Haldex clutch limits are guesses until we see real values.
    private static final int[] W_CH = {CH_COOLANT, CH_OIL, CH_DSG, CH_IAT, CH_CLUTCH};
    private static final String[] W_NAME = {"COOLANT", "OIL", "DSG", "INTAKE", "CLUTCH"};
    private static final int[] W_AMBER = {108, 130, 110, 55, 90};
    private static final int[] W_RED = {115, 140, 125, 70, 110};
    private static final int OIL_COLD = 60;

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
    public int boxX;
    public int boxY;
    public int boxW;
    public int boxH;
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

    private final int[] wLevel = new int[W_CH.length + 1];
    private final String[] wText = new String[W_CH.length + 1];

    public void build(int view, int page) {
        boolean large = view == VIEW_LARGE;
        count = 0;
        if (large) {
            // Between the two small dials of the large (Classic) view, under the tab bar.
            boxX = 335;
            boxY = 102;
            boxW = 720;
            boxH = 356;
        } else {
            // The map area left of the big rev counter in the small (Sport) view.
            boxX = 36;
            boxY = 122;
            boxW = 462;
            boxH = 320;
        }
        box(0, 0, boxW, boxH, BG, true);
        if (page == PAGE_PRESETS)
            presets(large);
        else
            live(large);
    }

    private void live(boolean large) {
        int pad = 16;
        int inner = boxW - 2 * pad;
        int rpm = ok(CH_RPM) ? r(val[CH_RPM]) : 0;
        boolean blink = (now / 100) % 2 == 0;
        boolean over = link && rpm >= shiftRpm;
        boolean limit = link && rpm >= limitRpm;

        // Rev bar: white, amber in the last 1000 rpm before the shift point, all red from the
        // shift point, the whole bar flashing at the limiter.
        int segs = large ? 32 : 24;
        int barY = large ? 16 : 12;
        int barH = large ? 40 : 30;
        int gap = large ? 4 : 3;
        int span = barToRpm - barFromRpm;
        for (int i = 0; i < segs; i++) {
            int x0 = pad + inner * i / segs;
            int x1 = pad + inner * (i + 1) / segs - gap;
            int from = barFromRpm + span * i / segs;
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
            box(x0, barY, x1 - x0, barH, c, true);
        }
        int sx = pad + inner * (shiftRpm - barFromRpm) / span;
        box(sx - 1, barY - 5, 3, barH + 10, RED, shiftRpm > barFromRpm && shiftRpm < barToRpm);
        for (int k = (barFromRpm + 999) / 1000; k * 1000 <= barToRpm; k++)
            text(pad + inner * (k * 1000 - barFromRpm) / span, barY + barH + 4, large ? 18 : 14, false, CENTER,
                GREY, Integer.toString(k), true);

        // Gear, big. Red from the shift point, flashing at the limiter.
        String g = "-";
        if (ok(CH_GEAR)) {
            int gv = r(val[CH_GEAR]);
            g = gv <= 0 ? "N" : Integer.toString(gv);
        }
        int gc = !link ? GREY : limit ? (blink ? RED : WHITE) : over ? RED : WHITE;
        text(large ? 150 : 100, large ? 84 : 64, large ? 230 : 170, true, CENTER, gc, g, true);

        // Speed and rpm, right column.
        int rx = boxW - pad;
        String sp = "-";
        if (ok(CH_SPEED)) {
            int kmh1000 = val[CH_SPEED];
            sp = Integer.toString(r(imperial ? kmh1000 / 1000 * 621 + kmh1000 % 1000 * 621 / 1000 : kmh1000));
        }
        int vc = link ? WHITE : GREY;
        text(rx, large ? 86 : 66, large ? 96 : 68, true, RIGHT, vc, sp, true);
        text(rx, large ? 184 : 136, large ? 18 : 14, false, RIGHT, GREY, imperial ? "MPH" : "KM/H", true);
        text(rx, large ? 212 : 158, large ? 40 : 30, true, RIGHT, over ? RED : vc,
            ok(CH_RPM) ? Integer.toString(rpm) : "-", true);
        text(rx, large ? 254 : 190, large ? 16 : 13, false, RIGHT, GREY, "RPM", true);

        // Temperature warnings (up to 3, worst first), or NO LINK in the same place.
        int n = warnings(rpm);
        int wx = large ? 300 : 182;
        int wy = large ? 96 : 70;
        int ws = large ? 28 : 22;
        int wstep = large ? 36 : 28;
        for (int i = 0; i < 3; i++) {
            int best = -1;
            for (int j = 0; j < n; j++)
                if (wText[j] != null && (best < 0 || wLevel[j] > wLevel[best]))
                    best = j;
            String s = best >= 0 ? wText[best] : null;
            int c = best < 0 ? GREY : wLevel[best] == 3 ? RED : wLevel[best] == 2 ? AMBER : COLD;
            if (best >= 0)
                wText[best] = null;
            text(wx, wy + i * wstep, ws, true, LEFT, c, s, link && s != null);
        }
        text(wx, wy, ws, true, LEFT, AMBER, "NO LINK", !link);

        // Haldex: actual lock as a bar, target as a tick, and the active preset.
        int hy = large ? 290 : 250;
        int lab = large ? 18 : 14;
        int bw = large ? 400 : 190;
        int bh = large ? 26 : 22;
        int by = hy + (large ? 24 : 20);
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        text(pad, hy, lab, false, LEFT, GREY, "HALDEX", true);
        box(pad, by, bw, bh, DIM, true);
        box(pad, by, bw * act / 100, bh, link ? HALDEX : GREY, act > 0);
        box(pad + bw * tgt / 100 - 2, by - 4, 4, bh + 8, WHITE, ok(CH_HX_TARGET));
        text(pad + bw + 14, by - 3, large ? 30 : 24, true, LEFT, vc,
            ok(CH_HX_ACTUAL) ? percent(act) : "-", true);
        text(rx, hy, lab, false, RIGHT, GREY, "PRESET", true);
        text(rx, by - 6, large ? 34 : 26, true, RIGHT, link ? PRESET : GREY, presetName(), true);
    }

    private int warnings(int rpm) {
        int n = 0;
        for (int i = 0; i < W_CH.length; i++) {
            if (!ok(W_CH[i]))
                continue;
            int t = r(val[W_CH[i]]);
            int lvl = t >= W_RED[i] ? 3 : t >= W_AMBER[i] ? 2 : 0;
            String name = W_NAME[i];
            if (lvl == 0 && W_CH[i] == CH_OIL && t < OIL_COLD && rpm > 400) {
                lvl = 1;
                name = "OIL COLD";
            }
            if (lvl > 0) {
                StringBuffer sb = new StringBuffer(name);
                sb.append(' ').append(temp(t));
                wText[n] = sb.toString();
                wLevel[n] = lvl;
                n++;
            }
        }
        return n;
    }

    private void presets(boolean large) {
        int pad = 16;
        int top = large ? 14 : 10;
        int ts = large ? 18 : 14;
        text(pad, top, ts, true, LEFT, GREY, large ? "OPENHALDEX PRESETS" : "PRESETS", true);
        String hint = status != null ? status : large ? "TURN TO CHOOSE, PRESS TO APPLY" : "PRESS TO APPLY";
        text(boxW - pad, top, ts, false, RIGHT, status != null ? statusRgba : GREY, hint, true);

        int active = ok(CH_PRESET) ? r(val[CH_PRESET]) : -1;
        int rowTop = large ? 44 : 34;
        int rowH = large ? 38 : 34;
        int ns = large ? 28 : 22;
        int ms = large ? 16 : 13;
        for (int i = 0; i < MAXPRESET; i++) {
            int y0 = rowTop + i * rowH;
            boolean on = link && i < catN;
            box(pad - 6, y0, boxW - 2 * pad + 12, rowH - 4, CURSOR, on && i == cursor);
            box(pad, y0 + 8, 6, rowH - 20, ACTIVE, on && catId[i] == active);
            text(pad + 18, y0 + (rowH - 4 - ns) / 2, ns, i == cursor, LEFT, WHITE,
                on ? upper(catName[i]) : null, on);
            text(boxW - pad - 8, y0 + (rowH - 4 - ms) / 2, ms, false, RIGHT, on && catId[i] == active ? ACTIVE : GREY,
                on ? mode(catMode[i]) : null, on);
        }
        text(boxW / 2, boxH / 2 - 16, 26, true, CENTER, link ? GREY : AMBER,
            link ? "NO PRESET LIST YET" : "NO LINK", !link || catN == 0);
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

    private void text(int x0, int y0, int sz, boolean b, int al, int c, String s, boolean v) {
        int i = add(TEXT, x0, y0, 0, sz, c, v && s != null);
        size[i] = sz;
        bold[i] = b;
        align[i] = al;
        text[i] = s;
    }
}
