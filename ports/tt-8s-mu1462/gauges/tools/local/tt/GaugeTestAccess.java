package local.tt;

/** PC test access to the package-private gauge classes (tools only, never in the jar). */
public final class GaugeTestAccess {
    private GaugeTestAccess() {
    }

    public static String describe(byte[] buf, int len) {
        GaugePacket p = new GaugePacket();
        if (!p.parse(buf, len)) {
            return "parse FAILED";
        }
        StringBuffer b = new StringBuffer("key=").append(p.key).append(" seq=").append(p.seq)
            .append(" flags=").append(p.flags).append(" count=").append(p.count);
        for (int i = 0; i < p.count; i++) {
            b.append("\n  ch ").append(p.ids[i]).append(" = ").append(p.milli[i] / 1000.0);
        }
        // seq wrap rules
        b.append("\nnewer(1,-1)=").append(GaugePacket.newer(1, -1))
            .append(" newer(0,65535)=").append(GaugePacket.newer(0, 65535))
            .append(" newer(5,5)=").append(GaugePacket.newer(5, 5))
            .append(" newer(4,5)=").append(GaugePacket.newer(4, 5));
        b.append("\nfmt: ").append(Gauges.fmt(14.96f, 1)).append(' ').append(Gauges.fmt(-0.04f, 1)).append(' ')
            .append(Gauges.fmt(0.85f, 2)).append(' ').append(Gauges.fmt(6999.6f, 0));
        return b.toString();
    }

    public static byte[] dial(int size, float redFrom) {
        return GaugeArt.dial(size, redFrom);
    }

    public static byte[] needle(int size) {
        return GaugeArt.needle(size);
    }

    public static float angle(float frac) {
        return GaugeArt.angle(frac);
    }
}
