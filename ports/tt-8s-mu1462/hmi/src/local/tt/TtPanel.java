package local.tt;

import java.nio.ByteBuffer;

import de.audi.atip.hmi.event.RunnableEvent;
import de.esolutions.hmi.widgets.audi.base.HMITerminalImpl;
import de.esolutions.hmi.widgets.audi.base.Layout;
import de.esolutions.hmi.widgets.audi.base.eal.EALManager;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3D;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DImage;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DText;
import de.esolutions.hmi.widgets.audi.base.eal.TextureDescription;

/**
 * Our own drawing on the TT cockpit (tt-zoom v19, first stage: "hello panel").
 *
 * The TT's built-in guidance view only draws the nav engine's own route
 * (v15-v18 parked tests), so iPhone guidance has to be drawn by us. The
 * cockpit is the HMI's main terminal; its scene graph is built through
 * EALManager (the e.solutions engine layer): image nodes from RGBA byte
 * buffers, text nodes in the HMI's fonts, plain group nodes. Every EAL call
 * runs on the HMI event thread, posted as a RunnableEvent.
 *
 * The EALManager comes from the patched AnimationMIB2HighSport.setViewSize
 * (called at start-up and on every big/small stage change); if that never
 * fired, CombiMapController's static hmiService is used instead.
 *
 * Playback commands (local.tt.RgiPlayer, "panel ..."):
 *   panel info                          log screen size, layer nodes and KDK layout slots
 *   panel show PARENT X Y W H COLOR [TEXT]   draw a test box: PARENT front|screens|root,
 *                                       COLOR rrggbb, TEXT optional (white, HMI font)
 *   panel hide                          remove our nodes
 *   panel try MODE X Y W H COLOR [TEXT] v20 variants (front layer), each logs native transforms:
 *       group  image + text inside a group node (v19 way, solid colour, no border)
 *       direct image straight under the layer, positioned and scaled like the stock IconRendererHigh
 *       text   text node only, straight under the layer
 *       lines  QuickDraw outline rectangle
 * v19 car result: nodes drawn at the right place but squashed to a thin strip.
 * v20 found why: on the car createTextureDescription takes (buffer, FORMAT, width, height)
 * and getFont takes (style 1 plain / 2 bold, SIZE). Both fixed in v21.
 *
 * v21 turn panel (PanelArt picture + HMI-font text), drawn at the stock banner spot
 * (layout constants: small view 991,152, large view 1105,110) unless "panel at" moved it:
 *   panel turn MAIN,DIR[,-1,ARM...] METRES BAR LANES DEST ETA EXIT STREET...  (v23)
 *       DEST metres to the destination (-1 none); ETA UTC epoch seconds, or seconds from now
 *       when small (playback), -1 none; EXIT exit number or -
 *       BAR -1: no bar; LANES - for none, else lanes separated by '/', each
 *       ANGLE[,ANGLE...]:STATUS (degrees, positive right; status 0/1/2), e.g. -90:2/0:0/0:0/90:0
 *   panel at X Y | panel at auto | panel off
 * live(): the iPhone feed (RgdBus) calls this on every change; /tmp/tt-panel-off disables it.
 *
 * v22 (car photos): the 300x340 panel was too big in the large view (half behind the speedometer,
 * cut at the right edge) and invisible in the small view, where the car's own page covers the
 * banner spot. Large view: compact 260x140 panel above the speedometer. Small view: the stock banner
 * is switched on (TtRgi) and the tall panel drawn at its spot. Position and scale per view are read
 * from /tmp/tt-panel-pos (lines: large X Y SCALE / small X Y SCALE), so they can be tuned live.
 *
 * v23 (user's layout): large view = two banners like stock nav, LEFT (maneuver: picture, distance,
 * exit number or street, lanes, bar) and RIGHT (destination flag, distance, arrival time); the maps
 * already label roads. Small view = one FULL panel on the right with everything. pos-file keys:
 * left / right / small.
 *
 * v24 (car photos): in the small view the stock banner (KDK displayable 20, drawn by the nav engine
 * and composited by the display manager) covers our panel, and on the car tab the cluster shows its
 * own page. Keeping the banner "on" makes the cluster show the head unit's picture at the banner spot
 * (every tab); making displayable 20 transparent should let our panel show there. Commands:
 *   panel kdk info | kdk alpha N (0-100, -1 = stock) | kdk pos X Y | kdk pos stock | kdk force 1|0
 *   panel layout auto|full|banners   large view: banners (map tab) or the full panel at the flap spot
 * pos-file key for the large-view full panel: flap (default 1105,110).
 *
 * v25 (v24 photos): with displayable 20 transparent the nav engine picture is gone, but the HMI's
 * grey KDK frame (EAL viewport 220, G24_KDK_BACKGROUND) still covers our panel, which lived in the
 * main viewport (210). Panels now go into our own viewport at index 225 (panel parent top|front),
 * the frame can be hidden (panel kdkbg hide|show), and pictures are rendered on the feed thread
 * (TurnSpec, prerender) so the HMI thread no longer stalls 300-800 ms per redraw.
 * v26 (v25 photos: frame transparent/hidden, but none of our panels drew): a new EAL viewport starts
 * hidden; topVp.setVisible(true) is now called after every redraw.
 * v27 (v26 photos: drawing works; texts sat too high, the stock frame is above our layer and
 * shows us only through its window, the nav engine's straight line flashed on every banner show):
 * one 210x150 panel in the stock banner window on every view (layout kdk, pos keys kdk-small /
 * kdk-large), texts placed by baseline, the stock banner on for CarPlay routes everywhere, the
 * nav picture kept transparent by a watcher (KdkGuard), and "panel grid on|off" for calibration.
 * Results go to /tmp/tt-rgd.log ("panel ..."). Java 1.4 library only.
 */
public final class TtPanel {
    private static final Object LOCK = new Object();
    private static EALManager eal;
    private static IWrappedNode3D root;
    private static IWrappedNode3DImage box;
    private static IWrappedNode3DText label;
    private static int serial;

    private TtPanel() {
    }

    /** Patched AnimationMIB2HighSport.setViewSize(eal, layout, small): remembers the cockpit EALManager. */
    public static void eal(Object o) {
        try {
            if (o instanceof EALManager) {
                boolean first;
                synchronized (LOCK) {
                    first = eal == null;
                    eal = (EALManager) o;
                }
                if (first) {
                    RgdBus.note("panel eal from setViewSize");
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** The cockpit's EALManager for other views (TeleView). */
    static EALManager ealManager() {
        return manager();
    }

    private static EALManager manager() {
        synchronized (LOCK) {
            if (eal != null) {
                return eal;
            }
        }
        try {
            java.lang.reflect.Field f = Class.forName(
                "de.esolutions.hmi.widgets.audi.evo.high.widgets.CombiMapController").getDeclaredField("hmiService");
            f.setAccessible(true);
            Object svc = f.get(null);
            if (svc instanceof de.audi.tghu.hmi.evo.IHMIServiceEvo) {
                Object t = ((de.audi.tghu.hmi.evo.IHMIServiceEvo) svc).getHMITerminal(0);
                if (t instanceof de.esolutions.hmi.widgets.audi.base.eal.HMITerminalEAL) {
                    EALManager m = ((de.esolutions.hmi.widgets.audi.base.eal.HMITerminalEAL) t).getEALManager();
                    synchronized (LOCK) {
                        eal = m;
                    }
                    RgdBus.note("panel eal from hmiService");
                    return m;
                }
            }
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("panel no eal: ").append(String.valueOf(t)).toString());
        }
        return null;
    }

    /** One "panel" playback command; the work is queued on the HMI thread. */
    public static String cmd(String arg) {
        if (arg.startsWith("turn ")) {
            prerender(arg);
        }
        EALManager m = manager();
        if (m == null) {
            return "panel: no EALManager yet";
        }
        try {
            HMITerminalImpl term = m.getTerminal();
            int id = ++serial;
            term.getFramework().getHMIService().getEventDispatcher().postEvent(
                new RunnableEvent(new PanelJob(m, arg, id)));
            return new StringBuffer("panel #").append(id).append(" queued: ").append(arg).toString();
        } catch (Throwable t) {
            return new StringBuffer("panel post failed ").append(String.valueOf(t)).toString();
        }
    }

    /** Runs on the HMI event thread (PanelJob). */
    static String onHmiThread(EALManager m, String arg, int id) {
        String[] w = words(arg);
        String op = w.length > 0 ? w[0] : "";
        if (op.equals("info")) {
            StringBuffer b = new StringBuffer("screen ");
            b.append(m.getScreenWidth()).append('x').append(m.getScreenHeight());
            b.append(" front=").append(describe(m.getPartialPopupsFrontNode()));
            b.append(" screens=").append(describe(m.getScreensNode()));
            try {
                Layout lay = m.getTerminal().getLayout();
                b.append(" layout=").append(lay == null ? "null" : lay.getClass().getName());
                int[] ids = { 58, 59, 60, 61, 118, 119, 120, 121, 122, 123, 124, 125 };
                for (int i = 0; i < ids.length && lay != null; i++) {
                    b.append(' ').append(ids[i]).append('=');
                    try {
                        b.append(lay.getIntegerConstant(ids[i]));
                    } catch (Throwable t) {
                        b.append('?');
                    }
                }
            } catch (Throwable t) {
                b.append(" layout error ").append(String.valueOf(t));
            }
            return b.toString();
        }
        if (op.equals("hide")) {
            clear(m);
            m.fireRenderEvent();
            return "hidden";
        }
        if (op.equals("show") && w.length >= 7) {
            clear(m);
            IWrappedNode3D parent = w[1].equals("screens") ? m.getScreensNode()
                : w[1].equals("root") ? (IWrappedNode3D) m.getMasterRoot() : m.getPartialPopupsFrontNode();
            if (parent == null) {
                return new StringBuffer("no parent node ").append(w[1]).toString();
            }
            int x = num(w[2]);
            int y = num(w[3]);
            int bw = Math.max(8, Math.min(1024, num(w[4])));
            int bh = Math.max(8, Math.min(1024, num(w[5])));
            int rgb = hex(w[6]);
            StringBuffer text = new StringBuffer();
            for (int i = 7; i < w.length; i++) {
                if (i > 7) {
                    text.append(' ');
                }
                text.append(w[i]);
            }
            root = m.createNode3D(parent, "ttPanel", (float) bw, (float) bh);
            if (root == null) {
                return "createNode3D returned null";
            }
            root.setPosition((float) x, (float) y, 0f);
            ByteBuffer buf = ByteBuffer.allocateDirect(bw * bh * 4);
            paintBox(buf, bw, bh, rgb);
            TextureDescription d = m.createTextureDescription(buf, 6, bw, bh, null, new Object());
            box = m.createImage3D(root, "ttPanelBox", d, 0, true, "ttPanel");
            StringBuffer b = new StringBuffer();
            if (box == null) {
                b.append("image null");
            } else {
                box.setSize((float) bw, (float) bh);
                b.append("image ok");
            }
            if (text.length() > 0) {
                Object font = HMITerminalImpl.fontLoader == null ? null : HMITerminalImpl.fontLoader.getFont(1, 28);
                if (font instanceof de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont) {
                    label = m.createText3D(root, "ttPanelText", text.toString(),
                        (de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont) font);
                    if (label != null) {
                        label.setColor(0xFFFFFFFF);
                        label.setPosition(12f, 12f, 0f);
                        b.append(", text ok");
                    } else {
                        b.append(", text null");
                    }
                } else {
                    b.append(", font ").append(String.valueOf(font));
                }
            }
            root.setVisible(true);
            m.fireRenderEvent();
            b.append(" at ").append(x).append(',').append(y).append(' ').append(bw).append('x').append(bh)
                .append(" parent ").append(describe(parent));
            return b.toString();
        }
        if (op.equals("at") && w.length >= 2) {
            if (w[1].equals("auto")) {
                posX = Integer.MIN_VALUE;
            } else if (w.length >= 3) {
                posX = num(w[1]);
                posY = num(w[2]);
            }
            if (mainRoot != null) {
                place(mainRoot, ZoomGate.small() ? "small" : "left");
            }
            m.fireRenderEvent();
            return "moved";
        }
        if (op.equals("turn") && w.length >= 8) {
            return turnCmd(m, w, id >= 0);
        }
        if (op.equals("kdk") && w.length >= 2) {
            return kdkCmd(m, w);
        }
        if (op.equals("kdkguard")) {
            return guardCheck(m);
        }
        if (op.equals("grid") && w.length >= 2) {
            gridFull = w[1].equals("full");
            return gridCmd(m, w[1].equals("on") || gridFull);
        }
        if (op.equals("parent") && w.length >= 2) {
            clearTurn(m);
            shownMode = -1;
            parentMode = w[1];
            if (lastTurn != null) {
                post(lastTurn, false);
            }
            return new StringBuffer("parent ").append(parentMode).toString();
        }
        if (op.equals("kdkbg") && w.length >= 2) {
            hideKdkBg = w[1].equals("hide");
            try {
                if (!hideKdkBg) {
                    m.fireRenderEvent();
                } else {
                    m.hideKDKBackground();
                }
            } catch (Throwable e) {
                return new StringBuffer("kdkbg error ").append(String.valueOf(e)).toString();
            }
            return new StringBuffer("kdkbg ").append(w[1]).append(" (show returns at the next stock redraw)").toString();
        }
        if (op.equals("layout") && w.length >= 2) {
            layoutForce = w[1];
            if (lastTurn != null) {
                post(lastTurn, false);
            }
            return new StringBuffer("layout ").append(layoutForce).toString();
        }
        if (op.equals("where")) {
            RgdBus.note(whereReport(m));
            return "where";
        }
        if (op.equals("fade") && w.length >= 2) {
            try {
                fadeNow = Float.parseFloat(w[1]);
            } catch (Throwable t) {
                fadeNow = 1f;
            }
            applyFade();
            m.fireRenderEvent();
            return "fade";
        }
        if (op.equals("off")) {
            fadeNow = 1f;
            clearTurn(m);
            lastTurn = null;
            // v29: the stock banner stays transparent after our panel goes (v28 showed its empty black
            // picture for a few seconds at the end of a route). Stock navigation is unaffected: every
            // time it shows the banner it sets opacity 0 and fades it in itself.
            m.fireRenderEvent();
            return "turn panel off";
        }
        if (op.equals("try") && w.length >= 7) {
            return tryMode(m, w);
        }
        return new StringBuffer("unknown: ").append(arg).toString();
    }

    private static String tryMode(EALManager m, String[] w) {
        clear(m);
        IWrappedNode3D parent = m.getPartialPopupsFrontNode();
        String mode = w[1];
        int x = num(w[2]);
        int y = num(w[3]);
        int bw = Math.max(8, Math.min(1024, num(w[4])));
        int bh = Math.max(8, Math.min(1024, num(w[5])));
        int rgb = hex(w[6]);
        StringBuffer text = new StringBuffer();
        for (int i = 7; i < w.length; i++) {
            if (i > 7) {
                text.append(' ');
            }
            text.append(w[i]);
        }
        StringBuffer b = new StringBuffer(mode);
        if (mode.equals("group")) {
            root = m.createNode3D(parent, "ttPanel", (float) bw, (float) bh);
            root.setPosition((float) x, (float) y, 0f);
            box = image(m, root, bw, bh, rgb);
            if (box != null) {
                box.setPosition(0f, 0f, 0f);
                box.setScale((float) bw / box.getUnscaledWidth(), (float) bh / box.getUnscaledHeight(), 1f);
            }
            label = text(m, root, text.toString(), 40);
            if (label != null) {
                label.setPosition(10f, 10f, 0f);
            }
        } else if (mode.equals("direct")) {
            box = image(m, parent, bw, bh, rgb);
            if (box != null) {
                box.setScale((float) bw / box.getUnscaledWidth(), (float) bh / box.getUnscaledHeight(), 1f);
                box.setPosition((float) x, (float) y, 0f);
            }
        } else if (mode.equals("text")) {
            label = text(m, parent, text.toString(), 40);
            if (label != null) {
                label.setColor(rgba(0xFF000000 | rgb));
                label.setPosition((float) x, (float) y, 0f);
            }
        } else if (mode.equals("lines")) {
            de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DQuickDraw q =
                m.createQuickDrawNode3D(parent, "ttPanelLines", bw, bh, 2);
            if (q == null) {
                b.append(" quickdraw null");
            } else {
                q.setLineWidth(6f);
                q.setLineColor(0xFF000000 | rgb);
                q.add(0f, 0f);
                q.add((float) bw, 0f);
                q.add((float) bw, (float) bh);
                q.add(0f, (float) bh);
                q.add(0f, 0f);
                q.setPosition((float) x, (float) y, 0f);
                root = q;
            }
        }
        m.fireRenderEvent();
        b.append(" | root ").append(dump(root)).append(" | box ").append(dump(box)).append(" | text ").append(dump(label));
        b.append(" | parent ").append(dump(parent));
        IWrappedNode3D p = parent == null ? null : parent.getParent();
        for (int i = 0; i < 4 && p != null; i++) {
            b.append(" | up").append(i + 1).append(' ').append(dump(p));
            p = p.getParent();
        }
        return b.toString();
    }

    private static IWrappedNode3DImage image(EALManager m, IWrappedNode3D parent, int bw, int bh, int rgb) {
        ByteBuffer buf = ByteBuffer.allocateDirect(bw * bh * 4);
        byte r = (byte) (rgb >> 16);
        byte g = (byte) (rgb >> 8);
        byte bl = (byte) rgb;
        for (int i = 0; i < bw * bh; i++) {
            buf.put(r).put(g).put(bl).put((byte) -1);
        }
        buf.rewind();
        TextureDescription d = m.createTextureDescription(buf, 6, bw, bh, null, new Object());
        return m.createImage3D(parent, "ttPanelBox", d, 0, true, "ttPanel");
    }

    private static IWrappedNode3DText text(EALManager m, IWrappedNode3D parent, String t, int size) {
        if (t.length() == 0) {
            return null;
        }
        Object font = HMITerminalImpl.fontLoader == null ? null : HMITerminalImpl.fontLoader.getFont(1, size);
        if (!(font instanceof de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont)) {
            return null;
        }
        IWrappedNode3DText n = m.createText3D(parent, "ttPanelText", t,
            (de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont) font);
        if (n != null) {
            n.setColor(0xFFFFFFFF);
        }
        return n;
    }

    /** Wrapper and native transform of a node, for the log. */
    private static String dump(IWrappedNode3D n) {
        if (n == null) {
            return "-";
        }
        StringBuffer b = new StringBuffer();
        try {
            b.append("w[").append(n.getX()).append(',').append(n.getY()).append(' ').append(n.getWidth()).append('x')
                .append(n.getHeight()).append(" u ").append(n.getUnscaledWidth()).append('x').append(n.getUnscaledHeight())
                .append(" o ").append(n.getOriginX()).append(',').append(n.getOriginY()).append(']');
            de.esolutions.graphics.eal.api.INode3D e = n.getNode();
            b.append(" n[").append(e.getName()).append(" t ").append(e.getTranslationX()).append(',')
                .append(e.getTranslationY()).append(',').append(e.getTranslationZ()).append(" s ").append(e.getScaleX())
                .append(',').append(e.getScaleY()).append(',').append(e.getScaleZ()).append(']');
            if (n instanceof IWrappedNode3DImage) {
                de.esolutions.graphics.eal.api.INode3DImage im = ((IWrappedNode3DImage) n).getImageNode();
                b.append(" img ").append(im.getWidth()).append('x').append(im.getHeight());
            }
        } catch (Throwable t) {
            b.append(" dump error ").append(String.valueOf(t));
        }
        return b.toString();
    }

    // ---- turn panels (v23 layouts) ---------------------------------------------------
    // Large view (map tab): LEFT banner = maneuver (picture, distance, exit or street, lanes, bar),
    // RIGHT banner = destination (flag, distance, arrival time), like stock nav's two boxes.
    // Small view (tach in the centre): one FULL panel on the right with everything.
    private static final int LEFT_W = 270;
    private static final int LEFT_H = 118;
    private static final int LEFT_H_LANES = 168;
    private static final int RIGHT_W = 210;
    private static final int RIGHT_H = 78;
    private static final int FULL_W = 300;
    private static final int FULL_H = 380;
    private static final java.io.File LIVE_OFF = new java.io.File("/tmp/tt-panel-off");
    private static final java.io.File POS = new java.io.File("/tmp/tt-panel-pos");
    private static int posX = Integer.MIN_VALUE;
    private static int posY;
    private static IWrappedNode3D mainRoot;
    private static IWrappedNode3DImage mainImage;
    private static IWrappedNode3DText text1;
    private static IWrappedNode3DText text2;
    private static IWrappedNode3DText text3;
    /** v27: the street under "Exit 12A" in the kdk layout. */
    private static IWrappedNode3DText text4;
    /** v27: our kdk panel is up, so the watcher keeps the nav engine's banner picture transparent. */
    private static volatile boolean kdkPanelUp;
    private static IWrappedNode3D destRoot;
    private static IWrappedNode3DImage destImage;
    private static IWrappedNode3DText dest1;
    private static IWrappedNode3DText dest2;
    private static String artKey;
    private static String liveKey;
    private static String lastTurn;
    private static boolean liveShown;
    private static int shownMode = -1;

    /**
     * The iPhone feed: called by RgdBus after every update (any thread). Queues a redraw only
     * when what is shown changes; the bar is quantised to 16 steps to limit picture redraws.
     */
    public static void live(boolean on, short[] icon, int metres, int bar, String lanes, int destM, long etaUtc,
                            String exit, String street) {
        try {
            if (!on || icon == null || icon.length < 2 || LIVE_OFF.exists()) {
                if (liveShown) {
                    liveShown = false;
                    liveKey = null;
                    post("off", false);
                    diagSoon("route off");
                }
                return;
            }
            long now = System.currentTimeMillis();
            if (!liveShown) {
                startUntil = now + 10000L;
            }
            if (lastNear && !near) {
                holdUntil = now + 5000L;
            }
            lastNear = near;
            updateAlpha(now);
            int[] ic = new int[icon.length];
            for (int i = 0; i < icon.length; i++) {
                ic[i] = icon[i];
            }
            // v40: 64 bar levels (the bar glides between the iPhone's rounded distances).
            int q = bar < 0 ? -1 : (bar / 4) * 4;
            // Destination distance in 0.1 mi steps, so it does not redraw every metre.
            int dq = destM < 0 ? -1 : (int) ((destM / 160) * 160);
            String k = rebuildArg(ic, metres, q, lanes, dq, etaUtc, exit, street);
            if (!k.equals(liveKey)) {
                liveKey = k;
                boolean first = !liveShown;
                if (first) {
                    burst(2000L);
                }
                liveShown = true;
                prerender(k);
                post(k, false);
                if (first) {
                    diagSoon("route on");
                }
            }
            startFader();
        } catch (Throwable ignored) {
        }
    }

    // ---- v45: banner only near turns on the CarPlay map, translucent background ------------
    /** Set by RgdBus before every live(): the next turn is close (RgdBus.nearTurn). */
    static volatile boolean near = true;
    private static boolean lastNear = true;
    private static volatile long holdUntil;
    private static volatile long startUntil;
    /** v46: hiding far from turns is opt-in (kit: sh tt.sh banner auto); default always shown. */
    private static final java.io.File AUTO = new java.io.File("/tmp/tt-panel-auto");
    /** Background opacity in percent on the CarPlay map (kit: sh tt.sh banner alpha N), default 95 (as before). */
    private static final java.io.File ALPHA = new java.io.File("/tmp/tt-panel-alpha");
    private static long alphaReadAt;
    private static int alphaPct = 95;
    private static boolean faderRunning;
    /** Opacity currently applied to our panel on the HMI thread (0 = hidden). */
    private static float fadeNow = 1f;
    private static boolean shownLogged = true;

    /** Whether the panel should be visible now. */
    static boolean wantVisible() {
        if (!liveShown || !AUTO.exists() || !Layers.on() || near) {
            return true;
        }
        long now = System.currentTimeMillis();
        return now < holdUntil || now < startUntil;
    }

    /** Background alpha: solid look off the map, the chosen opacity on the CarPlay map. */
    private static void updateAlpha(long now) {
        if (now - alphaReadAt > 2000L) {
            alphaReadAt = now;
            int p = 95;
            java.io.FileInputStream in = null;
            try {
                if (ALPHA.exists()) {
                    in = new java.io.FileInputStream(ALPHA);
                    byte[] b = new byte[16];
                    int n = in.read(b);
                    p = Integer.parseInt(new String(b, 0, Math.max(0, n)).trim());
                }
            } catch (Throwable t) {
                p = 95;
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (Throwable ignored) {
                }
            }
            alphaPct = p < 0 ? 0 : (p > 100 ? 100 : p);
        }
        int a = Layers.on() ? alphaPct * 255 / 100 : 0xF2;
        if (a != PanelArt.bgAlpha) {
            PanelArt.bgAlpha = a;
            liveKey = null;
        }
    }

    private static synchronized void endFader() {
        faderRunning = false;
    }

    private static synchronized void startFader() {
        if (faderRunning) {
            return;
        }
        faderRunning = true;
        Thread t = new Thread(new PanelFade(), "ttPanelFade");
        t.setDaemon(true);
        t.start();
    }

    /** Steps the panel opacity towards wantVisible() (about 0.3 s per fade) while a route runs. */
    static void fadeLoop() {
        float cur = 1f;
        try {
            while (liveShown) {
                float target = wantVisible() ? 1f : 0f;
                if (cur != target) {
                    cur = target > cur ? Math.min(1f, cur + 0.25f) : Math.max(0f, cur - 0.25f);
                    post(new StringBuffer("fade ").append(cur).toString(), false);
                    if (cur == target) {
                        RgdBus.note(target > 0f ? "panel: shown (turn near)" : "panel: hidden until the next turn");
                    }
                    Thread.sleep(80L);
                } else {
                    Thread.sleep(250L);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            endFader();
            fadeNow = 1f;
        }
    }

    /** HMI thread: apply fadeNow to our panel (also after it was rebuilt). */
    private static void applyFade() {
        if (mainRoot == null) {
            return;
        }
        try {
            // v48 (car photo: picture black, text only): setOpacity(1f) is not "fully visible" on
            // this HMI. The node's own opacity is only touched while a fade runs, scaled from the
            // value it had, and restored afterwards.
            if (fadeNow >= 1f) {
                if (fadeRoot == mainRoot) {
                    mainRoot.setOpacity(fadeBase);
                    fadeRoot = null;
                }
                mainRoot.setVisible(true);
            } else if (fadeNow <= 0f) {
                mainRoot.setVisible(false);
            } else {
                if (fadeRoot != mainRoot) {
                    fadeBase = mainRoot.getOpacity();
                    fadeRoot = mainRoot;
                    RgdBus.note(new StringBuffer("panel: node opacity ").append(fadeBase).toString());
                }
                mainRoot.setOpacity(fadeBase * fadeNow);
                mainRoot.setVisible(true);
            }
        } catch (Throwable ignored) {
        }
    }

    /** The panel node whose opacity a fade changed, and its original value. */
    private static IWrappedNode3D fadeRoot;
    private static float fadeBase = 1f;

    /** v50 Layers loop: /tmp/tt-panel-pos changed (redraw now) or /tmp/tt-panel-where asked for a report. */
    private static long posStamp = -1L;
    private static final java.io.File WHERE = new java.io.File("/tmp/tt-panel-where");
    /** "where": skin, view, mode, our panel and the stock window (v57: in its own method, plus diagState). */
    private static String whereReport(EALManager m) {
        StringBuffer b = new StringBuffer("where: skin=").append(ZoomGate.skinNow())
            .append(" small=").append(ZoomGate.small()).append(" smallMap=").append(ZoomGate.smallMap())
            .append(" mode=").append(currentMode())
            .append(" mapShowing=").append(mapShowing).append(" layers=").append(Layers.on())
            .append(diagState());
        if (mainRoot != null) {
            b.append(" panel at ").append((int) mainRoot.getX()).append(',').append((int) mainRoot.getY())
                .append(" visible=").append(mainRoot.isVisible());
        } else {
            b.append(" panel none (no route?)");
        }
        de.audi.atip.hmi.view.IDisplayManager d = dm(m);
        if (d != null) {
            try {
                int[] p = d.getPosition(KDK, 0);
                b.append(" | stock window pos ");
                if (p == null) {
                    b.append("null");
                } else {
                    b.append(p[0]).append(',').append(p[1]);
                }
            } catch (Throwable t) {
                b.append(" | pos error ").append(String.valueOf(t));
            }
            b.append(ext(d, KDK, " ext(20) ")).append(ext(d, 0, " ext(0) "));
            try {
                b.append(" opacity ").append(d.getOpacity(KDK, 0)).append(" ctx ").append(d.getCurrentContextID(0));
            } catch (Throwable ignored) {
            }
            b.append(visibleKdk(d));
        }
        b.append(" | pos file ").append(posLine("kdk-small") != null ? "kdk-small set " : "").append(posLine("kdk-large") != null ? "kdk-large set" : "");
        return b.toString();
    }

    private static String ext(de.audi.atip.hmi.view.IDisplayManager d, int id, String label) {
        StringBuffer b = new StringBuffer(label);
        try {
            int[] e = d.getExtends(id);
            for (int j = 0; e != null && j < e.length; j++) {
                b.append(j > 0 ? "," : "").append(e[j]);
            }
        } catch (Throwable t) {
            b.append("error");
        }
        return b.toString();
    }

    /** v57: which banner window the cockpit shows now. */
    private static String visibleKdk(Object d) {
        try {
            if (d instanceof de.audi.tghu.fwhmi.IDisplayManagerKombiControl) {
                return new StringBuffer(" visibleKDK ").append(((de.audi.tghu.fwhmi.IDisplayManagerKombiControl) d).getVisibleKDK(0)).toString();
            }
        } catch (Throwable ignored) {
        }
        return " visibleKDK ?";
    }

    /** v57 (drive 61: stock arrow in Sport after a route restart): the whole panel state. */
    private static String diagState() {
        StringBuffer b = new StringBuffer();
        try {
            b.append(" live=").append(liveShown).append(" turn=").append(lastTurn != null)
                .append(" rg=").append(de.audi.tghu.navi.app.cluster.TtRgi.rgActive(false))
                .append(" guard=").append(guardRunning).append(" fader=").append(faderRunning)
                .append(" fade=").append(fadeNow).append(" kdkAlpha=").append(kdkAlpha)
                .append(" kdkUp=").append(kdkPanelUp).append(" hideBg=").append(hideKdkBg)
                .append(" fixes=").append(guardFixes).append(" lastOp=").append(guardLastOpacity)
                .append(" parent=").append(parentMode)
                .append(" topVp=").append(topVp == null ? "none" : "set");
            IWrappedNode3D r = mainRoot;
            if (r != null) {
                b.append(" nodeOpacity=").append(r.getOpacity());
            }
        } catch (Throwable t) {
            b.append(" diag error ").append(String.valueOf(t));
        }
        return b.toString();
    }

    /** v57: last periodic state report. */
    private static long diagAt;

    /** v57: a state report soon after an event (route on/off, view or context change). */
    static void diagSoon(String why) {
        RgdBus.note(new StringBuffer("diag: ").append(why).toString());
        post("where", false);
    }

    static void poll() {
        try {
            long st = POS.exists() ? POS.lastModified() + POS.length() : 0L;
            if (posStamp != -1L && st != posStamp && lastTurn != null) {
                post(lastTurn, false);
            }
            posStamp = st;
            if (WHERE.exists()) {
                WHERE.delete();
                post("where", false);
            }
            // v57: a state report every 10 s while a route runs (diagnostics only)
            long now = System.currentTimeMillis();
            if ((liveShown || lastTurn != null) && now - diagAt >= 10000L) {
                diagAt = now;
                post("where", false);
            }
        } catch (Throwable ignored) {
        }
    }

    /** ZoomGate: large/small view changed; rebuild in the other layout. */
    public static void viewChanged() {
        burst(2000L);
        String t = lastTurn;
        if (t != null) {
            post(t, false);
        }
        diagSoon(new StringBuffer("view small=").append(ZoomGate.small()).toString());
    }

    private static void post(String arg, boolean log) {
        EALManager m = manager();
        if (m == null) {
            return;
        }
        try {
            m.getTerminal().getFramework().getHMIService().getEventDispatcher().postEvent(
                new RunnableEvent(new PanelJob(m, arg, log ? ++serial : -1)));
        } catch (Throwable ignored) {
        }
    }

    private static String rebuildArg(int[] icon, int metres, int bar, String lanes, int destM, long eta,
                                     String exit, String street) {
        StringBuffer a = new StringBuffer("turn ");
        for (int i = 0; i < icon.length; i++) {
            if (i > 0) {
                a.append(',');
            }
            a.append(icon[i]);
        }
        return a.append(' ').append(metres).append(' ').append(bar).append(' ')
            .append(lanes == null || lanes.length() == 0 ? "-" : lanes).append(' ')
            .append(destM).append(" @").append(eta).append(' ')
            .append(exit == null || exit.length() == 0 ? "-" : exit).append(' ')
            .append(street == null ? "" : street).toString();
    }

    /** Playback/live "turn ICON METRES BAR LANES DEST ETA EXIT STREET...". */
    private static String turnCmd(EALManager m, String[] w, boolean verbose) {
        TurnSpec t = TurnSpec.parse(w);
        if (t == null) {
            return "turn: bad arguments";
        }
        return turn(m, t, verbose);
    }

    // ---- v25: pictures pre-rendered off the HMI thread; our own top viewport --------------
    private static final java.util.Hashtable ART = new java.util.Hashtable();
    private static String parentMode = "top";
    private static boolean hideKdkBg;
    private static IWrappedNode3D topNode;
    /** v26: a new viewport starts hidden (WrappedViewport sets its link invisible); setVisible(true) shows it. */
    private static de.esolutions.hmi.widgets.audi.base.eal.IWrappedViewport topVp;

    /** Current panel mode from the view and the layout override. */
    static int currentMode() {
        if (layoutForce.equals("banners")) {
            return ZoomGate.small() ? TurnSpec.FULL : TurnSpec.BANNER;
        }
        if (layoutForce.equals("full")) {
            return ZoomGate.small() ? TurnSpec.FULL : TurnSpec.FLAP;
        }
        // v27 default ("kdk", also "auto"): one panel in the stock banner window, every view.
        // v28: the small view's window is wider (328x180), so it gets the wide panel.
        // v33: on the large view's map the stock banner is off (it hides the CarPlay map), so
        // the panel brings its own frame.
        // v37: with the CarPlay layer lists (Layers) the stock banner stays on the map too.
        if (mapShowing && !ZoomGate.small() && !kdkDirect() && !Layers.on()) {
            return TurnSpec.KDK_MAP;
        }
        return ZoomGate.small() ? TurnSpec.KDK_WIDE : TurnSpec.KDK;
    }

    /** Called on the feed thread before posting: draws the picture so the HMI thread only uploads it. */
    static void prerender(String arg) {
        try {
            TurnSpec t = TurnSpec.parse(words(arg));
            if (t == null) {
                return;
            }
            int mode = currentMode();
            String k = t.artKey(mode);
            synchronized (ART) {
                if (ART.containsKey(k)) {
                    return;
                }
            }
            byte[] rgba = t.render(mode).rgba();
            synchronized (ART) {
                if (ART.size() > 6) {
                    ART.clear();
                }
                ART.put(k, rgba);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * The parent node for our panels. "top" (default): a layer in our own viewport at index 225,
     * above the G24 KDK background viewport (220) that covered our panels in v24; "front": the
     * HMI's popupsUnboundFront layer in the main viewport (210), as in v19-v24.
     */
    private static IWrappedNode3D parent(EALManager m) {
        if (!parentMode.equals("top")) {
            return m.getPartialPopupsFrontNode();
        }
        if (topNode == null) {
            try {
                de.esolutions.hmi.widgets.audi.base.eal.IWrappedViewport vp =
                    m.createViewport("ttTop", 225, m.getScreenWidth(), m.getScreenHeight());
                de.esolutions.hmi.widgets.audi.base.eal.IWrappedLayer layer =
                    m.createLayer(vp, "ttTop", 0, m.getScreenWidth(), m.getScreenHeight(), 0);
                topNode = layer == null ? null : layer.getMainNode();
                topVp = vp;
                RgdBus.note(new StringBuffer("panel top viewport ").append(topNode == null ? "failed" : dump(topNode))
                    .toString());
            } catch (Throwable e) {
                RgdBus.note(new StringBuffer("panel top viewport error ").append(String.valueOf(e)).toString());
                topNode = null;
            }
        }
        return topNode != null ? topNode : m.getPartialPopupsFrontNode();
    }

    private static String turn(EALManager m, TurnSpec t, boolean verbose) {
        updateAlpha(System.currentTimeMillis());
        lastTurn = rebuildArg(t.icon, t.metres, t.bar, t.lanes, t.destM, t.eta, t.exit, t.street);
        int mode = currentMode();
        if (mode != shownMode) {
            clearTurn(m);
            shownMode = mode;
        }
        boolean full = mode != TurnSpec.BANNER;
        int pw = t.width(mode);
        int ph = t.height(mode);
        boolean kdkMode = mode == TurnSpec.KDK || mode == TurnSpec.KDK_WIDE || mode == TurnSpec.KDK_MAP;
        // v35: in the small view the banner window is gone while the map shows (banner off), so the
        // panel moves over the map, left of the tach (v33 grid photo).
        String spot = kdkMode ? (mode == TurnSpec.KDK_WIDE ? (mapShowing && !kdkDirect() && !Layers.on() ? "kdk-smallmap" : "kdk-small") : "kdk-large")
            : (mode == TurnSpec.FULL ? "small" : (mode == TurnSpec.FLAP ? "flap" : "left"));
        StringBuffer b = new StringBuffer(spot).append(' ');
        IWrappedNode3D front = parent(m);
        if (mainRoot == null) {
            mainRoot = m.createNode3D(front, "ttTurnPanel", (float) pw, (float) ph);
            if (mainRoot == null) {
                return "createNode3D null";
            }
        }
        place(mainRoot, spot);
        String k = t.artKey(mode);
        if (!k.equals(artKey) || mainImage == null) {
            long t0 = System.currentTimeMillis();
            byte[] rgba;
            synchronized (ART) {
                rgba = (byte[]) ART.get(k);
            }
            boolean pre = rgba != null;
            if (rgba == null) {
                rgba = t.render(mode).rgba();
            }
            IWrappedNode3DImage img = image(m, mainRoot, rgba, pw, ph);
            if (img == null) {
                return "image null";
            }
            if (mainImage != null) {
                try {
                    mainRoot.remove(mainImage);
                    m.destroy(mainImage);
                } catch (Throwable ignored) {
                }
            }
            mainImage = img;
            artKey = k;
            raise(mainRoot, text1);
            raise(mainRoot, text2);
            raise(mainRoot, text3);
            raise(mainRoot, text4);
            for (int i = 0; i < TN; i++) {
                raise(mainRoot, shd[i]);
                raise(mainRoot, tn[i]);
            }
            if (verbose) {
                b.append(pre ? "art pre " : "art ").append(System.currentTimeMillis() - t0).append(" ms ");
            }
        }
        String exit = t.exit;
        String street = t.street;
        String sub = exit.length() > 0 ? new StringBuffer("Exit ").append(exit).toString() : street;
        String destLine = destText(m, t.destM, t.eta);
        if (kdkMode) {
            kdkTexts(m, t, new KdkLayout(pw, ph, t.hasLanes(), t.exit.length() > 0, t.street.length() > 0),
                destLine);
            if (destRoot != null) {
                destRoot.setVisible(false);
            }
        } else if (full) {
            if (exit.length() > 0 && street.length() > 0) {
                sub = new StringBuffer("Exit ").append(exit).append("  ").append(street).toString();
            }
            text1 = setText(m, mainRoot, text1, "ttTurnDist", turnDistance(t.metres), 2, 40, 40f, (float) (ph - 158), pw - 56);
            text2 = setText(m, mainRoot, text2, "ttTurnSub", sub, 1, 24, 40f, (float) (ph - 106), pw - 56);
            text3 = setText(m, mainRoot, text3, "ttTurnDest", destLine, 1, 22, 60f, (float) (ph - 44), pw - 76);
            if (destRoot != null) {
                destRoot.setVisible(false);
            }
        } else {
            int s = t.hasLanes() ? ph - 62 : ph - 14;
            int tx = s + 14;
            int tw = pw - tx - 20;
            text1 = setText(m, mainRoot, text1, "ttTurnDist", turnDistance(t.metres), 2, 34, (float) tx, 10f, tw);
            text2 = setText(m, mainRoot, text2, "ttTurnSub", sub, 1, 21, (float) tx, 56f, tw);
            if (destLine.length() > 0) {
                if (destRoot == null) {
                    destRoot = m.createNode3D(front, "ttDestPanel", (float) RIGHT_W, (float) RIGHT_H);
                    if (destRoot != null) {
                        PanelArt da = new PanelArt(RIGHT_W, RIGHT_H);
                        da.destination();
                        destImage = image(m, destRoot, da.rgba(), RIGHT_W, RIGHT_H);
                    }
                }
                if (destRoot != null) {
                    place(destRoot, "right");
                    dest1 = setText(m, destRoot, dest1, "ttDestDist", destDistance(t.destM), 2, 28, 76f, 6f, RIGHT_W - 90);
                    dest2 = setText(m, destRoot, dest2, "ttDestEta", clock(m, t.eta), 1, 22, 76f, 42f, RIGHT_W - 90);
                    destRoot.setVisible(true);
                }
            } else if (destRoot != null) {
                destRoot.setVisible(false);
            }
        }
        // v45: visible, or faded/hidden far from a turn on the CarPlay map.
        applyFade();
        kdkPanelUp = kdkMode;
        if (topVp != null && parentMode.equals("top")) {
            try {
                topVp.setForceInvisible(false);
                topVp.setVisible(true);
                if (verbose) {
                    b.append("vp ").append(topVp.getLink().isVisible() ? "visible " : "HIDDEN ");
                }
            } catch (Throwable e) {
                b.append("vp error ").append(String.valueOf(e)).append(' ');
            }
        }
        applyKdk(m);
        if (hideKdkBg) {
            try {
                m.hideKDKBackground();
            } catch (Throwable ignored) {
            }
        }
        m.fireRenderEvent();
        if (verbose) {
            b.append("at ").append((int) mainRoot.getX()).append(',').append((int) mainRoot.getY())
                .append(" parent ").append(parentMode).append(" t1 ").append(dump(text1 != null ? text1 : tn[0]));
        }
        return b.toString();
    }

    private static IWrappedNode3DImage image(EALManager m, IWrappedNode3D parent, byte[] rgba, int w, int h) {
        ByteBuffer buf = ByteBuffer.allocateDirect(rgba.length);
        buf.put(rgba);
        buf.rewind();
        TextureDescription d = m.createTextureDescription(buf, 6, w, h, null, new Object());
        IWrappedNode3DImage img = m.createImage3D(parent, "ttArt", d, 0, true, "ttPanel");
        if (img != null) {
            img.setScale((float) w / img.getUnscaledWidth(), (float) h / img.getUnscaledHeight(), 1f);
            img.setPosition(0f, 0f, 0f);
        }
        return img;
    }

    private static final int KDK = 20;
    private static final java.io.File KDK_FORCE = new java.io.File("/tmp/tt-rgi-kdk");
    private static String layoutForce = "auto";
    /** -2 = auto (v27 default): transparent while our kdk panel is up, stock otherwise; -1 = stock. */
    private static int kdkAlpha = -2;
    private static int kdkPX = Integer.MIN_VALUE;
    private static int kdkPY;

    private static de.audi.atip.hmi.view.IDisplayManager dm(EALManager m) {
        try {
            return m.getTerminal().getFramework().getHMIService().getDisplayManager();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Re-applies our opacity/position for the stock banner displayable (the HMI resets them). */
    private static void applyKdk(EALManager m) {
        int alpha = kdkAlpha == -2 ? (kdkPanelUp ? 0 : -1) : kdkAlpha;
        if (alpha < 0 && kdkPX == Integer.MIN_VALUE) {
            return;
        }
        de.audi.atip.hmi.view.IDisplayManager d = dm(m);
        if (d == null) {
            return;
        }
        try {
            if (alpha >= 0) {
                d.setOpacity(KDK, 0, alpha);
            }
            if (kdkPX != Integer.MIN_VALUE) {
                d.setPosition(KDK, 0, kdkPX, kdkPY);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String kdkCmd(EALManager m, String[] w) {
        de.audi.atip.hmi.view.IDisplayManager d = dm(m);
        StringBuffer b = new StringBuffer("kdk ").append(w[1]);
        if (d == null) {
            return b.append(" no display manager").toString();
        }
        try {
            if (w[1].equals("alpha") && w.length >= 3) {
                kdkAlpha = w[2].equals("auto") ? -2 : num(w[2]);
                if (kdkAlpha == -2) {
                    d.setOpacity(KDK, 0, kdkPanelUp ? 0 : 100);
                } else if (kdkAlpha >= 0) {
                    d.setOpacity(KDK, 0, kdkAlpha);
                } else {
                    d.setOpacity(KDK, 0, 100);
                }
            } else if (w[1].equals("pos") && w.length >= 3) {
                if (w[2].equals("stock")) {
                    kdkPX = Integer.MIN_VALUE;
                    boolean small = ZoomGate.small();
                    d.setPosition(KDK, 0, small ? 991 : 1105, small ? 152 : 110);
                } else if (w.length >= 4) {
                    kdkPX = num(w[2]);
                    kdkPY = num(w[3]);
                    d.setPosition(KDK, 0, kdkPX, kdkPY);
                }
            } else if (w[1].equals("force") && w.length >= 3) {
                if (w[2].startsWith("1")) {
                    new java.io.FileOutputStream(KDK_FORCE.getPath()).close();
                } else {
                    KDK_FORCE.delete();
                }
                de.audi.tghu.navi.app.cluster.TtRgi.viewChanged();
            }
            b.append(" | opacity ").append(d.getOpacity(KDK, 0));
            int[] pos = d.getPosition(KDK, 0);
            b.append(" pos ");
            for (int i = 0; pos != null && i < pos.length; i++) {
                b.append(i > 0 ? "," : "").append(pos[i]);
            }
            b.append(" context ").append(d.getCurrentContextID(0));
            int[] ds = d.getDisplayables(0);
            b.append(" displayables ");
            for (int i = 0; ds != null && i < ds.length; i++) {
                b.append(i > 0 ? "," : "").append(ds[i]);
            }
            if (d instanceof de.audi.tghu.fwhmi.IDisplayManagerKombiControl) {
                de.audi.tghu.fwhmi.IDisplayManagerKombiControl k = (de.audi.tghu.fwhmi.IDisplayManagerKombiControl) d;
                b.append(" kdkVisible ").append(k.isKDKVisible(0)).append(" visibleKdk ").append(k.getVisibleKDK(0));
            }
            b.append(" small ").append(ZoomGate.small());
            b.append(" alphaMode ").append(kdkAlpha).append(" guardFixes ").append(guardFixes)
                .append(" lastSeen ").append(guardLastOpacity);
        } catch (Throwable t) {
            b.append(" error ").append(String.valueOf(t));
        }
        return b.toString();
    }

    /** Re-adds a text node so it is drawn after (above) a newly created picture. */
    private static void raise(IWrappedNode3D root, IWrappedNode3D n) {
        if (root != null && n != null) {
            try {
                root.remove(n);
                root.add(n);
            } catch (Throwable ignored) {
            }
        }
    }

    /** "3.0 mi  3:43 PM", or "" when neither is known. */
    private static String destText(EALManager m, int destM, long eta) {
        StringBuffer b = new StringBuffer();
        if (destM >= 0) {
            b.append(destDistance(destM));
        }
        String c = clock(m, eta);
        if (c.length() > 0) {
            if (b.length() > 0) {
                b.append("   ");
            }
            b.append(c);
        }
        return b.toString();
    }

    /** Local clock time (12 h) of a UTC epoch-seconds value, e.g. "3:43 PM". */
    private static String clock(EALManager m, long etaUtc) {
        if (etaUtc <= 0L) {
            return "";
        }
        long ms = etaUtc * 1000L;
        try {
            // v48: the cockpit clock (what the car shows) plus the time left. The HMI's UTC offset
            // is 0 on the TT, so convertUTCTimeToLocalTime returned UTC.
            long kombi = m.getTerminal().getFramework().getKombiTime();
            if (kombi > 0L) {
                ms = kombi + (ms - System.currentTimeMillis());
            } else {
                ms = m.getTerminal().getFramework().convertUTCTimeToLocalTime(ms);
            }
        } catch (Throwable ignored) {
            // keep UTC
        }
        int mins = (int) ((ms / 60000L) % 1440L);
        if (mins < 0) {
            mins += 1440;
        }
        int hh = mins / 60;
        int mm = mins % 60;
        StringBuffer b = new StringBuffer();
        b.append(hh % 12 == 0 ? 12 : hh % 12).append(':');
        if (mm < 10) {
            b.append('0');
        }
        return b.append(mm).append(hh < 12 ? " AM" : " PM").toString();
    }

    private static String keyOf(int[] a) {
        StringBuffer b = new StringBuffer();
        for (int i = 0; a != null && i < a.length; i++) {
            b.append(',').append(a[i]);
        }
        return b.toString();
    }

    private static IWrappedNode3DText setText(EALManager m, IWrappedNode3D root, IWrappedNode3DText n, String name,
                                              String t, int style, int size, float x, float y, int maxWidth) {
        Object font = HMITerminalImpl.fontLoader == null ? null : HMITerminalImpl.fontLoader.getFont(style, size);
        if (!(font instanceof de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont)) {
            return n;
        }
        de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont f = (de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont) font;
        String text = fit(m, t == null || t.length() == 0 ? " " : t, f, maxWidth);
        if (n == null) {
            n = m.createText3D(root, name, text, f);
            if (n == null) {
                return null;
            }
            n.setColor(0xFFFFFFFF);
        } else if (!text.equals(n.getText())) {
            n.setText(text, f);
        }
        n.setPosition(x, y, 0f);
        return n;
    }

    /** Shortens t with "..." until it fits maxWidth pixels in font f. */
    private static String fit(EALManager m, String t, de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont f,
                              int maxWidth) {
        try {
            if (maxWidth <= 0 || m.getTextWidth(t, f) <= maxWidth) {
                return t;
            }
            int n = t.length();
            while (n > 1) {
                n--;
                String c = new StringBuffer(t.substring(0, n)).append("...").toString();
                if (m.getTextWidth(c, f) <= maxWidth) {
                    return c;
                }
            }
        } catch (Throwable ignored) {
        }
        return t;
    }

    /**
     * Position and scale of one panel: "panel at" overrides the main panel; else /tmp/tt-panel-pos
     * lines "left X Y SCALE" / "right X Y SCALE" / "small X Y SCALE"; else the defaults
     * (left 120,96 top-left of the map; right 1110,100 above the speedometer; small 991,152 banner spot;
     * flap 1105,110).
     */
    private static void place(IWrappedNode3D n, String which) {
        int x;
        int y;
        if (which.equals("kdk-smallmap")) {
            // v33 grid photo, small view with the map: the map shows at x 0..~520 (tach to the
            // right), its top edge is a diagonal from (0,110) to (200,50).
            x = 130;
            y = 76;
        } else if (which.equals("kdk-small")) {
            // v28 grid photos: the small view shows us only in 328x180 at 991,152 and the UI trim
            // line runs along its top edge, so the wide panel starts just below it.
            x = 991;
            y = 160;
            // v50: follow the stock banner window (other skins put it elsewhere).
            int[] k = kdkWindow();
            if (k != null) {
                x = k[0];
                y = k[1] + 8;
            }
        } else if (which.equals("small")) {
            x = 991;
            y = 152;
        } else if (which.equals("kdk-large")) {
            x = 1105;
            y = 110;
            int[] k = kdkWindow();
            if (k != null) {
                x = k[0];
                y = k[1];
            }
        } else if (which.equals("flap")) {
            x = 1105;
            y = 110;
        } else if (which.equals("right")) {
            // v24: v23 photos showed 1180,70 clipped by the rounded top-right corner.
            x = 1110;
            y = 100;
        } else {
            // v24: v23 photos showed 30,64 clipped by the rounded top-left corner.
            x = 120;
            y = 96;
        }
        float scale = 1f;
        String[] v = posLine(which);
        if (v != null) {
            x = num(v[1]);
            y = num(v[2]);
            if (v.length > 3) {
                try {
                    scale = Float.parseFloat(v[3]);
                } catch (Throwable ignored) {
                    scale = 1f;
                }
            }
        }
        if (posX != Integer.MIN_VALUE && !which.equals("right")) {
            x = posX;
            y = posY;
        }
        if (scale < 0.3f || scale > 2f) {
            scale = 1f;
        }
        n.setScale(scale, scale, 1f);
        n.setPosition((float) x, (float) y, 0f);
    }

    /**
     * v50: where the stock banner window (displayable 20) is now, {x, y}, or null if unknown.
     * Only off the Sport skin (1), whose hard-coded spots are measured and car-tested.
     */
    static int[] kdkWindow() {
        if (ZoomGate.skinNow() == 1) {
            return null;
        }
        try {
            EALManager m = manager();
            de.audi.atip.hmi.view.IDisplayManager d = m == null ? null : dm(m);
            int[] p = d == null ? null : d.getPosition(KDK, 0);
            if (p != null && p.length >= 2 && p[0] > 0 && p[0] < 1440 && p[1] >= 0 && p[1] < 540) {
                return p;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String[] posLine(String which) {
        if (!POS.exists()) {
            return null;
        }
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(POS.getPath());
            byte[] buf = new byte[512];
            int n;
            try {
                n = in.read(buf);
            } finally {
                in.close();
            }
            if (n <= 0) {
                return null;
            }
            String[] lines = split(new String(buf, 0, n), '\n');
            for (int i = 0; i < lines.length; i++) {
                String[] w = words(lines[i].trim());
                if (w.length >= 3 && w[0].equals(which)) {
                    return w;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** US units: feet below 1000 ft (50 ft steps), then miles. */
    /** v62: the phone's own distance texts and the metres they belong to (RgdBus, before live()). */
    private static volatile int phoneTurnM = -1;
    private static volatile String phoneTurn;
    private static volatile int phoneDestQ = -1;
    private static volatile String phoneDest;

    static void phoneDistances(int turnM, String turnText, int destM, String destText) {
        phoneTurn = turnText;
        phoneTurnM = turnM;
        phoneDest = destText;
        // live() keys the destination in 160 m steps; match the same value
        phoneDestQ = destM < 0 ? -1 : (destM / 160) * 160;
    }

    /** The phone's text for this turn distance when it sent one, else our own rounding. */
    static String turnDistance(int metres) {
        String s = phoneTurn;
        return s != null && metres == phoneTurnM ? s : distance(metres);
    }

    static String destDistance(int metres) {
        String s = phoneDest;
        return s != null && metres == phoneDestQ ? s : distance(metres);
    }

    static String distance(int metres) {
        if (metres < 0) {
            return "";
        }
        int feet = (int) (metres * 3.28084 + 0.5);
        StringBuffer b = new StringBuffer();
        if (feet < 1000) {
            b.append(((feet + 25) / 50) * 50).append(" ft");
        } else {
            int tenths = (int) (metres / 160.9344 + 0.5);
            if (tenths >= 100) {
                b.append(tenths / 10).append(" mi");
            } else {
                b.append(tenths / 10).append('.').append(tenths % 10).append(" mi");
            }
        }
        return b.toString();
    }

    private static void clearTurn(EALManager m) {
        IWrappedNode3D[] roots = { mainRoot, destRoot };
        for (int i = 0; i < roots.length; i++) {
            if (roots[i] != null) {
                try {
                    IWrappedNode3D p = roots[i].getParent();
                    if (p != null) {
                        p.remove(roots[i]);
                    }
                    m.destroy(roots[i]);
                } catch (Throwable ignored) {
                }
            }
        }
        mainRoot = null;
        mainImage = null;
        text1 = null;
        text2 = null;
        text3 = null;
        text4 = null;
        for (int i = 0; i < TN; i++) {
            tn[i] = null;
            shd[i] = null;
        }
        kdkPanelUp = false;
        destRoot = null;
        destImage = null;
        dest1 = null;
        dest2 = null;
        artKey = null;
    }

    private static String[] split(String s, char sep) {
        java.util.Vector v = new java.util.Vector();
        int start = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == sep) {
                v.addElement(s.substring(start, i));
                start = i + 1;
            }
        }
        String[] out = new String[v.size()];
        v.copyInto(out);
        return out;
    }

    private static int[] ints(String s) {
        java.util.Vector v = new java.util.Vector();
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '-' || (c >= '0' && c <= '9')) {
                int j = i + 1;
                while (j < n && s.charAt(j) >= '0' && s.charAt(j) <= '9') {
                    j++;
                }
                try {
                    v.addElement(new Integer(Integer.parseInt(s.substring(i, j))));
                } catch (Throwable ignored) {
                }
                i = j;
            } else {
                i++;
            }
        }
        int[] out = new int[v.size()];
        for (int k = 0; k < out.length; k++) {
            out[k] = ((Integer) v.elementAt(k)).intValue();
        }
        return out;
    }

    private static void clear(EALManager m) {
        if (label != null) {
            try {
                m.destroy(label);
            } catch (Throwable ignored) {
            }
            label = null;
        }
        if (box != null) {
            try {
                m.destroy(box);
            } catch (Throwable ignored) {
            }
            box = null;
        }
        if (root != null) {
            try {
                IWrappedNode3D p = root.getParent();
                if (p != null) {
                    p.remove(root);
                }
                m.destroy(root);
            } catch (Throwable ignored) {
            }
            root = null;
        }
    }

    /** Filled box with a 4 px white border and rounded corners; RGBA bytes. */
    private static void paintBox(ByteBuffer buf, int w, int h, int rgb) {
        byte r = (byte) (rgb >> 16);
        byte g = (byte) (rgb >> 8);
        byte bl = (byte) rgb;
        int rad = Math.min(16, Math.min(w, h) / 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int dx = x < rad ? rad - x : (x >= w - rad ? x - (w - rad - 1) : 0);
                int dy = y < rad ? rad - y : (y >= h - rad ? y - (h - rad - 1) : 0);
                boolean inside = dx * dx + dy * dy <= rad * rad;
                boolean border = x < 4 || y < 4 || x >= w - 4 || y >= h - 4
                    || (dx > 0 && dy > 0 && dx * dx + dy * dy > (rad - 4) * (rad - 4));
                if (!inside) {
                    buf.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
                } else if (border) {
                    buf.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1);
                } else {
                    buf.put(r).put(g).put(bl).put((byte) 0xE0);
                }
            }
        }
        buf.rewind();
    }

    private static String describe(Object n) {
        if (n == null) {
            return "null";
        }
        if (n instanceof IWrappedNode3D) {
            IWrappedNode3D d = (IWrappedNode3D) n;
            StringBuffer b = new StringBuffer();
            try {
                b.append(d.getNode().getName()).append(" pos ").append((int) d.getX()).append(',').append((int) d.getY())
                    .append(" size ").append((int) d.getWidth()).append('x').append((int) d.getHeight())
                    .append(d.isVisible() ? " visible" : " hidden");
            } catch (Throwable t) {
                b.append(n.getClass().getName());
            }
            return b.toString();
        }
        return n.getClass().getName();
    }

    private static String[] words(String s) {
        java.util.Vector v = new java.util.Vector();
        int i = 0;
        int n = s.length();
        while (i < n) {
            while (i < n && s.charAt(i) == ' ') {
                i++;
            }
            int j = i;
            while (j < n && s.charAt(j) != ' ') {
                j++;
            }
            if (j > i) {
                v.addElement(s.substring(i, j));
            }
            i = j;
        }
        String[] out = new String[v.size()];
        v.copyInto(out);
        return out;
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int hex(String s) {
        try {
            return Integer.parseInt(s, 16);
        } catch (Throwable t) {
            return 0x202020;
        }
    }

    // ---- v27: keep the nav engine's banner picture transparent ----------------------------
    // Stock (CombiMapController.handleKdkSingleTerminal) sets displayable 20 to opacity 0 and then
    // runs a ~1 s fade-in (AnimationControllerMIB2High.startKDKAnimation) every time it shows the
    // banner, which overrode our opacity until the next redraw (the straight line seen in v26).
    // A small watcher thread re-checks it every 100 ms while our panel is up, and every 30 ms
    // for 2 s after anything that shows the banner (route start, view change, TtRgi.kdkRg).
    private static volatile long burstUntil;
    private static volatile boolean guardRunning;
    /** v33: the cockpit shows the map: display context 10, or 89 (10 + 79 while the stock banner is up). */
    private static volatile boolean mapShowing;
    private static int lastCtx = -1;
    private static boolean gridFull;

    /**
     * v36 experiment (/tmp/tt-kdk-direct): context 89 is context 10 plus the banner layer and
     * nothing else (stock defineContextsForG24), so maybe it is the nav engine's guidance view,
     * not the banner window, that paints the stock map over CarPlay. In this mode the window is
     * opened directly with setKDKVisible(20) and the guidance view is never requested.
     */
    private static final java.io.File KDK_DIRECT = new java.io.File("/tmp/tt-kdk-direct");
    private static boolean directOn;

    public static boolean kdkDirect() {
        return KDK_DIRECT.exists();
    }

    /** v38: new layer lists wait for a real context switch (guardCheck does one on the plain map). */
    private static volatile boolean relayer;
    private static volatile long relayerUntil;

    /** v37 Layers: the layer lists changed; redraw in the new mode (banner frame or own frame). */
    static void layersChanged() {
        relayerUntil = System.currentTimeMillis() + 20000L;
        relayer = true;
        String t = lastTurn;
        if (t != null && (liveShown || kdkPanelUp)) {
            post(t, false);
        }
        burst(2000L);
    }

    /** TtRgi.kdkWanted: is the map showing? Reads the context now when the watcher is not running. */
    public static boolean mapShowing() {
        // v34: always the live context (v33 trusted a stale flag while the watcher ran, so a
        // banner could stay up on the map until the next view change). The flag stays the
        // watcher's, so it still sees the change and redraws.
        try {
            EALManager m = manager();
            de.audi.atip.hmi.view.IDisplayManager d = m == null ? null : dm(m);
            if (d != null) {
                return isMap(d.getCurrentContextID(0));
            }
        } catch (Throwable ignored) {
        }
        return mapShowing;
    }

    private static boolean isMap(int ctx) {
        int base = ctx >= 79 && ctx <= 157 ? ctx - 79 : ctx;
        return base == 10;
    }
    private static int guardFixes;
    private static int guardLastOpacity = -1;

    /** TtRgi.kdkRg: the stock banner is being switched on. */
    public static void kdkShowing() {
        burst(2000L);
    }

    static void burst(long ms) {
        burstUntil = System.currentTimeMillis() + ms;
        synchronized (LOCK) {
            if (guardRunning) {
                return;
            }
            guardRunning = true;
        }
        try {
            Thread th = new Thread(new KdkGuard(), "ttKdkGuard");
            th.setDaemon(true);
            th.start();
        } catch (Throwable t) {
            guardRunning = false;
        }
    }

    /** KdkGuard thread body. */
    static void guardLoop() {
        try {
            while (true) {
                long now = System.currentTimeMillis();
                // v34: also for as long as a route is declared (playback included), so the map
                // check never stops while the stock banner could come up.
                boolean watch = (kdkAlpha == -2 && kdkPanelUp) || liveShown
                    || de.audi.tghu.navi.app.cluster.TtRgi.rgActive(false);
                if (!watch && !relayer && now > burstUntil) {
                    break;
                }
                if (watch || relayer) {
                    post("kdkguard", false);
                }
                Thread.sleep(now < burstUntil ? 30L : 100L);
            }
        } catch (Throwable ignored) {
        }
        guardRunning = false;
    }

    private static String guardCheck(EALManager m) {
        de.audi.atip.hmi.view.IDisplayManager d = dm(m);
        if (d == null) {
            return "no dm";
        }
        // v33: follow the cockpit's context. When the map appears or goes, re-declare the route
        // (TtRgi.kdkWanted turns the stock banner off on the map) and redraw in the new mode.
        try {
            int ctx = d.getCurrentContextID(0);
            if (ctx != lastCtx) {
                boolean map = isMap(ctx);
                RgdBus.note(new StringBuffer("context ").append(lastCtx).append(" -> ").append(ctx)
                    .append(map ? " (map)" : "").append(" small ").append(ZoomGate.small()).toString());
                lastCtx = ctx;
                post("where", false);
                if (map != mapShowing) {
                    mapShowing = map;
                    de.audi.tghu.navi.app.cluster.TtRgi.viewChanged();
                    String t = lastTurn;
                    if (t != null && (liveShown || kdkPanelUp)) {
                        post(t, false);
                    }
                    burst(2000L);
                }
            }
        } catch (Throwable ignored) {
        }
        if (relayer) {
            // v38 (car test): after the iPhone went, the map stayed black until a tab switch, and
            // after it came back the stock map stayed. The display manager only uses a new list
            // when it switches into the context, and the HMI skips a switch to the current one,
            // so go to 89 and straight back. Only on the plain map without the banner.
            // v41: on the map with the banner still up (unplugged during a route) wait for it to go,
            // up to 20 s, instead of giving up.
            try {
                int ctx = d.getCurrentContextID(0);
                boolean banner = d instanceof de.audi.tghu.fwhmi.IDisplayManagerKombiControl
                    && ((de.audi.tghu.fwhmi.IDisplayManagerKombiControl) d).getVisibleKDK(0) == KDK;
                if (ctx == 10 && !banner) {
                    relayer = false;
                    d.switchContext(89, 0, null);
                    d.switchContext(10, 0, null);
                    RgdBus.note("layers: re-applied on the map (10 -> 89 -> 10)");
                } else if (ctx == 89 && banner) {
                    // v42: banner up on the map. With supervisor v7 the renderer runs all session
                    // and nothing switches the display behind the HMI, so the new 89 list needs a
                    // real switch too: car page (0 -> 79 with the banner) and back (10 -> 89).
                    relayer = false;
                    d.switchContext(0, 0, null);
                    d.switchContext(10, 0, null);
                    RgdBus.note("layers: re-applied with the banner (79 -> 89)");
                } else if (ctx != 89 || System.currentTimeMillis() > relayerUntil) {
                    relayer = false;
                    RgdBus.note(new StringBuffer("layers: re-apply skipped, context ").append(ctx)
                        .append(banner ? " (banner up)" : "").toString());
                }
            } catch (Throwable t) {
                RgdBus.note(new StringBuffer("layers: re-apply failed ").append(String.valueOf(t)).toString());
            }
        }
        try {
            boolean want = kdkDirect() && (liveShown || kdkPanelUp)
                && de.audi.tghu.navi.app.cluster.TtRgi.rgActive(false);
            if (d instanceof de.audi.tghu.fwhmi.IDisplayManagerKombiControl) {
                de.audi.tghu.fwhmi.IDisplayManagerKombiControl k = (de.audi.tghu.fwhmi.IDisplayManagerKombiControl) d;
                if (want && k.getVisibleKDK(0) != KDK) {
                    k.setKDKVisible(KDK, 0);
                    if (!directOn) {
                        RgdBus.note("kdk direct: window opened (setKDKVisible 20)");
                    }
                    directOn = true;
                } else if (!want && directOn) {
                    k.setKDKVisible(-1, 0);
                    directOn = false;
                    RgdBus.note("kdk direct: window closed");
                }
            }
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("kdk direct failed ").append(String.valueOf(t)).toString());
        }
        if (kdkAlpha != -2 || !kdkPanelUp) {
            return "idle";
        }
        try {
            int o = d.getOpacity(KDK, 0);
            if (o != 0) {
                d.setOpacity(KDK, 0, 0);
                guardFixes++;
                guardLastOpacity = o;
            }
            if (kdkPX != Integer.MIN_VALUE) {
                d.setPosition(KDK, 0, kdkPX, kdkPY);
            }
        } catch (Throwable ignored) {
        }
        return "ok";
    }

    /** v27: EAL text nodes are positioned by their baseline; this is the baseline for a top edge. */
    private static float base(int top, int size) {
        return top + size * 0.82f;
    }

    // ---- v27: calibration grid ---------------------------------------------------------------
    private static IWrappedNode3D gridRoot;
    private static final int GRID_X = 900;
    private static final int GRID_Y = 40;
    private static final int GRID_W = 540;
    private static final int GRID_H = 460;

    /** "panel grid on|off": absolute screen grid over the banner area, labelled every 100 px. */
    private static String gridCmd(EALManager m, boolean on) {
        if (gridRoot != null) {
            try {
                IWrappedNode3D p = gridRoot.getParent();
                if (p != null) {
                    p.remove(gridRoot);
                }
                m.destroy(gridRoot);
            } catch (Throwable ignored) {
            }
            gridRoot = null;
        }
        if (!on) {
            m.fireRenderEvent();
            return "grid off";
        }
        IWrappedNode3D front = parent(m);
        // v33: "grid full" covers the whole screen (where does our layer show over the map?).
        int gx = gridFull ? 0 : GRID_X;
        int gy = gridFull ? 0 : GRID_Y;
        int gw = gridFull ? 1440 : GRID_W;
        int gh = gridFull ? 540 : GRID_H;
        gridRoot = m.createNode3D(front, "ttGrid", (float) gw, (float) gh);
        if (gridRoot == null) {
            return "grid node failed";
        }
        gridRoot.setPosition((float) gx, (float) gy, 0f);
        PanelArt g = new PanelArt(gw, gh);
        g.grid(gx, gy);
        image(m, gridRoot, g.rgba(), gw, gh);
        int n = 0;
        for (int x = gridFull ? 100 : 1000; x <= 1400; x += 100) {
            for (int y = 100; y <= (gridFull ? 500 : 400); y += 100) {
                String t = new StringBuffer().append(x).append(',').append(y).toString();
                IWrappedNode3DText tx = setText(m, gridRoot, null, "ttGridLabel", t, 2, 16,
                    (float) (x - gx + 3), base(y - gy + 3, 16), 0);
                if (tx != null) {
                    tx.setColor(rgba(0xFF40FF40));
                    n++;
                }
            }
        }
        gridRoot.setVisible(true);
        showTop();
        m.fireRenderEvent();
        return new StringBuffer("grid on, labels ").append(n).toString();
    }

    private static void showTop() {
        if (topVp != null && parentMode.equals("top")) {
            try {
                topVp.setForceInvisible(false);
                topVp.setVisible(true);
            } catch (Throwable ignored) {
            }
        }
    }

    // ---- v29: TT-style texts (shadowed, big number + small unit, exit badge label) ------------
    private static final int TN = 5;
    private static final IWrappedNode3DText[] tn = new IWrappedNode3DText[TN];
    private static final IWrappedNode3DText[] shd = new IWrappedNode3DText[TN];
    private static final int WHITE = 0xFFFFFFFF;
    private static final int SOFT = 0xFFB9C3CD;

    /**
     * Text nodes take their colour as 0xRRGGBBAA, not 0xAARRGGBB (v30 photos: grey text came out
     * pink and the green grid labels faint magenta). Colours here are written ARGB and converted.
     */
    private static int rgba(int argb) {
        return (argb << 8) | (argb >>> 24);
    }

    private static de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont font(int style, int size) {
        Object f = HMITerminalImpl.fontLoader == null ? null : HMITerminalImpl.fontLoader.getFont(style, size);
        return f instanceof de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont
            ? (de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont) f : null;
    }

    private static int textW(EALManager m, String s, int style, int size) {
        de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont f = font(style, size);
        if (f == null || s.length() == 0) {
            return 0;
        }
        try {
            return m.getTextWidth(s, f);
        } catch (Throwable t) {
            return (int) (s.length() * size * 0.6f);
        }
    }

    /** One text with a soft dark shadow under it (two nodes; the shadow is created first). */
    private static void tx(EALManager m, int i, String name, String text, int style, int size, float x, float y,
                           int maxW, int color) {
        shd[i] = setText(m, mainRoot, shd[i], name, text, style, size, x + 1.5f, y + 2f, maxW);
        if (shd[i] != null) {
            shd[i].setColor(rgba(0x99000000));
        }
        tn[i] = setText(m, mainRoot, tn[i], name, text, style, size, x, y, maxW);
        if (tn[i] != null) {
            tn[i].setColor(rgba(color));
        }
    }

    /**
     * v29 texts for the kdk panels (positions from KdkLayout, like the picture). The distance is a
     * big bold number with a small unit; it shrinks instead of being cut ("1.2..." in v28).
     */
    private static void kdkTexts(EALManager m, TurnSpec t, KdkLayout l, String destLine) {
        String d = turnDistance(t.metres);
        int sp = d.indexOf(' ');
        String num = sp < 0 ? d : d.substring(0, sp);
        String unit = sp < 0 ? "" : d.substring(sp + 1);
        int ns = l.numSize;
        int nw = textW(m, num, 2, ns);
        int uw = textW(m, unit, 1, l.unitSize);
        while (ns > 22 && nw + 6 + uw > l.cw) {
            ns -= 2;
            nw = textW(m, num, 2, ns);
        }
        // Number and unit: one group, centred, sharing the baseline of the full-size number.
        float baseY = base(l.numTop, l.numSize);
        int gx = l.centre(nw + 6 + uw);
        tx(m, 0, "ttNum", num, 2, ns, (float) gx, baseY, 0, WHITE);
        tx(m, 1, "ttUnit", unit, 1, l.unitSize, (float) (gx + nw + 6), baseY, 0, SOFT);
        // Top line: tighter gap, then a smaller font, then without AM/PM, and only then "...".
        String topText = l.wide ? destLine : clock(m, t.eta);
        int gap = topText.indexOf("   ");
        if (gap >= 0) {
            topText = new StringBuffer(topText.substring(0, gap)).append("  ")
                .append(topText.substring(gap + 3)).toString();
        }
        int ts = l.topSize;
        while (ts > l.topSize - 4 && textW(m, topText, 1, ts) > l.cw) {
            ts--;
        }
        if (textW(m, topText, 1, ts) > l.cw && (topText.endsWith(" AM") || topText.endsWith(" PM"))) {
            topText = topText.substring(0, topText.length() - 3);
        }
        String top = fitText(m, topText, 1, ts, l.cw);
        tx(m, 2, "ttTop", top, 1, ts, (float) l.centre(textW(m, top, 1, ts)),
            base(l.topY, l.topSize), 0, SOFT);
        String label = KdkLayout.badgeLabel(t.exit);
        if (l.badgeTop >= 0) {
            int bw = l.badgeW(label);
            String lt = fitText(m, label, 2, l.badgeText, bw - 8);
            int lw = textW(m, lt, 2, l.badgeText);
            float lx = l.badgeX(label) + Math.max(4, (bw - lw) / 2);
            float ly = base(l.badgeTop + (l.badgeH - l.badgeText) / 2 - 1, l.badgeText);
            tx(m, 3, "ttBadge", lt, 2, l.badgeText, lx, ly, 0, WHITE);
        } else {
            tx(m, 3, "ttBadge", "", 2, l.badgeText, (float) l.col, 0f, 0, WHITE);
        }
        if (l.streetTop >= 0) {
            // With a badge the street is secondary (grey); without one it is the main line (white).
            // v35: up to 3 px smaller before "..." ("Kieth H..." in the narrow panel).
            int ss = l.streetSize;
            while (ss > l.streetSize - 3 && textW(m, t.street, 1, ss) > l.cw) {
                ss--;
            }
            String st = fitText(m, t.street, 1, ss, l.cw);
            tx(m, 4, "ttStreet", st, 1, ss, (float) l.centre(textW(m, st, 1, ss)),
                base(l.streetTop, l.streetSize), 0, l.badgeTop >= 0 ? SOFT : WHITE);
        } else {
            tx(m, 4, "ttStreet", "", 1, l.streetSize, (float) l.col, 0f, 0, SOFT);
        }
    }

    /** The text shortened with "..." to maxW (so centring uses the width that is really shown). */
    private static String fitText(EALManager m, String t, int style, int size, int maxW) {
        de.esolutions.hmi.widgets.audi.base.eal.IWrappedFont f = font(style, size);
        if (f == null || t == null || t.length() == 0) {
            return t == null || t.length() == 0 ? " " : t;
        }
        return fit(m, t, f, maxW);
    }
}
