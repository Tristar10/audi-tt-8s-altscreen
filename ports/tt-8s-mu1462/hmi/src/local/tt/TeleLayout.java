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
 * Coordinates are cockpit screen pixels (1440x540). A turned BOX turns about (x, y), the middle of its start edge. A text slot's y is the top of the text; the
 * car places text by baseline at y + 0.82 * size. Colours are 0xRRGGBBAA, like EAL text colours.
 *
 * Java 1.4 library only (HMI-patching.md): no string +, no generics, no autoboxing.
 */
public final class TeleLayout {
    public static final int VIEW_LARGE = 0;
    public static final int VIEW_SMALL = 1;
    public static final int PAGE_LIVE = 0;
    public static final int PAGE_PRESETS = 1;
    /** Haldex visualisation (tele19, after the OpenHaldex iPhone app's drivetrain card). */
    public static final int PAGE_HALDEX = 2;
    public static final int BOX = 0;
    public static final int TEXT = 1;
    public static final int QUAD = 2;
    public static final int ICON = 3;
    /**
     * The rev strip painted whole (TeleStrip): size 0 the track picture, size 1 the lit picture
     * shown from x to x + w only (a clip rectangle on the car). Only when paintedStrip is set.
     */
    public static final int STRIP = 4;
    /**
     * The car picture on the Haldex page (tele20): size CAR_UP (Sport, nose up) or CAR_SIDE
     * (Classic, nose right); x, y, w, h its place. Only when carArt is set.
     */
    public static final int IMAGE = 5;
    public static final int CAR_UP = 0;
    public static final int CAR_SIDE = 1;
    /** Classic's themed notch glow (2026-11-25, after the FL5): live page only, under the line and gear. */
    public static final int NOTCH_GLOW = 2;
    public static final int NOTCH_GLOW_W = 248;
    public static final int NOTCH_GLOW_H = 124;
    /** Sport's theme chibis (2026-11-25): live page only. White-haired top right, doll bottom right. */
    public static final int SP_WHITE = 3;
    public static final int SP_WHITE_X = 428;
    public static final int SP_WHITE_Y = 104;
    public static final int SP_WHITE_W = 69;
    public static final int SP_WHITE_H = 96;
    public static final int SP_DOLL = 4;
    public static final int SP_DOLL_X = 410;
    public static final int SP_DOLL_Y = 381;
    public static final int SP_DOLL_W = 73;
    public static final int SP_DOLL_H = 86;
    /** Sport's rpm bloom (a soft red disc, scaled each frame) and the emblem it reveals (2026-11-25). */
    public static final int SP_BLOOM = 5;
    public static final int SP_BLOOM_S = 256;
    public static final int SP_EMBLEM = 6;
    public static final int SP_EMBLEM_S = 340;
    static final int BLOOM_CX = 236;
    static final int BLOOM_CY = 282;
    static final int BLOOM_RX = 290;
    static final int BLOOM_RY = 236;
    static final int BLOOM_FROM = 4500;
    /** The bright layer inside the dark one (user: the Viper goes from dark to bright red). */
    public static final int SP_BLOOM_HOT = 7;
    static final int BLOOM_HOT_FROM = 5300;
    /** Picture sizes on the screen (the files on the car are exactly these). */
    public static final int CAR_UP_W = 128;
    public static final int CAR_UP_H = 264;
    public static final int CAR_SIDE_W = 384;
    public static final int CAR_SIDE_H = 186;
    /** The car pictures are on the head unit (TeleView sets it): the page draws the livery. */
    public boolean carArt;
    /** The notch glow picture is on the head unit (TeleView sets it). */
    public boolean glowArt;
    /** The Sport chibi pictures are on the head unit (TeleView sets it). */
    public boolean chibiArt;
    /** The Sport bloom and emblem pictures are on the head unit (TeleView sets it). */
    public boolean bloomArt;
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
    public static final int CH_BRAKE = 21;
    public static final int CH_HX_TARGET = 10;
    public static final int CH_HX_ACTUAL = 11;
    public static final int CH_GEAR = 13;
    public static final int CH_DSG = 16;
    public static final int CH_CLUTCH = 19;
    /** Steering angle in degrees, right positive (board channel 20). */
    public static final int CH_STEER = 20;
    public static final int CH_PRESET = 22;
    /** OpenHaldex effective mode (MODES index; 11 = MANUAL), board v1.1.21. */
    public static final int CH_MODE = 12;
    /** Manual lock setpoint %, 255 when not in MANUAL (board v1.1.21). */
    public static final int CH_LOCK_SET = 23;
    /** DSG launch control active, 0/1 (GETRIEBE_14 GE_LaunchControl; OpenHaldex v1.1.22). */
    public static final int CH_LAUNCH = 24;
    /** The board's launch detector (OpenHaldex v1.1.23): 0 idle, 1 armed, 2 running, 3 done, 4 aborted. */
    public static final int CH_LSTATE = 25;
    /** Seconds x1000: running time while ch 25 = 2, the 0-100 km/h result while 3 (held 30 s). */
    public static final int CH_LTIME = 26;

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
    public static final int NOTCH = 0x110606FF;
    public static final int NOTCH_IN = 0x4A0E0AFF;
    public static final int NOTCH_HOT = 0x40100CFF;
    public static final int NOTCH_HOT_IN = 0xB8241AFF;
    public static final int LED_OFF = 0x1E242BFF;
    public static final int BLUE = 0x2F7BFFFF;
    public static final int STRIP_LO = 0xDCE6EEFF;
    // Scheme 2026-11-24, matched to the livery and the two characters: ice white (the white-haired
    // girl, the wrap's white), sakura pink (the pink-haired girl), red past the shift point; mint and
    // cobalt for the first shift lights; dark glass for everything unlit.
    public static final int ICE = 0xE4F3FFFF;
    public static final int SAKURA = 0xFF6FAEFF;
    public static final int MINT = 0x3DEBB0FF;
    public static final int COBALT_LT = 0x3A86FFFF;
    public static final int GLASS_OFF = 0x101826FF;
    public static final int GLASS_EDGE = 0x2C3A52FF;
    public static final int VAC_COBALT = 0x2A4FD8FF;
    public static final int TRACK = 0x1A2027FF;
    public static final int EDGE = 0x2E363FFF;
    public static final int DARK_TEXT = 0x0B0F14FF;
    public static final int PEDAL_FILL = 0x34C759FF;
    public static final int BOOST_FILL = 0xFF5A36FF;
    public static final int VAC_FILL = 0x5A7A99FF;
    public static final int BRAKE_FILL = 0xFF3B30FF;
    public static final int REDZONE = 0x4A1210FF;
    public static final int BOOST_HI = 0xFFA07AFF;
    public static final int HALDEX = 0x2F9BFFFF;
    public static final int PRESET = 0x7FD3FFFF;
    public static final int COLD = 0x5AC8FAFF;
    public static final int CURSOR = 0x1C3B5AFF;
    public static final int ACTIVE = 0x34C759FF;

    private static final String[] MODES = {"STOCK", "FWD", "50:50", "60:40", "70:30", "80:20", "90:10",
        "SPEED", "THROTTLE", "MAP", "RPM", "MANUAL"};

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
    // Middle corners at 720 - 2000 k and 720 + 1800 k (k = the middle's px per rpm): 5000 rpm sits
    // on the screen's centre line x 720, with the gear, shift lights and panel (user 2026-11-24).
    // tele24: the ends 40 px in from the housing (car photos: both ends touched it)
    static final int[] TP_X = {64, 335, 1067, 1376};
    // User 2026-11-24: the flat middle fatter (68 px), lower so the shift lights above it can be
    // taller too; everything under the strip moved down by D_Y. The slopes taper from 28 px.
    static final int[] TP_Y = {266, 170, 170, 266};
    static final int HALF_MIN = 14;
    static final int HALF_MAX = 34;
    private static final int D_Y = 26;
    private static final int SLOPE_PIECES = 10;
    private static final int MID_RPM = 3000;
    // Shift lights: LEDS_SIDE per side under the tab bar, lighting from both ends towards the
    // middle, LED_STEP rpm apart up to the shift point.
    private static final int LEDS_SIDE = 6;
    private static final int LED_STEP = 200;
    // Red line and the notch holding the gear.
    private static final int LINE_Y = 200 + D_Y;
    private static final int NOTCH_X0 = 596;
    private static final int NOTCH_X1 = 844;
    private static final int NOTCH_SLOPE = 30;
    private static final int NOTCH_Y = 324 + D_Y;
    // Temperature row between the dials, under the notch.
    // tele23: the stock dials measured on a car photo (centres 235 / 1205, y 389, radius 116) reach
    // in further than estimated; the row stays clear of them, centred on x 720
    private static final int GRID_X0 = 378; // a clear gap to the dials (panel 370..1070)
    private static final int GRID_X1 = 1062;
    private static final int GRID_Y = 362 + D_Y;

    // Settings
    public int shiftRpm = 6500;
    public int limitRpm = 6800;
    public int barFromRpm = 0;
    public int barToRpm = 8000;
    /** Start of the red zone on the strip (the limiter will move above 7000 later). */
    public int redlineRpm = 6800;
    /** Temperatures in degrees F when true; the car uses C (user, 2026-11-23). */
    public boolean imperial = false;
    /** Boost in psi when true, bar when false. Channel 8 is taken as kPa above atmosphere. */
    public boolean boostPsi = false;
    /** Draw the rev strip as TeleStrip's two pictures (STRIP slots) instead of pieces (tele8 probe). */
    public boolean paintedStrip = false;
    /** Itasha theme on: the notch's fill is see-through so the theme's rings show behind the gear. */
    public boolean themed = false;
    /** Soft drop shadows under texts and a recessed look on bars (2026-11-25); icons carry their own. */
    public boolean shadows = true;
    /** The board is sending its test pattern (TTG1 flags bit 0): TeleLink sets it. */
    public boolean testData;
    /** The text shadow colour, RGBA. */
    static final int TEXT_SHADOW = 0x000000D8;

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
    // Haldex control page (TeleUi, tele17): written by TeleUi.tick before build.
    public boolean uiOpen;
    public int uiCursor;
    public int uiPreset;
    public boolean uiEdit;
    public int uiMode;
    public int uiLock;
    public String uiMsg;
    public int uiMsgRgba;
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
    // Boost arc range in kPa above atmosphere, and the peak hold.
    private static final int BOOST_MIN = -100;
    private static final int BOOST_MAX = 150;
    private int peakKpa = BOOST_MIN;
    private double brakeSm;
    private long peakAt;
    private final double[] pb = new double[4];

    private int page;

    public void build(int view, int page) {
        boolean large = view == VIEW_LARGE;
        this.page = page;
        count = 0;
        box(0, 0, SCREEN_W, SCREEN_H, BG, true);
        if (page == PAGE_PRESETS)
            presets(large);
        else if (large)
            liveLarge();
        else
            liveSmall();
        // floats over the page; the page itself is unchanged. Not while the pop-up is open: it
        // covers the middle anyway, and Classic + banner + pop-up reached the 400-slot limit
        if (page != PAGE_PRESETS && !uiOpen)
            launchOverlay(large);
        if (testData)
            testTag(large); // floats over the page and the launch banner
        if (uiOpen && page != PAGE_PRESETS)
            popup(large);
    }

    /**
     * A floating TEST DATA tag while the board sends its test pattern (board session 2026-11-25:
     * the pattern holds a fake launch and lock 100 %, which must never look real). Magenta, so it
     * matches nothing else on screen; blinks at 1 Hz.
     */
    private void testTag(boolean large) {
        boolean on = (now / 500) % 2 == 0;
        int cx = large ? 720 : 280;
        int y0 = large ? 432 : 108;
        int w = large ? 196 : 150;
        int h = large ? 26 : 22;
        int c = 0xFF2EC8FF;
        box(cx - w / 2 - 2, y0 - 2, w + 4, h + 4, on ? c : 0x7A1A66FF, true);
        box(cx - w / 2, y0, w, h, 0x0A0410F0, true);
        text(cx, y0 + 3, large ? 18 : 15, true, CENTER, on ? c : 0xFFFFFFFF, "TEST DATA", true);
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
        double r0 = l0 + l1; // start of the right slope = the red line (6800): red zone, red fill and limiter marks

        // No glow behind the gear (delicate pass: its layers showed as steps); the notch's fill
        // flashes with the shift lights instead.
        boolean hot = over && blink;

        // Shift lights above the strip, as wide as its fat middle, slanted like the housing's slopes,
        // lighting from both ends towards the middle: green, blue, red over the last
        // LEDS_SIDE * LED_STEP rpm. From the shift point all flash red. Lit lights glow.
        // Centred on the screen (x 720) like the gear and the tab bar, not on the strip's middle
        // (which follows the frame and sits left of centre); kept over the flat part.
        int lx0 = 720 - 330;
        int lx1 = 720 + 330;
        int mid = 24;
        int lgap = 6;
        int lw = ((lx1 - lx0 - mid) / 2 - (LEDS_SIDE - 1) * lgap) / LEDS_SIDE;
        int lh = 26;
        int ly = 106; // clear of the tab bar notch (the user's clipping guide)
        int sk = 8;
        for (int side = 0; side < 2; side++) {
            for (int i = 0; i < LEDS_SIDE; i++) {
                int on = shiftRpm - (LEDS_SIDE - i) * LED_STEP;
                boolean lit = link && (over ? blink : rpm >= on);
                int c = !lit ? GLASS_OFF : over ? RED : i < 2 ? MINT : i < 4 ? COBALT_LT : RED;
                int x0 = side == 0 ? lx0 + i * (lw + lgap) : lx1 - lw - i * (lw + lgap);
                int x1 = x0 + lw;
                int t = side == 0 ? sk : -sk; // top edge shifted: "/" on the left, "\" on the right
                // glass segment: a hairline rim, the body, a highlight across the top third when lit
                quad(x0 + t - 1, ly - 1, x1 + t + 1, ly - 1, x1 + 1, ly + lh + 1, x0 - 1, ly + lh + 1, GLASS_EDGE, true);
                quad(x0 + t, ly, x1 + t, ly, x1, ly + lh, x0, ly + lh, c, true);
                int hy = ly + 3;
                int hh = lh / 3;
                int ht = t * (lh - 3) / lh;
                int hb = t * (lh - 3 - hh) / lh;
                quad(x0 + ht + 3, hy, x1 + ht - 3, hy, x1 + hb - 3, hy + hh, x0 + hb + 3, hy + hh, 0xFFFFFF5C, lit);
            }
        }

        // Rev strip: a dark track with hairline edges; lit from cool white, warming to amber over the
        // last 1500 rpm before the shift point, all red past it, the whole strip flashing at the
        // limiter. Numbers sit inside the strip, dark on the lit part. From the shift point (6500)
        // the whole lit part is red and blinks in step with the shift lights (user 2026-11-24).
        if (paintedStrip) {
            int clipW = Math.max(0, Math.min(TeleStrip.W, TeleStrip.cutX(this, rpm) - TeleStrip.X0));
            int i0 = add(STRIP, TeleStrip.X0, TeleStrip.Y0, TeleStrip.W, TeleStrip.H, WHITE, true);
            size[i0] = 0;
            int i1 = add(STRIP, TeleStrip.X0, TeleStrip.Y0, clipW, TeleStrip.H, WHITE,
                link && clipW > 0 && !(over && !blink));
            size[i1] = over ? 2 : 1; // 2: the all-red lit picture from the shift point
        }
        for (int k = 0; k < 3 && !paintedStrip; k++) {
            double a = k == 0 ? 0 : k == 1 ? l0 : r0;
            double b = k == 0 ? l0 : k == 1 ? r0 : len;
            bandOff(a, b, half(a) + 1, half(a) + 3, half(b) + 1, half(b) + 3, EDGE, true);
            bandOff(a, b, -half(a) - 3, -half(a) - 1, -half(b) - 3, -half(b) - 1, EDGE, true);
        }
        // Continuous fill (tele6; the 84 coloured pieces were not exact and froze the car at the
        // limiter): the strip is a few straight pieces, each a dark track and a fill box that is
        // only scaled to the exact rpm, rotated along the slopes. The whole lit part takes one
        // colour by rpm (cool white, warming to amber over the last 1500 rpm, red past the shift
        // point); at the limiter the fill only blinks. No new pictures per frame.
        int fc = link && rpm >= shiftRpm ? RED : stripColourAt(rpm);
        boolean fillOn = link && !(over && !blink);
        int pieces = 2 * SLOPE_PIECES + 1;
        // Two passes: every track first, then every fill, so no track covers a fill's overlap.
        for (int pass = 0; pass < 2 && !paintedStrip; pass++)
        for (int k = 0; k < pieces; k++) {
            double s0;
            double s1;
            if (k < SLOPE_PIECES) {
                s0 = l0 * k / SLOPE_PIECES;
                s1 = l0 * (k + 1) / SLOPE_PIECES;
            } else if (k == SLOPE_PIECES) {
                s0 = l0;
                s1 = r0;
            } else {
                int j = k - SLOPE_PIECES - 1;
                s0 = r0 + (len - r0) * j / SLOPE_PIECES;
                s1 = r0 + (len - r0) * (j + 1) / SLOPE_PIECES;
            }
            int p0 = sToRpm(s0);
            int p1 = sToRpm(s1);
            int track = p0 >= redlineRpm ? REDZONE : TRACK;
            double frac = rpm <= p0 ? 0 : rpm >= p1 ? 1 : (rpm - p0) / (double) (p1 - p0);
            if (k == SLOPE_PIECES) {
                int y0 = TP_Y[1] - HALF_MAX;
                int w0 = TP_X[2] - TP_X[1];
                if (pass == 0)
                    box(TP_X[1], y0, w0, 2 * HALF_MAX, track, true);
                else
                    box(TP_X[1], y0, (int) Math.round(w0 * frac), 2 * HALF_MAX, fc, fillOn && frac > 0);
            } else {
                // Track and full fill: tapered quads that share their edges exactly (no steps); the
                // piece holding the current rpm adds a box scaled to it (tele7: tele6 boxes showed
                // seams and steps).
                double e1 = k == SLOPE_PIECES - 1 ? 0 : 1.5; // overlap hides the joint
                if (pass == 0) {
                    bandOff(s0, s1 + e1, -half(s0), half(s0), -half(s1 + e1), half(s1 + e1), track, true);
                    // red zone: hazard stripes on the track, covered by the fill when lit
                    for (double a = s0; k > SLOPE_PIECES && a < s1 - 2; a += 14) {
                        double b = Math.min(s1, a + 6);
                        double sl = 10;
                        stripe(a, b, sl, 0x7A1A14FF);
                    }
                    continue;
                }
                bandOff(s0, s1 + e1, -half(s0), half(s0), -half(s1 + e1), half(s1 + e1), fc, fillOn && frac >= 1);
                double th = 2 * half(s0 + (s1 - s0) * frac / 2);
                double from = k == SLOPE_PIECES + 1 ? s0 - 2 : s0;
                tp(from + 0.5, pa);
                double ang = Math.toDegrees(Math.atan2(pa[2], -pa[3]));
                tp(from, pa);
                rbox(pa[0], pa[1], (s1 - from) * frac, th, ang, fc, fillOn && frac > 0 && frac < 1);
            }
        }
        // A white-hot edge where the fill ends: reads the exact rpm (scheme 2026-11-24).
        if (link && rpm > barFromRpm && rpm < barToRpm) {
            double se = rpmToS(rpm);
            tp(se, pa);
            double hv = half(se) / Math.max(0.5, -pa[3]);
            box(rd(pa[0]) - 2, rd(pa[1] - hv) + 2, 3, rd(2 * hv) - 4, 0xFFFFFFFF, !(over && !blink));
        }
        // Shift point: a red cut across the strip where the fat middle ends.
        box(TP_X[2] - 1, TP_Y[2] - HALF_MAX - 4, 3, 2 * HALF_MAX + 8, RED, true); // vertical (user 2026-11-24)
        // Limiter range: red marks along the outer edge of the right slope.
        double sl = rpmToS(limitRpm);
        for (int i = 0; i < 5; i++) {
            double a = sl + (len - sl) * i / 5;
            double b = sl + (len - sl) * (i + 1) / 5 - 3;
            bandOff(a, b, half(a) + 3, half(a) + 5, half(b) + 3, half(b) + 5, RED, limitRpm < barToRpm);
        }
        for (int k = (barFromRpm + 999) / 1000; k * 1000 < barToRpm && !paintedStrip; k++) {
            if (k == 0)
                continue;
            // vertical tick through the strip at each 1000 (user 2026-11-24: the one under 3 was missing)
            tp(rpmToS(k * 1000), pa);
            double hv = half(rpmToS(k * 1000)) / Math.max(0.5, -pa[3]);
            boolean litT = link && rpm > k * 1000 && !(over && !blink);
            box(rd(pa[0]) - 1, rd(pa[1] - hv), 2, rd(2 * hv), litT ? 0x0B0F1499 : LINE, true);
        }
        for (int k = (barFromRpm + 999) / 1000; k * 1000 <= barToRpm; k++) {
            if (k == 0)
                continue;
            double sk0 = rpmToS(k * 1000);
            double th = 2 * half(sk0);
            boolean litK = link && rpm > k * 1000 && !(over && !blink);
            int nc = litK ? DARK_TEXT : k * 1000 >= redlineRpm ? RED : GREY;
            int sz = (int) Math.min(32, th - 10);
            // upright (user 2026-11-24), centred on the strip where its rpm is
            tp(k * 1000 >= barToRpm ? sk0 - 14 : sk0, pa);
            text(rd(pa[0]), rd(pa[1] - 0.46 * sz), sz, true, CENTER, nc, Integer.toString(k), true);
        }

        boolean hxPage = page == PAGE_HALDEX;
        // Red line across with a soft glow, and the notch holding the gear: two layers for depth,
        // flashing with the shift lights from the shift point.
        if (!hxPage) {
        // themed: no fill (the theme's red glow shows behind the gear) except the shift flash
        quad(NOTCH_X0, LINE_Y, NOTCH_X1, LINE_Y, NOTCH_X1 - NOTCH_SLOPE, NOTCH_Y, NOTCH_X0 + NOTCH_SLOPE, NOTCH_Y,
            themed ? 0xFF4A3070 : hot ? NOTCH_HOT : NOTCH, !themed || hot);
        if (themed && glowArt) {
            int gi = add(IMAGE, NOTCH_X0, LINE_Y, NOTCH_GLOW_W, NOTCH_GLOW_H, WHITE, true);
            size[gi] = NOTCH_GLOW;
        }
        // 2026-11-25 (user: the notch was too flimsy): a 4 px line with a soft red glow on both
        // sides, the glow first (one wide translucent layer, then the line over it).
        for (int k = 0; k < 2; k++) {
            int w = k == 0 ? 7 : 2;
            int c = k == 0 ? 0xFF2A2A40 : RED; // RGBA
            box(GRID_X0, LINE_Y - w + 1, NOTCH_X0 - GRID_X0 + w, 2 * w, c, true);
            box(NOTCH_X1 - w, LINE_Y - w + 1, GRID_X1 - NOTCH_X1 + w, 2 * w, c, true);
            quad(NOTCH_X0 - w, LINE_Y - w + 1, NOTCH_X0 + w, LINE_Y - w + 1, NOTCH_X0 + NOTCH_SLOPE + w, NOTCH_Y + w,
                NOTCH_X0 + NOTCH_SLOPE - w, NOTCH_Y + w, c, true);
            quad(NOTCH_X1 - w, LINE_Y - w + 1, NOTCH_X1 + w, LINE_Y - w + 1, NOTCH_X1 - NOTCH_SLOPE + w, NOTCH_Y + w,
                NOTCH_X1 - NOTCH_SLOPE - w, NOTCH_Y + w, c, true);
            box(NOTCH_X0 + NOTCH_SLOPE + w, NOTCH_Y - w + 1, NOTCH_X1 - NOTCH_X0 - 2 * NOTCH_SLOPE - 2 * w, 2 * w, c, true);
        }
        }
        // themed: past the shift point the gear flashes dark navy over the red glow (red on red
        // did not read)
        int gc = !link ? GREY : over && !blink ? (themed ? 0x0A1030FF : RED) : WHITE;
        if (!hxPage)
            gearGlyph(720, 208 + D_Y, 104, gc);

        // Top-left pocket, along the rising slope: the active OpenHaldex preset, its mode above it,
        // and a Wi-Fi warning only while the link is slow or lost.
        boolean slow = link && rateHz >= 0 && rateHz < 10;
        double ps = l0 * 0.36; // ends before the slope's corner (tele24)
        // tele24: parallel to the strip's top edge and close to it (car photo: tilted less than the
        // edge and close to the frame's top)
        int pl = slopeText(ps, half(ps) + 24 + 0.82 * 30, 30, true, LEFT, link ? PRESET : GREY, presetName(), true);
        turn(pl, (float) -edgeAngle());
        int ml = slopeText(ps + 12, half(ps + 12) + 58 + 0.82 * 16, 16, true, LEFT, GREY, presetMode(), true);
        turn(ml, (float) -edgeAngle());
        slopeIcon(l0 * 0.20, half(l0 * 0.20) + 14, 26, TeleIcons.WIFI, AMBER, !link || slow);

        // Top-right pocket, along the falling slope: Haldex lock. Icon and big percent, and under
        // them a bar along the slope with the target as a white tick.
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        // 2026-11-25: the first 200 px of the slope only (the chibi left this corner), so the
        // portrait's face is clear of the readout and its dark plate
        double ba = r0 + 16;
        double bb = r0 + 200;
        // Fixed pieces (no shape changes per frame on the car): each piece is lit or not, and the
        // target tick is a white mark over the piece holding the target.
        int hs = 20;
        int ti = Math.min(hs - 1, tgt * hs / 100);
        for (int j = 0; j < hs; j++) {
            double a = ba + (bb - ba) * j / hs;
            double b = ba + (bb - ba) * (j + 1) / hs - 1.2;
            boolean on = link && act > 0 && j * 100 / hs < act;
            bandOff(a, b, half(a) + 34, half(a) + 46, half(b) + 34, half(b) + 46, on ? HALDEX : DIM, true);
            double m = (a + b) / 2;
            bandOff(m - 1.5, m + 1.5, half(m) + 30, half(m) + 50, half(m) + 30, half(m) + 50, WHITE,
                ok(CH_HX_TARGET) && j == ti);
        }
        // dark plate under the Haldex corner while themed (the portrait sits behind it)
        bandOff(ba - 8, bb + 4, half(ba) + 26, half(ba) + 104, half(bb) + 26, half(bb) + 104, 0x05091CB0, themed);
        int hi = slopeIcon(ba + 4, half(ba) + 58, 32, TeleIcons.AWD, ICONC, true);
        rot[hi] = (float) edgeAngle();
        int hv = slopeText(ba + 50, half(ba + 50) + 56 + 0.82 * 40, 40, false, LEFT, vc,
            ok(CH_HX_ACTUAL) ? percent(act) : "-", true);
        turn(hv, (float) edgeAngle());

        if (hxPage) {
            // car sideways, nose to the right; gauge and stats to its right; the gear small in the corner
            // 2026-11-25 (user): the car toward the middle with the torque share at its tail
            // (rear) and nose (front); stats with the actual lock on the right; the gear bottom left
            drivetrain(452, 228, 384, 196, true);
            torqueSplit();
            hxStats(924, 232, 1058, 26, 46, true);
            gearGlyph(408, 388, 34, gc); // bottom left, under the rear share
            return;
        }
        // Left of the notch: rpm, and the driver's inputs under it: accelerator (green) and brake
        // pressure (red, full bar at 100 bar).
        icon(GRID_X0 + 14, 210 + D_Y, 32, TeleIcons.TACH, ICONC, true);
        text(NOTCH_X0 - 8, 204 + D_Y, 40, false, RIGHT, over ? RED : vc, ok(CH_RPM) ? Integer.toString(rpm) : "-", link);
        text(NOTCH_X0 - 8, 208 + D_Y, 34, true, RIGHT, AMBER, "NO LINK", !link);
        int ped = ok(CH_PEDAL) ? clamp(r(val[CH_PEDAL]), 0, 100) : 0;
        icon(GRID_X0 + 16, 254 + D_Y, 26, TeleIcons.PEDAL, ICONC, true);
        bar(GRID_X0 + 54, 261 + D_Y, 70, 12, ped, 0, link ? PEDAL_FILL : GREY, -1);
        text(NOTCH_X0 - 8, 255 + D_Y, 24, false, RIGHT, vc, ok(CH_PEDAL) ? percent(ped) : "-", true);
        // Brake pressure smoothed (EMA at the 30 Hz tick) with a 2 bar dead band: the sensor reads
        // about 1 bar off the brakes (road test 2026-11-23).
        double rawBrake = ok(CH_BRAKE) ? Math.max(0, val[CH_BRAKE] / 1000.0) : 0;
        brakeSm += 0.3 * (rawBrake - brakeSm);
        int brk = brakeSm < 2.0 ? 0 : clamp((int) Math.round(brakeSm), 0, 999);
        icon(GRID_X0 + 16, 288 + D_Y, 26, TeleIcons.BRAKE, brk > 0 && link ? BRAKE_FILL : ICONC, true);
        bar(GRID_X0 + 54, 295 + D_Y, 70, 12, Math.min(brk, 100), 0, link ? BRAKE_FILL : GREY, -1);
        text(NOTCH_X0 - 8, 289 + D_Y, 24, false, RIGHT, vc, ok(CH_BRAKE) ? withUnit(brk, " bar") : "-", true);

        // Right of the notch: boost as a turbo-style arc. Segments sweep from vacuum (steel blue)
        // through zero into boost, warming amber -> orange -> red, with a glow while on boost; a white
        // marker holds the peak for 3 s; the big value sits inside the arc.
        int kpa = ok(CH_BOOST) ? r(val[CH_BOOST]) : 0;
        if (kpa >= peakKpa || now - peakAt > 3000) {
            peakKpa = kpa;
            peakAt = now;
        }
        boolean boosting = link && kpa > 0;
        // Boost as a row of segments (after the FL5's +R meter, laid flat: user 2026-11-24): turbo
        // icon, value and unit on top; segments lit from zero to the right in the boost colours (or
        // to the left in steel blue on vacuum), unlit dark grey; a white mark holds the peak for 3 s;
        // a scale bracket under it (-1, 0, +1.5 bar). Fixed boxes: only their colour changes.
        // FL5-thick (user 2026-11-24): 25 tall stripes of 10 kPa, 46 px high
        int segN = 24;
        int pitch = 8; // tele23: narrower, clear of the speedometer
        int bx = 866;
        int by = 278;
        int bh = 46;
        int bwAll = segN * pitch - 2;
        int range = BOOST_MAX - BOOST_MIN;
        int zeroSeg = segN * (0 - BOOST_MIN) / range;
        int lvl = link ? (int) Math.round(segN * (clamp(kpa, BOOST_MIN, BOOST_MAX) - BOOST_MIN) / (double) range)
            : zeroSeg;
        icon(bx - 2, 236, 30, TeleIcons.TURBO, boosting ? BOOST_FILL : ICONC, true);
        text(bx + 38, 228, 40, false, LEFT, boosting ? WHITE : vc, ok(CH_BOOST) ? boost(kpa) : "-", true);
        text(bx + bwAll, 246, 16, true, RIGHT, GREY, boostPsi ? "PSI" : "BAR", true);
        for (int i = 0; i < segN; i++) {
            int v = BOOST_MIN + range * (2 * i + 1) / (2 * segN);
            boolean on = link && (i >= zeroSeg ? i < lvl : i >= lvl);
            box(bx + i * pitch, by, pitch - 3, bh, !on ? GLASS_OFF : v < 0 ? VAC_COBALT : boostColour(v), true);
        }
        int zx = bx + zeroSeg * pitch - 1;
        int ly2 = by + bh + 5;
        box(bx, ly2, bwAll, 2, GREY, true);
        box(bx, ly2 - 5, 2, 7, GREY, true);
        box(zx - 1, ly2 - 5, 2, 7, GREY, true);
        box(bx + bwAll - 2, ly2 - 5, 2, 7, GREY, true);
        text(bx, ly2 + 6, 16, false, LEFT, GREY, boostPsi ? "-14" : "-1", true);
        text(zx, ly2 + 6, 16, false, CENTER, GREY, "0", true);
        text(bx + bwAll, ly2 + 6, 16, false, RIGHT, GREY, boostPsi ? "+22" : "+1.5", true);
        // one soft highlight across the bar's upper part (no dark lower edge)
        box(bx, by + 4, bwAll, bh / 4, 0xFFFFFF30, true);
        // peak: a small sakura triangle above the bar
        int pk = (int) Math.round(segN * (clamp(peakKpa, BOOST_MIN, BOOST_MAX) - BOOST_MIN) / (double) range);
        int px = bx + pk * pitch - 2;
        quad(px - 7, by - 12, px + 7, by - 12, px, by - 3, px, by - 3, SAKURA, link && peakKpa > 0);

        // Temperatures in one row, an icon and a value in each cell, short dividers between.
        int cw = (GRID_X1 - GRID_X0) / T_CH.length;
        // themed: the hairline over the row (was in the theme picture, which crossed the Haldex page's car)
        box(GRID_X0 + 8, GRID_Y - 10, GRID_X1 - GRID_X0 - 16, 1, 0x3E8BFF8C, themed);
        for (int t = 0; t < T_CH.length; t++) {
            int cx0 = GRID_X0 + t * cw;
            if (t > 0)
                box(cx0, GRID_Y + 4, 1, 24, LINE, true);
            tempCell(t, cx0 + 12, cx0 + cw - 12, GRID_Y, 30, 32, rpm);
        }
    }

    // ---------------------------------------------------------------- launch banner (2026-11-25)
    // User: when launch control is set up (brake at least half, throttle at least 30 %), a
    // floating, dramatic message over the screen; the screen under it stays as it is. Armed while
    // stopped with brake >= 50 bar and throttle >= 30 %; stays until under 40 bar / 20 % / rolling
    // over 5 km/h (no flicker). Amber while armed, green and pulsing once the Haldex is locked
    // (>= 90 %). Fixed shapes, only colours change per frame (cheap on the car).
    static final int LAUNCH_BRAKE = 50;
    static final int LAUNCH_PEDAL = 30;
    static final int LAUNCH_AMBER = 0xFFB020FF;
    static final int LAUNCH_GO = 0x3CE08AFF;
    private boolean launchOn;
    private double launchBrake;

    private boolean launchArmed(int ped, int brk) {
        int spd = ok(CH_SPEED) ? r(val[CH_SPEED]) : 0;
        // the car's own launch control (ch 24, board v1.1.22) decides when it is sent; the
        // brake / throttle guess only without it
        if (link && ok(CH_LAUNCH)) {
            launchOn = val[CH_LAUNCH] >= 500 && spd <= 5;
            return launchOn;
        }
        if (!link || !ok(CH_PEDAL) || !ok(CH_BRAKE)) {
            launchOn = false;
        } else if (launchOn) {
            launchOn = brk >= LAUNCH_BRAKE - 10 && ped >= LAUNCH_PEDAL - 10 && spd <= 5;
        } else {
            launchOn = brk >= LAUNCH_BRAKE && ped >= LAUNCH_PEDAL && spd < 3;
        }
        return launchOn;
    }

    /**
     * The same banner while the board times the launch: "x.xx s" counting while running (amber),
     * then "0-100  x.xx s" in green (the board holds it 30 s).
     */
    private void launchTimed(boolean large, boolean done, int ms) {
        int c = done ? LAUNCH_GO : LAUNCH_AMBER;
        int cx = large ? 720 : 250;
        int cy = large ? 312 : 282;
        int w = large ? 640 : 380;
        int h = large ? 80 : 70;
        int sk = large ? 22 : 18;
        int x0 = cx - w / 2;
        int x1 = cx + w / 2;
        int y0 = cy - h / 2;
        int y1 = cy + h / 2;
        quad(x0 + sk - 9, y0 - 9, x1 + sk + 9, y0 - 9, x1 - sk + 9, y1 + 9, x0 - sk - 9, y1 + 9, (c & 0xFFFFFF00) | 0x40, true);
        quad(x0 + sk, y0, x1 + sk, y0, x1 - sk, y1, x0 - sk, y1, 0x05060CEE, true);
        thick(x0 + sk, y0 + 1, x1 + sk, y0 + 1, 3, c, true);
        thick(x0 - sk, y1 - 1, x1 - sk, y1 - 1, 3, c, true);
        int cs = Math.max(0, ms) / 10; // hundredths
        StringBuffer t = new StringBuffer();
        t.append(cs / 100).append('.');
        if (cs % 100 < 10)
            t.append('0');
        t.append(cs % 100).append(" s");
        int ts = large ? 60 : 38;
        text(cx, cy - (int) (ts * 0.6), ts, true, CENTER, done ? WHITE : c, t.toString(), true);
        int sw2 = large ? 300 : 230;
        box(cx - sw2 / 2, y1 + 5, sw2, large ? 26 : 24, 0x05060CD8, true);
        text(cx, y1 + 7, large ? 18 : 16, true, CENTER, c, done ? "0-100 KM/H" : "LAUNCH  RUNNING", true);
    }

    private void launchOverlay(boolean large) {
        int ped = ok(CH_PEDAL) ? clamp(r(val[CH_PEDAL]), 0, 100) : 0;
        double rb = ok(CH_BRAKE) ? Math.max(0, val[CH_BRAKE] / 1000.0) : 0;
        launchBrake += 0.3 * (rb - launchBrake);
        // PROPOSAL (2026-11-25, needs the user's OK before a build): with the board's own detector
        // (ch 25) the banner follows it, and shows the run time and the 0-100 result in the
        // existing banner; without ch 25 nothing changes.
        int lst = link && ok(CH_LSTATE) ? r(val[CH_LSTATE]) : -1;
        if (lst >= 0) {
            if (lst == 2 || lst == 3) {
                launchTimed(large, lst == 3, ok(CH_LTIME) ? val[CH_LTIME] : 0);
                return;
            }
            if (lst != 1)
                return;
        } else if (!launchArmed(ped, (int) Math.round(launchBrake)))
            return;
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        boolean ready = act >= 90;
        int c = ready ? LAUNCH_GO : LAUNCH_AMBER;
        boolean pulse = (now / 125) % 2 == 0;
        int cx = large ? 720 : 250;
        int cy = large ? 312 : 282; // Classic: the state line stays above the temperature row
        int w = large ? 640 : 380;
        int h = large ? 80 : 70;
        int sk = large ? 22 : 18;
        int x0 = cx - w / 2;
        int x1 = cx + w / 2;
        int y0 = cy - h / 2;
        int y1 = cy + h / 2;
        // halo (pulsing when ready), plate, bright edges top and bottom
        int hg = 9;
        quad(x0 + sk - hg, y0 - hg, x1 + sk + hg, y0 - hg, x1 - sk + hg, y1 + hg, x0 - sk - hg, y1 + hg,
            (c & 0xFFFFFF00) | (ready && pulse ? 0x80 : 0x40), true);
        quad(x0 + sk, y0, x1 + sk, y0, x1 - sk, y1, x0 - sk, y1, 0x05060CEE, true);
        thick(x0 + sk, y0 + 1, x1 + sk, y0 + 1, 3, c, true);
        thick(x0 - sk, y1 - 1, x1 - sk, y1 - 1, 3, c, true);
        // hazard stripes at both ends, parallel to the slanted ends
        int ns = large ? 5 : 3;
        int sp = 14;
        int sw = 7;
        for (int i = 0; i < ns; i++) {
            int a0 = x0 + 10 + i * sp;
            quad(a0 + sk, y0 + 5, a0 + sk + sw, y0 + 5, a0 - sk + sw, y1 - 5, a0 - sk, y1 - 5, c, true);
            int b0 = x1 - 10 - sw - i * sp;
            quad(b0 + sk, y0 + 5, b0 + sk + sw, y0 + 5, b0 - sk + sw, y1 - 5, b0 - sk, y1 - 5, c, true);
        }
        // chevrons running in toward the word, one lit at a time
        int nc = large ? 3 : 2;
        int lit = (int) ((now / 110) % nc);
        int cs = large ? 15 : 12;
        int cw0 = large ? 12 : 10;
        for (int i = 0; i < nc; i++) {
            int cc = i == lit ? c : (c & 0xFFFFFF00) | 0x55;
            int lx = x0 + 12 + ns * sp + 6 + i * (large ? 26 : 16);
            thick(lx, cy - cs, lx + cw0, cy, 5, cc, true);
            thick(lx + cw0, cy, lx, cy + cs, 5, cc, true);
            int rx = x1 - 12 - ns * sp - 6 - i * (large ? 26 : 16);
            thick(rx, cy - cs, rx - cw0, cy, 5, cc, true);
            thick(rx - cw0, cy, rx, cy + cs, 5, cc, true);
        }
        int ts = large ? 60 : 38;
        text(cx, cy - (int) (ts * 0.6), ts, true, CENTER, ready && !pulse ? WHITE : c, "LAUNCH", true);
        // the state under the plate
        String st = new StringBuffer(ready ? "READY" : "ARMED").append("   LOCK ").append(act).append('%').toString();
        int sw2 = large ? 300 : 230;
        box(cx - sw2 / 2, y1 + 5, sw2, large ? 26 : 24, 0x05060CD8, true);
        text(cx, y1 + 7, large ? 18 : 16, true, CENTER, c, st, true);
    }

    /** Boost scheme: ice at zero, sakura at 0.9 bar, red at full boost. */
    private static int boostColour(int kpa) {
        if (kpa <= 0)
            return ICE;
        if (kpa < 90)
            return mix(ICE, SAKURA, kpa / 90f);
        return mix(SAKURA, RED, Math.min(1f, (kpa - 90) / 60f));
    }




    /** The lit strip's one colour at this rpm, in four steps (few node colours on the car). */
    private int stripColourAt(int rpmNow) {
        // ice -> sakura in four steps (few node colours on the car); red from the shift point
        int from = shiftRpm - 2500;
        if (rpmNow <= from)
            return ICE;
        int step = Math.min(3, (rpmNow - from) * 4 / 2500);
        return mix(ICE, SAKURA, step / 3f);
    }

    /** The scheme's lit colour at an rpm: ice white, warming to sakura pink over 2500 rpm before shift. */
    static int iceSakura(int rpmAt, int shift) {
        int from = shift - 2500;
        if (rpmAt <= from)
            return ICE;
        return mix(ICE, SAKURA, Math.min(1f, (rpmAt - from) / 2500f));
    }


    static int mix(int a, int b, float t) {
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
        box(x0, y0, bw, bh, DIM, true);
        int a = Math.min(fromPct, pct);
        int b = Math.max(fromPct, pct);
        box(x0 + bw * a / 100, y0, bw * (b - a) / 100, bh, c, b > a);
        if (shadows) {
            // recessed track, raised fill (2026-11-25): a dark line inside the track's top, a light
            // top edge on the fill, a faint light lip under the track
            box(x0, y0, bw, Math.max(2, bh / 4), 0x000000B0, true);
            box(x0 + bw * a / 100, y0, bw * (b - a) / 100, Math.max(1, bh / 4), 0xFFFFFF48, b > a);
            box(x0, y0 + bh, bw, 1, 0xFFFFFF30, true);
        }
        box(x0 + bw * tickPct / 100 - 1, y0 - 4, 2, bh + 8, GREY, tickPct >= 0);
    }

    private static String withUnit(int v, String unit) {
        StringBuffer sb = new StringBuffer();
        sb.append(v).append(unit);
        return sb.toString();
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
    static double half(double s) {
        double l0 = segLen(0);
        double l1 = segLen(1);
        double l2 = segLen(2);
        if (s <= l0)
            return HALF_MIN + (HALF_MAX - HALF_MIN) * Math.max(0, s) / l0;
        if (s <= l0 + l1)
            return HALF_MAX;
        return HALF_MAX - (HALF_MAX - HALF_MIN) * Math.min(1, (s - l0 - l1) / l2);
    }

    double rpmToS(int rpm) {
        double l0 = segLen(0);
        double l1 = segLen(1);
        double l2 = segLen(2);
        if (rpm <= MID_RPM)
            return l0 * Math.max(0, rpm - barFromRpm) / (double) (MID_RPM - barFromRpm);
        if (rpm <= redlineRpm)
            return l0 + l1 * (rpm - MID_RPM) / (double) (redlineRpm - MID_RPM);
        return l0 + l1 + l2 * Math.min(1.0, (rpm - redlineRpm) / (double) (barToRpm - redlineRpm));
    }

    int sToRpm(double s) {
        double l0 = segLen(0);
        double l1 = segLen(1);
        double l2 = segLen(2);
        if (s <= l0)
            return barFromRpm + (int) ((MID_RPM - barFromRpm) * s / l0);
        if (s <= l0 + l1)
            return MID_RPM + (int) ((redlineRpm - MID_RPM) * (s - l0) / l1);
        return redlineRpm + (int) ((barToRpm - redlineRpm) * (s - l0 - l1) / l2);
    }

    /** The angle of the strip's top edge on a slope, in degrees (it tapers: steeper than the centre). */
    private static double edgeAngle() {
        double dy = (TP_Y[0] - TP_Y[1]) + (HALF_MAX - HALF_MIN);
        return Math.toDegrees(Math.atan2(dy, TP_X[1] - TP_X[0]));
    }

    /** Text along the strip at length s, its top edge offTop px out from the centre (negative: in). */
    private int slopeText(double s, double offTop, int sz, boolean b, int al, int c, String str, boolean v) {
        tp(s, pa);
        if (shadows) {
            int d = shadowOffset(sz);
            int si = add(TEXT, rd(pa[0] + pa[2] * offTop) + d, rd(pa[1] + pa[3] * offTop) + d, 0, sz, TEXT_SHADOW, v && str != null);
            size[si] = sz;
            bold[si] = b;
            align[si] = al;
            text[si] = str;
            rot[si] = (float) Math.toDegrees(Math.atan2(pa[2], -pa[3]));
        }
        int i = add(TEXT, rd(pa[0] + pa[2] * offTop), rd(pa[1] + pa[3] * offTop), 0, sz, c, v && str != null);
        size[i] = sz;
        bold[i] = b;
        align[i] = al;
        text[i] = str;
        rot[i] = (float) Math.toDegrees(Math.atan2(pa[2], -pa[3]));
        return i;
    }

    /** Icon standing on the strip at length s, its bottom edge off px out from the centre. */
    private int slopeIcon(double s, double off, int px, int id, int c, boolean v) {
        tp(s, pa);
        double out = off + px;
        int i = add(ICON, rd(pa[0] + pa[2] * out), rd(pa[1] + pa[3] * out), px, px, c, v);
        size[i] = id;
        rot[i] = (float) Math.toDegrees(Math.atan2(pa[2], -pa[3]));
        return i;
    }

    static double segLen(int k) {
        return Math.sqrt(sq(TP_X[k + 1] - TP_X[k]) + sq(TP_Y[k + 1] - TP_Y[k]));
    }


    /**
     * The Sport view's panel outline: the user's Sport clipping guide (sim ref/sport-clip-guide.svg,
     * 2026-11-24) shrunk 8 px inwards, left of the big rev counter (centre 720,309, r 264), cut
     * straight at y 106 under the status strip notch (printed by the sim's OutlinePts, then the thin
     * sliver between the notch and the dial dropped). Clockwise from the top right: along the top,
     * the notch's left corner, the bezel down the left side, the bottom strip, up the dial's ring.
     * Shared with the theme's housing and the redline warning frame.
     */
    static final int[] SP_X = {541, 215, 194, 176, 93, 40, 19, 16, 28, 57, 74, 96, 124, 155, 490, 465, 451, 449,
        458, 478, 507};
    static final int[] SP_Y = {106, 106, 98, 84, 113, 145, 169, 184, 275, 373, 409, 440, 452, 455, 455, 404, 349,
        290, 235, 185, 140};

    private void liveSmall() {
        // Only the map area left of the big rev counter shows (SP_X/SP_Y). From the top: Haldex
        // lock, the gear with the temperatures beside it, preset and mode, boost. 2026-11-24: content
        // 20 px further left (the doll chibi sits clear of the tach), oil temperature dropped (the
        // stock Sport view shows it), the rest bigger.
        int rpm = ok(CH_RPM) ? r(val[CH_RPM]) : 0;
        boolean blink = (now / 100) % 2 == 0;
        boolean over = link && rpm >= shiftRpm;
        boolean limit = link && rpm >= limitRpm;
        int vc = link ? WHITE : GREY;
        int x0 = 44; // tele21: left edge follows the frame's curve
        int x1 = 416;

        // Redline warning, under everything else: a red tint over the panel, flashing with the
        // shift lights (a fixed shape, only shown or hidden).
        boolean warn = over && blink;
        box(28, 106, 420, 349, 0xFF3B3030, warn); // the bezel and the dial cover what lies outside SP_X/SP_Y
        quad(448, 106, 541, 106, 458, 235, 448, 290, 0xFF3B3030, warn); // up to the dial's ring
        quad(448, 290, 451, 349, 490, 455, 448, 455, 0xFF3B3030, warn);

        // 2026-11-25 (user, after the Viper's tach): the shift lights became a red bloom growing
        // from the panel's middle with rpm (4500 to the shift point), an emblem in dark lines that
        // shows only where the bloom is, and a slim arc of segments along the rev counter's ring
        // for the exact count. From the shift point all of it flashes with the frame.
        if (themed && bloomArt) {
            double t = link ? Math.max(0, Math.min(1, (rpm - BLOOM_FROM) / (double) (shiftRpm - BLOOM_FROM))) : 0;
            double k = over ? (blink ? 1.0 : 0.9) : 0.12 + 0.88 * t;
            int bw = Math.max(8, (int) Math.round(2 * BLOOM_RX * k));
            int bh = Math.max(8, (int) Math.round(2 * BLOOM_RY * k));
            int bi = add(IMAGE, BLOOM_CX - bw / 2, BLOOM_CY - bh / 2, bw, bh, WHITE, t > 0);
            size[bi] = SP_BLOOM;
            // the bright layer: starts later, catches up with the dark one at the shift point
            double th = link ? Math.max(0, Math.min(1, (rpm - BLOOM_HOT_FROM) / (double) (shiftRpm - BLOOM_HOT_FROM))) : 0;
            double kh = over ? (blink ? 1.0 : 0.9) : 0.15 + 0.85 * th;
            int hw = Math.max(8, (int) Math.round(2 * BLOOM_RX * kh));
            int hh = Math.max(8, (int) Math.round(2 * BLOOM_RY * kh));
            int hi = add(IMAGE, BLOOM_CX - hw / 2, BLOOM_CY - hh / 2, hw, hh, WHITE, th > 0);
            size[hi] = SP_BLOOM_HOT;
            // the sketch only with the bright layer, from BLOOM_HOT_FROM (user 2026-11-25: hidden at low rpm)
            int ei = add(IMAGE, BLOOM_CX - SP_EMBLEM_S / 2, BLOOM_CY - SP_EMBLEM_S / 2, SP_EMBLEM_S, SP_EMBLEM_S, WHITE, th > 0 || over);
            size[ei] = SP_EMBLEM;
        }
        // the theme's two chibis (pictures, so the Haldex page can leave them out)
        // (the two chibis left Sport 2026-11-25, user: too crowded; the pictures stay unused)
        int na = 10;
        double aTop = 160;
        double aBot = 193;
        for (int i = 0; i < na; i++) {
            int on = shiftRpm - (na - i) * 120;
            boolean lit = link && (over ? blink : rpm >= on);
            int c = !lit ? GLASS_OFF : over ? RED : i < 4 ? SAKURA : i < 7 ? 0xFF6A4AFF : RED;
            double a0 = aBot - (aBot - aTop) * i / na - 0.6;
            double a1 = aBot - (aBot - aTop) * (i + 1) / na + 0.6;
            ring(720, 309, 277, 293, a0, a1, GLASS_EDGE, true);
            ring(720, 309, 278, 292, a0 - 0.15, a1 + 0.15, c, true);
        }

        if (page == PAGE_HALDEX) {
            // 2026-11-25 (user's sketch): the car upright on the left with the torque share beside
            // its axles, then a column further right (the chibis are hidden on this page): the
            // lock as a segmented bar, target, steering, clutch. No gear here.
            drivetrain(70, 142, 160, 280, false);
            torqueSplit();
            lockBar(318, 146, 446);
            hxStats(318, 236, 446, 28, 60, false);
            boostSmall(416, link ? WHITE : GREY); // the same boost row as the live page (user 2026-11-25)
        } else {
        // Haldex lock: icon, strip with the target tick, percent.
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        // 2026-11-25 (user: too crowded): a shorter row centred in the space between the panel's
        // top line and the gear, and between the frame and the arc (16 px margins: 52..473)
        int hbw = 150;
        int hx = 142; // icon 32 + 12 + bar + 14 + "100%" ~80; 24 px right of centre, the portrait is left of it
        icon(hx, 132, 32, TeleIcons.AWD, ICONC, true);
        bar(hx + 44, 143, hbw, 10, act, 0, link ? HALDEX : GREY, -1);
        box(hx + 44 + hbw * tgt / 100 - 1, 136, 2, 24, WHITE, ok(CH_HX_TARGET));
        text(hx + 44 + hbw + 14 + 80, 128, 36, false, RIGHT, vc, ok(CH_HX_ACTUAL) ? percent(act) : "-", true);

        // Gear, red past the shift point, flashing at the limiter; temperatures beside it.
        // with the bloom (red behind it) the gear stays white past the shift point and flashes
        // dark navy at the limiter; red on red did not read
        boolean bloomOn = themed && bloomArt;
        int gc = !link ? GREY : limit ? (blink ? (bloomOn ? 0x0A1030FF : RED) : WHITE) : over && !bloomOn ? RED : WHITE;
        // 2026-11-25 (user): the gear a little smaller and right, the icons nearer their values
        gearGlyph(152, 192, 156, gc);
        text(152, 352, 22, true, CENTER, AMBER, "NO LINK", !link);
        box(244, 192, 2, 168, LINE, true); // ends above AUTOCROSS
        int row = 0;
        for (int t = 0; t < T_CH.length; t++) {
            if (T_CH[t] == CH_OIL)
                continue; // the stock Sport view shows oil temperature
            tempCell(t, 276, x1, 190 + row * 56, 30, 32, rpm); // 32 px, rows 56 apart
            row++;
        }

        // Preset and mode under the gear; Wi-Fi warning beside them while the link is slow or lost.
        // 2026-11-25 (user): smaller, centred under the gear
        text(152, 366, 22, false, CENTER, link ? PRESET : GREY, presetName(), true);
        text(152, 392, 13, true, CENTER, GREY, presetMode(), true);
        boolean slow = link && rateHz >= 0 && rateHz < 10;
        icon(222, 368, 20, TeleIcons.WIFI, AMBER, !link || slow);

        boostSmall(x1, vc);
        }

        // Redline warning frame on top: a thick red line along the panel outline with a glow,
        // flashing with the shift lights.
        for (int k = 0; k < SP_X.length; k++) {
            int j = (k + 1) % SP_X.length;
            thick(SP_X[k], SP_Y[k], SP_X[j], SP_Y[j], 18, 0xFF3B3050, warn);
            thick(SP_X[k], SP_Y[k], SP_X[j], SP_Y[j], 6, RED, warn);
        }
    }

    /** Sport's boost row along the panel's bottom: icon, strip from vacuum to boost with zero marked, value. */
    private void boostSmall(int x1, int vc) {
        int kpa = ok(CH_BOOST) ? r(val[CH_BOOST]) : 0;
        boolean boosting = link && kpa > 0;
        int zero = (0 - BOOST_MIN) * 100 / (BOOST_MAX - BOOST_MIN);
        int pos = clamp((kpa - BOOST_MIN) * 100 / (BOOST_MAX - BOOST_MIN), 0, 100);
        icon(118, 420, 24, TeleIcons.TURBO, boosting ? BOOST_FILL : ICONC, true);
        bar(150, 430, 160, 10, pos, zero, !link ? GREY : kpa >= 0 ? boostColour(kpa) : VAC_FILL, zero); // clear of "-0.1"
        text(x1 - 36, 416, 32, false, RIGHT, boosting ? WHITE : vc, ok(CH_BOOST) ? boost(kpa) : "-", true);
        text(x1, 430, 14, true, RIGHT, GREY, boostPsi ? "PSI" : "BAR", true);
    }

    // ---------------------------------------------------------------- Haldex page (tele19)
    // After the OpenHaldex iPhone app's drivetrain card (HaldexVisual.swift): the car from above
    // with an x-ray drivetrain, the rear lit cyan with the lock, torque dashes travelling rearward
    // along the prop shaft (faster with more lock), the coupling filling up, the front wheels turned
    // with the steering (15:1); a ring gauge for the actual lock with the target as a pink dot;
    // target, steering and Haldex clutch temperature beside it. Colours from the app's theme.
    private static final int HX_ICE = 0xE0EDFFFF;
    private static final int HX_CYAN = 0x66DBFFFF;
    private static final int HX_BLUE = 0x264DFFFF;
    private static final int HX_PINK = 0xFF548FFF;
    private static final int HX_NAVY = 0x05091CFF;
    private static final int HX_IDLE = 0x1A1F2AFF;

    /** Lock 0..100 as a cyan of rising strength (5 steps: few node colours on the car). */
    private static int rearColour(int lock) {
        if (lock <= 0)
            return 0x5A6270FF;
        int step = Math.min(4, (lock + 19) / 20);
        return mix(0x24607AFF, HX_CYAN, step / 4f);
    }

    /**
     * The car in the box (bx, by, bw, bh): sideways (nose right) when side, else upright (nose up).
     * Body outline, x-ray drivetrain, wheels, the coupling, torque dashes, a steering trace.
     */
    private void drivetrain(int bx, int by, int bw, int bh, boolean side) {
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int modeNow = ok(CH_MODE) ? r(val[CH_MODE]) : -1;
        boolean off = modeNow == 0;
        double steer = ok(CH_STEER) ? val[CH_STEER] / 1000.0 : 0;
        double road = Math.max(-35, Math.min(35, steer / 15.0)); // road-wheel angle, deg
        // the car's picture box: length along the car L, width W (the app's 660 x 1360 aspect)
        int pw = side ? CAR_SIDE_W : CAR_UP_W;
        int ph = side ? CAR_SIDE_H : CAR_UP_H;
        int px = bx + (bw - pw) / 2;
        int py = by + (bh - ph) / 2;
        double cL = side ? pw : ph;
        double cW = side ? ph : pw;
        carBox[0] = px;
        carBox[1] = py;
        carBox[2] = pw;
        carBox[3] = ph;
        carSide = side;
        carL = cL;
        carW = cW;
        if (carArt) {
            int i = add(IMAGE, px, py, pw, ph, WHITE, true);
            size[i] = side ? CAR_SIDE : CAR_UP;
        } else {
            car(0.08, 0.02, 0.92, 0.98, 0x0B1430FF);
            car(0.20, 0.33, 0.80, 0.47, 0x13204AFF);
        }
        double cx = 0.499;
        double fy = 0.232;
        double ry = 0.788;
        double cpl = ry - 0.07;
        boolean driving = link && act > 0 && !off;
        int rc = !link ? 0x5A6270FF : rearColour(act);
        int fc = !link ? 0x6B7385FF : off ? 0xB4BECCFF : HX_ICE;
        // 2026-11-25 (user: too crude): solid parts with a dark casing, a cyan glow along the
        // driven path (prop shaft, coupling, rear axle) while the coupling locks, tyres with a lit
        // tread, differentials as discs, the coupling as a stack of clutch plates lighting up with
        // the lock. The lock % is in the corner readout, not here.
        // wheels inside the body, outer edge flush with its side, as in the app (user 2026-11-25;
        // the body measured on the car picture: 0.093 .. 0.905 of its width at both axles)
        double tl = 0.150 * cL;
        double tw = 0.130 * cW;
        double fx = 0.340;
        double rx = 0.335;
        double ti = 0.5 * 0.130 + 0.012; // axles stop at the tyre's inner edge (fraction of the width)
        // glow under the driven path
        int glow = (rc & 0xFFFFFF00) | 0x48;
        if (driving) {
            seg0(cx, fy + 0.04, cx, ry, 14, glow);
            seg0(cx - rx + ti, ry, cx + rx - ti, ry, 16, glow);
        }
        // front axle and the prop shaft, rear axle
        seg(cx - fx + ti, fy, cx + fx - ti, fy, 6, fc);
        seg(cx, fy, cx, cpl, 4, driving ? rc : 0x6A7280FF);
        seg(cx, cpl, cx, ry, 5, rc);
        seg(cx - rx + ti, ry, cx + rx - ti, ry, 6, rc);
        // torque pills travelling rearward along the shaft, faster with more lock
        int nd = 5;
        double speed = 0.4 + 1.2 * act / 100.0;
        double ph0 = (now % 100000L) / 1000.0 * speed;
        for (int k = 0; k < nd; k++) {
            double f = (ph0 + k / (double) nd) % 1.0;
            int[] d = cpt(cx, fy + 0.05 + (cpl - 0.05 - fy - 0.05) * f);
            if (carSide)
                box(d[0] - 4, d[1] - 2, 8, 4, WHITE, driving);
            else
                box(d[0] - 2, d[1] - 4, 4, 8, WHITE, driving);
        }
        // differentials: a dark disc with a lit centre
        for (int k = 0; k < 2; k++) {
            int[] dp = cpt(cx, k == 0 ? fy : ry);
            icon(dp[0] - 11, dp[1] - 11, 22, TeleIcons.DOT, 0x05091CFF, true);
            icon(dp[0] - 7, dp[1] - 7, 14, TeleIcons.DOT, k == 0 ? fc : rc, true);
        }
        // the coupling: a dark housing and six clutch plates across the shaft, lit with the lock
        int[] c = cpt(cx, cpl);
        int hl = 40; // along the shaft
        int hw = 26; // across
        int plates = 6;
        int lit = link ? (act * plates + 99) / 100 : 0;
        if (carSide) {
            box(c[0] - hl / 2 - 2, c[1] - hw / 2 - 2, hl + 4, hw + 4, rc, true);
            box(c[0] - hl / 2, c[1] - hw / 2, hl, hw, HX_NAVY, true);
        } else {
            box(c[0] - hw / 2 - 2, c[1] - hl / 2 - 2, hw + 4, hl + 4, rc, true);
            box(c[0] - hw / 2, c[1] - hl / 2, hw, hl, HX_NAVY, true);
        }
        for (int k = 0; k < plates; k++) {
            int o = -hl / 2 + 5 + k * (hl - 10) / (plates - 1);
            boolean on = k < lit;
            int pc = on ? mix(HX_BLUE, HX_CYAN, (k + 1) / (float) plates) : 0x2A3346FF;
            if (carSide)
                box(c[0] - o - 1, c[1] - hw / 2 + 4, 3, hw - 8, pc, true);
            else
                box(c[0] - hw / 2 + 4, c[1] + o - 1, hw - 8, 3, pc, true);
        }
        // tyres: front turned with the steering
        tyre(cx - fx, fy, road, tl, tw, fc);
        tyre(cx + fx, fy, road, tl, tw, fc);
        tyre(cx - rx, ry, 0, tl, tw, rc);
        tyre(cx + rx, ry, 0, tl, tw, rc);
        String tag = !link ? "NOT CONNECTED" : off ? "STOCK CONTROL" : null;
        text(side ? bx + 8 : bx + bw / 2, side ? by + 2 : by + bh - 16, 13, true, side ? LEFT : CENTER,
            !link ? HX_PINK : HX_ICE, tag, tag != null);
    }

    private final int[] carBox = new int[4];
    private boolean carSide;
    private double carL;
    private double carW;

    /** A point given as fractions of the upright car picture (u across, v from the nose). */
    private int[] cpt(double u, double v) {
        if (carSide) // nose right: along the car = -x, the car's left side up
            return new int[] {rd(carBox[0] + carBox[2] - v * carL), rd(carBox[1] + u * carW)};
        return new int[] {rd(carBox[0] + u * carW), rd(carBox[1] + v * carL)};
    }

    private void car(double u0, double v0, double u1, double v1, int c) {
        int[] p = cpt(u0, v0);
        int[] q = cpt(u1, v1);
        int x = Math.min(p[0], q[0]);
        int y = Math.min(p[1], q[1]);
        box(x - 1, y - 1, Math.abs(q[0] - p[0]) + 2, Math.abs(q[1] - p[1]) + 2, 0x2C3A52FF, true);
        box(x, y, Math.abs(q[0] - p[0]), Math.abs(q[1] - p[1]), c, true);
    }

    /** A segment without the dark casing (the glow). */
    private void seg0(double u0, double v0, double u1, double v1, int t, int c) {
        int[] p = cpt(u0, v0);
        int[] q = cpt(u1, v1);
        thick(p[0], p[1], q[0], q[1], t, c, true);
    }

    private void seg(double u0, double v0, double u1, double v1, int t, int c) {
        int[] p = cpt(u0, v0);
        int[] q = cpt(u1, v1);
        thick(p[0], p[1], q[0], q[1], t + 4, 0x0000008C, true);
        thick(p[0], p[1], q[0], q[1], t, c, true);
    }

    /**
     * A tyre centred at (u, v), turned by deg (clockwise seen from above), as in the app: the TYRE
     * icon (see-through rounded box with a solid edge in c), tl along the car, tw across, turned
     * about its top-left corner so its centre stays on the wheel.
     */
    private void tyre(double u, double v, double deg, double tl, double tw, int c) {
        int[] p = cpt(u, v);
        double ang = carSide ? deg : deg - 90; // the tyre runs along the car
        double rad = Math.toRadians(ang);
        double ax = Math.cos(rad);
        double ay = Math.sin(rad);
        int w = (int) Math.round(tl);
        int h = (int) Math.round(tw);
        // top-left = centre - R(ang) * (w/2, h/2)
        double tx = p[0] - (ax * w / 2 - ay * h / 2);
        double ty = p[1] - (ay * w / 2 + ax * h / 2);
        int i = add(ICON, rd(tx), rd(ty), w, h, c, true);
        size[i] = TeleIcons.TYRE;
        rot[i] = (float) ang;
    }

    /** The lock ring: 270 degrees open at the bottom, 30 segments, the target as a pink dot. */
    private void lockRing(int cx, int cy, int r, int thick) {
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        int segs = 30;
        for (int i = 0; i < segs; i++) {
            double a0 = 225 - 270.0 * i / segs - 0.8;
            double a1 = 225 - 270.0 * (i + 1) / segs + 0.8;
            boolean on = link && act > 0 && i * 100 / segs < act;
            ring(cx, cy, r - thick, r, a0, a1, on ? mix(HX_BLUE, HX_CYAN, i / (float) segs) : HX_IDLE, true);
        }
        for (int i = 0; i <= 10; i++) {
            double a = 225 - 27.0 * i;
            ring(cx, cy, r - thick - (i % 5 == 0 ? 9 : 5), r - thick - 2, a + 0.6, a - 0.6,
                i % 5 == 0 ? 0xFFFFFF80 : 0xFFFFFF33, true);
        }
        // target dot: one fixed dot per 5 %, the one at the target shows
        for (int i = 0; i <= 20; i++) {
            double a = Math.toRadians(225 - 270.0 * i / 20);
            int dx = (int) Math.round(cx + Math.cos(a) * (r - thick / 2.0));
            int dy = (int) Math.round(cy - Math.sin(a) * (r - thick / 2.0));
            icon(dx - 7, dy - 7, 14, TeleIcons.DOT, HX_PINK, link && ok(CH_HX_TARGET) && i == (tgt + 2) / 5);
        }
        text(cx, cy - r / 3 - 4, r * 3 / 4, true, CENTER, link ? WHITE : GREY, link ? Integer.toString(act) : "-", true);
        text(cx, cy + r / 2 + 8, 12, true, CENTER, GREY, "% LOCK", true);
    }

    /** Ring segment between radii from angle a0 to a1 (degrees, counter-clockwise from the right). */
    private void ring(int cx, int cy, double rIn, double rOut, double a0, double a1, int c, boolean v) {
        double r0 = Math.toRadians(a0);
        double r1 = Math.toRadians(a1);
        quad(cx + rd(Math.cos(r0) * rOut), cy - rd(Math.sin(r0) * rOut), cx + rd(Math.cos(r1) * rOut),
            cy - rd(Math.sin(r1) * rOut), cx + rd(Math.cos(r1) * rIn), cy - rd(Math.sin(r1) * rIn),
            cx + rd(Math.cos(r0) * rIn), cy - rd(Math.sin(r0) * rIn), c, v);
    }

    /**
     * [LOCK,] TARGET, STEERING, CLUTCH: label over value (vs px), rows rg apart, from x0 to x1 at
     * y0. LOCK is the actual lock (Classic, user 2026-11-25); Sport shows it in its ring.
     */
    private void hxStats(int x0, int y0, int x1, int vs, int rg, boolean lockRow) {
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        int vc = link ? WHITE : GREY;
        int ls = vs >= 26 ? 13 : 12;
        int vy = ls + 4;
        int y = y0;
        if (lockRow) {
            text(x0, y, ls, true, LEFT, GREY, "LOCK", true);
            box(x0, y + vy + vs / 2 - 3, 7, 7, HX_CYAN, link);
            text(x0 + 14, y + vy, vs, true, LEFT, link && act > 0 ? HX_CYAN : vc,
                ok(CH_HX_ACTUAL) ? percent(act) : "-", true);
            y += rg;
        }
        text(x0, y, ls, true, LEFT, GREY, "TARGET", true);
        box(x0, y + vy + vs / 2 - 3, 7, 7, HX_PINK, link);
        text(x0 + 14, y + vy, vs, true, LEFT, vc, ok(CH_HX_TARGET) ? percent(tgt) : "-", true);
        y += rg;
        text(x0, y, ls, true, LEFT, GREY, "STEERING", true);
        String st = "-";
        if (ok(CH_STEER)) {
            int d = r(val[CH_STEER]);
            st = new StringBuffer(d > 0 ? "+" : "").append(d).append('°').toString();
        }
        text(x0, y + vy, vs, true, LEFT, vc, st, true);
        y += rg;
        int ct = ok(CH_CLUTCH) ? r(val[CH_CLUTCH]) : -1;
        boolean hot = ct >= 90;
        text(x0, y, ls, true, LEFT, GREY, "CLUTCH", true);
        text(x0, y + vy, vs, true, LEFT, hot ? HX_PINK : vc, ct >= 0 ? temp(ct) : "-", true);
        // (the clutch temperature bar under it went 2026-11-25, user)
    }

    /** Sport's lock (user's sketch, 2026-11-25): LOCK, the value, a 10-segment bar with the target tick. */
    private void lockBar(int x0, int y0, int x1) {
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        int tgt = ok(CH_HX_TARGET) ? clamp(r(val[CH_HX_TARGET]), 0, 100) : 0;
        text(x0, y0, 12, true, LEFT, GREY, "LOCK", true);
        text(x1, y0 - 6, 30, true, RIGHT, link && act > 0 ? HX_CYAN : link ? WHITE : GREY,
            ok(CH_HX_ACTUAL) ? percent(act) : "-", true);
        int segs = 10;
        int gap = 3;
        int sw = (x1 - x0 - (segs - 1) * gap) / segs;
        int by = y0 + 36;
        for (int k = 0; k < segs; k++) {
            boolean on = link && act > 0 && k * 100 / segs < act;
            box(x0 + k * (sw + gap), by, sw, 14, on ? mix(HX_BLUE, HX_CYAN, (k + 1) / (float) segs) : HX_IDLE, true);
        }
        int ti = Math.min(segs - 1, tgt * segs / 100);
        box(x0 + ti * (sw + gap) + sw / 2 - 1, by - 4, 3, 22, HX_PINK, link && ok(CH_HX_TARGET));
    }

    /**
     * Classic's torque share beside the sideways car: the rear share at its tail, the front share
     * at its nose. Not sent by the board: the nominal rear share is min(lock, 50) (board session
     * 2026-11-25: lock is the coupling target, 5050 = 100, 6040 = 40, 7030 = 30, ..., FWD = 0).
     */
    private void torqueSplit() {
        int act = ok(CH_HX_ACTUAL) ? clamp(r(val[CH_HX_ACTUAL]), 0, 100) : 0;
        boolean have = link && ok(CH_HX_ACTUAL);
        int rear = Math.min(act, 50);
        int front = 100 - rear;
        int rc = !have ? GREY : rear > 0 ? rearColour(act) : GREY;
        int rx, ry, fx, fy, ns, cs;
        if (carSide) { // Classic: at the tail and the nose
            int midY = carBox[1] + carBox[3] / 2;
            rx = carBox[0] - 38;
            fx = carBox[0] + carBox[2] + 40;
            ry = midY;
            fy = midY;
            ns = 42;
            cs = 12;
        } else { // Sport: right of the car, beside each axle
            rx = carBox[0] + carBox[2] + 34;
            fx = rx;
            fy = cpt(0.5, 0.232)[1];
            ry = cpt(0.5, 0.788)[1];
            ns = 40;
            cs = 11;
        }
        text(rx, ry - ns + 4, ns, true, CENTER, have ? (rear > 0 ? HX_CYAN : WHITE) : GREY,
            have ? Integer.toString(rear) : "-", true);
        text(rx + 1, ry + 8, cs, true, CENTER, GREY, "% REAR", true);
        text(fx, fy - ns + 4, ns, true, CENTER, have ? HX_ICE : GREY, have ? Integer.toString(front) : "-", true);
        text(fx, fy + 8, cs, true, CENTER, GREY, "% FRONT", true);
        box(rx - 16, ry + 26, 32, 3, rc, have && rear > 0);
    }

    /** A line from (ax,ay) to (bx,by), w px thick, as a quad (a little longer to close joints). */
    private void thick(int ax, int ay, int bx, int by, int w, int c, boolean v) {
        double dx = bx - ax;
        double dy = by - ay;
        double l = Math.max(1, Math.sqrt(dx * dx + dy * dy));
        double ux = dx / l;
        double uy = dy / l;
        double nx = -uy * w / 2;
        double ny = ux * w / 2;
        double e = w / 2.0;
        double sx = ax - ux * e;
        double sy = ay - uy * e;
        double ex = bx + ux * e;
        double ey = by + uy * e;
        quad(rd(sx + nx), rd(sy + ny), rd(ex + nx), rd(ey + ny), rd(ex - nx), rd(ey - ny), rd(sx - nx), rd(sy - ny), c, v);
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
        text(x1, yy + (is - vs) / 2, vs, false, RIGHT, c, v, true);
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
    static double tpLen() {
        double l = 0;
        for (int i = 1; i < TP_X.length; i++)
            l += Math.sqrt(sq(TP_X[i] - TP_X[i - 1]) + sq(TP_Y[i] - TP_Y[i - 1]));
        return l;
    }

    static void tp(double s, double[] o) {
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

    /** A hazard stripe across the strip: from s a..b at the inner edge, slanted by sl at the outer. */
    private void stripe(double a, double b, double sl, int c) {
        double ha = half(a);
        double hb = half(b);
        tp(a, pa);
        double ax = pa[0], ay = pa[1], nx = pa[2], ny = pa[3];
        tp(b, pb);
        double bx = pb[0], by = pb[1];
        double ux = (bx - ax) / Math.max(0.001, b - a);
        double uy = (by - ay) / Math.max(0.001, b - a);
        quad(rd(ax - nx * ha), rd(ay - ny * ha), rd(bx - nx * hb), rd(by - ny * hb),
            rd(bx + ux * sl + nx * hb), rd(by + uy * sl + ny * hb), rd(ax + ux * sl + nx * ha), rd(ay + uy * sl + ny * ha),
            c, true);
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

    /** A mode's short name (channel 12 numbering), "?" when out of range. */
    static String modeName(int m) {
        return mode(m);
    }

    /**
     * The Haldex control pop-up (TeleUi, tele18): the view dimmed, a glass card with three rows
     * (PRESET, MODE, LOCK), the focused row lit; while editing, the value between arrows in sakura;
     * the board's answer or a hint along the bottom. Classic: centred on the gear; Sport: over the
     * panel.
     */
    private void popup(boolean large) {
        int cx = large ? 720 : 269;
        int x0 = large ? 470 : 98; // Sport: clear of the frame's curve (ClipCheck)
        int x1 = large ? 970 : 440;
        int y0 = large ? 112 : 116; // Sport: top corner clear of the bezel curve (the Sport guide)
        int rowH = large ? 74 : 76; // Sport: card ends above the frame's bottom-left curve (ClipCheck)
        box(0, 0, SCREEN_W, SCREEN_H, 0x000000B0, true); // dim the view
        box(x0, y0, x1 - x0, 46 + 3 * rowH + 40, 0x08102AF2, true);
        box(x0, y0, x1 - x0, 2, 0x2FD4FFFF, true);
        box(x0, y0 + 46 + 3 * rowH + 38, x1 - x0, 2, 0x2FD4FFFF, true);
        box(x0, y0, 2, 46 + 3 * rowH + 40, 0x3E8BFFFF, true);
        box(x1 - 2, y0, 2, 46 + 3 * rowH + 40, 0x3E8BFFFF, true);
        box(x0 + 2, y0 + 2, 60, 4, 0xFF5FA8FF, true); // the characters' colours as accents
        box(x1 - 62, y0 + 2, 60, 4, 0x2FD4FFFF, true);
        text(x0 + 18, y0 + 14, 16, true, LEFT, GREY, "OPENHALDEX", true);
        text(x1 - 18, y0 + 16, 13, false, RIGHT, link ? GREY : AMBER, link ? "LINKED" : "NO LINK", true);

        int active = ok(CH_PRESET) ? r(val[CH_PRESET]) : -1;
        int modeNow = ok(CH_MODE) ? r(val[CH_MODE]) : -1;
        int lockNow = ok(CH_LOCK_SET) ? r(val[CH_LOCK_SET]) : 255;
        String activeName = "NONE";
        for (int i = 0; i < catN; i++)
            if (catId[i] == active)
                activeName = upper(catName[i]);
        String[] label = {"PRESET", "MODE", "LOCK"};
        for (int row = 0; row < 3; row++) {
            int ry = y0 + 46 + row * rowH;
            boolean focus = uiCursor == row;
            boolean ed = focus && uiEdit;
            box(x0 + 10, ry + 4, x1 - x0 - 20, rowH - 8, ed ? 0x3A1430FF : CURSOR, focus);
            box(x0 + 10, ry + 4, 4, rowH - 8, ed ? SAKURA : 0x2FD4FFFF, focus);
            text(x0 + 24, ry + 10, 13, true, LEFT, focus ? WHITE : GREY, label[row], true);
            String v;
            if (row == 0)
                v = ed ? (uiPreset < catN ? upper(catName[uiPreset]) : "-") : link ? activeName : "-";
            else if (row == 1)
                v = ed ? mode(uiMode) : modeNow >= 0 ? mode(modeNow) : "-";
            else
                v = ed ? percent(uiLock) : lockNow <= 100 ? percent(lockNow) : "OFF";
            int vs = large ? 32 : 28;
            int vy = ry + (row == 2 ? 14 : 22);
            text(cx, vy, vs, true, CENTER, ed ? SAKURA : link ? WHITE : GREY, v, true);
            text(x0 + 40, vy + 2, vs - 4, true, CENTER, SAKURA, "<", ed);
            text(x1 - 40, vy + 2, vs - 4, true, CENTER, SAKURA, ">", ed);
            if (row == 2) {
                int lv = ed ? uiLock : lockNow <= 100 ? lockNow : 0;
                int bx = cx - (large ? 140 : 110);
                int bw = large ? 280 : 220;
                box(bx, ry + rowH - 22, bw, 10, DIM, true);
                box(bx, ry + rowH - 22, bw * lv / 100, 10, ed ? SAKURA : HALDEX, lv > 0);
                for (int t = 1; t < 10; t++)
                    box(bx + bw * t / 10, ry + rowH - 24, 1, 14, 0x08102AFF, true);
            }
        }
        int hy = y0 + 46 + 3 * rowH + 10;
        String hint = uiEdit ? "ROLL: CHANGE   PRESS: SEND   BACK: CANCEL" : "ROLL: ROW   PRESS: EDIT   BACK: CLOSE";
        text(cx, hy, large ? 16 : 13, uiMsg != null, CENTER, uiMsg != null ? uiMsgRgba : GREY,
            uiMsg != null ? uiMsg : hint, true);
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

    /** True once a build asked for more than MAX slots (the extra ones are dropped; TeleView logs it). */
    public boolean overflow;

    private int add(int k, int x0, int y0, int w0, int h0, int c, boolean v) {
        if (count >= MAX) {
            overflow = true;
            v = false;
            count = MAX - 1; // reuse the last slot, hidden: never write past the arrays
        }
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

    /** A box turned by ang degrees about (x0, y0), the middle of its start edge; w along the turn. */
    private void rbox(double x0, double y0, double w0, double h0, double ang, int c, boolean v) {
        int i = add(BOX, rd(x0), rd(y0), (int) Math.round(w0), (int) Math.round(h0), c, v && w0 >= 0.5);
        rot[i] = (float) ang;
    }

    /** The gear as one sharp glyph (TeleIcons), centred at cx, top at y0, gh high. */
    private void gearGlyph(int cx, int y0, int gh, int c) {
        String g = gear();
        int id = TeleIcons.glyph(g.length() > 0 ? g.charAt(0) : '-');
        if (id < 0)
            id = TeleIcons.glyph('-');
        int gw = Math.round(gh * TeleIcons.GLYPH_ASPECT);
        int i = add(ICON, cx - gw / 2, y0, gw, gh, c, true);
        size[i] = id;
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

    /**
     * Turns a slope text and its shadow (the slot before it, when shadows are on) to deg.
     */
    private void turn(int i, float deg) {
        rot[i] = deg;
        if (shadows && i > 0 && kind[i - 1] == TEXT && rgba[i - 1] == TEXT_SHADOW)
            rot[i - 1] = deg;
    }

    /** Shadow offset for text of size sz: 1 px small, 2 px from 18 px, 3 px from 30 px. */
    private static int shadowOffset(int sz) {
        return sz >= 30 ? 3 : sz >= 18 ? 2 : 1;
    }

    private void text(int x0, int y0, int sz, boolean b, int al, int c, String s, boolean v) {
        if (shadows) {
            // 2026-11-25 (user: not very 3d): a soft dark copy under every text, down and right
            int d = shadowOffset(sz);
            int si = add(TEXT, x0 + d, y0 + d, 0, sz, TEXT_SHADOW, v && s != null);
            size[si] = sz;
            bold[si] = b;
            align[si] = al;
            text[si] = s;
        }
        int i = add(TEXT, x0, y0, 0, sz, c, v && s != null);
        size[i] = sz;
        bold[i] = b;
        align[i] = al;
        text[i] = s;
    }
}
