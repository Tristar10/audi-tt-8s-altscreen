import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import local.tt.TeleLayout;
import local.tt.TeleStrip;

/**
 * PC-only preview of the tele8 probe (not part of the jar): TeleStrip's two pictures composed the
 * way the car shows them, the lit one cut at a vertical clip edge, at a few rpm values. The white
 * marks above and below the strip are the probe's cut markers.
 *
 *   javac --release 8 -d out hmi/src/local/tt/TeleLayout.java hmi/src/local/tt/TeleIcons.java hmi/src/local/tt/TeleStrip.java gauges/tools/StripPreview.java
 *   java -cp out StripPreview docs/strip-probe-preview.png
 */
public class StripPreview {
    public static void main(String[] a) throws Exception {
        TeleLayout t = new TeleLayout();
        TeleStrip s = TeleStrip.paint(t);
        int[] rpms = {1800, 4520, 6620};
        int rowH = 200;
        BufferedImage out = new BufferedImage(1440, rpms.length * rowH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int r = 0; r < rpms.length; r++) {
            int oy = r * rowH - (TeleStrip.Y0 - 12);
            int cut = TeleStrip.cutX(t, rpms[r]);
            for (int py = 0; py < TeleStrip.H; py++) {
                for (int px = 0; px < TeleStrip.W; px++) {
                    int sx = TeleStrip.X0 + px;
                    int sy = TeleStrip.Y0 + py + oy;
                    int c = out.getRGB(sx, sy);
                    c = over(c, s.track[py * TeleStrip.W + px]);
                    if (sx < cut) {
                        c = over(c, s.lit[py * TeleStrip.W + px]);
                    }
                    out.setRGB(sx, sy, c);
                }
            }
            g.setColor(Color.WHITE);
            g.fillRect(cut - 1, TeleStrip.Y0 + oy - 2, 2, 10);
            g.fillRect(cut - 1, TeleStrip.Y0 + oy + TeleStrip.H - 8, 2, 10);
            g.setFont(new Font("Bahnschrift", Font.BOLD, 22));
            g.drawString(new StringBuffer().append(rpms[r]).append(" rpm, cut x ").append(cut).toString(), 560,
                r * rowH + 170);
        }
        ImageIO.write(out, "png", new File(a[0]));
        System.out.println(new StringBuffer("painted in ").append(s.paintMs).append(" ms").toString());
    }

    private static int over(int dst, int src) {
        int sa = src >>> 24;
        int r = blend((dst >> 16) & 0xFF, (src >> 16) & 0xFF, sa);
        int gg = blend((dst >> 8) & 0xFF, (src >> 8) & 0xFF, sa);
        int b = blend(dst & 0xFF, src & 0xFF, sa);
        return (r << 16) | (gg << 8) | b;
    }

    private static int blend(int d, int s, int a) {
        return (s * a + d * (255 - a)) / 255;
    }
}
