package local.tt;

/**
 * One finished layout, copied off the tick thread so the HMI thread can apply it while the next one
 * is being built. A plain class (no synthetic accessors on the car's J9).
 */
final class TeleFrame {
    boolean show;
    int key;
    int count;
    int[] kind;
    int[] x;
    int[] y;
    int[] w;
    int[] h;
    int[] rgba;
    int[] size;
    int[] align;
    boolean[] bold;
    boolean[] vis;
    String[] text;
    float[] rot;
    int[] qx;
    int[] qy;
    /** tele8 probe: the painted strip with its lit picture cut at pCut (wiki Telemetry.md). */
    boolean probe;
    int pMode;
    int pRpm;
    int pCut;
    boolean pLit;

    static TeleFrame hidden() {
        TeleFrame f = new TeleFrame();
        f.show = false;
        return f;
    }

    static TeleFrame of(TeleLayout t, int key) {
        TeleFrame f = new TeleFrame();
        int n = t.count;
        f.show = true;
        f.key = key;
        f.count = n;
        f.kind = copy(t.kind, n);
        f.x = copy(t.x, n);
        f.y = copy(t.y, n);
        f.w = copy(t.w, n);
        f.h = copy(t.h, n);
        f.rgba = copy(t.rgba, n);
        f.size = copy(t.size, n);
        f.align = copy(t.align, n);
        f.bold = new boolean[n];
        f.vis = new boolean[n];
        f.text = new String[n];
        f.rot = new float[n];
        System.arraycopy(t.bold, 0, f.bold, 0, n);
        System.arraycopy(t.vis, 0, f.vis, 0, n);
        System.arraycopy(t.text, 0, f.text, 0, n);
        System.arraycopy(t.rot, 0, f.rot, 0, n);
        f.qx = copy(t.qx, n * 4);
        f.qy = copy(t.qy, n * 4);
        return f;
    }

    static TeleFrame probe(int mode, int rpm, int cut, boolean lit) {
        TeleFrame f = new TeleFrame();
        f.show = true;
        f.probe = true;
        f.key = -2;
        f.pMode = mode;
        f.pRpm = rpm;
        f.pCut = cut;
        f.pLit = lit;
        return f;
    }

    /** Same picture as another frame (so nothing needs to be sent to the HMI thread). */
    boolean sameAs(TeleFrame o) {
        if (o == null || o.show != show || o.key != key || o.count != count || o.probe != probe) {
            return false;
        }
        if (probe) {
            return o.pMode == pMode && o.pRpm == pRpm && o.pCut == pCut && o.pLit == pLit;
        }
        if (!show) {
            return true;
        }
        for (int i = 0; i < count; i++) {
            if (o.vis[i] != vis[i] || o.rgba[i] != rgba[i] || o.w[i] != w[i] || o.x[i] != x[i] || o.y[i] != y[i]) {
                return false;
            }
            String a = o.text[i];
            String b = text[i];
            if (a == null ? b != null : !a.equals(b)) {
                return false;
            }
        }
        return true;
    }

    private static int[] copy(int[] a, int n) {
        int[] b = new int[n];
        System.arraycopy(a, 0, b, 0, n);
        return b;
    }
}
