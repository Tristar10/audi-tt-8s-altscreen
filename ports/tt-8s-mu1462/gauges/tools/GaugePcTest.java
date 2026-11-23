import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;

/**
 * PC-only checks for the gauge sketch (not part of the jar). Compiled in package-less form against
 * the gauge classes; GaugeArt and GaugePacket are package-private/public in local.tt, so this calls
 * them through local.tt.GaugeTestAccess.
 *
 *   java GaugePcTest packet.bin preview.png
 * 1. Parses a datagram written by scripts/gauge-send.py --dump and prints it.
 * 2. Renders a dial with the needle at 0 %, 50 %, 90 % to preview.png (needle rotated here with
 *    AWT, the way the car rotates the needle node), so the pictures can be judged without the car.
 */
public class GaugePcTest {
    public static void main(String[] a) throws Exception {
        byte[] buf = new byte[512];
        FileInputStream in = new FileInputStream(a[0]);
        int n = in.read(buf);
        in.close();
        System.out.println(local.tt.GaugeTestAccess.describe(buf, n));

        int size = 300;
        float[] fracs = {0f, 0.5f, 0.9f};
        BufferedImage out = new BufferedImage(size * fracs.length, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setColor(new java.awt.Color(0x2A3440));
        g.fillRect(0, 0, out.getWidth(), out.getHeight()); // stand-in for the map under the gauges
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
            java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        BufferedImage face = img(local.tt.GaugeTestAccess.dial(size, 170f / 260f + 60f / 260f), size);
        BufferedImage needle = img(local.tt.GaugeTestAccess.needle(size), size);
        for (int i = 0; i < fracs.length; i++) {
            g.drawImage(face, i * size, 0, null);
            AffineTransform t = new AffineTransform();
            t.translate(i * size + size / 2.0, size / 2.0);
            t.rotate(Math.toRadians(local.tt.GaugeTestAccess.angle(fracs[i])));
            t.translate(-size / 2.0, -size / 2.0);
            g.drawImage(needle, t, null);
        }
        g.dispose();
        javax.imageio.ImageIO.write(out, "png", new File(a[1]));
        System.out.println("wrote " + a[1]);
    }

    private static BufferedImage img(byte[] rgba, int size) {
        BufferedImage b = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < size * size; i++) {
            int r = rgba[i * 4] & 0xFF, gg = rgba[i * 4 + 1] & 0xFF, bl = rgba[i * 4 + 2] & 0xFF, al = rgba[i * 4 + 3] & 0xFF;
            b.setRGB(i % size, i / size, (al << 24) | (r << 16) | (gg << 8) | bl);
        }
        return b;
    }
}
