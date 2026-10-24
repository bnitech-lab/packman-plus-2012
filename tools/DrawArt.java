import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

/**
 * Draws every image in imgs/ for PackMan plus from scratch, with plain Java2D shapes
 * and a small built-in pixel font. No outside artwork is used.
 *
 * Run from the repository root (JDK 11 or newer, works headless):
 *
 *     java tools/DrawArt.java
 *
 * Each file keeps the name, format and pixel size the game expects. The maze in
 * bgimg.jpg is built from the map array in src/PackMan_plus.java, so the walls line
 * up with the coins the game draws on top of it.
 */
public class DrawArt {

    // The game stretches bgimg.jpg and start.png over the 435 px wide play area
    // (MaxFWidth) and the whole panel height. 547 px is the panel height of the
    // 575 px window with a 28 px title bar; other systems differ by a few pixels.
    static final double SCREEN_W = 435;
    static final double SCREEN_H = 547;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        Path out = root.resolve("imgs");
        Files.createDirectories(out);
        char[][] map = readMap(root.resolve("src").resolve("PackMan_plus.java"));

        writeJpeg(drawMaze(349, 440, map), out.resolve("bgimg.jpg").toFile(), 0.95f);
        writePng(drawStart(318, 477), out.resolve("start.png").toFile());

        Color[] body = {
            new Color(215, 38, 61), new Color(123, 201, 80), new Color(142, 108, 239),
            new Color(62, 124, 177), new Color(242, 165, 65), new Color(46, 196, 182)
        };
        Color[] eyes = {
            new Color(255, 230, 90), new Color(255, 255, 255), new Color(120, 255, 240),
            new Color(255, 230, 90), new Color(120, 255, 240), new Color(255, 255, 255)
        };
        for (int i = 0; i < 6; i++) {
            BufferedImage img = canvas(50, 50);
            Graphics2D g = smooth(img);
            g.scale(50 / 25.0, 50 / 25.0);
            drawRobot(g, 0, 0, 25, body[i], eyes[i], i % 3);
            g.dispose();
            writePng(img, out.resolve("ghost" + i + ".png").toFile());
        }

        BufferedImage scared = canvas(50, 50);
        Graphics2D gs = scared.createGraphics(); // no anti-aliasing: GIF has one transparent colour
        gs.scale(50 / 25.0, 50 / 25.0);
        drawScaredRobot(gs, 0, 0, 25);
        gs.dispose();
        writeGif(scared, out.resolve("scaredghost.gif").toFile());

        writePng(item(36, 41, 25, 25, DrawArt::drawApple), out.resolve("apple.png").toFile());
        writePng(item(47, 34, 30, 25, DrawArt::drawShoe), out.resolve("shoes.png").toFile());
        writePng(item(73, 71, 30, 25, DrawArt::drawTop), out.resolve("android.png").toFile());
        writePng(item(57, 49, 30, 25, DrawArt::drawHeart), out.resolve("heart.png").toFile());

        writePng(banner(66, 14, "READY", new Color(255, 216, 77)), out.resolve("Ready.png").toFile());
        writePng(banner(56, 13, "GO!", new Color(92, 230, 92)), out.resolve("GO.png").toFile());
        writePng(banner(79, 12, "VICTORY", new Color(255, 201, 77)), out.resolve("Victory.png").toFile());
        writePng(banner(51, 12, "LOSE", new Color(255, 90, 90)), out.resolve("Lose.png").toFile());

        System.out.println("Wrote 17 images to " + out.toAbsolutePath().normalize());
    }

    // ------------------------------------------------------------------ map

    /** Reads the 27 x 21 map array from the game source. */
    static char[][] readMap(Path source) throws IOException {
        String src = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
        int start = src.indexOf("static char map[][]");
        int end = src.indexOf("};", start);
        if (start < 0 || end < 0) {
            throw new IllegalStateException("map array not found in " + source);
        }
        Matcher m = Pattern.compile("'(.)'").matcher(src.substring(start, end));
        List<Character> cells = new ArrayList<>();
        while (m.find()) {
            cells.add(m.group(1).charAt(0));
        }
        int rows = 27, cols = 21;
        if (cells.size() != rows * cols) {
            throw new IllegalStateException("expected 567 map cells, found " + cells.size());
        }
        char[][] map = new char[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                map[i][j] = cells.get(i * cols + j);
            }
        }
        return map;
    }

    // ------------------------------------------------------------------ maze background

    static BufferedImage drawMaze(int w, int h, char[][] map) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = smooth(img);
        g.scale(w / SCREEN_W, h / SCREEN_H);
        g.setPaint(new GradientPaint(0, 0, new Color(18, 22, 44), 0, (float) SCREEN_H, new Color(10, 12, 26)));
        g.fill(new Rectangle2D.Double(0, 0, SCREEN_W, SCREEN_H));

        // Cell (row i, column j) is centred where the game draws its coin: (20j + 12.5, 20i + 17.5).
        double t = 4.5; // half the wall thickness; leaves a 31 px corridor for the 30 px hero
        Area walls = new Area();
        int rows = map.length, cols = map[0].length;
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (map[i][j] != '#') {
                    continue;
                }
                double cx = 20 * j + 12.5, cy = 20 * i + 17.5;
                walls.add(new Area(new RoundRectangle2D.Double(cx - t, cy - t, 2 * t, 2 * t, 2 * t, 2 * t)));
                boolean right = j + 1 < cols && map[i][j + 1] == '#';
                boolean down = i + 1 < rows && map[i + 1][j] == '#';
                if (right) {
                    walls.add(new Area(new Rectangle2D.Double(cx, cy - t, 20, 2 * t)));
                }
                if (down) {
                    walls.add(new Area(new Rectangle2D.Double(cx - t, cy, 2 * t, 20)));
                }
                if (right && down && map[i + 1][j + 1] == '#') {
                    walls.add(new Area(new Rectangle2D.Double(cx, cy, 20, 20)));
                }
            }
        }
        g.setColor(new Color(36, 140, 112));
        g.fill(walls);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(126, 227, 192));
        g.draw(walls);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ title screen

    static BufferedImage drawStart(int w, int h) {
        BufferedImage img = canvas(w, h);
        Graphics2D g = smooth(img);
        g.scale(w / SCREEN_W, h / SCREEN_H);
        double W = SCREEN_W, H = SCREEN_H;

        g.setPaint(new GradientPaint(0, 0, new Color(30, 18, 58), 0, (float) H, new Color(10, 10, 24)));
        g.fill(new Rectangle2D.Double(0, 0, W, H));

        // scattered crumbs
        Random rnd = new Random(2012);
        Color[] crumb = { new Color(255, 210, 63, 90), new Color(255, 79, 163, 80), new Color(126, 227, 192, 80) };
        for (int k = 0; k < 90; k++) {
            g.setColor(crumb[k % crumb.length]);
            double d = 2 + rnd.nextDouble() * 3;
            g.fill(new Ellipse2D.Double(rnd.nextDouble() * W, rnd.nextDouble() * H, d, d));
        }

        // frame
        g.setStroke(new BasicStroke(3f));
        g.setColor(new Color(255, 182, 39));
        g.draw(new RoundRectangle2D.Double(8, 8, W - 16, H - 16, 26, 26));
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(255, 182, 39, 120));
        g.draw(new RoundRectangle2D.Double(14, 14, W - 28, H - 28, 20, 20));

        // title
        double px = 7;
        double tw = PixelFont.width("PackMan", px);
        PixelFont.draw(g, "PackMan", (W - tw) / 2 + 4, 64 + 4, px, new Color(194, 65, 12), null, 0);
        PixelFont.draw(g, "PackMan", (W - tw) / 2, 64, px, new Color(255, 210, 63), new Color(42, 21, 0), 1.5);

        // "plus" badge
        double bw = 128, bh = 54, bx = (W + tw) / 2 - bw + 6, by = 128;
        g.setColor(new Color(255, 79, 163));
        g.fill(new RoundRectangle2D.Double(bx, by, bw, bh, 26, 26));
        g.setStroke(new BasicStroke(3f));
        g.setColor(Color.WHITE);
        g.draw(new RoundRectangle2D.Double(bx, by, bw, bh, 26, 26));
        double ppx = 4;
        PixelFont.draw(g, "plus", bx + (bw - PixelFont.width("plus", ppx)) / 2, by + 9, ppx, Color.WHITE, new Color(120, 20, 70), 1);

        // a corridor with the chase
        g.setColor(new Color(36, 140, 112));
        g.fill(new RoundRectangle2D.Double(30, 222, W - 60, 9, 9, 9));
        g.fill(new RoundRectangle2D.Double(30, 304, W - 60, 9, 9, 9));
        g.setStroke(new BasicStroke(1.5f));
        g.setColor(new Color(126, 227, 192));
        g.draw(new RoundRectangle2D.Double(30, 222, W - 60, 9, 9, 9));
        g.draw(new RoundRectangle2D.Double(30, 304, W - 60, 9, 9, 9));

        Color[] body = { new Color(215, 38, 61), new Color(142, 108, 239), new Color(46, 196, 182) };
        Color[] eyes = { new Color(255, 230, 90), new Color(120, 255, 240), new Color(255, 255, 255) };
        for (int k = 0; k < 3; k++) {
            drawRobot(g, 40 + k * 48, 246, 44, body[k], eyes[k], k);
        }
        drawHero(g, 214, 246, 46);
        g.setColor(new Color(255, 214, 0));
        for (int k = 0; k < 5; k++) {
            g.fill(new Ellipse2D.Double(282 + k * 26, 266, 7, 7));
        }

        // power-up row
        double iy = 338;
        double[] ix = { 98, 160, 222, 284 };
        Painter[] items = { DrawArt::drawApple, DrawArt::drawShoe, DrawArt::drawTop, DrawArt::drawHeart };
        double[] iw = { 25, 30, 30, 30 };
        for (int k = 0; k < 4; k++) {
            Graphics2D gi = (Graphics2D) g.create();
            gi.translate(ix[k], iy);
            gi.scale(1.5, 1.5);
            items[k].paint(gi, iw[k], 25);
            gi.dispose();
        }

        // prompt
        double qpx = 2.5;
        String prompt = "CLICK START BUTTON";
        PixelFont.draw(g, prompt, (W - PixelFont.width(prompt, qpx)) / 2, 402, qpx, Color.WHITE, null, 0);

        // maker badge
        double mpx = 3;
        String maker = "BNI Tech";
        double mw = PixelFont.width(maker, mpx) + 28, mh = 7 * mpx + 18;
        double mx = (W - mw) / 2, my = 456;
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(mx, my, mw, mh, 14, 14));
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(200, 29, 37));
        g.draw(new RoundRectangle2D.Double(mx, my, mw, mh, 14, 14));
        PixelFont.draw(g, maker, mx + 14, my + 9, mpx, new Color(200, 29, 37), null, 0);

        double ypx = 2;
        PixelFont.draw(g, "2012", (W - PixelFont.width("2012", ypx)) / 2, my + mh + 12, ypx, new Color(170, 170, 190), null, 0);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ characters

    /** The hero for the title screen: a rounded yellow loaf with eyes, feet and a sprout. */
    static void drawHero(Graphics2D g, double x, double y, double s) {
        Color outline = new Color(150, 90, 10);
        // sprout
        g.setColor(new Color(90, 190, 80));
        g.fill(rotated(new Ellipse2D.Double(x + s * 0.50, y - s * 0.02, s * 0.16, s * 0.28), -0.6, x + s * 0.55, y + s * 0.2));
        g.fill(rotated(new Ellipse2D.Double(x + s * 0.34, y + s * 0.0, s * 0.16, s * 0.26), 0.6, x + s * 0.45, y + s * 0.2));
        // feet
        g.setColor(new Color(240, 140, 30));
        g.fill(new Ellipse2D.Double(x + s * 0.14, y + s * 0.86, s * 0.30, s * 0.16));
        g.fill(new Ellipse2D.Double(x + s * 0.58, y + s * 0.86, s * 0.30, s * 0.16));
        // body
        Shape body = new RoundRectangle2D.Double(x + s * 0.04, y + s * 0.16, s * 0.92, s * 0.76, s * 0.42, s * 0.42);
        g.setColor(new Color(255, 210, 63));
        g.fill(body);
        g.setStroke(new BasicStroke((float) (s * 0.05)));
        g.setColor(outline);
        g.draw(body);
        // eyes looking right
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x + s * 0.30, y + s * 0.30, s * 0.20, s * 0.26));
        g.fill(new Ellipse2D.Double(x + s * 0.58, y + s * 0.30, s * 0.20, s * 0.26));
        g.setColor(new Color(30, 30, 40));
        g.fill(new Ellipse2D.Double(x + s * 0.39, y + s * 0.38, s * 0.10, s * 0.13));
        g.fill(new Ellipse2D.Double(x + s * 0.67, y + s * 0.38, s * 0.10, s * 0.13));
        // cheeks and mouth
        g.setColor(new Color(255, 140, 140));
        g.fill(new Ellipse2D.Double(x + s * 0.14, y + s * 0.58, s * 0.14, s * 0.08));
        g.fill(new Ellipse2D.Double(x + s * 0.80, y + s * 0.58, s * 0.10, s * 0.08));
        g.setColor(new Color(150, 30, 40));
        g.fill(new Ellipse2D.Double(x + s * 0.50, y + s * 0.62, s * 0.18, s * 0.14));
    }

    /** A small boxy robot with an antenna, a visor and treads. variant picks the antenna. */
    static void drawRobot(Graphics2D g, double x, double y, double s, Color body, Color eye, int variant) {
        double u = s / 25.0;
        Color dark = body.darker().darker();
        g.setStroke(new BasicStroke((float) (1.0 * u), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        // antenna
        g.setColor(new Color(200, 200, 210));
        if (variant == 0) {
            g.draw(new Line2D.Double(x + 12.5 * u, y + 5.5 * u, x + 12.5 * u, y + 2.5 * u));
            g.setColor(eye);
            g.fill(new Ellipse2D.Double(x + 11 * u, y + 0.8 * u, 3 * u, 3 * u));
        } else if (variant == 1) {
            g.draw(new Line2D.Double(x + 9 * u, y + 5.5 * u, x + 7.5 * u, y + 2 * u));
            g.draw(new Line2D.Double(x + 16 * u, y + 5.5 * u, x + 17.5 * u, y + 2 * u));
            g.setColor(eye);
            g.fill(new Ellipse2D.Double(x + 6.3 * u, y + 0.8 * u, 2.4 * u, 2.4 * u));
            g.fill(new Ellipse2D.Double(x + 16.3 * u, y + 0.8 * u, 2.4 * u, 2.4 * u));
        } else {
            Path2D zig = new Path2D.Double();
            zig.moveTo(x + 12.5 * u, y + 5.5 * u);
            zig.lineTo(x + 11 * u, y + 4 * u);
            zig.lineTo(x + 14 * u, y + 2.7 * u);
            zig.lineTo(x + 12 * u, y + 1 * u);
            g.draw(zig);
        }
        // ear bolts
        g.setColor(dark);
        g.fill(new RoundRectangle2D.Double(x + 1.5 * u, y + 9.5 * u, 3 * u, 5 * u, 1.5 * u, 1.5 * u));
        g.fill(new RoundRectangle2D.Double(x + 20.5 * u, y + 9.5 * u, 3 * u, 5 * u, 1.5 * u, 1.5 * u));
        // treads
        g.setColor(new Color(60, 60, 70));
        g.fill(new RoundRectangle2D.Double(x + 5 * u, y + 19.5 * u, 6.5 * u, 4 * u, 2 * u, 2 * u));
        g.fill(new RoundRectangle2D.Double(x + 13.5 * u, y + 19.5 * u, 6.5 * u, 4 * u, 2 * u, 2 * u));
        // head
        Shape head = new RoundRectangle2D.Double(x + 3.5 * u, y + 5 * u, 18 * u, 15 * u, 5 * u, 5 * u);
        g.setColor(body);
        g.fill(head);
        g.setColor(dark);
        g.draw(head);
        // visor and eyes
        g.setColor(new Color(27, 31, 42));
        g.fill(new RoundRectangle2D.Double(x + 6 * u, y + 8 * u, 13 * u, 6 * u, 3 * u, 3 * u));
        g.setColor(eye);
        g.fill(new RoundRectangle2D.Double(x + 8 * u, y + 9.5 * u, 3 * u, 3 * u, 1 * u, 1 * u));
        g.fill(new RoundRectangle2D.Double(x + 14 * u, y + 9.5 * u, 3 * u, 3 * u, 1 * u, 1 * u));
        // grille
        g.setColor(dark);
        for (int k = 0; k < 3; k++) {
            g.draw(new Line2D.Double(x + (10 + 2.5 * k) * u, y + 15.5 * u, x + (10 + 2.5 * k) * u, y + 17.5 * u));
        }
    }

    /** The robot when the hero can catch it: pale, X-shaped eyes, bent antenna, a sweat drop. */
    static void drawScaredRobot(Graphics2D g, double x, double y, double s) {
        double u = s / 25.0;
        Color body = new Color(170, 182, 214);
        Color dark = new Color(70, 80, 120);
        g.setStroke(new BasicStroke((float) (1.0 * u)));
        g.setColor(new Color(200, 200, 210));
        Path2D bent = new Path2D.Double();
        bent.moveTo(x + 12.5 * u, y + 5.5 * u);
        bent.lineTo(x + 12.5 * u, y + 3 * u);
        bent.lineTo(x + 16 * u, y + 2.5 * u);
        g.draw(bent);
        g.setColor(dark);
        g.fill(new Rectangle2D.Double(x + 1.5 * u, y + 9.5 * u, 3 * u, 5 * u));
        g.fill(new Rectangle2D.Double(x + 20.5 * u, y + 9.5 * u, 3 * u, 5 * u));
        g.setColor(new Color(60, 60, 70));
        g.fill(new Rectangle2D.Double(x + 5 * u, y + 19.5 * u, 6.5 * u, 4 * u));
        g.fill(new Rectangle2D.Double(x + 13.5 * u, y + 19.5 * u, 6.5 * u, 4 * u));
        Shape head = new RoundRectangle2D.Double(x + 3.5 * u, y + 5 * u, 18 * u, 15 * u, 5 * u, 5 * u);
        g.setColor(body);
        g.fill(head);
        g.setColor(dark);
        g.draw(head);
        g.setColor(new Color(235, 240, 250));
        g.fill(new Rectangle2D.Double(x + 6 * u, y + 8 * u, 13 * u, 6 * u));
        g.setColor(new Color(200, 40, 60));
        g.setStroke(new BasicStroke((float) (1.2 * u)));
        double[] ex = { 8, 14 };
        for (double e : ex) {
            g.draw(new Line2D.Double(x + e * u, y + 9.3 * u, x + (e + 3) * u, y + 12.7 * u));
            g.draw(new Line2D.Double(x + (e + 3) * u, y + 9.3 * u, x + e * u, y + 12.7 * u));
        }
        g.setColor(dark);
        g.draw(new Line2D.Double(x + 10 * u, y + 16.5 * u, x + 15 * u, y + 16.5 * u));
        g.setColor(new Color(110, 200, 255));
        Path2D drop = new Path2D.Double();
        drop.moveTo(x + 22.5 * u, y + 2 * u);
        drop.lineTo(x + 24.5 * u, y + 5.5 * u);
        drop.lineTo(x + 20.5 * u, y + 5.5 * u);
        drop.closePath();
        g.fill(drop);
        g.fill(new Ellipse2D.Double(x + 20.5 * u, y + 4.3 * u, 4 * u, 3.4 * u));
    }

    // ------------------------------------------------------------------ power-ups

    interface Painter {
        /** Paints inside a box of w x h display units. */
        void paint(Graphics2D g, double w, double h);
    }

    /** Draws a power-up in its in-game display box, stretched to the file's pixel size. */
    static BufferedImage item(int w, int h, double displayW, double displayH, Painter p) {
        BufferedImage img = canvas(w, h);
        Graphics2D g = smooth(img);
        g.scale(w / displayW, h / displayH);
        p.paint(g, displayW, displayH);
        g.dispose();
        return img;
    }

    /** Far-sight apple (SuperPower 1). */
    static void drawApple(Graphics2D g, double w, double h) {
        Area apple = new Area(new Ellipse2D.Double(2.5, 7, 11.5, 15.5));
        apple.add(new Area(new Ellipse2D.Double(11, 7, 11.5, 15.5)));
        g.setColor(new Color(110, 70, 30));
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(12.5, 8.5, 13.5, 2.5));
        g.setColor(new Color(90, 180, 70));
        g.fill(rotated(new Ellipse2D.Double(14, 1.5, 7, 3.6), -0.35, 14, 3.3));
        g.setColor(new Color(232, 70, 20));
        g.fill(apple);
        g.setColor(new Color(150, 35, 10));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(apple);
        g.setColor(new Color(255, 230, 210));
        g.fill(rotated(new Ellipse2D.Double(5.5, 10, 3, 5.5), 0.4, 7, 12.7));
    }

    /** Speed-up shoe (SuperPower 2). */
    static void drawShoe(Graphics2D g, double w, double h) {
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(150, 200, 255));
        g.draw(new Line2D.Double(0.8, 10, 4.5, 10));
        g.draw(new Line2D.Double(0.8, 13.5, 3.8, 13.5));
        g.draw(new Line2D.Double(0.8, 17, 4.5, 17));
        Path2D upper = new Path2D.Double();
        upper.moveTo(6, 19);
        upper.lineTo(7.5, 8.5);
        upper.quadTo(10.5, 6.5, 14, 8);
        upper.lineTo(16, 12);
        upper.quadTo(22, 12.5, 26, 15);
        upper.quadTo(28.5, 17, 28, 19.5);
        upper.closePath();
        g.setColor(new Color(47, 97, 215));
        g.fill(upper);
        g.setColor(new Color(20, 45, 120));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(upper);
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(5, 18.5, 24, 3.6, 3, 3));
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(10, 16.5, 15.5, 11.5));
        g.draw(new Line2D.Double(13, 17, 18.5, 12.5));
        g.setColor(new Color(255, 214, 0));
        g.fill(new Ellipse2D.Double(16.5, 11.6, 1.8, 1.8));
        g.fill(new Ellipse2D.Double(19, 12.2, 1.8, 1.8));
    }

    /** Power-up toy (SuperPower 3): a spinning top. Replaces the old android.png. */
    static void drawTop(Graphics2D g, double w, double h) {
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 170));
        g.draw(new java.awt.geom.Arc2D.Double(2, 7, 26, 12, 200, 60, java.awt.geom.Arc2D.OPEN));
        g.draw(new java.awt.geom.Arc2D.Double(2, 7, 26, 12, -20, 60, java.awt.geom.Arc2D.OPEN));
        g.setColor(new Color(150, 150, 160));
        g.fill(new RoundRectangle2D.Double(13.6, 1.5, 2.8, 5.5, 1.2, 1.2));
        Path2D top = new Path2D.Double();
        top.moveTo(15, 5.5);
        top.curveTo(21, 5.5, 25.5, 9, 25.5, 12.5);
        top.curveTo(25.5, 15.5, 19, 19, 15, 23.5);
        top.curveTo(11, 19, 4.5, 15.5, 4.5, 12.5);
        top.curveTo(4.5, 9, 9, 5.5, 15, 5.5);
        top.closePath();
        g.setColor(new Color(179, 224, 17));
        g.fill(top);
        Shape clip = g.getClip();
        g.clip(top);
        g.setColor(new Color(110, 150, 10));
        g.fill(new Rectangle2D.Double(0, 11, 30, 3));
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(new Rectangle2D.Double(0, 15.5, 30, 1.4));
        g.setClip(clip);
        g.setColor(new Color(70, 100, 0));
        g.setStroke(new BasicStroke(0.8f));
        g.draw(top);
    }

    /** Extra-life heart (SuperPower 4). */
    static void drawHeart(Graphics2D g, double w, double h) {
        Path2D heart = new Path2D.Double();
        heart.moveTo(15, 23);
        heart.curveTo(6, 17, 3, 12.5, 3, 8.5);
        heart.curveTo(3, 4.5, 6, 2.5, 9, 2.5);
        heart.curveTo(12, 2.5, 14, 4.5, 15, 6.5);
        heart.curveTo(16, 4.5, 18, 2.5, 21, 2.5);
        heart.curveTo(24, 2.5, 27, 4.5, 27, 8.5);
        heart.curveTo(27, 12.5, 24, 17, 15, 23);
        heart.closePath();
        g.setColor(new Color(240, 90, 200));
        g.fill(heart);
        g.setColor(new Color(150, 30, 120));
        g.setStroke(new BasicStroke(0.9f));
        g.draw(heart);
        g.setColor(new Color(255, 210, 245));
        g.fill(rotated(new Ellipse2D.Double(6, 5, 3.5, 5), 0.5, 7.7, 7.5));
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(13.7, 8, 2.6, 9));
        g.fill(new Rectangle2D.Double(10.5, 11.2, 9, 2.6));
    }

    // ------------------------------------------------------------------ banners

    /** READY / GO / VICTORY / LOSE: the game draws these at 100 x 30, so lay them out in that box. */
    static BufferedImage banner(int w, int h, String text, Color fill) {
        BufferedImage img = canvas(w, h);
        Graphics2D g = img.createGraphics();
        g.scale(w / 100.0, h / 30.0);
        double px = Math.min(94 / PixelFont.cols(text), 24 / 7.0);
        double tw = PixelFont.width(text, px);
        PixelFont.draw(g, text, (100 - tw) / 2, (30 - 7 * px) / 2, px, fill, new Color(30, 15, 5), px * 0.45);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ helpers

    static BufferedImage canvas(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D smooth(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        return g;
    }

    static Shape rotated(Shape s, double angle, double cx, double cy) {
        return AffineTransform.getRotateInstance(angle, cx, cy).createTransformedShape(s);
    }

    static void writePng(BufferedImage img, File f) throws IOException {
        if (!ImageIO.write(img, "png", f)) {
            throw new IOException("no PNG writer");
        }
    }

    static void writeJpeg(BufferedImage img, File f, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        f.delete();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(f)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    /** Writes a single-frame GIF with palette index 255 as the transparent colour. */
    static void writeGif(BufferedImage argb, File f) throws IOException {
        int w = argb.getWidth(), h = argb.getHeight();
        Map<Integer, Integer> index = new HashMap<>();
        byte[] r = new byte[256], gr = new byte[256], b = new byte[256];
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb.getRGB(x, y);
                if ((c >>> 24) < 128) {
                    px[y * w + x] = 255;
                    continue;
                }
                int rgb = c & 0xFFFFFF;
                Integer i = index.get(rgb);
                if (i == null) {
                    i = index.size();
                    if (i >= 255) {
                        throw new IllegalStateException("more than 255 colours in " + f.getName());
                    }
                    index.put(rgb, i);
                    r[i] = (byte) (rgb >> 16);
                    gr[i] = (byte) (rgb >> 8);
                    b[i] = (byte) rgb;
                }
                px[y * w + x] = i;
            }
        }
        IndexColorModel icm = new IndexColorModel(8, 256, r, gr, b, 255);
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, icm);
        WritableRaster raster = out.getRaster();
        raster.setPixels(0, 0, w, h, px);
        if (!ImageIO.write(out, "gif", f)) {
            throw new IOException("no GIF writer");
        }
    }

    /** A 5 x 7 pixel font (9 rows with descenders) drawn as filled squares. */
    static final class PixelFont {
        static final Map<Character, String[]> GLYPHS = new HashMap<>();

        static void put(char c, String... rows) {
            GLYPHS.put(c, rows);
        }

        static {
            put('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
            put('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
            put('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
            put('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
            put('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
            put('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
            put('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
            put('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
            put('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
            put('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
            put('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
            put('N', "#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#");
            put('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
            put('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
            put('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
            put('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
            put('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
            put('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
            put('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
            put('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
            put('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
            put('a', ".....", ".....", ".###.", "....#", ".####", "#...#", ".####");
            put('c', ".....", ".....", ".###.", "#....", "#....", "#...#", ".###.");
            put('e', ".....", ".....", ".###.", "#...#", "#####", "#....", ".###.");
            put('h', "#....", "#....", "#.##.", "##..#", "#...#", "#...#", "#...#");
            put('k', "#....", "#....", "#..#.", "#.#..", "##...", "#.#..", "#..#.");
            put('l', ".##..", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
            put('n', ".....", ".....", "#.##.", "##..#", "#...#", "#...#", "#...#");
            put('p', ".....", ".....", "####.", "#...#", "#...#", "#...#", "####.", "#....", "#....");
            put('s', ".....", ".....", ".####", "#....", ".###.", "....#", "####.");
            put('u', ".....", ".....", "#...#", "#...#", "#...#", "#..##", ".##.#");
            put('0', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###.");
            put('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
            put('2', ".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####");
            put(' ', "...", "...", "...", "...", "...", "...", "...");
        }

        static String[] glyph(char c) {
            String[] rows = GLYPHS.get(c);
            if (rows == null) {
                throw new IllegalArgumentException("no glyph for '" + c + "'");
            }
            return rows;
        }

        static int cols(String s) {
            int n = 0;
            for (int i = 0; i < s.length(); i++) {
                n += glyph(s.charAt(i))[0].length() + (i + 1 < s.length() ? 1 : 0);
            }
            return n;
        }

        static double width(String s, double px) {
            return cols(s) * px;
        }

        /** Draws s with its top-left corner at (x, y). outlineWidth 0 means no outline. */
        static void draw(Graphics2D g, String s, double x, double y, double px, Color fill, Color outline, double outlineWidth) {
            for (int pass = (outline == null ? 1 : 0); pass < 2; pass++) {
                double cx = x;
                double o = pass == 0 ? outlineWidth : 0;
                g.setColor(pass == 0 ? outline : fill);
                for (int i = 0; i < s.length(); i++) {
                    String[] rows = glyph(s.charAt(i));
                    for (int r = 0; r < rows.length; r++) {
                        for (int c = 0; c < rows[r].length(); c++) {
                            if (rows[r].charAt(c) == '#') {
                                g.fill(new Rectangle2D.Double(cx + c * px - o, y + r * px - o, px + 2 * o, px + 2 * o));
                            }
                        }
                    }
                    cx += (rows[0].length() + 1) * px;
                }
            }
        }
    }
}
