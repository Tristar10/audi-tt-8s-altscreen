package local.tt;

/**
 * Small one-colour icons for the autocross view, drawn from shapes in code (no image files), so
 * the car and the PC preview get the same pixels. Each icon is a list of shapes on a 0..100 grid;
 * later shapes add to or cut from earlier ones. Rendered once per size and colour, 4x4 supersampled.
 *
 * Java 1.4 library only (HMI-patching.md).
 */
public final class TeleIcons {
    public static final int COOLANT = 0;
    public static final int OIL = 1;
    public static final int IAT = 2;
    public static final int DSG = 3;
    public static final int CLUTCH = 4;
    public static final int AWD = 5;
    public static final int TACH = 6;
    public static final int WIFI = 7;
    public static final int PEDAL = 8;
    public static final int TURBO = 9;
    public static final int COUNT = 10;

    // Shape ops. CAP x0 y0 x1 y1 r (thick line, round ends) | CIRC x y r | RECT x0 y0 x1 y1 |
    // POLY n x0 y0 ... | CUT_CIRC x y r | CUT_RECT x0 y0 x1 y1 | CUT_POLY n x0 y0 ...
    private static final float CAP = 0;
    private static final float CIRC = 1;
    private static final float RECT = 2;
    private static final float POLY = 3;
    private static final float CUT_CIRC = 4;
    private static final float CUT_RECT = 5;
    private static final float CUT_POLY = 6;

    private static final float[][] ICONS = {
        // COOLANT: thermometer with scale marks, waves either side of the bulb
        {CAP, 46, 10, 46, 56, 7, CIRC, 46, 66, 13,
            CAP, 58, 20, 70, 20, 3.5f, CAP, 58, 32, 70, 32, 3.5f, CAP, 58, 44, 70, 44, 3.5f,
            CAP, 2, 90, 10, 84, 3.5f, CAP, 10, 84, 18, 90, 3.5f, CAP, 18, 90, 26, 96, 3.5f, CAP, 26, 96, 32, 90, 3.5f,
            CAP, 62, 90, 70, 84, 3.5f, CAP, 70, 84, 78, 90, 3.5f, CAP, 78, 90, 86, 96, 3.5f, CAP, 86, 96, 96, 90, 3.5f},
        // OIL: oil can with spout and a drop
        {RECT, 16, 46, 64, 78, RECT, 32, 38, 46, 46, POLY, 4, 62, 50, 94, 36, 97, 42, 62, 66,
            CIRC, 16, 60, 14, CUT_CIRC, 16, 60, 7, CUT_RECT, 16, 40, 30, 84, RECT, 16, 46, 30, 78,
            POLY, 3, 84, 70, 96, 70, 90, 54, CIRC, 90, 72, 6},
        // IAT: three air arrows into a thermometer
        {CAP, 74, 10, 74, 56, 7, CIRC, 74, 66, 13,
            CAP, 4, 26, 42, 26, 4, POLY, 3, 42, 17, 56, 26, 42, 35,
            CAP, 4, 48, 38, 48, 4, POLY, 3, 38, 39, 52, 48, 38, 57,
            CAP, 4, 70, 40, 70, 4, POLY, 3, 40, 61, 54, 70, 40, 79},
        // DSG: gear lever H-pattern
        {CAP, 20, 16, 20, 84, 5, CAP, 50, 16, 50, 84, 5, CAP, 80, 16, 80, 84, 5, CAP, 20, 50, 80, 50, 5,
            CIRC, 20, 14, 9, CIRC, 50, 14, 9, CIRC, 80, 14, 9, CIRC, 20, 86, 9, CIRC, 50, 86, 9, CIRC, 80, 86, 9},
        // CLUTCH: clutch plates on a shaft, with a thermometer (Haldex coupling temperature)
        {CAP, 2, 50, 58, 50, 3.5f, RECT, 10, 22, 17, 78, RECT, 22, 22, 29, 78, RECT, 34, 22, 41, 78,
            RECT, 46, 22, 53, 78, CAP, 80, 10, 80, 56, 7, CIRC, 80, 66, 13},
        // AWD: four wheels, two axles, prop shaft, coupling at the rear
        {RECT, 6, 8, 22, 36, RECT, 78, 8, 94, 36, RECT, 6, 64, 22, 92, RECT, 78, 64, 94, 92,
            CAP, 22, 22, 78, 22, 4, CAP, 22, 78, 78, 78, 4, CAP, 50, 22, 50, 78, 4, RECT, 38, 54, 62, 72},
        // TACH: dial arc with a needle
        {CIRC, 50, 58, 46, CUT_CIRC, 50, 58, 36, CUT_RECT, 0, 72, 100, 100,
            CAP, 50, 58, 74, 28, 5, CIRC, 50, 58, 9},
        // WIFI: three arcs over a dot
        {CIRC, 50, 84, 78, CUT_CIRC, 50, 84, 67, CIRC, 50, 84, 55, CUT_CIRC, 50, 84, 44,
            CIRC, 50, 84, 32, CUT_CIRC, 50, 84, 21,
            CUT_POLY, 4, 50, 84, 0, 34, 0, 100, 50, 100, CUT_POLY, 4, 50, 84, 100, 34, 100, 100, 50, 100,
            CIRC, 50, 84, 10},
        // PEDAL: slanted pedal pad with grooves, on its arm
        {POLY, 4, 34, 8, 84, 8, 74, 66, 24, 66, CUT_POLY, 4, 33, 20, 80, 20, 79, 25, 32, 25,
            CUT_POLY, 4, 31, 33, 78, 33, 77, 38, 30, 38, CUT_POLY, 4, 29, 46, 76, 46, 75, 51, 28, 51,
            CAP, 48, 66, 38, 94, 6},
        // TURBO: compressor housing (snail) with its outlet
        {CIRC, 44, 58, 38, CUT_CIRC, 44, 58, 27, CIRC, 44, 58, 15, CUT_CIRC, 44, 58, 6,
            RECT, 44, 16, 96, 38, CUT_RECT, 54, 24, 96, 31, CUT_CIRC, 44, 58, 27, CIRC, 44, 58, 15,
            CUT_CIRC, 44, 58, 6},
    };

    private TeleIcons() {
    }

    /** Icon pixels, ARGB, px * px, in colour rgb (0xRRGGBB). */
    public static int[] argb(int id, int px, int rgb) {
        float[] ops = ICONS[id];
        int[] out = new int[px * px];
        int ss = 4;
        for (int py = 0; py < px; py++) {
            for (int pxl = 0; pxl < px; pxl++) {
                int hits = 0;
                for (int sy = 0; sy < ss; sy++) {
                    for (int sx = 0; sx < ss; sx++) {
                        float u = (pxl + (sx + 0.5f) / ss) * 100f / px;
                        float v = (py + (sy + 0.5f) / ss) * 100f / px;
                        if (inside(ops, u, v))
                            hits++;
                    }
                }
                int a = hits * 255 / (ss * ss);
                out[py * px + pxl] = (a << 24) | (rgb & 0xFFFFFF);
            }
        }
        return out;
    }

    private static boolean inside(float[] o, float u, float v) {
        boolean in = false;
        int i = 0;
        while (i < o.length) {
            int op = (int) o[i];
            if (op == (int) CAP) {
                if (capHit(u, v, o[i + 1], o[i + 2], o[i + 3], o[i + 4], o[i + 5]))
                    in = true;
                i += 6;
            } else if (op == (int) CIRC || op == (int) CUT_CIRC) {
                float dx = u - o[i + 1];
                float dy = v - o[i + 2];
                if (dx * dx + dy * dy <= o[i + 3] * o[i + 3])
                    in = op == (int) CIRC;
                i += 4;
            } else if (op == (int) RECT || op == (int) CUT_RECT) {
                if (u >= o[i + 1] && u <= o[i + 3] && v >= o[i + 2] && v <= o[i + 4])
                    in = op == (int) RECT;
                i += 5;
            } else {
                int n = (int) o[i + 1];
                if (polyHit(o, i + 2, n, u, v))
                    in = op == (int) POLY;
                i += 2 + 2 * n;
            }
        }
        return in;
    }

    private static boolean capHit(float u, float v, float x0, float y0, float x1, float y1, float r) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float len2 = dx * dx + dy * dy;
        float t = len2 == 0 ? 0 : ((u - x0) * dx + (v - y0) * dy) / len2;
        if (t < 0)
            t = 0;
        else if (t > 1)
            t = 1;
        float ex = u - (x0 + t * dx);
        float ey = v - (y0 + t * dy);
        return ex * ex + ey * ey <= r * r;
    }

    private static boolean polyHit(float[] o, int at, int n, float u, float v) {
        boolean c = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = o[at + 2 * i];
            float yi = o[at + 2 * i + 1];
            float xj = o[at + 2 * j];
            float yj = o[at + 2 * j + 1];
            if ((yi > v) != (yj > v) && u < (xj - xi) * (v - yi) / (yj - yi) + xi)
                c = !c;
        }
        return c;
    }
}
