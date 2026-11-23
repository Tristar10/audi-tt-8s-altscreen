package local.tt;

import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Head-unit end of the OpenHaldex link (wiki/Telemetry.md, same protocol as gauges/headunit/ttlink.c).
 *
 * Listens on UDP 49160 (the stock firewall passes 49153-49162 from hotspot clients) while the
 * autocross view is enabled, and closes the port when it is not, so ttlink can still be used for
 * link tests. Big-endian datagrams:
 *   TTG1 telemetry: "TTG1" key u32, seq u16, flags u8, count u8, count * (u16 id, s32 value*1000).
 *        Answered with TTA1: "TTA1" seq u16, 0 u16, rx u32, lost u32, uptime ms u32.
 *   TTP1 preset catalog: "TTP1" key u32, version u16, count u8, count * (id u8, mode u8, len u8, name).
 *   TTR1 reply to a TTC1 command: "TTR1" key u32, seq u16, result u8, active u8, version u16.
 * Several senders may stream (a second CAN board later): channels are merged, newest wins. Preset
 * commands go only to the sender of the catalog (OpenHaldex).
 *
 * Java 1.4 library only (HMI-patching.md): no string +, no generics, no autoboxing.
 */
final class TeleLink implements Runnable {
    static final int PORT = 49160;
    static final int CHANNELS = TeleLayout.CHANNELS;
    /** A channel older than this is shown as no data. */
    static final long STALE_MS = 1500L;
    /** No datagram for this long: the link is down. */
    static final long LINK_MS = 2000L;

    private static final Object LOCK = new Object();
    private static final int[] VAL = new int[CHANNELS];
    private static final long[] AT = new long[CHANNELS];
    private static final int[] CAT_ID = new int[TeleLayout.MAXPRESET];
    private static final int[] CAT_MODE = new int[TeleLayout.MAXPRESET];
    private static final String[] CAT_NAME = new String[TeleLayout.MAXPRESET];

    private static boolean started;
    private static volatile boolean wanted;
    private static int key;
    private static long lastRx;
    private static int lastSeq = -1;
    private static long rx;
    private static long lost;
    private static long bad;
    private static int catN;
    private static int catVer = -1;
    private static InetAddress ohAddr;
    private static int ohPort;
    private static int cmdSeq;
    private static int rateHz = -1;
    private static long rateWindow;
    private static int rateCount;
    private static String lastReply = "";
    private static DatagramSocket sock;
    private static final long T0 = System.currentTimeMillis();

    private TeleLink() {
    }

    /** Once per HMI lifetime. */
    static void start(int k) {
        synchronized (LOCK) {
            if (started) {
                return;
            }
            started = true;
            key = k;
        }
        Thread t = new Thread(new TeleLink(), "tt-tele-rx");
        t.setDaemon(true);
        t.start();
    }

    /** Open the port while the view is enabled; close it otherwise (so ttlink can bind it). */
    static void want(boolean w) {
        wanted = w;
    }

    public void run() {
        byte[] buf = new byte[600];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        for (;;) {
            DatagramSocket s = sock;
            if (!wanted) {
                if (s != null) {
                    s.close();
                    sock = null;
                    RgdBus.note("tele rx closed");
                }
                sleep(500L);
                continue;
            }
            try {
                if (s == null) {
                    s = new DatagramSocket(PORT); // all interfaces: the hotspot is uap0, not loopback
                    s.setSoTimeout(500);
                    sock = s;
                    RgdBus.note(new StringBuffer("tele rx listening udp ").append(PORT).toString());
                }
                p.setLength(buf.length);
                s.receive(p);
                handle(s, buf, p.getLength(), p.getAddress(), p.getPort());
            } catch (InterruptedIOException timeout) {
                // nothing for half a second: the view shows staleness
            } catch (Throwable t) {
                RgdBus.note(new StringBuffer("tele rx error ").append(String.valueOf(t)).toString());
                if (s != null) {
                    s.close();
                }
                sock = null;
                sleep(2000L); // e.g. ttlink holds the port
            }
        }
    }

    private static void handle(DatagramSocket s, byte[] b, int n, InetAddress from, int port) throws Exception {
        long now = System.currentTimeMillis();
        if (n >= 12 && magic(b, 'T', 'T', 'G', '1')) {
            if (rd32(b, 4) != key) {
                bump(true);
                return;
            }
            int seq = rd16(b, 8);
            int count = b[11] & 0xFF;
            if (12 + count * 6 > n) {
                bump(false);
                return;
            }
            synchronized (LOCK) {
                if (lastSeq >= 0 && now - lastRx < 5000L) {
                    int gap = (seq - lastSeq - 1) & 0xFFFF;
                    if (gap > 0 && gap < 1000) {
                        lost += gap;
                    }
                }
                lastSeq = seq;
                lastRx = now;
                rx++;
                rateCount++;
                if (now - rateWindow >= 1000L) {
                    rateHz = (int) (rateCount * 1000L / Math.max(1L, now - rateWindow));
                    rateCount = 0;
                    rateWindow = now;
                }
                for (int i = 0; i < count; i++) {
                    int o = 12 + i * 6;
                    int id = rd16(b, o);
                    if (id > 0 && id < CHANNELS) {
                        VAL[id] = (int) rd32(b, o + 2);
                        AT[id] = now;
                    }
                }
            }
            byte[] a = new byte[20];
            a[0] = 'T';
            a[1] = 'T';
            a[2] = 'A';
            a[3] = '1';
            wr16(a, 4, seq);
            wr32(a, 8, rx);
            wr32(a, 12, lost);
            wr32(a, 16, now - T0);
            s.send(new DatagramPacket(a, a.length, from, port));
        } else if (n >= 11 && magic(b, 'T', 'T', 'P', '1')) {
            if (rd32(b, 4) != key) {
                bump(true);
                return;
            }
            int ver = rd16(b, 8);
            int cnt = b[10] & 0xFF;
            int o = 11;
            int[] ids = new int[TeleLayout.MAXPRESET];
            int[] modes = new int[TeleLayout.MAXPRESET];
            String[] names = new String[TeleLayout.MAXPRESET];
            if (cnt > TeleLayout.MAXPRESET) {
                bump(false);
                return;
            }
            for (int i = 0; i < cnt; i++) {
                if (o + 3 > n) {
                    bump(false);
                    return;
                }
                int len = b[o + 2] & 0xFF;
                if (len < 1 || len > 16 || o + 3 + len > n) {
                    bump(false);
                    return;
                }
                ids[i] = b[o] & 0xFF;
                modes[i] = b[o + 1] & 0xFF;
                names[i] = new String(b, o + 3, len, "US-ASCII");
                o += 3 + len;
            }
            boolean changed;
            synchronized (LOCK) {
                changed = ver != catVer;
                catVer = ver;
                catN = cnt;
                for (int i = 0; i < cnt; i++) {
                    CAT_ID[i] = ids[i];
                    CAT_MODE[i] = modes[i];
                    CAT_NAME[i] = names[i];
                }
                ohAddr = from;
                ohPort = port;
            }
            if (changed) {
                RgdBus.note(new StringBuffer("tele catalog v=").append(ver).append(' ').append(cnt).append(" presets")
                    .toString());
            }
        } else if (n >= 14 && magic(b, 'T', 'T', 'R', '1')) {
            StringBuffer r = new StringBuffer("reply cmd ").append(rd16(b, 8)).append(" result ")
                .append(b[10] & 0xFF).append(" active ").append(b[11] & 0xFF);
            synchronized (LOCK) {
                lastReply = r.toString();
            }
            RgdBus.note(new StringBuffer("tele ").append(lastReply).toString());
        } else {
            bump(false);
        }
    }

    /** Asks OpenHaldex to apply a preset (TTC1, sent once; channel 22 confirms). */
    static String apply(int presetId) {
        InetAddress to;
        int port;
        int ver;
        int seq;
        synchronized (LOCK) {
            to = ohAddr;
            port = ohPort;
            ver = catVer;
            seq = cmdSeq = (cmdSeq + 1) & 0xFFFF;
        }
        DatagramSocket s = sock;
        if (to == null || ver < 0 || s == null) {
            return "tele apply: no catalog from OpenHaldex yet";
        }
        byte[] c = new byte[14];
        c[0] = 'T';
        c[1] = 'T';
        c[2] = 'C';
        c[3] = '1';
        wr32(c, 4, key);
        wr16(c, 8, seq);
        c[10] = 1;
        c[11] = (byte) presetId;
        wr16(c, 12, ver);
        try {
            s.send(new DatagramPacket(c, c.length, to, port));
        } catch (Throwable t) {
            return new StringBuffer("tele apply failed ").append(String.valueOf(t)).toString();
        }
        return new StringBuffer("tele apply preset ").append(presetId).append(" cmd ").append(seq).toString();
    }

    /** Copies the latest state into the layout's inputs. */
    static void snapshot(TeleLayout t, long now) {
        synchronized (LOCK) {
            t.link = lastRx != 0 && now - lastRx < LINK_MS;
            for (int i = 0; i < CHANNELS; i++) {
                t.val[i] = VAL[i];
                t.has[i] = AT[i] != 0 && now - AT[i] < STALE_MS;
            }
            t.catN = catN;
            for (int i = 0; i < catN; i++) {
                t.catId[i] = CAT_ID[i];
                t.catMode[i] = CAT_MODE[i];
                t.catName[i] = CAT_NAME[i];
            }
            t.rateHz = t.link ? rateHz : -1;
        }
    }

    /** Fake values for a parked test without OpenHaldex: slow sweeps on every channel. */
    static void demo(TeleLayout t, long now) {
        double s = (Math.sin(now / 1400.0) + 1.0) / 2.0;
        double f = (Math.sin(now / 900.0) + 1.0) / 2.0;
        t.link = true;
        t.rateHz = 20;
        put(t, TeleLayout.CH_RPM, 900 + 6200 * s);
        put(t, TeleLayout.CH_GEAR, 2 + (int) (s * 3.99));
        put(t, TeleLayout.CH_PEDAL, 100 * s);
        put(t, TeleLayout.CH_BRAKE, 60 * (1 - f) * (1 - s));
        put(t, TeleLayout.CH_BOOST, -60 + 200 * s);
        put(t, TeleLayout.CH_HX_TARGET, 100 * f);
        put(t, TeleLayout.CH_HX_ACTUAL, 100 * Math.max(0.0, f - 0.1));
        put(t, TeleLayout.CH_COOLANT, 92);
        put(t, TeleLayout.CH_OIL, 100 + 35 * s);
        put(t, TeleLayout.CH_IAT, 38);
        put(t, TeleLayout.CH_DSG, 80);
        put(t, TeleLayout.CH_CLUTCH, 45 + 50 * f);
        put(t, TeleLayout.CH_PRESET, 2);
        String[] names = {"Street", "Autocross", "Launch", "Wet", "FWD"};
        int[] modes = {0, 9, 2, 9, 1};
        t.catN = names.length;
        for (int i = 0; i < names.length; i++) {
            t.catId[i] = i + 1;
            t.catName[i] = names[i];
            t.catMode[i] = modes[i];
        }
    }

    private static void put(TeleLayout t, int ch, double v) {
        t.val[ch] = (int) Math.round(v * 1000.0);
        t.has[ch] = true;
    }

    static String info() {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            return new StringBuffer("rx=").append(rx).append(" lost=").append(lost).append(" bad=").append(bad)
                .append(" rate=").append(rateHz).append("Hz lastRx=")
                .append(lastRx == 0 ? -1 : now - lastRx).append("ms catalog=").append(catN).append(" v=")
                .append(catVer).append(" port=").append(sock == null ? "closed" : "open").append(' ')
                .append(lastReply).toString();
        }
    }

    private static void bump(boolean keyWrong) {
        synchronized (LOCK) {
            bad++;
        }
    }

    private static boolean magic(byte[] b, char a, char c, char d, char e) {
        return b[0] == a && b[1] == c && b[2] == d && b[3] == e;
    }

    private static int rd16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static long rd32(byte[] b, int o) {
        return ((long) (b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static void wr16(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 8);
        b[o + 1] = (byte) v;
    }

    private static void wr32(byte[] b, int o, long v) {
        b[o] = (byte) (v >> 24);
        b[o + 1] = (byte) (v >> 16);
        b[o + 2] = (byte) (v >> 8);
        b[o + 3] = (byte) v;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
