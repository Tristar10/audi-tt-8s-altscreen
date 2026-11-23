package local.tt;

import java.io.File;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;

import de.audi.atip.hmi.event.RunnableEvent;
import de.esolutions.hmi.widgets.audi.base.HMITerminalImpl;
import de.esolutions.hmi.widgets.audi.base.eal.EALManager;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedLayer;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3D;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DImage;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DText;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedViewport;
import de.esolutions.hmi.widgets.audi.base.eal.TextureDescription;

/**
 * SKETCH, not built into tt-zoom.jar yet. Live engine/Haldex gauges on the cockpit map tab, fed by
 * an ESP32 (OpenHaldex) over the MMI Wi-Fi hotspot. Design and test plan: wiki/Gauges.md.
 *
 * Path: ESP32 -> UDP 49160 (GaugePacket "TTG1") -> tt-gauge-rx thread -> VAL[] -> tt-gauge-tick
 * (15 Hz) posts at most one GaugeJob at a time -> HMI thread: our own EAL viewport 223 (above the
 * KDK background 220, below the turn panel's 225).
 *
 * Drawing rule (from the turn panel, Cockpit-drawing.md): pictures are drawn once per page/view
 * change, off the HMI thread where possible; per frame the HMI thread only rotates needles
 * (setRotationZ), scales bar fills and changes texts. No texture upload per frame.
 *
 * Steering wheel: while the gauges show, ZoomGate hands the wheel to wheel() instead of zooming
 * CarPlay (2-line patch, see the wiki), one page per notch.
 *
 * Commands (RgiPlayer "gauge ..." line, or tt.sh): gauge on|off | page N | demo on|off |
 * units metric|imperial | rot deg|rad|flip | rot test DEG | rot off | info
 * Files: /mnt/app/root/tt/gauge-on (on at start), /mnt/app/root/tt/gauge-key (decimal key),
 * /mnt/app/root/tt/gauge-metric (metric units; default imperial for the US car).
 */
public final class Gauges implements Runnable {
    private static final Object LOCK = new Object();
    private static final int[] VAL = new int[GaugePacket.CHANNELS];
    private static final long[] AT = new long[GaugePacket.CHANNELS];
    private static final File ON_FILE = new File("/mnt/app/root/tt/gauge-on");
    private static final File KEY_FILE = new File("/mnt/app/root/tt/gauge-key");
    private static final File METRIC_FILE = new File("/mnt/app/root/tt/gauge-metric");
    private static final int VIEWPORT_INDEX = 223;
    private static final String[] PAGE_NAMES = {"BOOST", "TEMPS", "HALDEX"};

    private static boolean started;
    private static volatile boolean on;
    private static volatile boolean demo;
    private static volatile boolean imperial = true;
    private static volatile int page;
    private static volatile boolean dirty;
    private static volatile boolean pending;
    private static volatile boolean shownVisible;
    private static volatile boolean mapNow;
    private static int key;
    private static int lastSeq = -1;
    private static long lastRx;
    private static int lastFlags;
    private static int rxCount;
    private static int badCount;
    private static long lastPageChange;
    private static long jobs;
    private static long jobMsMax;

    // Needle rotation calibration (unknown until the first parked test: degrees or radians, which way).
    private static volatile float rotScale = 1f;
    private static volatile float rotSign = 1f;
    private static volatile float rotTest = Float.NaN;

    // HMI thread only
    private static IWrappedViewport vp;
    private static IWrappedNode3D root;
    private static GaugeWidget[] built;
    private static IWrappedNode3DText noData;
    private static int builtKey = -1;
    private static EALManager eal;

    private final int role;

    private Gauges(int role) {
        this.role = role;
    }

    /** Once per HMI lifetime (call from RgdBus.start in the real integration). */
    public static void start() {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            started = true;
        }
        on = ON_FILE.exists();
        imperial = !METRIC_FILE.exists();
        key = readKey();
        try {
            Thread rx = new Thread(new Gauges(0), "tt-gauge-rx");
            rx.setDaemon(true);
            rx.start();
            Thread tick = new Thread(new Gauges(1), "tt-gauge-tick");
            tick.setDaemon(true);
            tick.start();
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("gauge start failed ").append(String.valueOf(t)).toString());
        }
    }

    public void run() {
        if (role == 0) {
            rxLoop();
        } else {
            tickLoop();
        }
    }

    // ---------------------------------------------------------------- receive

    private static void rxLoop() {
        DatagramSocket s = null;
        byte[] buf = new byte[512];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        GaugePacket g = new GaugePacket();
        for (;;) {
            try {
                if (s == null) {
                    s = new DatagramSocket(GaugePacket.PORT); // all interfaces: the hotspot is not loopback
                    s.setSoTimeout(1000);
                    RgdBus.note(new StringBuffer("gauge rx listening udp ").append(GaugePacket.PORT).toString());
                }
                p.setLength(buf.length);
                s.receive(p);
                if (!g.parse(buf, p.getLength()) || g.key != key) {
                    synchronized (LOCK) {
                        badCount++;
                    }
                    continue;
                }
                long now = System.currentTimeMillis();
                synchronized (LOCK) {
                    if (now - lastRx > 5000L) {
                        lastSeq = -1; // sender restarted or came back into range
                    }
                    if (!GaugePacket.newer(g.seq, lastSeq)) {
                        continue;
                    }
                    lastSeq = g.seq;
                    lastRx = now;
                    lastFlags = g.flags;
                    rxCount++;
                    if (!demo) {
                        for (int i = 0; i < g.count; i++) {
                            int id = g.ids[i];
                            if (id > 0 && id < GaugePacket.CHANNELS) {
                                VAL[id] = g.milli[i];
                                AT[id] = now;
                            }
                        }
                        dirty = true;
                    }
                }
            } catch (InterruptedIOException timeout) {
                // no datagram for a second: the tick thread shows staleness
            } catch (Throwable t) {
                RgdBus.note(new StringBuffer("gauge rx error ").append(String.valueOf(t)).toString());
                if (s != null) {
                    s.close();
                    s = null;
                }
                sleep(2000L);
            }
        }
    }

    // ---------------------------------------------------------------- tick (15 Hz)

    private static void tickLoop() {
        long lastMapCheck = 0;
        boolean lastFlash = false;
        int lastStale = -1;
        for (;;) {
            sleep(66L);
            long now = System.currentTimeMillis();
            if (demo) {
                demoValues(now);
            }
            if (now - lastMapCheck >= 200L) {
                lastMapCheck = now;
                try {
                    mapNow = TtPanel.mapShowing();
                } catch (Throwable t) {
                    mapNow = false;
                }
            }
            boolean want = on && mapNow;
            boolean flash = ((now / 250L) & 1L) == 0L;
            int stale = staleMask(now);
            boolean changed = want != shownVisible
                || (want && (dirty || flash != lastFlash || stale != lastStale));
            lastFlash = flash;
            lastStale = stale;
            if (!changed || pending) {
                continue;
            }
            EALManager m = manager();
            if (m == null) {
                continue;
            }
            if (want) {
                prepareArt(page, ZoomGate.small());
            }
            dirty = false;
            pending = true;
            try {
                m.getTerminal().getFramework().getHMIService().getEventDispatcher().postEvent(
                    new RunnableEvent(new GaugeJob(m)));
            } catch (Throwable t) {
                pending = false;
            }
        }
    }

    static void jobDone(long ms) {
        synchronized (LOCK) {
            jobs++;
            if (ms > jobMsMax) {
                jobMsMax = ms;
            }
        }
        pending = false;
    }

    /** Bit per page-0..31 channel that is stale; only used to notice a change. */
    private static int staleMask(long now) {
        int mask = 0;
        synchronized (LOCK) {
            for (int id = 1; id < 32; id++) {
                if (now - AT[id] > GaugePacket.STALE_MS) {
                    mask |= 1 << id;
                }
            }
        }
        return mask;
    }

    /** Parked test without an ESP32: slow sweeps on every channel. */
    private static void demoValues(long now) {
        double t = now / 1000.0;
        double s = (Math.sin(t * 0.7) + 1.0) / 2.0;
        synchronized (LOCK) {
            put(GaugePacket.RPM, 900 + 6000 * s, now);
            put(GaugePacket.BOOST, -50 + 230 * s, now);
            put(GaugePacket.TORQUE, 30 + 420 * s, now);
            put(GaugePacket.PEDAL, 100 * s, now);
            put(GaugePacket.SPEED, 40 + 60 * s, now);
            put(GaugePacket.COOLANT, 88 + 6 * Math.sin(t * 0.1), now);
            put(GaugePacket.OIL, 100 + 15 * Math.sin(t * 0.08), now);
            put(GaugePacket.IAT, 30 + 10 * s, now);
            put(GaugePacket.DSG_OIL, 75 + 10 * Math.sin(t * 0.05), now);
            put(GaugePacket.LAMBDA, 0.80 + 0.2 * (1 - s), now);
            put(GaugePacket.HALDEX_LOCK, 100 * ((Math.sin(t * 1.3) + 1.0) / 2.0), now);
            put(GaugePacket.HALDEX_ACT, 100 * ((Math.sin(t * 1.3 - 0.4) + 1.0) / 2.0), now);
            put(GaugePacket.HALDEX_MODE, 2, now);
            dirty = true;
        }
    }

    private static void put(int id, double si, long now) {
        VAL[id] = (int) Math.round(si * 1000.0);
        AT[id] = now;
    }

    // ---------------------------------------------------------------- wheel

    /**
     * ZoomGate.gui, right before it would send a zoom step to CarPlay. True = the step was ours
     * (page change), so CarPlay must not zoom. One page per 250 ms, however fast the wheel turns.
     */
    public static boolean wheel(int direction) {
        if (!on || !shownVisible || direction == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            if (now - lastPageChange < 250L) {
                return true;
            }
            lastPageChange = now;
            int n = PAGE_NAMES.length;
            page = (page + (direction > 0 ? 1 : n - 1)) % n;
            dirty = true;
        }
        RgdBus.note(new StringBuffer("gauge page ").append(PAGE_NAMES[page]).toString());
        return true;
    }

    // ---------------------------------------------------------------- pages

    /**
     * The pages. Ranges and warnings in SI units (converted for display). Screen positions are a
     * first guess for 1440x540 and must be checked with "panel grid on" photos: large view = the
     * whole screen on the map tab, small view = the map area left of the tach (x 0..~520,
     * top edge a diagonal (0,110)-(200,50), bottom ~440).
     */
    private static GaugeWidget[] layout(int pg, boolean small) {
        float none = Float.MAX_VALUE;
        if (small) {
            switch (pg) {
                case 0:
                    return new GaugeWidget[] {
                        GaugeWidget.bar(GaugePacket.BOOST, "BOOST", -60f, 200f, 170f, 40, 200, 420, 12),
                        GaugeWidget.bar(GaugePacket.TORQUE, "TORQUE", 0f, 500f, none, 40, 290, 420, 12),
                        GaugeWidget.bar(GaugePacket.HALDEX_LOCK, "HALDEX", 0f, 100f, none, 40, 380, 420, 12),
                    };
                case 1:
                    return new GaugeWidget[] {
                        GaugeWidget.bar(GaugePacket.COOLANT, "COOLANT", 40f, 130f, 112f, 40, 200, 420, 12),
                        GaugeWidget.bar(GaugePacket.OIL, "OIL", 40f, 150f, 135f, 40, 290, 420, 12),
                        GaugeWidget.bar(GaugePacket.DSG_OIL, "DSG OIL", 40f, 140f, 120f, 40, 380, 420, 12),
                    };
                default:
                    return new GaugeWidget[] {
                        GaugeWidget.bar(GaugePacket.HALDEX_LOCK, "LOCK REQ", 0f, 100f, none, 40, 200, 420, 12),
                        GaugeWidget.bar(GaugePacket.HALDEX_ACT, "LOCK ACT", 0f, 100f, none, 40, 290, 420, 12),
                        GaugeWidget.bar(GaugePacket.PEDAL, "PEDAL", 0f, 100f, none, 40, 380, 420, 12),
                    };
            }
        }
        switch (pg) {
            case 0:
                return new GaugeWidget[] {
                    GaugeWidget.dial(GaugePacket.BOOST, "BOOST", -60f, 200f, 170f, 290, 280, 300),
                    GaugeWidget.dial(GaugePacket.RPM, "RPM", 0f, 7500f, 6500f, 720, 270, 340),
                    GaugeWidget.dial(GaugePacket.TORQUE, "TORQUE", 0f, 500f, none, 1150, 280, 300),
                    GaugeWidget.bar(GaugePacket.PEDAL, "PEDAL", 0f, 100f, none, 520, 480, 400, 10),
                };
            case 1:
                return new GaugeWidget[] {
                    GaugeWidget.bar(GaugePacket.COOLANT, "COOLANT", 40f, 130f, 112f, 420, 150, 600, 14),
                    GaugeWidget.bar(GaugePacket.OIL, "OIL", 40f, 150f, 135f, 420, 240, 600, 14),
                    GaugeWidget.bar(GaugePacket.DSG_OIL, "DSG OIL", 40f, 140f, 120f, 420, 330, 600, 14),
                    GaugeWidget.bar(GaugePacket.IAT, "INTAKE AIR", -20f, 80f, 60f, 420, 420, 600, 14),
                };
            default:
                return new GaugeWidget[] {
                    GaugeWidget.dial(GaugePacket.HALDEX_LOCK, "HALDEX LOCK", 0f, 100f, none, 420, 270, 320),
                    GaugeWidget.dial(GaugePacket.SPEED, "SPEED", 0f, 250f, none, 1020, 270, 320),
                    GaugeWidget.bar(GaugePacket.HALDEX_ACT, "ACTUAL", 0f, 100f, none, 570, 200, 300, 10),
                    GaugeWidget.bar(GaugePacket.PEDAL, "PEDAL", 0f, 100f, none, 570, 300, 300, 10),
                    GaugeWidget.num(GaugePacket.HALDEX_MODE, "MODE", none, 650, 380, 140, 40),
                };
        }
    }

    /** Off the HMI thread: draw the faces the next build will upload (GaugeArt caches them). */
    private static void prepareArt(int pg, boolean small) {
        try {
            GaugeWidget[] ws = layout(pg, small);
            for (int i = 0; i < ws.length; i++) {
                if (ws[i].type == GaugeWidget.DIAL) {
                    GaugeArt.dial(ws[i].w, redFrac(ws[i]));
                    GaugeArt.needle(ws[i].w);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static float redFrac(GaugeWidget w) {
        return w.warn == Float.MAX_VALUE ? 2f : (w.warn - w.min) / (w.max - w.min);
    }

    // ---------------------------------------------------------------- HMI thread

    /** GaugeJob. Returns a note for the log, or null when there is nothing to say. */
    static String onHmiThread(EALManager m) {
        boolean want = on && mapNow;
        if (!want) {
            if (root != null && shownVisible) {
                root.setVisible(false);
                m.fireRenderEvent();
            }
            shownVisible = false;
            return null;
        }
        String note = null;
        if (root == null) {
            vp = m.createViewport("ttGauge", VIEWPORT_INDEX, m.getScreenWidth(), m.getScreenHeight());
            IWrappedLayer layer = m.createLayer(vp, "ttGauge", 0, m.getScreenWidth(), m.getScreenHeight(), 0);
            root = layer == null ? null : layer.getMainNode();
            if (root == null) {
                return "no layer";
            }
            note = new StringBuffer("viewport ").append(m.getScreenWidth()).append('x').append(m.getScreenHeight()).toString();
        }
        boolean small = ZoomGate.small();
        int k = page * 4 + (small ? 1 : 0) + (imperial ? 2 : 0);
        if (k != builtKey) {
            build(m, layout(page, small), small);
            builtKey = k;
            // StringBuffer only: '+' on strings compiles to StringBuilder, which the car's 1.4 library lacks
            StringBuffer b = new StringBuffer();
            if (note != null) {
                b.append(note).append(", ");
            }
            note = b.append("built ").append(PAGE_NAMES[page]).append(small ? " small" : " large").toString();
        }
        update(m);
        root.setVisible(true);
        vp.setForceInvisible(false);
        vp.setVisible(true); // a new viewport starts hidden (Cockpit-drawing trap v25/v26)
        m.fireRenderEvent();
        shownVisible = true;
        return note;
    }

    private static void build(EALManager m, GaugeWidget[] ws, boolean small) {
        root.removeAllChildren();
        built = ws;
        // page title and dots, top left of the area
        int tx = small ? 40 : 60;
        int ty = small ? 140 : 40;
        text(m, null, "ttgTitle", PAGE_NAMES[page], 2, 22, tx, ty, 0xFFFFFFFF, false);
        StringBuffer dots = new StringBuffer();
        for (int i = 0; i < PAGE_NAMES.length; i++) {
            dots.append(i == page ? '●' : '○').append(' ');
        }
        text(m, null, "ttgDots", dots.toString(), 1, 16, tx, ty + 28, 0xFF9AA0A6, false);
        noData = text(m, null, "ttgNoData", " ", 2, 20, tx + 200, ty, 0xFFFFB020, false);
        for (int i = 0; i < ws.length; i++) {
            GaugeWidget w = ws[i];
            w.forget();
            float lo = display(w.ch, w.min);
            float hi = display(w.ch, w.max);
            if (w.type == GaugeWidget.DIAL) {
                image(m, root, GaugeArt.dial(w.w, redFrac(w)), w.w, w.w, w.w, w.w, w.x, w.y);
                // needle: a group at the dial centre is what rotates, so the pivot is the centre
                // whether the engine turns a node about its origin or about its middle
                IWrappedNode3D g = m.createNode3D(root, "ttgNeedle", 0f, 0f);
                if (g != null) {
                    g.setPosition(w.x + w.w / 2f, w.y + w.w / 2f, 0f);
                    image(m, g, GaugeArt.needle(w.w), w.w, w.w, w.w, w.w, -w.w / 2, -w.w / 2);
                }
                w.needle = g;
                int cx = w.x + w.w / 2;
                text(m, null, "ttgLabel", w.label, 1, 18, cx, w.y + w.w * 3 / 4, 0xFF9AA0A6, true);
                text(m, null, "ttgLo", fmt(lo, 0), 1, 14, w.x + w.w / 5, w.y + w.w * 7 / 8, 0xFF9AA0A6, true);
                text(m, null, "ttgHi", fmt(hi, 0), 1, 14, w.x + w.w * 4 / 5, w.y + w.w * 7 / 8, 0xFF9AA0A6, true);
            } else if (w.type == GaugeWidget.BAR) {
                image(m, root, GaugeArt.solid(8, 8, 0x80FFFFFF), 8, 8, w.w, w.h, w.x, w.y); // track
                w.fill = image(m, root, GaugeArt.solid(8, 8, 0xFFFFFFFF), 8, 8, w.w, w.h, w.x, w.y);
                w.fillWarn = image(m, root, GaugeArt.solid(8, 8, 0xFFE0302A), 8, 8, w.w, w.h, w.x, w.y);
                if (w.fillWarn != null) {
                    w.fillWarn.setVisible(false);
                }
                text(m, null, "ttgLabel", w.label, 1, 18, w.x, w.y - 30, 0xFF9AA0A6, false);
            } else {
                text(m, null, "ttgLabel", w.label, 1, 18, w.x, w.y, 0xFF9AA0A6, false);
            }
        }
    }

    private static void update(EALManager m) {
        GaugeWidget[] ws = built;
        if (ws == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean flash = ((now / 250L) & 1L) == 0L;
        boolean anyData;
        synchronized (LOCK) {
            anyData = demo || now - lastRx < 3000L;
        }
        setText(m, noData, anyData ? " " : "NO DATA", 2, 20);
        for (int i = 0; i < ws.length; i++) {
            GaugeWidget w = ws[i];
            int raw;
            long at;
            synchronized (LOCK) {
                raw = VAL[w.ch];
                at = AT[w.ch];
            }
            boolean fresh = now - at <= GaugePacket.STALE_MS;
            float si = raw / 1000f;
            float frac = w.max > w.min ? (si - w.min) / (w.max - w.min) : 0f;
            frac = frac < 0f ? 0f : frac > 1f ? 1f : frac;
            boolean warn = fresh && si >= w.warn;
            String s = fresh ? new StringBuffer(fmt(display(w.ch, si), decimals(w.ch))).append(unit(w.ch)).toString()
                : "--";
            int color = warn ? (flash ? 0xFFFF3B30 : 0xFFFFFFFF) : fresh ? 0xFFFFFFFF : 0xFF707478;

            if (w.type == GaugeWidget.DIAL && w.needle != null) {
                float a = Float.isNaN(rotTest) ? (fresh ? GaugeArt.angle(frac) : GaugeArt.angle(0f)) : rotTest;
                if (Float.isNaN(w.shownAngle) || Math.abs(a - w.shownAngle) > 0.3f) {
                    w.needle.setRotationZ(rotSign * a * rotScale);
                    w.shownAngle = a;
                }
                int cx = w.x + w.w / 2;
                w.value = valueText(m, w, s, 2, w.w / 7, cx, w.y + w.w / 2 + w.w / 8, color, true);
            } else if (w.type == GaugeWidget.BAR && w.fill != null) {
                float f = fresh ? frac : 0f;
                if (Math.abs(f - w.shownFrac) > 0.002f || warn != w.shownWarn) {
                    IWrappedNode3DImage show = warn ? w.fillWarn : w.fill;
                    IWrappedNode3DImage hide = warn ? w.fill : w.fillWarn;
                    if (hide != null) {
                        hide.setVisible(false);
                    }
                    if (show != null) {
                        show.setVisible(f > 0.001f);
                        show.setScale(f * w.w / show.getUnscaledWidth(), (float) w.h / show.getUnscaledHeight(), 1f);
                    }
                    w.shownFrac = f;
                    w.shownWarn = warn;
                }
                w.value = valueText(m, w, s, 2, 24, w.x + w.w, w.y - 34, color, false);
            } else if (w.type == GaugeWidget.NUM) {
                w.value = valueText(m, w, s, 2, w.h, w.x, w.y + 24, color, false);
            }
        }
    }

    /** The value text of a widget: right-aligned at x for bars (alignRight via negative width), centred for dials. */
    private static IWrappedNode3DText valueText(EALManager m, GaugeWidget w, String s, int style, int size, int x,
                                                int y, int color, boolean centre) {
        if (w.value != null && s.equals(w.shownText) && color == w.shownColor) {
            return w.value;
        }
        int px = x;
        if (w.type == GaugeWidget.BAR) {
            px = x - textW(m, s, style, size); // right-aligned to the end of the bar
        }
        IWrappedNode3DText n = w.value;
        if (n == null) {
            n = text(m, null, "ttgValue", s, style, size, px, y, color, centre);
        } else {
            setText(m, n, s, style, size);
            place(m, n, s, style, size, px, y, centre);
            n.setColor(rgba(color));
        }
        w.shownText = s;
        w.shownColor = color;
        return n;
    }

    // ---------------------------------------------------------------- EAL helpers

    /** Uploads an iw x ih picture and shows it scaled to w x h at x, y (solids are 8x8, stretched). */
    private static IWrappedNode3DImage image(EALManager m, IWrappedNode3D parent, byte[] rgba, int iw, int ih,
                                             int w, int h, int x, int y) {
        ByteBuffer buf = ByteBuffer.allocateDirect(rgba.length);
        buf.put(rgba);
        buf.rewind();
        TextureDescription d = m.createTextureDescription(buf, 6, iw, ih, null, new Object());
        IWrappedNode3DImage img = m.createImage3D(parent, "ttgArt", d, 0, true, "ttGauge");
        if (img != null) {
            img.setScale((float) w / img.getUnscaledWidth(), (float) h / img.getUnscaledHeight(), 1f);
            img.setPosition((float) x, (float) y, 0f);
        }
        return img;
    }

    private static IWrappedNode3DText text(EALManager m, IWrappedNode3D parent, String name, String s, int style,
                                           int size, int x, int y, int color, boolean centre) {
        IWrappedFont f = font(style, size);
        if (f == null) {
            return null;
        }
        IWrappedNode3DText n = m.createText3D(parent == null ? root : parent, name, s, f);
        if (n != null) {
            n.setColor(rgba(color));
            place(m, n, s, style, size, x, y, centre);
        }
        return n;
    }

    /** Each node keeps one style and size for its life; the caller passes the same ones again. */
    private static void setText(EALManager m, IWrappedNode3DText n, String s, int style, int size) {
        IWrappedFont f = font(style, size);
        if (n != null && f != null) {
            n.setText(s, f);
        }
    }

    /** y is the top of the text; nodes sit on their baseline (Cockpit-drawing trap v26). */
    private static void place(EALManager m, IWrappedNode3DText n, String s, int style, int size, int x, int y,
                              boolean centre) {
        float px = centre ? x - textW(m, s, style, size) / 2f : x;
        n.setPosition(px, y + 0.82f * size, 0f);
    }

    private static IWrappedFont font(int style, int size) {
        Object f = HMITerminalImpl.fontLoader == null ? null : HMITerminalImpl.fontLoader.getFont(style, size);
        return f instanceof IWrappedFont ? (IWrappedFont) f : null;
    }

    private static int textW(EALManager m, String s, int style, int size) {
        IWrappedFont f = font(style, size);
        if (f == null || s.length() == 0) {
            return 0;
        }
        try {
            return m.getTextWidth(s, f);
        } catch (Throwable t) {
            return (int) (s.length() * size * 0.6f);
        }
    }

    /** EAL text colours are 0xRRGGBBAA (Cockpit-drawing trap v30); written here as ARGB. */
    private static int rgba(int argb) {
        return (argb << 8) | (argb >>> 24);
    }

    private static EALManager manager() {
        if (eal != null) {
            return eal;
        }
        try {
            java.lang.reflect.Field f = Class.forName(
                "de.esolutions.hmi.widgets.audi.evo.high.widgets.CombiMapController").getDeclaredField("hmiService");
            f.setAccessible(true);
            Object svc = f.get(null);
            if (svc instanceof de.audi.tghu.hmi.evo.IHMIServiceEvo) {
                Object t = ((de.audi.tghu.hmi.evo.IHMIServiceEvo) svc).getHMITerminal(0);
                if (t instanceof de.esolutions.hmi.widgets.audi.base.eal.HMITerminalEAL) {
                    eal = ((de.esolutions.hmi.widgets.audi.base.eal.HMITerminalEAL) t).getEALManager();
                }
            }
        } catch (Throwable ignored) {
        }
        return eal;
    }

    // ---------------------------------------------------------------- units

    static float display(int ch, float si) {
        if (!imperial) {
            return si;
        }
        switch (ch) {
            case GaugePacket.SPEED:
                return si * 0.621371f;
            case GaugePacket.COOLANT:
            case GaugePacket.OIL:
            case GaugePacket.IAT:
            case GaugePacket.DSG_OIL:
                return si * 1.8f + 32f;
            case GaugePacket.BOOST:
                return si * 0.145038f;
            default:
                return si;
        }
    }

    static String unit(int ch) {
        switch (ch) {
            case GaugePacket.SPEED:
                return imperial ? " mph" : " km/h";
            case GaugePacket.COOLANT:
            case GaugePacket.OIL:
            case GaugePacket.IAT:
            case GaugePacket.DSG_OIL:
                return imperial ? "°F" : "°C";
            case GaugePacket.BOOST:
                return imperial ? " psi" : " kPa";
            case GaugePacket.TORQUE:
                return " Nm";
            case GaugePacket.PEDAL:
            case GaugePacket.HALDEX_LOCK:
            case GaugePacket.HALDEX_ACT:
                return "%";
            case GaugePacket.BATTERY:
                return " V";
            case GaugePacket.LAT_G:
            case GaugePacket.LONG_G:
                return " g";
            default:
                return "";
        }
    }

    static int decimals(int ch) {
        switch (ch) {
            case GaugePacket.LAMBDA:
            case GaugePacket.LAT_G:
            case GaugePacket.LONG_G:
                return 2;
            case GaugePacket.BOOST:
                return imperial ? 1 : 0;
            case GaugePacket.BATTERY:
                return 1;
            default:
                return 0;
        }
    }

    /** No String.format on the car's library. */
    static String fmt(float v, int dec) {
        long mul = 1;
        for (int i = 0; i < dec; i++) {
            mul *= 10;
        }
        long n = Math.round(Math.abs(v) * mul);
        StringBuffer b = new StringBuffer();
        if (v < 0 && n != 0) {
            b.append('-');
        }
        b.append(n / mul);
        if (dec > 0) {
            b.append('.');
            String f = String.valueOf(n % mul);
            for (int i = f.length(); i < dec; i++) {
                b.append('0');
            }
            b.append(f);
        }
        return b.toString();
    }

    // ---------------------------------------------------------------- commands

    public static String cmd(String arg) {
        String[] w = split(arg == null ? "" : arg.trim());
        String c = w.length > 0 ? w[0] : "info";
        if (c.equals("on") || c.equals("off")) {
            on = c.equals("on");
            dirty = true;
        } else if (c.equals("page") && w.length > 1) {
            int p = Integer.parseInt(w[1]);
            if (p >= 0 && p < PAGE_NAMES.length) {
                page = p;
                dirty = true;
            }
        } else if (c.equals("demo") && w.length > 1) {
            demo = w[1].equals("on");
            dirty = true;
        } else if (c.equals("units") && w.length > 1) {
            imperial = !w[1].equals("metric");
            dirty = true;
        } else if (c.equals("rot") && w.length > 1) {
            if (w[1].equals("deg")) {
                rotScale = 1f;
            } else if (w[1].equals("rad")) {
                rotScale = (float) (Math.PI / 180.0);
            } else if (w[1].equals("flip")) {
                rotSign = -rotSign;
            } else if (w[1].equals("test") && w.length > 2) {
                rotTest = Float.parseFloat(w[2]);
            } else if (w[1].equals("off")) {
                rotTest = Float.NaN;
            }
            builtKey = -1; // rebuild so every needle is set again
            dirty = true;
        }
        return info();
    }

    static String info() {
        synchronized (LOCK) {
            return new StringBuffer("gauge on=").append(on).append(" visible=").append(shownVisible)
                .append(" map=").append(mapNow).append(" page=").append(PAGE_NAMES[page])
                .append(" demo=").append(demo).append(" imperial=").append(imperial)
                .append(" rx=").append(rxCount).append(" bad=").append(badCount)
                .append(" lastRx=").append(lastRx == 0 ? -1 : System.currentTimeMillis() - lastRx).append("ms")
                .append(" flags=").append(lastFlags).append(" jobs=").append(jobs).append(" jobMax=")
                .append(jobMsMax).append("ms rot=").append(rotSign * rotScale).toString();
        }
    }

    private static String[] split(String s) {
        java.util.StringTokenizer t = new java.util.StringTokenizer(s);
        String[] out = new String[t.countTokens()];
        for (int i = 0; i < out.length; i++) {
            out[i] = t.nextToken();
        }
        return out;
    }

    private static int readKey() {
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(KEY_FILE));
            try {
                String l = r.readLine();
                return l == null ? 0 : (int) Long.parseLong(l.trim());
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
