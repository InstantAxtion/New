import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Draws the launcher icon (a zombie reaching for a civilian on a city street) at every density. */
public class IconGen {
    public static void main(String[] args) throws Exception {
        String res = args[0];
        String[] dirs = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        int[] sizes = {48, 72, 96, 144, 192};
        for (int i = 0; i < dirs.length; i++) {
            File dir = new File(res, "mipmap-" + dirs[i]);
            dir.mkdirs();
            ImageIO.write(draw(sizes[i]), "png", new File(dir, "ic_launcher.png"));
        }
    }

    static BufferedImage draw(int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.scale(s / 96.0, s / 96.0);
        g.setClip(new RoundRectangle2D.Double(2, 2, 92, 92, 26, 26));
        // Sky: dark green-blue fading down.
        g.setPaint(new GradientPaint(0, 0, new Color(0x1B2A33), 0, 96, new Color(0x2E4A3A)));
        g.fillRect(0, 0, 96, 96);
        // Skyline with lit windows.
        int[][] towers = {{4, 40, 14}, {18, 26, 12}, {30, 46, 10}, {40, 18, 16}, {56, 34, 12}, {68, 24, 14}, {82, 42, 12}};
        for (int[] t : towers) {
            g.setColor(new Color(0x10181D));
            g.fillRect(t[0], t[1], t[2], 96 - t[1]);
            g.setColor(new Color(0xF0D98C));
            for (int y = t[1] + 4; y < 70; y += 6)
                for (int x = t[0] + 2; x < t[0] + t[2] - 2; x += 4)
                    if (((x * 7 + y * 13) % 5) == 0) g.fillRect(x, y, 2, 3);
        }
        // Street.
        g.setColor(new Color(0x2A2D32));
        g.fillRect(0, 74, 96, 22);
        // The zombie rising in front: head, shoulders, reaching hands.
        Color skin = new Color(0x7C9A5E), dark = new Color(0x4E6A3A);
        g.setColor(new Color(0x3A4A2A));
        g.fill(new Ellipse2D.Double(20, 66, 56, 40));
        g.setColor(skin);
        g.fill(new Ellipse2D.Double(31, 38, 34, 38));
        g.setColor(dark);
        g.fill(new Ellipse2D.Double(34, 38, 28, 12));
        // Eyes and mouth.
        g.setColor(new Color(0x1A0A0A));
        g.fill(new Ellipse2D.Double(38, 52, 8, 7));
        g.fill(new Ellipse2D.Double(50, 52, 8, 7));
        g.setColor(new Color(0xFF3B2F));
        g.fill(new Ellipse2D.Double(41, 54, 3, 3));
        g.fill(new Ellipse2D.Double(53, 54, 3, 3));
        g.setColor(new Color(0x5A1414));
        g.fill(new RoundRectangle2D.Double(42, 64, 12, 6, 4, 4));
        g.setColor(new Color(0xE8E0C8));
        for (int x = 43; x < 53; x += 3) g.fillRect(x, 64, 2, 2);
        hand(g, 14, 48, -0.4, skin);
        hand(g, 82, 48, 0.4, skin);
        // Blood drip.
        g.setColor(new Color(0x8E1010));
        g.fill(new Ellipse2D.Double(46, 69, 4, 7));
        g.setClip(null);
        g.dispose();
        return img;
    }

    static void hand(Graphics2D g, double x, double y, double ang, Color skin) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(ang);
        g.setColor(skin);
        g.fill(new RoundRectangle2D.Double(-5, 0, 10, 30, 6, 6));
        g.fill(new Ellipse2D.Double(-7, -6, 14, 12));
        for (int f = -1; f <= 1; f++) g.fill(new RoundRectangle2D.Double(-1.5 + f * 4.2, -14, 3.2, 11, 3, 3));
        g.setTransform(old);
    }
}
