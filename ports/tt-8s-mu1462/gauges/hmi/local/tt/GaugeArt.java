package local.tt;

import java.util.Hashtable;

/**
 * SKETCH. Gauge pictures in plain Java (no AWT on the car), straight (not premultiplied) RGBA8888
 * like PanelArt.rgba(). Drawn once per page/view change, never per frame: per frame only node
 * rotations, scales and texts change (see Gauges.update).
 *
 * Angles: clockwise from straight up. A dial sweeps SWEEP degrees, centred on up, so value 0..1
 * maps to -SWEEP/2 .. +SWEEP/2.
 */
final class GaugeArt {
    static final float SWEEP = 270f;
    private static final Hashtable CACHE = new Hashtable();

    private GaugeArt() {
    }

    /** Value fraction 0..1 to needle angle in degrees (clockwise from up). */
    static float angle(float frac) {
        if (frac < 0f) {
            frac = 0f;
        } else if (frac > 1f) {
            frac = 1f;
        }
        return -SWEEP / 2f + frac * SWEEP;
    }

    /**
     * Dial face, size x size: faint dark disc (so it reads over a map), outer arc, ticks every
     * 10 % (major) and 2.5 % (minor), red arc from redFrom (0..1, >= 1 for none) to the end.
     */
    static byte[] dial(int size, float redFrom) {
        String k = new StringBuffer("dial ").append(size).append(' ').append((int) (redFrom * 1000)).toString();
        synchronized (CACHE) {
            byte[] hit = (byte[]) CACHE.get(k);
            if (hit != null) {
                return hit;
            }
        }
        int[] px = new int[size * size];
        float c = (size - 1) / 2f;
        float rOut = size / 2f - 3f;
        float rArc = rOut - 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - c;
                float dy = y - c;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                if (r > rOut + 1f) {
                    continue;
                }
                float a = (float) Math.toDegrees(Math.atan2(dx, -dy)); // clockwise from up
                float frac = (a + SWEEP / 2f) / SWEEP;
                boolean inSweep = frac >= 0f && frac <= 1f;
                // background disc
                int col = blend(0, 0xB0101418, cover(rOut - r));
                if (inSweep) {
                    // thin outer arc
                    col = blend(col, 0xFFC8CCD0, cover(1.5f - Math.abs(r - rArc)));
                    // red zone, a thicker arc just inside
                    if (frac >= redFrom) {
                        col = blend(col, 0xFFE0302A, cover(4f - Math.abs(r - (rArc - 6f))));
                    }
                    // ticks: distance along the arc to the nearest tick, at radius r
                    float minor = frac * 40f;
                    int nearest = Math.round(minor);
                    boolean major = nearest % 4 == 0;
                    float len = major ? 20f : 10f;
                    float half = major ? 1.6f : 0.8f;
                    float along = Math.abs(minor - nearest) / 40f * (float) Math.toRadians(SWEEP) * r;
                    if (r <= rArc && r >= rArc - len) {
                        col = blend(col, major ? 0xFFFFFFFF : 0xFF9AA0A6, cover(half - along + 0.5f));
                    }
                }
                px[y * size + x] = col;
            }
        }
        byte[] out = rgba(px);
        synchronized (CACHE) {
            if (CACHE.size() > 16) {
                CACHE.clear();
            }
            CACHE.put(k, out);
        }
        return out;
    }

    /**
     * Needle, size x size, pointing straight up from the centre, with a hub. The node holding it is
     * rotated (setRotationZ), so it is drawn only once.
     */
    static byte[] needle(int size) {
        String k = new StringBuffer("needle ").append(size).toString();
        synchronized (CACHE) {
            byte[] hit = (byte[]) CACHE.get(k);
            if (hit != null) {
                return hit;
            }
        }
        int[] px = new int[size * size];
        float c = (size - 1) / 2f;
        float len = size * 0.42f;
        float tail = size * 0.08f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float across = Math.abs(x - c);
                float along = c - y; // up is positive
                int col = 0;
                if (along >= -tail && along <= len) {
                    float t = (along + tail) / (len + tail);
                    float half = 3.5f - 2.5f * t; // tapered
                    col = blend(col, 0xFFFF5A1F, cover(half - across + 0.5f));
                }
                float dx = x - c;
                float dy = y - c;
                float r = (float) Math.sqrt(dx * dx + dy * dy);
                col = blend(col, 0xFF1C2024, cover(11f - r));
                col = blend(col, 0xFFC8CCD0, cover(1.2f - Math.abs(r - 10f)));
                px[y * size + x] = col;
            }
        }
        byte[] out = rgba(px);
        synchronized (CACHE) {
            CACHE.put(k, out);
        }
        return out;
    }

    /** Solid w x h block (bar tracks and fills). */
    static byte[] solid(int w, int h, int argb) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, argb);
        return rgba(px);
    }

    /** 0..1 coverage of a signed distance (positive inside), one pixel of soft edge. */
    private static float cover(float d) {
        return d <= 0f ? 0f : d >= 1f ? 1f : d;
    }

    /** Straight-alpha "over": src (ARGB, its alpha times k) over dst. */
    private static int blend(int dst, int src, float k) {
        if (k <= 0f) {
            return dst;
        }
        float sa = ((src >>> 24) / 255f) * k;
        float da = (dst >>> 24) / 255f;
        float oa = sa + da * (1f - sa);
        if (oa <= 0f) {
            return 0;
        }
        int r = mix(src >> 16, dst >> 16, sa, da, oa);
        int g = mix(src >> 8, dst >> 8, sa, da, oa);
        int b = mix(src, dst, sa, da, oa);
        return ((int) (oa * 255f + 0.5f) << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int s, int d, float sa, float da, float oa) {
        float v = ((s & 0xFF) * sa + (d & 0xFF) * da * (1f - sa)) / oa;
        int i = (int) (v + 0.5f);
        return i < 0 ? 0 : i > 255 ? 255 : i;
    }

    private static byte[] rgba(int[] px) {
        byte[] out = new byte[px.length * 4];
        for (int i = 0, j = 0; i < px.length; i++) {
            int c = px[i];
            out[j++] = (byte) (c >> 16);
            out[j++] = (byte) (c >> 8);
            out[j++] = (byte) c;
            out[j++] = (byte) (c >>> 24);
        }
        return out;
    }
}
