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
    /** Above the turn panel's 225, so the autocross view covers it. */
    private static final int VIEWPORT_INDEX = 228;
    private static final int SOLID = 8;
    private static final Hashtable ART = new Hashtable();

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
    private static volatile int rotGen;
    private static long lastPress;
    private static long jobs;
    private static long jobMsMax;
    private static long statJobs;
    private static long statMs;
    private static long statMax;
    private static long statAt;
    private static long statSlots;
    private static int artKey = -1;
    private static volatile long postedAt;
    private static long lostJobs;

    // HMI thread only
    private static IWrappedViewport vp;
    private static IWrappedNode3D root;
    private static TeleSlot[] slots;
    private static int builtKey = -1;
    private static int builtRotGen = -1;
    private static boolean shownVisible;

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
                    boolean small = ZoomGate.small();
                    layout.build(small ? TeleLayout.VIEW_SMALL : TeleLayout.VIEW_LARGE, TeleLayout.PAGE_LIVE);
                    f = TeleFrame.of(layout, (small ? 1 : 0) | (rotGen << 4));
                    prepareArt(f);
                } else {
                    f = TeleFrame.hidden();
                }
                if (f.sameAs(last)) {
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
                .append(ART.size()).append(" | ").append(TeleLink.rxStats()).toString();
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
        if (f.key != artKey) {
            ART.clear(); // quads belong to one view's geometry
            artKey = f.key;
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
            return new Long(((long) (100000 + i) << 32) | (c & 0xFFFFFFFFL));
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

    /** A quad filled into its bounding box, 4x4 samples per pixel for soft edges. */
    private static byte[] quad(TeleFrame f, int i, int sc) {
        int tw = (f.w[i] + sc - 1) / sc;
        int th = (f.h[i] + sc - 1) / sc;
        float[] xs = new float[4];
        float[] ys = new float[4];
        for (int k = 0; k < 4; k++) {
            xs[k] = (f.qx[4 * i + k] - f.x[i]) / (float) sc;
            ys[k] = (f.qy[4 * i + k] - f.y[i]) / (float) sc;
        }
        int c = f.rgba[i];
        int a = c & 0xFF;
        byte[] b = new byte[tw * th * 4];
        for (int py = 0; py < th; py++) {
            for (int px = 0; px < tw; px++) {
                int hits = 0;
                for (int sy = 0; sy < 4; sy++) {
                    for (int sx = 0; sx < 4; sx++) {
                        if (inside(xs, ys, px + (sx + 0.5f) / 4f, py + (sy + 0.5f) / 4f)) {
                            hits++;
                        }
                    }
                }
                put(b, (py * tw + px) * 4, c >>> 8, a * hits / 16);
            }
        }
        return b;
    }

    private static boolean inside(float[] xs, float[] ys, float u, float v) {
        boolean in = false;
        for (int i = 0, j = 3; i < 4; j = i++) {
            if ((ys[i] > v) != (ys[j] > v) && u < (xs[j] - xs[i]) * (v - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
                in = !in;
            }
        }
        return in;
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
        if (slots == null || f.key != builtKey || slots.length != f.count || builtRotGen != rotGen) {
            root.removeAllChildren();
            slots = new TeleSlot[f.count];
            for (int i = 0; i < f.count; i++) {
                TeleSlot s = new TeleSlot();
                s.group = m.createNode3D(root, "ttTeleS", 0f, 0f);
                slots[i] = s;
            }
            builtKey = f.key;
            builtRotGen = rotGen;
            note = new StringBuffer(note == null ? "" : note).append(" built ").append(f.count).append(" slots")
                .toString();
        }
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

    private static void image(EALManager m, TeleSlot s, TeleFrame f, int i) {
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
        float rot = f.kind[i] == TeleLayout.ICON && !rotOff ? f.rot[i] : 0f;
        if (f.x[i] != s.sx || f.y[i] != s.sy || f.w[i] != s.sw || f.h[i] != s.sh || rot != s.shownRot) {
            float uw = img.getUnscaledWidth();
            float uh = img.getUnscaledHeight();
            img.setScale(f.w[i] / (uw > 0 ? uw : 1f), f.h[i] / (uh > 0 ? uh : 1f), 1f);
            if (rot != 0f) {
                // turn a group about the icon's top-left corner, whatever the node's own pivot is
                s.group.setPosition(f.x[i], f.y[i], 0f);
                s.group.setRotationZ(rot * rotSign * rotScale);
                img.setPosition(0f, 0f, 0f);
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
     * A scroll-wheel press. Two within 450 ms switch the autocross view on or off. Not wired yet:
     * how the press reaches the HMI is still to be traced in the car.
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
                .append(rotOff ? 0f : rotSign * rotScale).append(' ').append(TeleLink.info()).toString();
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
