import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import local.tt.TeleLayout;

/**
 * PC-only preview of the telemetry pages (not part of the jar). Draws TeleLayout's slots with AWT
 * over cockpit photos, so the layout can be judged without the car. The car uses the HMI font;
 * this uses Bahnschrift, so text widths differ a little.
 *
 *   javac --release 8 -d out hmi/src/local/tt/TeleLayout.java gauges/tools/TelePreview.java
 *   java -cp out TelePreview docs/photos/cockpit-large-view.jpg docs/photos/cockpit-small-view.jpg docs/telemetry-preview.png
 *
 * The photos are scaled to the 1440x540 screen by width; positions are approximate.
 */
public class TelePreview {
    private static final int CAP = 46;
    private static final String FONT = "Bahnschrift";

    public static void main(String[] a) throws Exception {
        BufferedImage large = ImageIO.read(new File(a[0]));
        BufferedImage small = ImageIO.read(new File(a[1]));
        String[] caps = {
            "1  Large view, Live: cruising in 2nd, Autocross preset",
            "2  Large view, Live: past the shift point (bar and gear red), oil hot",
            "3  Large view, Live: at the limiter, whole bar flashes",
            "4  Small view, Live: past the shift point",
            "5  Large view, Presets: wheel moves the cursor, press applies",
            "6  Small view, Presets"};
        BufferedImage out = new BufferedImage(1440, caps.length * (540 + CAP), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        TeleLayout t = base();
        set(t, TeleLayout.CH_RPM, 3200);
        set(t, TeleLayout.CH_SPEED, 45);
        set(t, TeleLayout.CH_GEAR, 2);
        set(t, TeleLayout.CH_HX_TARGET, 40);
        set(t, TeleLayout.CH_HX_ACTUAL, 34);
        panel(g, 0, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_LIVE, caps[0]);

        set(t, TeleLayout.CH_RPM, 6420);
        set(t, TeleLayout.CH_SPEED, 84);
        set(t, TeleLayout.CH_HX_TARGET, 85);
        set(t, TeleLayout.CH_HX_ACTUAL, 80);
        set(t, TeleLayout.CH_OIL, 132);
        panel(g, 1, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_LIVE, caps[1]);

        set(t, TeleLayout.CH_RPM, 6750);
        set(t, TeleLayout.CH_SPEED, 89);
        t.now = 0;
        panel(g, 2, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_LIVE, caps[2]);

        set(t, TeleLayout.CH_RPM, 6420);
        set(t, TeleLayout.CH_SPEED, 84);
        panel(g, 3, small, t, TeleLayout.VIEW_SMALL, TeleLayout.PAGE_LIVE, caps[3]);

        t.cursor = 3;
        panel(g, 4, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_PRESETS, caps[4]);
        panel(g, 5, small, t, TeleLayout.VIEW_SMALL, TeleLayout.PAGE_PRESETS, caps[5]);
        g.dispose();
        ImageIO.write(out, "png", new File(a[2]));
        System.out.println("wrote " + a[2]);
    }

    private static TeleLayout base() {
        TeleLayout t = new TeleLayout();
        t.link = true;
        set(t, TeleLayout.CH_COOLANT, 92);
        set(t, TeleLayout.CH_OIL, 98);
        set(t, TeleLayout.CH_IAT, 38);
        set(t, TeleLayout.CH_DSG, 80);
        set(t, TeleLayout.CH_CLUTCH, 45);
        set(t, TeleLayout.CH_PRESET, 2);
        String[] names = {"Street", "Autocross", "Launch", "Wet", "FWD"};
        int[] modes = {0, 9, 2, 9, 1};
        for (int i = 0; i < names.length; i++) {
            t.catId[i] = i + 1;
            t.catName[i] = names[i];
            t.catMode[i] = modes[i];
        }
        t.catN = names.length;
        return t;
    }

    private static void set(TeleLayout t, int ch, double v) {
        t.val[ch] = (int) Math.round(v * 1000);
        t.has[ch] = true;
    }

    private static void panel(Graphics2D g, int row, BufferedImage bg, TeleLayout t, int view, int page, String cap) {
        int oy = row * (540 + CAP);
        g.setColor(new Color(0x15181C));
        g.fillRect(0, oy, 1440, CAP);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 24));
        g.drawString(cap, 16, oy + 32);
        Shape clip = g.getClip();
        g.setClip(0, oy + CAP, 1440, 540);
        g.drawImage(bg, 0, oy + CAP, 1440, bg.getHeight() * 1440 / bg.getWidth(), null);
        t.build(view, page);
        int bx = t.boxX;
        int by = oy + CAP + t.boxY;
        for (int i = 0; i < t.count; i++) {
            if (!t.vis[i])
                continue;
            int c = t.rgba[i];
            g.setColor(new Color((c >>> 24) & 0xFF, (c >>> 16) & 0xFF, (c >>> 8) & 0xFF, c & 0xFF));
            if (t.kind[i] == TeleLayout.BOX) {
                g.fillRect(bx + t.x[i], by + t.y[i], t.w[i], t.h[i]);
            } else {
                Font f = new Font(FONT, t.bold[i] ? Font.BOLD : Font.PLAIN, t.size[i]);
                g.setFont(f);
                FontMetrics fm = g.getFontMetrics(f);
                int tw = fm.stringWidth(t.text[i]);
                int x = bx + t.x[i];
                if (t.align[i] == TeleLayout.CENTER)
                    x -= tw / 2;
                else if (t.align[i] == TeleLayout.RIGHT)
                    x -= tw;
                g.drawString(t.text[i], x, by + t.y[i] + Math.round(0.82f * t.size[i]));
            }
        }
        g.setClip(clip);
    }
}
