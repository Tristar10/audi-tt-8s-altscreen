package local.tt;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.Hashtable;

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
 * The OpenHaldex autocross view on the cockpit (wiki/Telemetry.md): our own opaque viewport over
 * the map, drawn from TeleLayout's slots.
 *
 * Threads, following the turn panel's rules (Cockpit-drawing.md):
 *  - tt-tele-rx (TeleLink) receives the datagrams.
 *  - tt-tele-tick, up to 30 Hz: copies the values into the layout, builds it, draws every picture the
 *    frame needs that is not cached yet (quads, icons, solid blocks) and posts one TeleJob when the
 *    picture changed. At most one job is in flight.
 *  - The HMI thread (TeleJob) only creates nodes, uploads pictures drawn earlier and changes
 *    visibility, text, colour (by switching to the slot's node for that colour), position and scale.
 *
 * Switches, read every second (kit: sh tt.sh tele ...), RAM unless noted:
 *   /tmp/tt-tele-on      view on (until the scroll-wheel double press is traced)
 *   /tmp/tt-tele-off     view off even when the flash default is on
 *   /mnt/app/root/tt/tele-on   flash: on at every start
 *   /tmp/tt-tele-demo    sweeping fake values, no OpenHaldex needed (the UDP port stays closed)
 *   /tmp/tt-tele-rot     tilted text and icons: -deg (default, right on the car), deg, rad, -rad, off
 *   /tmp/tt-tele-font    text size in percent of the layout's (default 70, see fontPct)
 *   /tmp/tt-tele-beep    tele10: "N [system|ring]" plays the head unit's tone N once (or "default",
 *                        "stop"), then the file is deleted; the log has the delay (TeleBeep)
 *   /tmp/tt-tele-shiftbeep  tele10: "N [system|ring]": play that tone each time rpm rises past
 *                        the shift point (re-armed 300 rpm below it)
 *   /tmp/tt-tele-theme   tele17: the itasha theme (or /mnt/app/root/tt/tele-theme): the background
 *                        pictures /mnt/app/root/tt/theme-classic.rgba and theme-sport.rgba (1440 x
 *                        540, RGBA, made by the simulator's Theme --export) under the view, and a
 *                        see-through gear notch
 *   /tmp/tt-tele-ui      tele17: one control-page action (open, up, down, press, back, close),
 *                        read every tick and deleted (TeleUi)
 *   /tmp/tt-shot         tele18: a screenshot of the cockpit, written by the head unit itself as a
 *                        PNG to the path in the file (HMIService.takeScreenshot, the stock key-combo
 *                        screenshot's call), then the file is deleted
 *   /tmp/tt-tele-glide   tele17: glide between data updates (or /mnt/app/root/tt/tele-glide); TeleGlide
 *   /tmp/tt-tele-probe   tele8 probe instead of the view: the painted rev strip (TeleStrip), the
 *                        lit picture cut by a clip rectangle at the rpm. "grp" (default): the clip
 *                        on a group holding the picture; "img": on the picture itself
 * Shows only while the map tab or the small view's map area is up. Playback "tele ..." lines:
 * apply N | info.
 *
 * Java 1.4 library only (HMI-patching.md): no string +, no generics, no autoboxing.
 */
public final class TeleView implements Runnable {
    private static final File DIR = new File("/mnt/app/root/tt");
    private static final File DEFAULT_ON = new File(DIR, "tele-on");
    private static final File KEY_FILE = new File(DIR, "tele-key");
    private static final File ON_FILE = new File("/tmp/tt-tele-on");
    private static final File OFF_FILE = new File("/tmp/tt-tele-off");
    private static final File DEMO_FILE = new File("/tmp/tt-tele-demo");
    private static final File ROT_FILE = new File("/tmp/tt-tele-rot");
    private static final File FONT_FILE = new File("/tmp/tt-tele-font");
    private static final File PROBE_FILE = new File("/tmp/tt-tele-probe");
    private static final File THEME_RAM = new File("/tmp/tt-tele-theme");
    private static final File THEME_FLASH = new File(DIR, "tele-theme");
    private static final File[] THEME_FILES = {new File(DIR, "theme-classic.rgba"), new File(DIR, "theme-sport.rgba")};
    private static final int TW = TeleLayout.SCREEN_W;
    private static final int TH = TeleLayout.SCREEN_H;
    /** The theme pictures read from flash, by view (kept while the theme is on). */
    private static final byte[][] THEME = new byte[2][];
    private static final boolean[] THEME_BAD = new boolean[2];
    private static volatile boolean themeOn;
    private static final File GLIDE_RAM = new File("/tmp/tt-tele-glide");
    private static final File GLIDE_FLASH = new File(DIR, "tele-glide");
    private static volatile boolean glide;
    private static final TeleGlide GLIDE = new TeleGlide();
    private static final File UI_FILE = new File("/tmp/tt-tele-ui");
    private static final File SHOT_FILE = new File("/tmp/tt-shot");
    private static final TeleUi UI = new TeleUi();
    private static boolean overflowNoted;
    private static final File BEEP_FILE = new File("/tmp/tt-tele-beep");
    private static final File SHIFTBEEP_FILE = new File("/tmp/tt-tele-shiftbeep");
    private static volatile String shiftBeep;
    private static boolean beepArmed = true;
    /** Above the turn panel's 225, so the autocross view covers it. */
    private static final int VIEWPORT_INDEX = 228;
    private static final int SOLID = 8;
    private static final Hashtable ART = new Hashtable();
    /** Quad coverage masks by geometry (tele11): one per shape, recoloured cheaply for each colour. */
    private static final Hashtable MASKS = new Hashtable();
    /** New pictures uploaded per HMI job at most (tele11): a big first build shows over a few frames. */
    private static final int UPLOADS_PER_JOB = 48;
    private static volatile boolean needMore;

    private static boolean started;
    private static volatile boolean on;
    private static volatile boolean demo;
    private static volatile boolean pending;
    private static volatile boolean mapNow;
    private static volatile float rotScale = 1f;
    private static volatile float rotSign = 1f;
    private static volatile boolean rotOff;
    /**
     * The car's font at a given size is about 1.45x the preview's (tele1 photos: cap height about
     * the size itself), so texts are drawn at this fraction of the layout's size, baseline kept.
     * /tmp/tt-tele-font holds a percentage to try others.
     */
    private static volatile int fontPct = 70;
    /** -1 off, 0 clip on a group, 1 clip on the picture (PROBE_FILE). */
    private static volatile int probeMode = -1;
    private static volatile byte[] stripTrack;
    private static volatile byte[] stripLit;
    private static volatile String probeInfo = "-";
    private static volatile int rotGen;
    private static long lastPress;
    private static long jobs;
    private static long jobMsMax;
    private static long statJobs;
    private static long statMs;
    private static long statMax;
    private static long statAt;
    private static long statSlots;
    private static volatile long postedAt;
    private static long lostJobs;

    // HMI thread only
    private static IWrappedViewport vp;
    private static IWrappedNode3D root;
    private static TeleSlot[] slots;
    private static int builtKey = -1;
    private static int builtRotGen = -1;
    /** tele11: every view built so far keeps its nodes (key -> ViewNodes); a switch only swaps them. */
    private static final Hashtable VIEWS = new Hashtable();
    private static int uploadsLeft;
    private static boolean shownVisible;
    private static int pBuiltMode = -1;
    private static IWrappedNode3D pClip;
    private static IWrappedNode3DImage pLitImg;
    private static IWrappedNode3D pMarkA;
    private static IWrappedNode3D pMarkB;
    private static IWrappedNode3DText pTxt;
    private static int pScr0;

    private TeleView() {
    }

    /** Once per HMI lifetime (RgdBus.start). */
    public static void start() {
        synchronized (ART) {
            if (started) {
                return;
            }
            started = true;
        }
        try {
            readSwitches();
            TeleLink.start(readKey());
            Thread t = new Thread(new TeleView(), "tt-tele-tick");
            t.setDaemon(true);
            t.start();
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("tele start failed ").append(String.valueOf(t)).toString());
        }
    }

    // ---------------------------------------------------------------- tick thread

    public void run() {
        TeleLayout layout = new TeleLayout();
        TeleFrame last = null;
        long lastFiles = 0;
        long lastMap = 0;
        for (;;) {
            sleep(33L); // up to 30 frames/s; one job in flight at a time
            long now = System.currentTimeMillis();
            try {
                if (now - lastFiles >= 1000L) {
                    lastFiles = now;
                    readSwitches();
                }
                if (now - lastMap >= 200L) {
                    lastMap = now;
                    try {
                        mapNow = TtPanel.mapShowing();
                    } catch (Throwable t) {
                        mapNow = false;
                    }
                }
                TeleLink.want(on && !demo);
                if (on) {
                    stats(now);
                }
                if (pending) {
                    // A job posted while the HMI was still starting can be dropped: then nothing would
                    // ever be drawn again (tele3 "tele on does not draw"). Give up on it after 2 s.
                    if (now - postedAt < 2000L) {
                        continue;
                    }
                    synchronized (ART) {
                        lostJobs++;
                    }
                    RgdBus.note("tele job lost (no answer in 2 s), sending again");
                    pending = false;
                    last = null;
                }
                boolean show = on && mapNow;
                TeleFrame f;
                if (show) {
                    for (int i = 0; i < TeleLayout.CHANNELS; i++) {
                        layout.has[i] = false;
                    }
                    layout.now = now;
                    if (demo) {
                        TeleLink.demo(layout, now);
                    } else {
                        TeleLink.snapshot(layout, now);
                    }
                    if (glide) {
                        GLIDE.apply(layout, now, false);
                    }
                    TeleLink.demoLink = demo;
                    if (UI_FILE.exists()) {
                        String ua = readLine(UI_FILE);
                        UI_FILE.delete();
                        TeleUi.post(TeleUi.action(ua));
                    }
                    UI.tick(layout, now);
                    shiftTone(layout);
                    if (probeMode >= 0) {
                        f = probeFrame(layout, now);
                    } else {
                        boolean small = ZoomGate.small();
                        int view = small ? TeleLayout.VIEW_SMALL : TeleLayout.VIEW_LARGE;
                        int theme = themeOn && themeLoaded(view) ? view : -1;
                        layout.themed = theme >= 0;
                        layout.overflow = false;
                        layout.build(view, TeleLayout.PAGE_LIVE);
                        if (layout.overflow && !overflowNoted) {
                            overflowNoted = true;
                            RgdBus.note("tele layout: more than 400 slots, the extra ones are dropped");
                        }
                        f = TeleFrame.of(layout, (small ? 1 : 0) | (UI.isOpen() ? 2 : 0) | (rotGen << 4));
                        f.theme = theme;
                        prepareArt(f);
                    }
                } else {
                    f = TeleFrame.hidden();
                }
                if (f.sameAs(last) && !needMore) {
                    if (show && probeMode < 0) {
                        prewarm(now);
                    }
                    continue;
                }
                EALManager m = TtPanel.ealManager();
                if (m == null) {
                    continue;
                }
                pending = true;
                postedAt = now;
                try {
                    m.getTerminal().getFramework().getHMIService().getEventDispatcher().postEvent(
                        new RunnableEvent(new TeleJob(m, f)));
                    last = f;
                } catch (Throwable t) {
                    pending = false;
                }
            } catch (Throwable t) {
                RgdBus.note(new StringBuffer("tele tick error ").append(String.valueOf(t)).toString());
                sleep(1000L);
            }
        }
    }

    /** tele8 probe frame; paints the strip pictures the first time (tick thread). */
    private static TeleFrame probeFrame(TeleLayout layout, long now) {
        if (stripLit == null) {
            TeleStrip s = TeleStrip.paint(layout);
            long t0 = System.currentTimeMillis();
            stripTrack = argbToRgba(s.track, 255);
            stripLit = argbToRgba(s.lit, 255);
            RgdBus.note(new StringBuffer("tele probe: strip painted in ").append(s.paintMs).append(" ms, converted in ")
                .append(System.currentTimeMillis() - t0).append(" ms, ").append(TeleStrip.W).append('x')
                .append(TeleStrip.H).toString());
        }
        int rpm = layout.link && layout.has[TeleLayout.CH_RPM] ? (layout.val[TeleLayout.CH_RPM] + 500) / 1000 : 0;
        boolean blink = (now / 100) % 2 == 0;
        boolean lit = layout.link && !(rpm >= layout.limitRpm && !blink);
        return TeleFrame.probe(probeMode, rpm, TeleStrip.cutX(layout, rpm), lit);
    }

    private static TeleLayout other;
    private static int prewarmedGen = -1;

    /**
     * tele11: while nothing changes, paint the other view's pictures in the background (no
     * uploads), so the first switch to it does not have to.
     */
    private static void prewarm(long now) {
        int gen = rotGen;
        if (prewarmedGen == gen) {
            return;
        }
        if (other == null) {
            other = new TeleLayout();
        }
        boolean small = ZoomGate.small();
        other.now = now;
        other.link = true;
        for (int i = 0; i < TeleLayout.CHANNELS; i++) {
            other.has[i] = false;
        }
        other.build(small ? TeleLayout.VIEW_LARGE : TeleLayout.VIEW_SMALL, TeleLayout.PAGE_LIVE);
        prepareArt(TeleFrame.of(other, -1));
        prewarmedGen = gen;
    }

    /** Reads a view's theme picture from flash once (tick thread); false if missing or wrong size. */
    private static boolean themeLoaded(int view) {
        if (THEME[view] != null) {
            return true;
        }
        if (THEME_BAD[view]) {
            return false;
        }
        File f = THEME_FILES[view];
        long len = f.length();
        if (!f.exists() || len != (long) TW * TH * 4) {
            THEME_BAD[view] = true;
            RgdBus.note(new StringBuffer("tele theme: ").append(f.getPath()).append(f.exists() ? " has the wrong size " : " missing ")
                .append(len).toString());
            return false;
        }
        try {
            byte[] b = new byte[(int) len];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            try {
                int o = 0;
                while (o < b.length) {
                    int n = in.read(b, o, b.length - o);
                    if (n < 0) {
                        break;
                    }
                    o += n;
                }
            } finally {
                in.close();
            }
            THEME[view] = b;
            Runtime rt = Runtime.getRuntime();
            RgdBus.note(new StringBuffer("tele theme: loaded ").append(f.getPath()).append(", heap free ")
                .append(rt.freeMemory() / 1024).append(" KB of ").append(rt.totalMemory() / 1024).append(" KB").toString());
            return true;
        } catch (Throwable t) {
            THEME_BAD[view] = true;
            RgdBus.note(new StringBuffer("tele theme: read failed ").append(String.valueOf(t)).toString());
            return false;
        }
    }

    static void jobDone(long ms) {
        String line = null;
        synchronized (ART) {
            jobs++;
            if (ms > jobMsMax) {
                jobMsMax = ms;
            }
            statJobs++;
            statMs += ms;
            if (ms > statMax) {
                statMax = ms;
            }
            long now = System.currentTimeMillis();
            if (statAt == 0) {
                statAt = now;
            }
        }
        pending = false;
    }

    /** Every 10 s while the view or its receiver is wanted (tick thread, so a stuck job shows too). */
    private static void stats(long now) {
        String line;
        synchronized (ART) {
            if (statAt == 0) {
                statAt = now;
                return;
            }
            if (now - statAt < 10000L) {
                return;
            }
            line = new StringBuffer("tele stats: on=").append(on).append(" demo=").append(demo).append(" map=")
                .append(mapNow).append(", ").append(statJobs * 1000L / (now - statAt)).append(" frames/s, job avg ")
                .append(statMs / Math.max(1L, statJobs)).append(" ms max ").append(statMax)
                .append(" ms, changed slots avg ").append(statSlots / Math.max(1L, statJobs)).append(", pending ")
                .append(pending ? (now - postedAt) / 1000 : 0).append(" s, lost jobs ").append(lostJobs).append(", art ")
                .append(ART.size()).append(probeMode >= 0 ? ", probe " : "").append(probeMode >= 0 ? probeInfo : "")
                .append(" | ").append(TeleLink.rxStats()).toString();
            statJobs = 0;
            statMs = 0;
            statMax = 0;
            statSlots = 0;
            statAt = now;
        }
        RgdBus.note(line);
    }

    /** Off the HMI thread: draws every picture this frame shows that is not cached yet. */
    private static void prepareArt(TeleFrame f) {
        // tele11: pictures are keyed by shape and colour, not by view, so both views keep theirs
        // (clearing on every switch made Classic repaint everything each time). Cap the memory.
        if (ART.size() > 1500) {
            ART.clear();
            MASKS.clear();
        }
        for (int i = 0; i < f.count; i++) {
            if (!f.vis[i]) {
                continue;
            }
            Long k = artKey(f, i);
            if (k == null || ART.containsKey(k)) {
                continue;
            }
            byte[] px;
            if (f.kind[i] == TeleLayout.BOX) {
                px = solid(f.rgba[i]);
            } else if (f.kind[i] == TeleLayout.ICON) {
                px = argbToRgba(TeleIcons.argb(f.size[i], f.w[i], f.h[i], f.rgba[i] >>> 8), f.rgba[i] & 0xFF);
            } else {
                px = quad(f, i, quadScale(f, i));
            }
            ART.put(k, px);
        }
    }

    private static Long artKey(TeleFrame f, int i) {
        int c = f.rgba[i];
        if (f.kind[i] == TeleLayout.BOX) {
            return new Long(0x100000000L | (c & 0xFFFFFFFFL));
        }
        if (f.kind[i] == TeleLayout.ICON) {
            return new Long(((long) (2 + f.size[i] * 1000000 + f.w[i] * 1000 + f.h[i]) << 32) | (c & 0xFFFFFFFFL));
        }
        if (f.kind[i] == TeleLayout.QUAD) {
            return new Long(((long) (shapeKey(f, i) | 0x40000000) << 32) | (c & 0xFFFFFFFFL));
        }
        return null;
    }

    /** Big soft shapes (the glow) are drawn at half size and stretched. */
    private static int quadScale(TeleFrame f, int i) {
        return f.w[i] * f.h[i] > 40000 ? 2 : 1;
    }

    /**
     * EAL blends pictures as premultiplied alpha (tele1 on the car: a 10 % red glow came out as a
     * bright red block), so every picture's colour is multiplied by its alpha here.
     */
    private static void put(byte[] b, int j, int rgb, int a) {
        b[j] = (byte) (((rgb >> 16) & 0xFF) * a / 255);
        b[j + 1] = (byte) (((rgb >> 8) & 0xFF) * a / 255);
        b[j + 2] = (byte) ((rgb & 0xFF) * a / 255);
        b[j + 3] = (byte) a;
    }

    private static byte[] solid(int rgba) {
        byte[] b = new byte[SOLID * SOLID * 4];
        for (int j = 0; j < b.length; j += 4) {
            put(b, j, rgba >>> 8, rgba & 0xFF);
        }
        return b;
    }

    private static byte[] argbToRgba(int[] px, int alpha) {
        byte[] b = new byte[px.length * 4];
        for (int i = 0, j = 0; i < px.length; i++, j += 4) {
            int c = px[i];
            put(b, j, c, ((c >>> 24) * alpha) / 255);
        }
        return b;
    }

    /** The shape of a quad (corners relative to its box, and the box size) as an int. */
    private static int shapeKey(TeleFrame f, int i) {
        int h = f.w[i] * 31 + f.h[i];
        for (int k = 0; k < 4; k++) {
            h = h * 131 + (f.qx[4 * i + k] - f.x[i]);
            h = h * 131 + (f.qy[4 * i + k] - f.y[i]);
        }
        return h & 0x3FFFFFFF;
    }

    /**
     * A quad filled into its bounding box with soft edges. tele11: coverage from the distance to
     * the edges (one value per pixel; the shapes are convex), computed once per shape and kept, so a
     * colour change only recolours (tele10 tested 16 samples per pixel for every colour: Classic
     * took seconds to build on the head unit).
     */
    private static byte[] quad(TeleFrame f, int i, int sc) {
        int tw = (f.w[i] + sc - 1) / sc;
        int th = (f.h[i] + sc - 1) / sc;
        Integer mk = new Integer(shapeKey(f, i) * 3 + sc);
        byte[] mask = (byte[]) MASKS.get(mk);
        if (mask == null) {
            float[] xs = new float[4];
            float[] ys = new float[4];
            for (int k = 0; k < 4; k++) {
                xs[k] = (f.qx[4 * i + k] - f.x[i]) / (float) sc;
                ys[k] = (f.qy[4 * i + k] - f.y[i]) / (float) sc;
            }
            mask = coverage(xs, ys, tw, th);
            MASKS.put(mk, mask);
        }
        int c = f.rgba[i];
        int a = c & 0xFF;
        byte[] b = new byte[tw * th * 4];
        for (int j = 0; j < mask.length; j++) {
            put(b, j * 4, c >>> 8, a * (mask[j] & 0xFF) / 255);
        }
        return b;
    }

    /** Coverage 0..255 of a convex quad (two corners may coincide) per pixel of a tw x th box. */
    private static byte[] coverage(float[] xs, float[] ys, int tw, int th) {
        // inward unit normals of the edges, from the winding
        float area = 0;
        for (int k = 0, j = 3; k < 4; j = k++) {
            area += xs[j] * ys[k] - xs[k] * ys[j];
        }
        float sgn = area >= 0 ? 1f : -1f;
        float[] nx = new float[4];
        float[] ny = new float[4];
        float[] nc = new float[4];
        int ne = 0;
        for (int k = 0; k < 4; k++) {
            int j = (k + 1) & 3;
            float ex = xs[j] - xs[k];
            float ey = ys[j] - ys[k];
            float len = (float) Math.sqrt(ex * ex + ey * ey);
            if (len < 0.01f) {
                continue; // two corners in one place (a triangle)
            }
            nx[ne] = -ey / len * sgn;
            ny[ne] = ex / len * sgn;
            nc[ne] = -(nx[ne] * xs[k] + ny[ne] * ys[k]);
            ne++;
        }
        byte[] m = new byte[tw * th];
        for (int py = 0; py < th; py++) {
            float v = py + 0.5f;
            for (int px = 0; px < tw; px++) {
                float u = px + 0.5f;
                float d = 1e9f;
                for (int k = 0; k < ne; k++) {
                    float dk = nx[k] * u + ny[k] * v + nc[k];
                    if (dk < d) {
                        d = dk;
                    }
                }
                float cov = d + 0.5f;
                m[py * tw + px] = (byte) (cov <= 0 ? 0 : cov >= 1 ? 255 : (int) (cov * 255));
            }
        }
        return m;
    }

    // ---------------------------------------------------------------- HMI thread

    static String onHmiThread(EALManager m, TeleFrame f) {
        if (!f.show) {
            if (root != null && shownVisible) {
                root.setVisible(false);
                m.fireRenderEvent();
            }
            shownVisible = false;
            return null;
        }
        String note = null;
        if (root == null) {
            vp = m.createViewport("ttTele", VIEWPORT_INDEX, m.getScreenWidth(), m.getScreenHeight());
            IWrappedLayer layer = m.createLayer(vp, "ttTele", 0, m.getScreenWidth(), m.getScreenHeight(), 0);
            root = layer == null ? null : layer.getMainNode();
            if (root == null) {
                return "no layer";
            }
            note = new StringBuffer("viewport ").append(m.getScreenWidth()).append('x').append(m.getScreenHeight())
                .toString();
        }
        if (f.probe) {
            String r = probeOnHmi(m, f);
            return note == null ? r : r == null ? note : new StringBuffer(note).append(' ').append(r).toString();
        }
        if (pBuiltMode >= 0) {
            // back from the probe: it cleared the tree
            root.removeAllChildren();
            VIEWS.clear();
            pBuiltMode = -1;
        }
        if (slots == null || f.key != builtKey || slots.length != f.count || builtRotGen != rotGen) {
            // tele11: each view keeps its own group of nodes; a switch hides one and shows the other
            if (builtRotGen != rotGen) {
                root.removeAllChildren(); // rotation or font changed: every node is placed anew
                VIEWS.clear();
            }
            Integer vk = new Integer(f.key);
            ViewNodes vn = (ViewNodes) VIEWS.get(vk);
            if (vn == null || vn.slots.length != f.count) {
                if (vn != null) {
                    root.remove(vn.group);
                }
                vn = new ViewNodes();
                vn.group = m.createNode3D(root, "ttTeleV", 0f, 0f);
                vn.slots = new TeleSlot[f.count];
                for (int i = 0; i < f.count; i++) {
                    TeleSlot s = new TeleSlot();
                    s.group = m.createNode3D(vn.group, "ttTeleS", 0f, 0f);
                    vn.slots[i] = s;
                }
                VIEWS.put(vk, vn);
                note = new StringBuffer(note == null ? "" : note).append(" built ").append(f.count).append(" slots")
                    .toString();
            }
            java.util.Enumeration e = VIEWS.elements();
            while (e.hasMoreElements()) {
                ViewNodes o = (ViewNodes) e.nextElement();
                o.group.setVisible(o == vn);
            }
            slots = vn.slots;
            builtKey = f.key;
            builtRotGen = rotGen;
        }
        uploadsLeft = UPLOADS_PER_JOB;
        needMore = false;
        int changed = 0;
        for (int i = 0; i < f.count; i++) {
            TeleSlot s = slots[i];
            if (s.group == null) {
                continue;
            }
            boolean vis = f.vis[i] && (f.kind[i] != TeleLayout.BOX || (f.w[i] > 0 && f.h[i] > 0));
            if (vis != s.shownVis) {
                s.group.setVisible(vis);
                s.shownVis = vis;
            }
            if (!vis) {
                continue;
            }
            int c0 = s.shownColour;
            int x0 = s.sx;
            String t0 = s.shownText;
            if (f.kind[i] == TeleLayout.TEXT) {
                text(m, s, f, i);
            } else {
                image(m, s, f, i);
            }
            if (c0 != s.shownColour || x0 != s.sx || t0 != s.shownText) {
                changed++;
            }
        }
        synchronized (ART) {
            statSlots += changed;
        }
        root.setVisible(true);
        vp.setForceInvisible(false);
        vp.setVisible(true); // a new viewport starts hidden (Cockpit-drawing trap v25)
        m.fireRenderEvent();
        shownVisible = true;
        return note;
    }

    /**
     * tele8 probe: background, the painted track, the painted lit strip under a clip rectangle whose
     * right edge is the rpm, white marks where the edge should be, and a readout of what EAL made of
     * the clip (its screen rectangle), so a photo tells whether the clip works and where it lands.
     */
    private static String probeOnHmi(EALManager m, TeleFrame f) {
        String note = null;
        if (pBuiltMode != f.pMode) {
            root.removeAllChildren();
            VIEWS.clear();
            slots = null;
            builtKey = -1;
            IWrappedNode3D g = m.createNode3D(root, "ttTeleP", 0f, 0f);
            IWrappedNode3DImage bg = picture(m, g, solid(TeleLayout.BG), SOLID, SOLID);
            bg.setScale(TeleLayout.SCREEN_W / (float) SOLID, TeleLayout.SCREEN_H / (float) SOLID, 1f);
            bg.setPosition(0f, 0f, 0f);
            IWrappedNode3D gt = m.createNode3D(root, "ttTeleP", 0f, 0f);
            IWrappedNode3DImage ti = picture(m, gt, stripTrack, TeleStrip.W, TeleStrip.H);
            ti.setPosition(TeleStrip.X0, TeleStrip.Y0, 0f);
            pClip = m.createNode3D(root, "ttTeleC", 0f, 0f);
            pLitImg = picture(m, pClip, stripLit, TeleStrip.W, TeleStrip.H);
            pLitImg.setPosition(TeleStrip.X0, TeleStrip.Y0, 0f);
            IWrappedNode3D c = clipNode(f.pMode);
            pScr0 = c.getScreenWidth();
            if (pScr0 <= 0) {
                c.setScreenSize(m.getScreenWidth(), m.getScreenHeight()); // crop() would leave nothing
            }
            // the stock order (ComboBoxRendererHigh, MultilineTextFieldRendererHigh)
            c.setClippingInheritance(IWrappedNode3D.CLIPPING_TYPE_INHERIT_FROM_ANCESTOR
                | IWrappedNode3D.CLIPPING_TYPE_INHERIT_TO_CHILDREN);
            c.useClippingRectangle(true);
            setClip(f);
            c.setClipping(true);
            pMarkA = mark(m);
            pMarkB = mark(m);
            IWrappedNode3D gx = m.createNode3D(root, "ttTeleP", 0f, 0f);
            IWrappedFont font = font(2, 20);
            pTxt = font == null ? null : m.createText3D(gx, "ttTeleT", "probe", font);
            if (pTxt != null) {
                pTxt.setColor(TeleLayout.WHITE);
                pTxt.setPosition(40f, 360f, 0f);
            }
            pBuiltMode = f.pMode;
            note = new StringBuffer("probe built, clip on ").append(f.pMode == 1 ? "picture" : "group")
                .append(", node screen width before ").append(pScr0).toString();
        }
        setClip(f);
        pClip.setVisible(f.pLit && f.pCut > TeleStrip.X0);
        pMarkA.setPosition(f.pCut - 1, TeleStrip.Y0 - 14, 0f);
        pMarkB.setPosition(f.pCut - 1, TeleStrip.Y0 + TeleStrip.H + 2, 0f);
        IWrappedNode3D c = clipNode(f.pMode);
        String info = new StringBuffer(f.pMode == 1 ? "img" : "grp").append(" rpm ").append(f.pRpm).append(" cut ")
            .append(f.pCut).append(" | clip on screen x ").append((int) c.getScreenClippingX()).append(" y ")
            .append((int) c.getScreenClippingY()).append(" w ").append((int) c.getScreenClippingWidth()).append(" h ")
            .append((int) c.getScreenClippingHeight()).append(" | screen ").append(c.getScreenWidth()).append('x')
            .append(c.getScreenHeight()).append(" (was ").append(pScr0).append(')').toString();
        probeInfo = info;
        if (pTxt != null) {
            IWrappedFont font = font(2, 20);
            if (font != null) {
                pTxt.setText(new StringBuffer("PROBE ").append(info).toString(), font);
            }
        }
        root.setVisible(true);
        vp.setForceInvisible(false);
        vp.setVisible(true);
        m.fireRenderEvent();
        shownVisible = true;
        return note;
    }

    private static IWrappedNode3D clipNode(int mode) {
        return mode == 1 ? (IWrappedNode3D) pLitImg : pClip;
    }

    /** The clip's right edge at the cut; the rectangle is in the clip node's own coordinates. */
    private static void setClip(TeleFrame f) {
        float w = Math.max(0, Math.min(TeleStrip.W, f.pCut - TeleStrip.X0));
        if (f.pMode == 1) {
            pLitImg.setClippingRectangle(0f, 0f, w, TeleStrip.H);
        } else {
            pClip.setClippingRectangle(TeleStrip.X0, TeleStrip.Y0, w, TeleStrip.H);
        }
    }

    /** A 2 x 12 white mark in its own group (the group is moved). */
    private static IWrappedNode3D mark(EALManager m) {
        IWrappedNode3D g = m.createNode3D(root, "ttTeleP", 0f, 0f);
        IWrappedNode3DImage img = picture(m, g, solid(TeleLayout.WHITE), SOLID, SOLID);
        img.setScale(2f / SOLID, 12f / SOLID, 1f);
        img.setPosition(0f, 0f, 0f);
        return g;
    }

    private static IWrappedNode3DImage picture(EALManager m, IWrappedNode3D parent, byte[] px, int w, int h) {
        ByteBuffer buf = ByteBuffer.allocateDirect(px.length);
        buf.put(px);
        buf.rewind();
        TextureDescription d = m.createTextureDescription(buf, 6, w, h, null, new Object());
        return m.createImage3D(parent, "ttTele", d, 0, true, "ttTele");
    }

    private static void image(EALManager m, TeleSlot s, TeleFrame f, int i) {
        if (i == 0) {
            // tele17: the theme picture in place of the black background
            boolean want = f.theme >= 0 && THEME[f.theme] != null;
            if (want && s.themeImg == null && uploadsLeft > 0) {
                uploadsLeft--;
                ByteBuffer buf = ByteBuffer.allocateDirect(TW * TH * 4);
                buf.put(THEME[f.theme]);
                buf.rewind();
                TextureDescription d = m.createTextureDescription(buf, 6, TW, TH, null, new Object());
                s.themeImg = m.createImage3D(s.group, "ttTele", d, 0, true, "ttTele");
                if (s.themeImg != null) {
                    s.themeImg.setPosition(0f, 0f, 0f);
                }
            }
            if (s.themeImg != null) {
                s.themeImg.setVisible(want);
                if (s.shownImg != null) {
                    s.shownImg.setVisible(!want);
                }
                if (want) {
                    return;
                }
            }
        }
        int c = f.rgba[i];
        if (c != s.shownColour) {
            IWrappedNode3DImage img = null;
            for (int k = 0; k < s.nImgs; k++) {
                if (s.colours[k] == c) {
                    img = s.imgs[k];
                }
            }
            if (img == null) {
                if (s.nImgs >= TeleSlot.MAX_COLOURS) {
                    return; // never expected: each slot uses only a few colours
                }
                if (uploadsLeft <= 0) {
                    needMore = true; // the next job carries on (tele11)
                    return;
                }
                uploadsLeft--;
                img = upload(m, s, f, i);
                if (img == null) {
                    return;
                }
                if (s.nImgs < TeleSlot.MAX_COLOURS) {
                    s.colours[s.nImgs] = c;
                    s.imgs[s.nImgs] = img;
                    s.nImgs++;
                }
                s.sx = Integer.MIN_VALUE; // place the new node
            }
            if (s.shownImg != null && s.shownImg != img) {
                s.shownImg.setVisible(false);
            }
            img.setVisible(true);
            s.shownImg = img;
            s.shownColour = c;
            s.sx = Integer.MIN_VALUE;
        }
        IWrappedNode3DImage img = s.shownImg;
        if (img == null) {
            return;
        }
        // Icons tilt only when rotation is on; turned boxes (rev strip pieces) always turn.
        boolean box = f.kind[i] == TeleLayout.BOX;
        boolean always = box || (f.kind[i] == TeleLayout.ICON && f.size[i] == TeleIcons.ARCQ);
        float rot = always || (f.kind[i] == TeleLayout.ICON && !rotOff) ? f.rot[i] : 0f;
        if (f.x[i] != s.sx || f.y[i] != s.sy || f.w[i] != s.sw || f.h[i] != s.sh || rot != s.shownRot) {
            float uw = img.getUnscaledWidth();
            float uh = img.getUnscaledHeight();
            img.setScale(f.w[i] / (uw > 0 ? uw : 1f), f.h[i] / (uh > 0 ? uh : 1f), 1f);
            if (rot != 0f) {
                // turn a group about the anchor (an icon's top-left corner, a box's start-edge
                // middle), whatever the node's own pivot is
                s.group.setPosition(f.x[i], f.y[i], 0f);
                s.group.setRotationZ(rot * rotSign * rotScale);
                img.setPosition(0f, box ? -f.h[i] / 2f : 0f, 0f);
            } else {
                img.setPosition(f.x[i], f.y[i], 0f);
            }
            s.sx = f.x[i];
            s.sy = f.y[i];
            s.sw = f.w[i];
            s.sh = f.h[i];
            s.shownRot = rot;
        }
    }

    /** Makes the node for this slot's current colour from the picture drawn on the tick thread. */
    private static IWrappedNode3DImage upload(EALManager m, TeleSlot s, TeleFrame f, int i) {
        Long k = artKey(f, i);
        byte[] px = k == null ? null : (byte[]) ART.get(k);
        if (px == null) {
            return null;
        }
        int tw;
        int th;
        if (f.kind[i] == TeleLayout.BOX) {
            tw = SOLID;
            th = SOLID;
        } else if (f.kind[i] == TeleLayout.ICON) {
            tw = f.w[i];
            th = f.h[i];
        } else {
            int sc = quadScale(f, i);
            tw = (f.w[i] + sc - 1) / sc;
            th = (f.h[i] + sc - 1) / sc;
        }
        ByteBuffer buf = ByteBuffer.allocateDirect(px.length);
        buf.put(px);
        buf.rewind();
        TextureDescription d = m.createTextureDescription(buf, 6, tw, th, null, new Object());
        return m.createImage3D(s.group, "ttTele", d, 0, true, "ttTele");
    }

    private static void text(EALManager m, TeleSlot s, TeleFrame f, int i) {
        String str = f.text[i];
        int size = f.size[i];
        int fs = Math.max(8, size * fontPct / 100);
        IWrappedFont font = font(f.bold[i] ? 2 : 1, fs);
        if (font == null || str == null) {
            return;
        }
        boolean changed = false;
        if (s.txt == null) {
            s.txt = m.createText3D(s.group, "ttTeleT", str, font);
            if (s.txt == null) {
                return;
            }
            s.shownText = str;
            changed = true;
        } else if (!str.equals(s.shownText)) {
            s.txt.setText(str, font);
            s.shownText = str;
            changed = true;
        }
        if (f.rgba[i] != s.shownColour) {
            s.txt.setColor(f.rgba[i]); // EAL text colours are 0xRRGGBBAA, as the layout's
            s.shownColour = f.rgba[i];
        }
        float rot = rotOff ? 0f : f.rot[i];
        if (changed || f.x[i] != s.sx || f.y[i] != s.sy || rot != s.shownRot) {
            float dx = 0f;
            if (f.align[i] != TeleLayout.LEFT) {
                int tw = textW(m, str, font, fs);
                dx = f.align[i] == TeleLayout.CENTER ? -tw / 2f : -tw;
            }
            if (rot != 0f) {
                s.group.setPosition(f.x[i], f.y[i], 0f);
                s.group.setRotationZ(rot * rotSign * rotScale);
                s.txt.setPosition(dx, 0.82f * size, 0f); // nodes sit on their baseline (trap v26)
            } else {
                s.txt.setPosition(f.x[i] + dx, f.y[i] + 0.82f * size, 0f);
            }
            s.sx = f.x[i];
            s.sy = f.y[i];
            s.shownRot = rot;
        }
    }

    private static IWrappedFont font(int style, int size) {
        Object f = HMITerminalImpl.fontLoader == null ? null : HMITerminalImpl.fontLoader.getFont(style, size);
        return f instanceof IWrappedFont ? (IWrappedFont) f : null;
    }

    private static int textW(EALManager m, String s, IWrappedFont f, int size) {
        if (s.length() == 0) {
            return 0;
        }
        try {
            return m.getTextWidth(s, f);
        } catch (Throwable t) {
            return (int) (s.length() * size * 0.6f);
        }
    }

    // ---------------------------------------------------------------- wheel, commands

    /**
     * A press of the toggle key (TeleKeys, tele12). Two within 450 ms switch the autocross view on
     * or off.
     */
    public static boolean press() {
        long now = System.currentTimeMillis();
        boolean dbl = now - lastPress < 450L;
        lastPress = dbl ? 0 : now;
        if (dbl) {
            setOn(!on);
            RgdBus.note(on ? "tele on (double press)" : "tele off (double press)");
        }
        return dbl;
    }

    public static String cmd(String arg) {
        java.util.StringTokenizer t = new java.util.StringTokenizer(arg == null ? "" : arg);
        String c = t.hasMoreTokens() ? t.nextToken() : "info";
        String a = t.hasMoreTokens() ? t.nextToken() : "";
        if (c.equals("apply")) {
            try {
                return TeleLink.apply(Integer.parseInt(a));
            } catch (NumberFormatException e) {
                return "tele apply N";
            }
        }
        return info();
    }

    private static void setOn(boolean v) {
        on = v;
        flag(v ? ON_FILE : OFF_FILE, true);
        flag(v ? OFF_FILE : ON_FILE, false);
    }

    private static void readSwitches() {
        on = ON_FILE.exists() || (DEFAULT_ON.exists() && !OFF_FILE.exists());
        demo = DEMO_FILE.exists();
        TeleKeys.install();
        TeleKeys.readSwitches();
        String sp = readLine(SHOT_FILE);
        if (sp != null) {
            SHOT_FILE.delete();
            RgdBus.note(shot(sp.length() > 0 ? sp : "/tmp/tt-shot.png"));
        }
        String bl = readLine(BEEP_FILE);
        if (bl != null) {
            BEEP_FILE.delete();
            RgdBus.note(beepCmd(bl));
        }
        shiftBeep = readLine(SHIFTBEEP_FILE);
        themeOn = THEME_RAM.exists() || THEME_FLASH.exists();
        glide = GLIDE_RAM.exists() || GLIDE_FLASH.exists();
        if (!themeOn) {
            THEME[0] = null;
            THEME[1] = null;
            THEME_BAD[0] = false;
            THEME_BAD[1] = false;
        }
        String pm = readLine(PROBE_FILE);
        probeMode = pm == null ? -1 : pm.equals("img") ? 1 : 0;
        // Default -deg: on the car (tele1) +deg tilted the corner text against the slopes.
        String r = readLine(ROT_FILE);
        float scale = 1f;
        float sign = -1f;
        boolean off = false;
        if (r != null) {
            sign = 1f;
            if (r.startsWith("-")) {
                sign = -1f;
                r = r.substring(1);
            }
            if (r.equals("rad")) {
                scale = (float) (Math.PI / 180.0);
            } else if (r.equals("off")) {
                off = true;
            }
        }
        int fp = 70;
        String fl = readLine(FONT_FILE);
        if (fl != null) {
            try {
                fp = Math.max(30, Math.min(150, Integer.parseInt(fl)));
            } catch (NumberFormatException ignored) {
                // keep 70
            }
        }
        if (fp != fontPct) {
            fontPct = fp;
            rotGen = (rotGen + 1) & 0xFFF; // rebuild: text nodes keep the font they were made with
        }
        if (scale != rotScale || sign != rotSign || off != rotOff) {
            rotScale = scale;
            rotSign = sign;
            rotOff = off;
            rotGen = (rotGen + 1) & 0xFFF; // rebuild, so every node is placed again
        }
    }

    /** A cockpit screenshot through the stock call (the head unit writes the PNG). */
    private static String shot(String path) {
        try {
            EALManager m = TtPanel.ealManager();
            if (m == null) {
                return "tt shot: HMI not up yet";
            }
            m.getTerminal().getFramework().getHMIService().takeScreenshot(0, path);
            return new StringBuffer("tt shot: requested ").append(path).toString();
        } catch (Throwable t) {
            return new StringBuffer("tt shot failed ").append(String.valueOf(t)).toString();
        }
    }

    /** "N [system|ring]", "default [system|ring]" or "stop". */
    private static String beepCmd(String l) {
        java.util.StringTokenizer t = new java.util.StringTokenizer(l);
        String a = t.hasMoreTokens() ? t.nextToken() : "";
        String which = t.hasMoreTokens() ? t.nextToken() : "system";
        if (a.equals("stop")) {
            return TeleBeep.stop();
        }
        if (a.equals("default")) {
            return TeleBeep.play(which, -1);
        }
        try {
            return TeleBeep.play(which, Integer.parseInt(a));
        } catch (NumberFormatException e) {
            return "tele beep: N [system|ring] | default [system|ring] | stop";
        }
    }

    /** Shift tone test: once per rise past the shift point, re-armed 300 rpm below it. */
    private static void shiftTone(TeleLayout layout) {
        String sb = shiftBeep;
        if (sb == null) {
            return;
        }
        int rpm = layout.link && layout.has[TeleLayout.CH_RPM] ? (layout.val[TeleLayout.CH_RPM] + 500) / 1000 : 0;
        if (beepArmed && rpm >= layout.shiftRpm) {
            beepArmed = false;
            RgdBus.note(new StringBuffer("tele shift tone at ").append(rpm).append(" rpm: ").append(beepCmd(sb))
                .toString());
        } else if (!beepArmed && rpm < layout.shiftRpm - 300) {
            beepArmed = true;
        }
    }

    private static void flag(File f, boolean v) {
        try {
            if (v) {
                new FileOutputStream(f).close();
            } else {
                f.delete();
            }
        } catch (Throwable ignored) {
            // the field still holds the state until the next restart
        }
    }

    static String info() {
        synchronized (ART) {
            return new StringBuffer("tele on=").append(on).append(" demo=").append(demo).append(" map=").append(mapNow)
                .append(" visible=").append(shownVisible).append(" jobs=").append(jobs).append(" jobMax=")
                .append(jobMsMax).append("ms art=").append(ART.size()).append(" rot=")
                .append(rotOff ? 0f : rotSign * rotScale).append(" probe=").append(probeMode).append(' ').append(probeInfo)
                .append(' ').append(TeleLink.info()).toString();
        }
    }

    private static int readKey() {
        try {
            String l = readLine(KEY_FILE);
            return l == null ? 0 : (int) Long.parseLong(l);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static String readLine(File f) {
        if (!f.exists()) {
            return null;
        }
        try {
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            try {
                String l = r.readLine();
                return l == null ? null : l.trim();
            } finally {
                r.close();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
