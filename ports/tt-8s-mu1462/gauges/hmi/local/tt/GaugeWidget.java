package local.tt;

import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3D;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DImage;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DText;

/**
 * SKETCH. One gauge on a page: what it shows (channel, range in SI units), where (screen pixels),
 * and its EAL nodes once built. The node fields are touched only on the HMI thread.
 */
final class GaugeWidget {
    static final int DIAL = 1; // round dial, rotating needle, value in the middle
    static final int BAR = 2;  // horizontal bar, label left, value right
    static final int NUM = 3;  // label and a big number

    final int type;
    final int ch;
    final String label;
    final float min;   // SI
    final float max;   // SI
    final float warn;  // SI, value >= warn flashes red; Float.MAX_VALUE for none
    final int x;
    final int y;
    final int w;
    final int h;

    // HMI thread only
    IWrappedNode3D needle;
    IWrappedNode3DImage fill;
    IWrappedNode3DImage fillWarn;
    IWrappedNode3DText value;
    IWrappedNode3DText valueShadow;
    String shownText;
    int shownColor;
    float shownAngle = Float.NaN;
    float shownFrac = -1f;
    boolean shownWarn;

    GaugeWidget(int type, int ch, String label, float min, float max, float warn, int x, int y, int w, int h) {
        this.type = type;
        this.ch = ch;
        this.label = label;
        this.min = min;
        this.max = max;
        this.warn = warn;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    /** Round dial centred at cx, cy. */
    static GaugeWidget dial(int ch, String label, float min, float max, float warn, int cx, int cy, int size) {
        return new GaugeWidget(DIAL, ch, label, min, max, warn, cx - size / 2, cy - size / 2, size, size);
    }

    /** Bar with its track at x, y (w x h); label above left, value above right. */
    static GaugeWidget bar(int ch, String label, float min, float max, float warn, int x, int y, int w, int h) {
        return new GaugeWidget(BAR, ch, label, min, max, warn, x, y, w, h);
    }

    /** Label over a big number, left edge x, top y, number size h. */
    static GaugeWidget num(int ch, String label, float warn, int x, int y, int w, int h) {
        return new GaugeWidget(NUM, ch, label, 0f, 1f, warn, x, y, w, h);
    }

    void forget() {
        needle = null;
        fill = null;
        fillWarn = null;
        value = null;
        valueShadow = null;
        shownText = null;
        shownColor = 0;
        shownAngle = Float.NaN;
        shownFrac = -1f;
        shownWarn = false;
    }
}
