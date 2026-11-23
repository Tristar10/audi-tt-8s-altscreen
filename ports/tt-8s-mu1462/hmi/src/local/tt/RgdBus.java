package local.tt;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Hashtable;

import de.audi.tghu.navi.app.cluster.TtRgi;

/**
 * Java end of the route-guidance bus for the Audi TT MU1462 (logging stage).
 *
 * The route-guidance hook inside mm-ipod connects to 127.0.0.1:19810 and only
 * asks the iPhone for turn-by-turn data while that connection is up. This
 * accepts it, reads the frames (16-byte big-endian header: magic 'CPHB', seq,
 * u16 type, u8 flags, u8 reserved, u32 length; then the payload) and records
 * every changed route-guidance update (type 0x0020, "key:type:value" lines):
 *   /tmp/tt-rgd.txt  the latest update, as received
 *   /tmp/tt-rgd.log  one line per change, capped at 64 KB
 * Nothing is sent back to the hook. Each replug starts a new mm-ipod, whose
 * connection replaces the previous one.
 *
 * v10: the accumulated route state also goes to the stock cockpit channel
 * through TtRgi (route on/off, distance to the turn, street names, distance
 * and arrival time). v12: it goes to the HMI's own cockpit turn display
 * through ClusterService (TtRgi), with the turn icon worked out by
 * ManeuverMapper. touch /tmp/tt-rgi-off stops that and clears it.
 * v13 modes, read live: /tmp/tt-rgi-noactive never reports "route active"
 * (which is what swaps the map tab for the arrow screen); /tmp/tt-rgi-near
 * reports it only within 400 m of the turn (or the metres written in the
 * file). Junction icons are sent in the stock shape {main, dir, -1, 0} unless
 * /tmp/tt-rgi-nopad exists; follow-road is sent as {11, 255} like stock.
 * Guidance ends only after the iPhone has reported no route (or arrival) for
 * OFF_DELAY ms, because iOS sends a short route_state 0 about every 30 s.
 * v15: the turn is sent in stock navigation's pattern (see TtRgi): follow,
 * prepare, approach with bargraph, at the turn; junction icons carry the
 * side-street arms stock sends. /tmp/tt-rgi-near is gone (the phases replace
 * it); /tmp/tt-rgi-nopad sends bare {main,dir} icons for A/B tests. The
 * 2-second tick re-sends whatever stock navigation overwrote. The log keeps
 * 256 KB plus one older file (/tmp/tt-rgd.log.1).
 *
 * Java 1.4 class library: no StringBuilder, no autoboxing, no generics.
 */
public final class RgdBus implements Runnable {
    private static final int PORT = 19810;
    private static final int MAGIC = 0x43504842;
    private static final int EVT_RGD_UPDATE = 0x0020;
    private static final int MAX_PAYLOAD = 128 * 1024;
    private static final File LOG = new File("/tmp/tt-rgd.log");
    private static final File LOG_OLD = new File("/tmp/tt-rgd.log.1");
    private static final long LOG_LIMIT = 262144L;
    private static final File LAST = new File("/tmp/tt-rgd.txt");
    private static final Object LOCK = new Object();
    private static final File RGI_OFF = new File("/tmp/tt-rgi-off");
    private static final File RGI_NOACTIVE = new File("/tmp/tt-rgi-noactive");
    private static final File RGI_NOPAD = new File("/tmp/tt-rgi-nopad");
    private static final long OFF_DELAY = 3000L;
    private static boolean started;
    private static Socket current;
    /** Accumulated key -> value of the current hook connection (deltas arrive). */
    private static final Hashtable STATE = new Hashtable();
    private static long offSince = -1L;
    private static boolean wantOn;
    private static boolean lastOn;
    private static String lastSummary = "";
    /** v48: when time_remaining_seconds was last received (System.currentTimeMillis). */
    private static long remainingAt;
    /** v51: the phone's UTC time (ms) at calAt, from eta_seconds - time_remaining_seconds. */
    private static long calAt;
    private static long calUtcMs;
    /** v43: when each lane-set slot (lgN_) was last sent, and the current maneuver id. */
    private static final Hashtable LANES_AT = new Hashtable();
    private static int laneFirst = -1;
    // v40 bar glide: the iPhone sends rounded distances (0.2 mi, 0.1 mi, 50 ft steps), so the
    // bar jumped. Between changes it now slides at the speed the changes imply.
    private static String gKey;
    private static int gRaw = -1;
    private static long gRawAt;
    private static long gChangeEvery;
    private static int gStep;
    private static double gEst = -1;
    private static double gSpeed;
    private static long gEstAt;

    private final Socket socket;
    private final boolean tick;

    private RgdBus(Socket socket, boolean tick) {
        this.socket = socket;
        this.tick = tick;
    }

    /** Patched CombiBAPListener.setCombiService(service) calls this first. */
    public static void combi(Object listener, Object service) {
        try {
            log(TtRgi.attach(listener, service));
        } catch (Throwable t) {
            log(new StringBuffer("combi attach failed ").append(String.valueOf(t)).toString());
        }
    }

    /** Starts the listener once per HMI lifetime; later calls do nothing. */
    public static void start() {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            started = true;
        }
        try {
            Thread t = new Thread(new RgdBus(null, false), "tt-rgd-bus");
            t.setDaemon(true);
            t.start();
            Thread k = new Thread(new RgdBus(null, true), "tt-rgi-tick");
            k.setDaemon(true);
            k.start();
            RgiPlayer.start();
            RouteRec.start();
            TeleView.start();
        } catch (Throwable t) {
            log(new StringBuffer("start failed ").append(String.valueOf(t)).toString());
        }
    }

    public void run() {
        if (tick) {
            while (true) {
                sleep(wantOn ? 250 : 2000);
                feed(false);
            }
        } else if (socket == null) {
            acceptLoop();
        } else {
            readLoop(socket);
        }
    }

    private static void acceptLoop() {
        ServerSocket server = null;
        int delay = 500;
        while (server == null) {
            try {
                server = new ServerSocket(PORT, 1, InetAddress.getByName("127.0.0.1"));
                log("listening on 127.0.0.1:19810");
            } catch (Throwable t) {
                log(new StringBuffer("bind failed ").append(String.valueOf(t)).toString());
                sleep(delay);
                if (delay < 10000) {
                    delay = delay * 2;
                }
            }
        }
        while (true) {
            try {
                Socket s = server.accept();
                Socket old;
                synchronized (LOCK) {
                    old = current;
                    current = s;
                }
                if (old != null) {
                    close(old);
                }
                synchronized (STATE) {
                    STATE.clear();
                }
                log("hook connected");
                ZoomGate.connected();
                Thread t = new Thread(new RgdBus(s, false), "tt-rgd-reader");
                t.setDaemon(true);
                t.start();
            } catch (Throwable t) {
                log(new StringBuffer("accept failed ").append(String.valueOf(t)).toString());
                sleep(1000);
            }
        }
    }

    private static void readLoop(Socket s) {
        int frames = 0;
        int updates = 0;
        byte[] last = null;
        try {
            DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
            while (true) {
                int magic = in.readInt();
                if (magic != MAGIC) {
                    log(new StringBuffer("bad magic 0x").append(Integer.toHexString(magic)).toString());
                    break;
                }
                in.readInt();
                int type = in.readUnsignedShort();
                in.readUnsignedByte();
                in.readUnsignedByte();
                int len = in.readInt();
                if (len < 0 || len > MAX_PAYLOAD) {
                    log(new StringBuffer("bad length ").append(len).toString());
                    break;
                }
                byte[] payload = new byte[len];
                in.readFully(payload);
                frames++;
                if (type == EVT_RGD_UPDATE && !replaying) {
                    if (last == null || !same(last, payload)) {
                        last = payload;
                        updates++;
                        writeLast(payload);
                        logUpdate(updates, payload);
                        RouteRec.message(payload);
                        merge(payload);
                        catalogue();
                        feed(true);
                    }
                } else if (frames <= 30) {
                    log(new StringBuffer("frame type 0x").append(Integer.toHexString(type))
                        .append(" len ").append(len).toString());
                }
            }
        } catch (EOFException e) {
            log(new StringBuffer("hook disconnected after ").append(frames).append(" frames").toString());
        } catch (Throwable t) {
            log(new StringBuffer("reader stopped after ").append(frames).append(" frames: ")
                .append(String.valueOf(t)).toString());
        } finally {
            close(s);
            boolean mine;
            synchronized (LOCK) {
                mine = current == s;
            }
            if (mine) {
                Layers.phoneGone();
                if (!replaying) {
                    synchronized (STATE) {
                        STATE.clear();
                    }
                    feed(true);
                }
            }
        }
    }

    private static final File CAT = new File("/tmp/tt-rgd-cat.txt");
    private static final File ROUTE = new File("/tmp/tt-route.txt");
    private static final java.util.HashSet SEEN = new java.util.HashSet();
    private static String lastList = "";

    /**
     * v32 research log, so maneuvers need not be driven: every distinct maneuver (mN_*) and lane
     * set (lgN_*) the iPhone sends is appended once to /tmp/tt-rgd-cat.txt (all its keys, sorted,
     * distances and times left out of the comparison), and the whole merged route is written to
     * /tmp/tt-route.txt whenever the maneuver list changes. Both are capped at 512 KB.
     */
    private static void catalogue() {
        try {
            Hashtable groups = new Hashtable();
            String list;
            String dest;
            synchronized (STATE) {
                list = str("maneuver_list");
                dest = str("destination");
                Enumeration e = STATE.keys();
                while (e.hasMoreElements()) {
                    String k = (String) e.nextElement();
                    int u = k.indexOf('_');
                    boolean m = k.length() > 1 && k.charAt(0) == 'm' && Character.isDigit(k.charAt(1));
                    boolean lg = k.startsWith("lg") && k.length() > 2 && Character.isDigit(k.charAt(2));
                    if (u < 0 || !(m || lg)) {
                        continue;
                    }
                    String g = k.substring(0, u);
                    java.util.Vector v = (java.util.Vector) groups.get(g);
                    if (v == null) {
                        v = new java.util.Vector();
                        groups.put(g, v);
                    }
                    v.addElement(new StringBuffer(k.substring(u + 1)).append('=').append(STATE.get(k)).toString());
                }
            }
            StringBuffer out = new StringBuffer();
            Enumeration ge = groups.keys();
            while (ge.hasMoreElements()) {
                String g = (String) ge.nextElement();
                java.util.Vector v = (java.util.Vector) groups.get(g);
                Object[] items = v.toArray();
                java.util.Arrays.sort(items);
                StringBuffer all = new StringBuffer();
                StringBuffer sig = new StringBuffer(g.startsWith("lg") ? "lg" : "m");
                for (int i = 0; i < items.length; i++) {
                    String it = (String) items[i];
                    all.append(' ').append(it);
                    String key = it.substring(0, it.indexOf('='));
                    if (key.indexOf("dist") < 0 && key.indexOf("time") < 0 && key.indexOf("eta") < 0
                        && !key.equals("index")) {
                        sig.append(' ').append(it);
                    }
                }
                String sg = sig.toString();
                if (!SEEN.contains(sg) && SEEN.size() < 4000) {
                    SEEN.add(sg);
                    out.append(System.currentTimeMillis() % 100000000L).append(' ').append(g).append(all).append('\n');
                }
            }
            if (out.length() > 0 && CAT.length() < 524288L) {
                if (CAT.length() == 0L) {
                    out.insert(0, "# maneuvers and lane sets as sent by the iPhone, each once\n");
                }
                append(CAT, new StringBuffer("# route to ").append(dest).append('\n').append(out).toString(), true);
            }
            if (!list.equals(lastList)) {
                lastList = list;
                StringBuffer r = new StringBuffer();
                synchronized (STATE) {
                    Object[] keys = new java.util.Vector(STATE.keySet()).toArray();
                    java.util.Arrays.sort(keys);
                    for (int i = 0; i < keys.length; i++) {
                        r.append(keys[i]).append('=').append(STATE.get(keys[i])).append('\n');
                    }
                }
                append(ROUTE, r.toString(), false);
            }
        } catch (Throwable t) {
            log(new StringBuffer("catalogue failed ").append(String.valueOf(t)).toString());
        }
    }

    private static void append(File f, String text, boolean add) {
        try {
            FileOutputStream o = new FileOutputStream(f.getPath(), add);
            try {
                o.write(text.getBytes("UTF-8"));
            } finally {
                o.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Applies one update: "@routeguidance" then "key:type:value" lines. */
    /**
     * v58: parked replay of recorded iPhone messages (RgiPlayer "rgd" lines). While true, the real
     * hook's updates are read but ignored, and the replayed ones take the live path (merge, feed,
     * TtRgi, TtPanel.live, Card, AutoZoom) exactly as the phone's would.
     */
    static volatile boolean replaying;

    /** v58: one recorded update, its lines separated by '|'. */
    static String replay(String text) {
        replaying = true;
        byte[] payload;
        try {
            payload = text.replace('|', '\n').getBytes("UTF-8");
        } catch (Throwable t) {
            payload = text.replace('|', '\n').getBytes();
        }
        merge(payload);
        feed(true);
        return "";
    }

    /** v58: forget the replayed state (as when the phone goes), feeding "no route" through. */
    static String replayClear() {
        synchronized (STATE) {
            STATE.clear();
        }
        feed(true);
        return "replay: state cleared";
    }

    /** v58: playback ended: clear the replayed route and give the display back to the phone. */
    static void replayEnd() {
        if (replaying) {
            replayClear();
            replaying = false;
        }
    }

    private static void merge(byte[] payload) {
        String text;
        try {
            text = new String(payload, "UTF-8");
        } catch (Throwable t) {
            text = new String(payload);
        }
        synchronized (STATE) {
            int start = 0;
            int n = text.length();
            while (start < n) {
                int end = text.indexOf('\n', start);
                if (end < 0) {
                    end = n;
                }
                String line = text.substring(start, end);
                start = end + 1;
                int a = line.indexOf(':');
                if (a <= 0) {
                    continue;
                }
                int b = line.indexOf(':', a + 1);
                if (b < 0) {
                    continue;
                }
                String key = line.substring(0, a);
                String val = line.substring(b + 1);
                Object old = STATE.put(key, val);
                // v53 (run46: the arrival time crept later every second): the phone re-sends an
                // unchanged time remaining with every update; only a new value is a new snapshot.
                if (key.equals("time_remaining_seconds") && !val.equals(old)) {
                    remainingAt = System.currentTimeMillis();
                }
                if (key.startsWith("lg")) {
                    int u = key.indexOf('_');
                    if (u > 2) {
                        LANES_AT.put(key.substring(0, u), new Long(System.currentTimeMillis()));
                    }
                }
            }
        }
    }

    /**
     * v62 (drive 61: "0.1 mi" on the phone, "550 ft" on our banner): the phone's own distance text,
     * e.g. "0.1 mi" or "500 ft", from PREFIX_str and PREFIX_units (iAP2: 0 km, 1 mi, 2 m, 3 yd,
     * 4 ft); null when the phone sent none.
     */
    private static String phoneText(String prefix) {
        String s = str(new StringBuffer(prefix).append("_str").toString()).trim();
        if (s.length() == 0) {
            return null;
        }
        long u = num(new StringBuffer(prefix).append("_units").toString(), -1);
        String unit = u == 0 ? "km" : u == 1 ? "mi" : u == 2 ? "m" : u == 3 ? "yd" : u == 4 ? "ft" : null;
        return unit == null ? null : new StringBuffer(s).append(' ').append(unit).toString();
    }

    private static String str(String key) {
        Object v = STATE.get(key);
        return v == null ? "" : (String) v;
    }

    private static long num(String key, long dflt) {
        Object v = STATE.get(key);
        if (v == null) {
            return dflt;
        }
        try {
            return Long.parseLong(((String) v).trim());
        } catch (Throwable t) {
            return dflt;
        }
    }

    /**
     * Works out the wanted cockpit state from STATE and hands it to TtRgi.
     * fromBus is false for the 2-second tick, which only finishes a pending off.
     */
    private static void feed(boolean fromBus) {
        boolean on;
        int turn;
        int dest;
        long eta;
        String road;
        String next;
        int main = -1;
        int dir = 0;
        boolean pad = false;
        boolean declare = true;
        short[] icon = null;
        int mState = 0;
        int bar = -1;
        int glideBar = -1;
        String lanes = "-";
        String exit = "";
        String manId = "";
        String turnText = null;
        String destText = null;
        long now = System.currentTimeMillis();
        synchronized (STATE) {
            if (!fromBus && offSince < 0L && !wantOn && !lastOn && !RgiPlayer.playing()) {
                return;
            }
            long state = num("route_state", 0);
            boolean route = !STATE.isEmpty() && num("source_supports_rg", 1) != 0
                && state != 0 && state != 2;
            if (route) {
                offSince = -1L;
                wantOn = true;
            } else if (wantOn) {
                if (offSince < 0L) {
                    offSince = now;
                }
                if (now - offSince >= OFF_DELAY || STATE.isEmpty()) {
                    wantOn = false;
                    offSince = -1L;
                }
            }
            on = wantOn && !RGI_OFF.exists();
            lastOn = on;
            RouteRec.route(on);
            turn = (int) num("dist_maneuver_m", -1);
            dest = (int) num("dist_dest_m", -1);
            turnText = phoneText("dist_maneuver");
            destText = phoneText("dist_dest");
            eta = num("eta_seconds", -1);
            // v48 (car photo: arrival 4:09 AM for 22:09): the HMI's UTC offset is 0 on the TT, so
            // the UTC ETA showed as UTC. With the phone's time remaining, the ETA is re-based on this
            // clock (TtPanel.clock adds it to the cockpit clock); eta_seconds is only the fallback.
            long rem = num("time_remaining_seconds", -1);
            // v51 (car photo: 10:05 for Google's 10:02): Google re-times the route by sending a new
            // eta_seconds without a new time remaining. Calibrate once per time-remaining message
            // (the phone's UTC "now" = eta - remaining at that moment), then follow eta_seconds.
            if (rem >= 0 && eta > 0 && remainingAt > 0L && remainingAt != calAt) {
                calAt = remainingAt;
                calUtcMs = (eta - rem) * 1000L;
            }
            if (eta > 0 && calAt > 0L) {
                eta = (calAt + (eta * 1000L - calUtcMs)) / 1000L;
            }
            road = str("current_road");
            next = "";
            String order = str("maneuver_list");
            int comma = order.indexOf(',');
            String first = (comma < 0 ? order : order.substring(0, comma)).trim();
            manId = first;
            if (first.length() > 0) {
                StringBuffer p = new StringBuffer("m").append(first).append('_');
                int plen = p.length();
                next = str(p.append("after_road").toString());
                if (next.length() == 0) {
                    p.setLength(plen);
                    next = str(p.append("name").toString());
                }
                p.setLength(plen);
                int type = (int) num(p.append("type").toString(), -1);
                if (type >= 0) {
                    p.setLength(plen);
                    String exitKey = p.append("exit_angle").toString();
                    p.setLength(plen);
                    String turnKey = p.append("turn_angle").toString();
                    boolean anglePresent = STATE.get(exitKey) != null || STATE.get(turnKey) != null;
                    int angle = (int) num(STATE.get(exitKey) != null ? exitKey : turnKey, 1000);
                    p.setLength(plen);
                    int junction = (int) num(p.append("junction_type").toString(), -1);
                    p.setLength(plen);
                    int side = (int) num(p.append("driving_side").toString(), -1);
                    try {
                        int[] m = ManeuverMapper.map(type, angle, junction, side, anglePresent);
                        main = m[0];
                        dir = m[1];
                        int[] o = stockCode(type, angle, side);
                        if (o != null) {
                            main = o[0];
                            dir = o[1];
                        }
                    } catch (Throwable t) {
                        main = -1;
                    }
                }
            }
            if (main == 11 && dir == 0) {
                dir = 255;
            }
            if (first.length() > 0) {
                dropOldLanes(first, now);
                // v43: Google sends lane_guidance_showing, 1 while it shows lanes itself (on the
                // approach); with 0 there are none to show. Apple Maps always sends 0: ignored.
                // v53 (run46: a right turn showed the left turn's lanes): lane sets are numbered by
                // their own counter, lane_guidance_index, not by maneuver id (maneuver 2 used set 1).
                boolean showing = "1".equals(str("lane_guidance_showing").trim());
                boolean google = str("source_name").indexOf("Google") >= 0;
                if (showing) {
                    lanes = laneSpec(str("lane_guidance_index").trim());
                } else {
                    lanes = google ? "-" : laneSpec(first);
                }
                StringBuffer ep = new StringBuffer("m").append(first).append('_');
                int eplen = ep.length();
                exit = exitNumber(str(ep.append("name").toString()));
                if (exit.length() == 0) {
                    ep.setLength(eplen);
                    exit = exitNumber(str(ep.append("after_road").toString()));
                }
            }
            pad = !RGI_NOPAD.exists();
            declare = !RGI_NOACTIVE.exists();
            if (main >= 0) {
                int[] ph = phase(main, turn);
                mState = ph[0];
                bar = ph[1];
                int g = glide(new StringBuffer(first).append('/').append(main).toString(), turn, now);
                glideBar = bar < 0 ? -1 : phase(main, g)[1];
                if (mState == 1) {
                    icon = new short[] { 11, 255 };
                } else if (mState == 2) {
                    icon = main >= 13 && main <= 20
                        ? new short[] { 12, (short) (dir & 0xff) } : new short[] { 11, 255 };
                } else {
                    icon = icon(main, dir, pad);
                }
            }
            StringBuffer sum = new StringBuffer("rgi on=");
            sum.append(on).append(" declare=").append(declare).append(" state=").append(state).append(" icon=").append(main).append(',').append(dir)
                .append(" phase=").append(mState).append(" bar=").append(bar)
                .append(" turn=").append(turn)
                .append(" dest=").append(dest).append(" eta=").append(eta)
                .append(" road=").append(road).append(" next=").append(next);
            String summary = sum.toString();
            if (!summary.equals(lastSummary)) {
                lastSummary = summary;
                log(summary);
            }
        }
        if (RgiPlayer.playing() && !replaying) {
            return;
        }
        try {
            // v54: the cockpit's bottom strip shows these two (StripText): "Left to Alpha St".
            String stripRoad = road;
            String stripNext = next;
            int sm = StripText.mode();
            if (sm != 2) {
                String t = StripText.turn(main, dir, next, exit);
                stripNext = t;
                if (sm == 1 && on && t.length() > 0) {
                    stripRoad = t;
                }
            }
            String result = TtRgi.update(on, declare, stockSafe(icon), mState, turn, bar, dest, eta, stripRoad, stripNext);
            TtPanel.near = nearTurn(main, turn);
            // v56: back to the v46 card distances (1 km, motorway 2 km); the card is opt-in.
            Card.route(on, TtPanel.near);
            AutoZoom.route(on, main, turn, manId);
            // Lanes only while approaching the turn, like stock.
            TtPanel.phoneDistances(turn, turnText, dest, destText);
            TtPanel.live(on, icon, turn, glideBar, mState >= 3 ? lanes : "-", dest, eta, exit, next);
            if (result != null) {
                log(result);
            }
        } catch (Throwable t) {
            log(new StringBuffer("cockpit update failed ").append(String.valueOf(t)).toString());
        }
    }

    /**
     * Where the TT's stock navigation uses a different code than luka's
     * mapper (run3): motorway exits are EXIT_RIGHT/LEFT {15,192}/{16,64}
     * rather than a slight turn, and keep left/right is FORK_2 {19,64}/{19,192}.
     * iAP2 types: 8 off ramp (side from the angle, right when unknown),
     * 13 keep left, 14 keep right, 22/23 motorway off ramp left/right.
     * Returns null to keep the mapper's code.
     */
    static int[] stockCode(int type, int angle, int side) {
        boolean lht = side == 1;
        boolean leftAngle = angle < 0 && angle > -180;
        switch (type) {
            // v32 pictures (TT-local codes, see PanelArt.TT_*; never sent to the stock cluster).
            case 7:
                return lht ? new int[] { 27, 64 } : new int[] { 26, 192 };
            case 9:
                return new int[] { 40, leftAngle ? 32 : 224 };
            case 11:
                return new int[] { 42, ManeuverMapper.map(type, angle, -1, side, angle != 1000)[1] };
            case 15:
            case 17:
                return new int[] { 44, 0 };
            case 20:
                return new int[] { 41, 64 };
            case 21:
                return new int[] { 41, 192 };
            case 52:
                return new int[] { 43, 64 };
            case 53:
                return new int[] { 43, 192 };
            case 8:
                return angle < 0 && angle > -180 ? new int[] { 16, 64 } : new int[] { 15, 192 };
            case 13:
                return new int[] { 19, 64 };
            case 14:
                return new int[] { 19, 192 };
            case 22:
                return new int[] { 16, 64 };
            case 23:
                return new int[] { 15, 192 };
            default:
                return null;
        }
    }

    /** The icon for the stock cluster: TT-local picture codes (40+) become a plain turn / follow road. */
    static short[] stockSafe(short[] icon) {
        if (icon == null || icon.length < 2 || icon[0] < 40) {
            return icon;
        }
        if (icon[0] == 44) {
            return new short[] { 11, 255 };
        }
        return new short[] { 13, icon[1] };
    }

    /**
     * v40: distance to the turn for the bar, between the iPhone's rounded values. When the
     * rounded value changes, the real distance is about halfway between old and new; from there
     * it moves at the speed of the last changes (10 m/s until the first change), never more than
     * half a step below the current value (the iPhone keeps sending the same value while the car
     * stands, so the bar can lead by at most that much). A jump up (reroute) or another maneuver
     * starts again from the iPhone's value.
     */
    static int glide(String key, int raw, long now) {
        if (raw < 0) {
            gKey = null;
            return raw;
        }
        if (gKey == null || !gKey.equals(key) || raw > gRaw + Math.max(gStep, 30)) {
            gKey = key;
            gRaw = raw;
            gRawAt = now;
            gChangeEvery = 0;
            gStep = 0;
            gEst = raw;
            gSpeed = 10;
            gEstAt = now;
            return raw;
        }
        if (raw != gRaw) {
            long dt = now - gRawAt;
            int step = gRaw - raw;
            if (dt > 300 && step > 0) {
                double v = step * 1000.0 / dt;
                gSpeed = v < 0.5 ? 0.5 : (v > 70 ? 70 : v);
                gChangeEvery = dt;
            }
            gStep = step > 0 ? step : gStep;
            double boundary = (gRaw + raw) / 2.0;
            gEst = Math.min(gEst, boundary);
            gRaw = raw;
            gRawAt = now;
            gEstAt = now;
        } else {
            gEst -= gSpeed * (now - gEstAt) / 1000.0;
            gEstAt = now;
        }
        double lo = gStep > 0 ? raw - gStep / 2.0 : raw - Math.min(raw / 4.0, 80.0);
        double hi = gStep > 0 ? raw + gStep / 2.0 : raw;
        if (gEst < lo) {
            gEst = lo;
        }
        if (gEst > hi) {
            gEst = hi;
        }
        if (gEst < 0) {
            gEst = 0;
        }
        return (int) (gEst + 0.5);
    }

    /**
     * {BAP maneuver state, bargraph} for a turn of icon code main at turn
     * metres, using the distances stock navigation used on the TT (run3):
     * city turns prepare from about 1250 m, approach (bargraph, full icon)
     * from 430 m, state 4 from 100 m; motorway exits and forks 3200 / 1750 /
     * 150 m. The bargraph runs 255 -> 0 over the approach. Codes that are not
     * turns (follow, off-road, calculating) stay in state 1; an unknown
     * distance shows the full icon without a bargraph.
     */
    /**
     * v45: whether the turn banner should show on the CarPlay map: the next turn (or the
     * destination) within 1 km, motorway exits and forks within 2 km. Unknown values count as near.
     */
    static boolean nearTurn(int main, int turn) {
        if (main < 0 || turn < 0) {
            return true;
        }
        boolean turnLike = main >= 12 && main != 28 && main != 29 && main != 30 && main != 31;
        boolean arrival = main >= 2 && main <= 5 || main == 38;
        if (main == 11 || (!turnLike && !arrival)) {
            return false;
        }
        boolean motorway = main >= 14 && main <= 20 || main == 40 || main == 43;
        return turn <= (motorway ? 2000 : 1000);
    }

    static int[] phase(int main, int turn) {
        boolean turnLike = main >= 12 && main != 28 && main != 29 && main != 30 && main != 31;
        boolean arrival = main >= 2 && main <= 5 || main == 38;
        if (main == 11 || (!turnLike && !arrival)) {
            return new int[] { 1, -1 };
        }
        if (turn < 0) {
            return new int[] { 3, -1 };
        }
        boolean motorway = main >= 14 && main <= 20 || main == 40 || main == 43;
        int prepare = motorway ? 3200 : 1250;
        int approach = motorway ? 1750 : 430;
        int last = motorway ? 150 : 100;
        if (turn > prepare) {
            return new int[] { 1, -1 };
        }
        if (turn > approach) {
            return new int[] { 2, -1 };
        }
        int bar = (int) ((turn * 255L) / approach);
        if (bar > 255) {
            bar = 255;
        }
        return new int[] { turn <= last ? 4 : 3, bar };
    }

    /**
     * The cockpit icon stream in stock's shape: {main, dir 0..255, side
     * streets as signed bytes}. Stock sends a plain turn as {13, dir, -1,
     * other arms of the crossing} (left: -1,0,-64; right: -1,64,0), a
     * motorway exit as {15|16, dir, -1}, forks and everything else as
     * {main, dir}. With pad false only {main, dir} is sent.
     */
    static short[] icon(int main, int dir, boolean pad) {
        short d = (short) (dir & 0xff);
        if (!pad) {
            return new short[] { (short) main, d };
        }
        if (main == 13 || main == 14) {
            int a = dir & 0xff;
            if (a > 32 && a < 128) {
                return new short[] { (short) main, d, -1, 0, -64 };
            }
            if (a > 128 && a < 224) {
                return new short[] { (short) main, d, -1, 64, 0 };
            }
            return new short[] { (short) main, d, -1, 0 };
        }
        if (main == 15 || main == 16) {
            return new short[] { (short) main, d, -1 };
        }
        return new short[] { (short) main, d };
    }

    /**
     * v43 (car photos): lane sets are numbered by maneuver id and often sent only once, at route
     * start. After a reroute the ids start again at 0 and the old route's sets stayed: a left turn
     * showed the lanes of the previous right turn. When the current id goes backwards, drop every
     * set not sent in the last 2 s (a new route's sets can arrive just before its maneuver list).
     * Call with STATE locked.
     */
    private static void dropOldLanes(String first, long now) {
        int fi;
        try {
            fi = Integer.parseInt(first.trim());
        } catch (Throwable t) {
            return;
        }
        if (laneFirst >= 0 && fi < laneFirst) {
            java.util.Vector gone = new java.util.Vector();
            for (Enumeration e = STATE.keys(); e.hasMoreElements();) {
                String k = (String) e.nextElement();
                int u = k.indexOf('_');
                if (k.startsWith("lg") && u > 2) {
                    Long at = (Long) LANES_AT.get(k.substring(0, u));
                    if (at == null || now - at.longValue() > 2000L) {
                        gone.addElement(k);
                    }
                }
            }
            for (int i = 0; i < gone.size(); i++) {
                STATE.remove(gone.elementAt(i));
            }
            if (gone.size() > 0) {
                log(new StringBuffer("lanes: route restarted (maneuver ").append(laneFirst).append(" -> ")
                    .append(fi).append("), dropped ").append(gone.size()).append(" old lane keys").toString());
            }
        }
        laneFirst = fi;
    }

    /**
     * Lane guidance for maneuver index idx as the panel's LANES token: lanes separated by
     * '/', each ANGLE[,ANGLE...]:STATUS. From lgN_index / lgN_lane_directions (one angle per
     * lane) / lgN_lane_angles ('|' per lane, ',' between extra angles) / lgN_lane_status.
     * "-" when there is none. Call with STATE locked.
     */
    static String laneSpec(String idx) {
        for (int k = 0; k < 32; k++) {
            StringBuffer p = new StringBuffer("lg").append(k).append('_');
            int plen = p.length();
            if (!idx.equals(str(p.append("index").toString()).trim())) {
                continue;
            }
            p.setLength(plen);
            String[] dirs = split(str(p.append("lane_directions").toString()), ',');
            p.setLength(plen);
            String[] st = split(str(p.append("lane_status").toString()), ',');
            p.setLength(plen);
            String[] ang = split(str(p.append("lane_angles").toString()), '|');
            if (dirs.length == 0 || (dirs.length == 1 && dirs[0].trim().length() == 0)) {
                return "-";
            }
            StringBuffer b = new StringBuffer();
            for (int i = 0; i < dirs.length && i < 12; i++) {
                if (i > 0) {
                    b.append('/');
                }
                String main = dirs[i].trim();
                b.append(main.length() == 0 ? "0" : main);
                if (i < ang.length) {
                    String[] extra = split(ang[i], ',');
                    for (int j = 0; j < extra.length; j++) {
                        String e = extra[j].trim();
                        if (e.length() > 0 && !e.equals(main)) {
                            b.append(',').append(e);
                        }
                    }
                }
                b.append(':').append(i < st.length && st[i].trim().length() > 0 ? st[i].trim() : "0");
            }
            return b.toString();
        }
        return "-";
    }

    /**
     * Exit number from maneuver text such as "Take exit 12A toward ..." or "Exit 245":
     * the token after the word exit, if it starts with a digit. "" if none.
     */
    static String exitNumber(String text) {
        if (text == null) {
            return "";
        }
        String low = text.toLowerCase();
        int from = 0;
        while (true) {
            int i = low.indexOf("exit", from);
            if (i < 0) {
                return "";
            }
            int j = i + 4;
            while (j < text.length() && (text.charAt(j) == ' ' || text.charAt(j) == '#')) {
                j++;
            }
            int k = j;
            while (k < text.length() && k - j < 6 && Character.isLetterOrDigit(text.charAt(k))) {
                k++;
            }
            if (k > j && Character.isDigit(text.charAt(j))) {
                return text.substring(j, k).toUpperCase();
            }
            from = i + 4;
        }
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

    private static boolean same(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static void writeLast(byte[] payload) {
        try {
            FileOutputStream out = new FileOutputStream(LAST.getPath(), false);
            try {
                out.write(payload);
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }

    /** One log line per update: newlines shown as " | ", cut at 700 characters. */
    private static void logUpdate(int n, byte[] payload) {
        StringBuffer b = new StringBuffer("rgd #");
        b.append(n).append(' ');
        int limit = payload.length < 700 ? payload.length : 700;
        for (int i = 0; i < limit; i++) {
            int c = payload[i] & 0xff;
            if (c == 10) {
                b.append(" | ");
            } else if (c >= 32 && c < 127) {
                b.append((char) c);
            } else {
                b.append('?');
            }
        }
        if (limit < payload.length) {
            b.append(" ...");
        }
        log(b.toString());
    }

    private static void close(Socket s) {
        try {
            s.close();
        } catch (Throwable ignored) {
        }
    }

    private static void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (Throwable ignored) {
        }
    }

    /** Log line from the playback thread. */
    static void note(String line) {
        log(line);
    }

    /** v59: the merged state as one "|"-separated update (RouteRec's seed; types are not kept). */
    static String stateDump() {
        StringBuffer b = new StringBuffer();
        synchronized (STATE) {
            java.util.Enumeration e = STATE.keys();
            while (e.hasMoreElements()) {
                Object k = e.nextElement();
                String v = String.valueOf(STATE.get(k)).replace('|', '/').replace((char) 10, ' ');
                if (b.length() > 0) {
                    b.append('|');
                }
                b.append(k).append(":s:").append(v);
            }
        }
        return b.toString();
    }

    private static void log(String line) {
        // v59: notes go into the route recording too (the cut "rgd #" copies do not)
        if (line != null && !line.startsWith("rgd #")) {
            RouteRec.note(line);
        }
        try {
            if (LOG.length() >= LOG_LIMIT) {
                LOG_OLD.delete();
                LOG.renameTo(LOG_OLD);
            }
            FileOutputStream out = new FileOutputStream(LOG.getPath(), true);
            try {
                StringBuffer b = new StringBuffer();
                b.append(System.currentTimeMillis() % 100000000L).append(' ').append(line).append('\n');
                out.write(b.toString().getBytes());
            } finally {
                out.close();
            }
        } catch (Throwable ignored) {
        }
    }
}
