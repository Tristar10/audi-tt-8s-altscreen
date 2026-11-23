package local.tt;

/**
 * SKETCH. The "TTG1" telemetry datagram from the ESP32 (OpenHaldex) to the head unit, UDP port 49160.
 * Plain Java 1.4, no HMI classes, so it can be tested on a PC (GaugePacketTest).
 *
 * All numbers big-endian.
 *   off  size
 *   0    4   magic "TTG1"
 *   4    4   key: u32 shared secret (0 = none). The hotspot is shared with passengers' phones.
 *   8    2   seq: u16, +1 per datagram, wraps. Older datagrams (reordered) are dropped.
 *   10   1   flags: bit0 CAN alive, bit1 diagnostic polling active, bit2 Haldex fault
 *   11   1   count N (1..40)
 *   12   N*6 entries: u16 channel id, s32 value * 1000 in the channel's SI unit (see Channel table)
 *
 * The sender includes only channels it has fresh values for; the head unit marks a channel stale
 * when it has not been seen for STALE_MS.
 */
public final class GaugePacket {
    public static final int PORT = 49160; // in the stock firewall's open WLAN range 49153-49162
    public static final int MAX = 40;
    public static final long STALE_MS = 1000L;

    // Channel ids. SI units, value * 1000 on the wire. Keep in step with esp32/tt_gauge_tx.h.
    public static final int RPM = 1;          // 1/min
    public static final int SPEED = 2;        // km/h
    public static final int PEDAL = 3;        // %
    public static final int TORQUE = 4;       // Nm (engine actual)
    public static final int COOLANT = 5;      // deg C
    public static final int OIL = 6;          // deg C
    public static final int IAT = 7;          // deg C
    public static final int BOOST = 8;        // kPa above ambient (negative = vacuum)
    public static final int LAMBDA = 9;       // lambda, 1.000 = stoich
    public static final int HALDEX_LOCK = 10; // % requested coupling lock
    public static final int HALDEX_ACT = 11;  // % reported by the Haldex controller
    public static final int HALDEX_MODE = 12; // OpenHaldex mode number (enum, not scaled meaningfully)
    public static final int GEAR = 13;        // 0 = N, -1 = R
    public static final int LAT_G = 14;       // g
    public static final int LONG_G = 15;      // g
    public static final int DSG_OIL = 16;     // deg C
    public static final int BATTERY = 17;     // V
    public static final int CHANNELS = 64;    // array size; ids >= this are ignored

    public int key;
    public int seq;
    public int flags;
    public int count;
    public final int[] ids = new int[MAX];
    public final int[] milli = new int[MAX];

    /** Parses buf[0..len). Returns false for anything that is not a complete TTG1 datagram. */
    public boolean parse(byte[] buf, int len) {
        if (len < 12 || buf[0] != 'T' || buf[1] != 'T' || buf[2] != 'G' || buf[3] != '1') {
            return false;
        }
        int n = buf[11] & 0xFF;
        if (n < 1 || n > MAX || len < 12 + n * 6) {
            return false;
        }
        key = s32(buf, 4);
        seq = u16(buf, 8);
        flags = buf[10] & 0xFF;
        count = n;
        for (int i = 0; i < n; i++) {
            int o = 12 + i * 6;
            ids[i] = u16(buf, o);
            milli[i] = s32(buf, o + 2);
        }
        return true;
    }

    /** True if seq is newer than last (16-bit wrap), or last is -1 (first datagram). */
    public static boolean newer(int seq, int last) {
        if (last < 0) {
            return true;
        }
        int d = (seq - last) & 0xFFFF;
        return d != 0 && d < 0x8000;
    }

    /** Writes a datagram (used by the demo source and the PC test). Returns its length. */
    public static int build(byte[] out, int key, int seq, int flags, int[] ids, int[] milli, int n) {
        out[0] = 'T';
        out[1] = 'T';
        out[2] = 'G';
        out[3] = '1';
        put32(out, 4, key);
        out[8] = (byte) (seq >> 8);
        out[9] = (byte) seq;
        out[10] = (byte) flags;
        out[11] = (byte) n;
        for (int i = 0; i < n; i++) {
            int o = 12 + i * 6;
            out[o] = (byte) (ids[i] >> 8);
            out[o + 1] = (byte) ids[i];
            put32(out, o + 2, milli[i]);
        }
        return 12 + n * 6;
    }

    private static int u16(byte[] b, int o) {
        return ((b[o] & 0xFF) << 8) | (b[o + 1] & 0xFF);
    }

    private static int s32(byte[] b, int o) {
        return ((b[o] & 0xFF) << 24) | ((b[o + 1] & 0xFF) << 16) | ((b[o + 2] & 0xFF) << 8) | (b[o + 3] & 0xFF);
    }

    private static void put32(byte[] b, int o, int v) {
        b[o] = (byte) (v >> 24);
        b[o + 1] = (byte) (v >> 16);
        b[o + 2] = (byte) (v >> 8);
        b[o + 3] = (byte) v;
    }
}
