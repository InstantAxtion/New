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
        g.setColor(new Color(0x3A3D43));
        g.fillRect(0, 0, 96, 96);
        g.setColor(new Color(0x8F8D87));
        g.fillRect(0, 0, 96, 20);
        g.fillRect(0, 76, 96, 20);
        g.setColor(new Color(0x6B6F78));
        g.fillRect(6, 0, 30, 14);
        g.setColor(new Color(0x7A6A5C));
        g.fillRect(48, 0, 40, 14);
        g.setColor(new Color(0xD9B43A));
        for (int x = 4; x < 96; x += 16) g.fillRect(x, 47, 9, 2);
        g.setColor(new Color(0x6E0A0A));
        g.fill(new Ellipse2D.Double(40, 52, 20, 12));
        person(g, 30, 50, 0, new Color(0x4E5A3E), new Color(0x7C9A5E), true);
        person(g, 68, 44, Math.PI, new Color(0xD9534F), new Color(0x4A2E1A), false);
        g.setClip(null);
        g.dispose();
        return img;
    }

    static void person(Graphics2D g, double x, double y, double ang, Color body, Color head, boolean zombie) {
        AffineTransform old = g.getTransform();
        g.translate(x, y);
        g.rotate(ang);
        double r = 13;
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new Ellipse2D.Double(-r * 0.7 + 2, -r + 2, r * 1.4, r * 2));
        if (zombie) {
            g.setColor(head);
            g.setStroke(new BasicStroke((float) (r * 0.42), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(r * 0.1, -r * 0.72, r * 1.6, -r * 0.5));
            g.draw(new Line2D.Double(r * 0.1, r * 0.72, r * 1.6, r * 0.5));
        } else {
            g.setColor(new Color(0xE0AC69));
            g.fill(new Ellipse2D.Double(-r * 0.3 - r * 0.28, -r * 0.95 - r * 0.28, r * 0.56, r * 0.56));
            g.fill(new Ellipse2D.Double(r * 0.3 - r * 0.28, r * 0.95 - r * 0.28, r * 0.56, r * 0.56));
        }
        g.setColor(body);
        g.fill(new Ellipse2D.Double(-r * 0.62, -r, r * 1.24, r * 2));
        g.setColor(head);
        g.fill(new Ellipse2D.Double(r * 0.08 - r * 0.56, -r * 0.56, r * 1.12, r * 1.12));
        if (zombie) {
            g.setColor(new Color(0x5A1414));
            g.fill(new Ellipse2D.Double(-r * 0.3, r * 0.0, r * 0.4, r * 0.4));
        }
        g.setTransform(old);
    }
}
