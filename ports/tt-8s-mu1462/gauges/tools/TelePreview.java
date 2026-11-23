import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;

import local.tt.TeleLayout;

/**
 * PC-only preview of the autocross view (not part of the jar). Draws TeleLayout's slots with AWT
 * over cockpit photos, so the layout can be judged without the car. The car uses the HMI font;
 * this uses Bahnschrift, so text widths differ a little.
 *
 *   javac --release 8 -d out hmi/src/local/tt/TeleLayout.java gauges/tools/TelePreview.java
 *   java -cp out TelePreview docs/photos/cockpit-large-view.jpg docs/photos/cockpit-small-view.jpg docs/telemetry-preview.png [masks]
 *
 * Our layer is clipped to what the cluster lets through (Cockpit-drawing.md) minus the stock parts
 * drawn above it (dials, tab bar, bottom bar), so the photo shows there instead, as in the car. A
 * slot cut by that clip is a collision. "masks" outlines those areas in green instead.
 * The photos are scaled to the 1440x540 screen by width; positions are approximate.
 */
public class TelePreview {
    private static final int CAP = 46;
    private static final String FONT = "Bahnschrift";

    public static void main(String[] a) throws Exception {
        BufferedImage large = ImageIO.read(new File(a[0]));
        BufferedImage small = ImageIO.read(new File(a[1]));
        boolean masks = a.length > 3 && a[3].equals("masks");
        String[] caps = {
            "1  Classic view, autocross (FL5 +R style): cruising in 2nd, 20 Hz link",
            "2  Classic view: shift lights coming on, oil hot (amber)",
            "3  Classic view: past the shift point: lights flash, rev line red, notch glows",
            "4  Sport view, autocross: gear and temperatures (icons: coolant, oil, intake, DSG, Haldex clutch)",
            "5  Classic view, presets (later): wheel moves the cursor, press applies"};
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
        panel(g, 0, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_LIVE, caps[0], masks);

        set(t, TeleLayout.CH_RPM, 5650);
        set(t, TeleLayout.CH_HX_TARGET, 85);
        set(t, TeleLayout.CH_HX_ACTUAL, 80);
        set(t, TeleLayout.CH_OIL, 132);
        panel(g, 1, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_LIVE, caps[1], masks);

        set(t, TeleLayout.CH_RPM, 6620);
        t.now = 0;
        panel(g, 2, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_LIVE, caps[2], masks);

        set(t, TeleLayout.CH_RPM, 4800);
        panel(g, 3, small, t, TeleLayout.VIEW_SMALL, TeleLayout.PAGE_LIVE, caps[3], masks);

        t.cursor = 3;
        panel(g, 4, large, t, TeleLayout.VIEW_LARGE, TeleLayout.PAGE_PRESETS, caps[4], masks);
        g.dispose();
        ImageIO.write(out, "png", new File(a[2]));
        System.out.println("wrote " + a[2]);
    }

    /** What of our layer stays visible: the cluster's window minus the stock parts drawn above. */
    private static Area visible(int view) {
        Area v;
        if (view == TeleLayout.VIEW_LARGE) {
            v = new Area(new Rectangle(0, 0, 1440, 540));
            v.subtract(new Area(new Ellipse2D.Double(190 - 128, 386 - 128, 256, 256))); // rev counter
            v.subtract(new Area(new Ellipse2D.Double(1220 - 128, 383 - 128, 256, 256))); // speedometer
            v.subtract(new Area(new Rectangle(428, 40, 560, 54))); // tab bar
            v.subtract(new Area(new Rectangle(0, 464, 1440, 76))); // bottom bar
            v.subtract(new Area(new Polygon(new int[] {0, 290, 0}, new int[] {0, 0, 110}, 3))); // frame, top left
            v.subtract(new Area(new Polygon(new int[] {1100, 1440, 1440}, new int[] {0, 0, 108}, 3))); // top right
        } else {
            v = new Area(new Polygon(new int[] {0, 200, 520, 520, 0}, new int[] {110, 50, 50, 468, 468}, 5));
            v.subtract(new Area(new Rectangle(180, 30, 300, 38))); // status line (radio, compass)
            v.subtract(new Area(new Ellipse2D.Double(791 - 290, 288 - 290, 580, 580))); // rev counter
        }
        return v;
    }

    private static TeleLayout base() {
        TeleLayout t = new TeleLayout();
        t.link = true;
        t.rateHz = 20;
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

    private static void panel(Graphics2D g, int row, BufferedImage bg, TeleLayout t, int view, int page, String cap,
                              boolean masks) {
        int oy = row * (540 + CAP);
        g.setColor(new Color(0x15181C));
        g.fillRect(0, oy, 1440, CAP);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Segoe UI", Font.BOLD, 24));
        g.drawString(cap, 16, oy + 32);
        Shape clip = g.getClip();
        g.setClip(0, oy + CAP, 1440, 540);
        g.drawImage(bg, 0, oy + CAP, 1440, bg.getHeight() * 1440 / bg.getWidth(), null);
        g.translate(0, oy + CAP);
        Area vis = visible(view);
        if (masks) {
            g.setColor(Color.GREEN);
            g.setStroke(new BasicStroke(3));
            g.draw(vis);
        } else {
            g.setClip(vis);
            draw(g, t, view, page);
        }
        g.translate(0, -(oy + CAP));
        g.setClip(clip);
    }

    private static void draw(Graphics2D g, TeleLayout t, int view, int page) {
        t.build(view, page);
        for (int i = 0; i < t.count; i++) {
            if (!t.vis[i])
                continue;
            int c = t.rgba[i];
            g.setColor(new Color((c >>> 24) & 0xFF, (c >>> 16) & 0xFF, (c >>> 8) & 0xFF, c & 0xFF));
            if (t.kind[i] == TeleLayout.BOX) {
                g.fillRect(t.x[i], t.y[i], t.w[i], t.h[i]);
            } else if (t.kind[i] == TeleLayout.QUAD) {
                int[] xs = new int[4];
                int[] ys = new int[4];
                for (int k = 0; k < 4; k++) {
                    xs[k] = t.qx[4 * i + k];
                    ys[k] = t.qy[4 * i + k];
                }
                g.fillPolygon(xs, ys, 4);
            } else if (t.kind[i] == TeleLayout.ICON) {
                int px = t.w[i];
                BufferedImage ic = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
                ic.setRGB(0, 0, px, px, local.tt.TeleIcons.argb(t.size[i], px, c >>> 8), 0, px);
                g.drawImage(ic, t.x[i], t.y[i], null);
            } else {
                Font f = new Font(FONT, t.bold[i] ? Font.BOLD : Font.PLAIN, t.size[i]);
                g.setFont(f);
                FontMetrics fm = g.getFontMetrics(f);
                int tw = fm.stringWidth(t.text[i]);
                int x = t.x[i];
                if (t.align[i] == TeleLayout.CENTER)
                    x -= tw / 2;
                else if (t.align[i] == TeleLayout.RIGHT)
                    x -= tw;
                g.drawString(t.text[i], x, t.y[i] + Math.round(0.82f * t.size[i]));
            }
        }
    }
}
