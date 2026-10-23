import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/** Draws the launcher icon (since 11.0: a line of riot police holding a street against the horde, from above) at every density. */
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
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(s / 96.0, s / 96.0);
        g.setClip(new RoundRectangle2D.Double(2, 2, 92, 92, 26, 26));
        // (11.0) Seen from above: a street, the horde coming down it, and a line of riot police holding it.
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B2E33), 0, 96, new Color(0x3A3E44)));
        g.fillRect(0, 0, 96, 96);
        // Pavements down each side and the centre line.
        g.setColor(new Color(0x6E6C66));
        g.fillRect(0, 0, 9, 96);
        g.fillRect(87, 0, 9, 96);
        g.setColor(new Color(0xD8B640));
        for (int y = 2; y < 96; y += 12) g.fillRect(47, y, 2, 7);
        // Blood on the road where they met.
        g.setColor(new Color(0x7A1010));
        g.fill(new Ellipse2D.Double(24, 52, 9, 5));
        g.fill(new Ellipse2D.Double(60, 49, 7, 4));
        g.fill(new Ellipse2D.Double(43, 55, 5, 3));
        // The horde: grey-green, arms out, crowding in from the top.
        double[][] dead = {{16, 40}, {30, 37}, {44, 39}, {58, 36}, {72, 40}, {84, 34}, {22, 26}, {37, 24}, {51, 26}, {65, 23},
                {79, 25}, {12, 13}, {28, 11}, {44, 12}, {59, 10}, {74, 12}, {88, 15}, {20, 0}, {36, -1}, {52, 1}, {68, -1}, {83, 2}};
        int k = 0;
        for (double[] d : dead) {
            double x = d[0], y = d[1];
            boolean near = y > 30;
            Color skin = new Color(k % 3 == 0 ? 0x8FA678 : k % 3 == 1 ? 0x7E9668 : 0x98AE80);
            Color body = new Color(k % 2 == 0 ? 0x4B5A44 : 0x5A4E44);
            k++;
            // Shadow, arms reaching down towards the line, body, head.
            g.setColor(new Color(0, 0, 0, 70));
            g.fill(new Ellipse2D.Double(x - 4.2, y - 2.6, 9.6, 7.6));
            g.setColor(skin);
            g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(x - 3, y + 1, x - 3.6, y + (near ? 8 : 6)));
            g.draw(new Line2D.Double(x + 3, y + 1, x + 3.6, y + (near ? 8 : 6)));
            g.setColor(new Color(0x10, 0x12, 0x10));
            g.fill(new Ellipse2D.Double(x - 5.1, y - 3.7, 10.2, 7.4));
            g.setColor(body);
            g.fill(new Ellipse2D.Double(x - 4.5, y - 3.1, 9, 6.2));
            g.setColor(skin);
            g.fill(new Ellipse2D.Double(x - 2.3, y - 1.6, 4.6, 4.6));
        }
        // The police line: officers shoulder to shoulder behind their shields.
        for (int i = 0; i < 6; i++) {
            double x = 13 + i * 14, y = 72;
            g.setColor(new Color(0, 0, 0, 80));
            g.fill(new Ellipse2D.Double(x - 5, y - 1.5, 12, 10));
            g.setColor(new Color(0x0C1220));
            g.fill(new Ellipse2D.Double(x - 6.2, y - 4.2, 12.4, 9.4));
            g.setColor(new Color(0x23366A));
            g.fill(new Ellipse2D.Double(x - 5.6, y - 3.6, 11.2, 8.2));
            // Helmet with a visor.
            g.setColor(new Color(0x111418));
            g.fill(new Ellipse2D.Double(x - 3, y - 2.6, 6, 6));
            g.setColor(new Color(0x8FC4DC));
            g.fill(new Rectangle2D.Double(x - 2.2, y - 2.8, 4.4, 1.3));
            // The shield, held out in front (towards the dead).
            g.setColor(new Color(0x0C1220));
            g.fill(new RoundRectangle2D.Double(x - 6.6, y - 9.6, 13.2, 3.8, 2, 2));
            g.setColor(new Color(0xB4CDE0));
            g.fill(new RoundRectangle2D.Double(x - 6, y - 9.1, 12, 2.8, 1.6, 1.6));
            g.setColor(new Color(0xF2F6FA));
            g.fill(new Rectangle2D.Double(x - 3.5, y - 8.4, 7, 1));
        }
        // A cruiser behind the line, lights flashing.
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new RoundRectangle2D.Double(30, 84, 38, 16, 6, 6));
        g.setColor(new Color(0x101010));
        g.fill(new RoundRectangle2D.Double(28, 82, 40, 16, 6, 6));
        g.setColor(new Color(0xF2F2F2));
        g.fill(new RoundRectangle2D.Double(29, 83, 38, 14, 5, 5));
        g.setColor(new Color(0x1C2A44));
        g.fill(new Rectangle2D.Double(38, 85, 18, 10));
        g.setColor(new Color(0xE8362E));
        g.fill(new Rectangle2D.Double(45, 84, 3, 6));
        g.setColor(new Color(0x2E6CF0));
        g.fill(new Rectangle2D.Double(45, 90, 3, 6));
        // Light from the lights over the officers' backs.
        g.setPaint(new RadialGradientPaint(46f, 90f, 30f, new float[]{0f, 1f}, new Color[]{new Color(255, 60, 60, 60), new Color(255, 60, 60, 0)}));
        g.fill(new Ellipse2D.Double(16, 60, 60, 60));
        g.setClip(null);
        // A thin dark rim round the edge.
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new RoundRectangle2D.Double(2.75, 2.75, 90.5, 90.5, 25, 25));
        g.dispose();
        return img;
    }
}
