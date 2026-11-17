package imageproof;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.PrintWriter;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;

// Draws two good-looking colour scenes (made entirely by code, no photos, no AI) with camera-style
// grain, then makes realistic edits and runs the real analysis on every file.
// Run: java -cp target/classes;target/test-classes imageproof.Gallery samples/gallery
public class Gallery {

    static final int W = 1024, H = 683;

    // ---- smooth value noise (for clouds and terrain) ----
    static double hash(int x, int y, int seed) {
        long h = x * 374761393L + y * 668265263L + seed * 144665L;
        h = (h ^ (h >> 13)) * 1274126177L;
        h = h ^ (h >> 16);
        return (h & 0xffff) / 65535.0;
    }

    static double smooth(double t) { return t * t * (3 - 2 * t); }

    static double noise(double x, double y, int seed) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = smooth(x - x0), fy = smooth(y - y0);
        double a = hash(x0, y0, seed), b = hash(x0 + 1, y0, seed), c = hash(x0, y0 + 1, seed), d = hash(x0 + 1, y0 + 1, seed);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    static double fbm(double x, double y, int seed) {
        double sum = 0, amp = 0.5;
        for (int o = 0; o < 5; o++) {
            sum += amp * noise(x, y, seed + o);
            x *= 2;
            y *= 2;
            amp *= 0.5;
        }
        return sum;
    }

    static double mix(double a, double b, double t) { return a + (b - a) * t; }

    static double[] lerp(double[] a, double[] b, double t) {
        return new double[] {mix(a[0], b[0], t), mix(a[1], b[1], t), mix(a[2], b[2], t)};
    }

    static int clamp(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }

    static BufferedImage fromRgb(double[][][] px) {
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                image.setRGB(x, y, (clamp(px[y][x][0]) << 16) | (clamp(px[y][x][1]) << 8) | clamp(px[y][x][2]));
            }
        }
        return image;
    }

    static Graphics2D pen(BufferedImage image) {
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        return g;
    }

    // Camera grain: luminance noise plus a little colour noise, like a real sensor.
    static void grain(BufferedImage image, long seed, double luma, double chroma) {
        Random r = new Random(seed);
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int rgb = image.getRGB(x, y);
                double l = r.nextGaussian() * luma;
                int red = clamp(((rgb >> 16) & 255) + l + r.nextGaussian() * chroma);
                int green = clamp(((rgb >> 8) & 255) + l + r.nextGaussian() * chroma);
                int blue = clamp((rgb & 255) + l + r.nextGaussian() * chroma);
                image.setRGB(x, y, (red << 16) | (green << 8) | blue);
            }
        }
    }

    // ---- scene 1: sunset over a mountain lake ----
    static BufferedImage mountainLake() {
        double[][][] px = new double[H][W][];
        int horizon = (int) (H * 0.58);
        double[] top = {38, 82, 165}, mid = {120, 165, 220}, low = {250, 205, 160};
        double sunX = W * 0.72, sunY = H * 0.40;
        for (int y = 0; y < horizon; y++) {
            double t = y / (double) horizon;
            double[] sky = t < 0.55 ? lerp(top, mid, t / 0.55) : lerp(mid, low, (t - 0.55) / 0.45);
            for (int x = 0; x < W; x++) {
                double d2 = ((x - sunX) * (x - sunX) + (y - sunY) * (y - sunY));
                double glow = Math.exp(-d2 / (2 * 140.0 * 140.0));
                double disc = d2 < 26 * 26 ? 1 : 0;
                double cloud = Math.max(0, Math.min(1, (fbm(x / 170.0, y / 55.0, 11) - 0.50) * 5)) * (1 - 0.5 * t);
                double[] c = {sky[0] + 255 * glow * 0.45, sky[1] + 190 * glow * 0.40, sky[2] + 90 * glow * 0.25};
                c = lerp(c, new double[] {255, 232, 214}, cloud * 0.8);
                c = lerp(c, new double[] {255, 250, 235}, disc);
                px[y][x] = c;
            }
        }
        // three mountain ridges
        double[][] ridgeColor = {{128, 138, 178}, {86, 100, 140}, {48, 62, 92}};
        double[] amp = {120, 95, 70}, base = {horizon - 40, horizon - 12, horizon};
        for (int k = 0; k < 3; k++) {
            for (int x = 0; x < W; x++) {
                int ridge = (int) (base[k] - amp[k] * (0.25 + fbm(x / (230.0 - 50 * k), k * 7.3, 40 + k)));
                for (int y = Math.max(0, ridge); y < horizon; y++) {
                    double shade = 1 - 0.35 * Math.max(0, Math.min(1, (y - ridge) / 90.0));
                    double tex = 0.88 + 0.24 * fbm(x / 14.0, y / 9.0, 70 + k);
                    px[y][x] = new double[] {ridgeColor[k][0] * shade * tex, ridgeColor[k][1] * shade * tex, ridgeColor[k][2] * shade * tex};
                }
            }
        }
        // lake = darkened mirror image of the scene above with ripples
        for (int y = horizon; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double ripple = (noise(x / 60.0, y / 3.0, 90) - 0.5) * 12;
                int sy = (int) Math.max(0, Math.min(horizon - 1, 2 * horizon - y - 1 + ripple));
                int sx = (int) Math.max(0, Math.min(W - 1, x + ripple * 2));
                double[] s = px[sy][sx];
                double fade = Math.min(1, (y - horizon) / 160.0);
                px[y][x] = lerp(new double[] {s[0] * 0.78, s[1] * 0.82, s[2] * 0.92}, new double[] {20, 42, 66}, fade * 0.65);
            }
        }
        BufferedImage image = fromRgb(px);
        Graphics2D g = pen(image);
        // pine trees on both shores
        pines(g, 0, 470, 6, 11, 70);
        pines(g, 760, 500, 5, 21, 80);
        g.setColor(new Color(15, 28, 26));
        g.fillOval(-120, H - 70, 520, 140);
        g.fillOval(640, H - 55, 520, 120);
        g.dispose();
        grain(image, 1, 2.6, 1.3);
        return image;
    }

    static void pines(Graphics2D g, int x0, int y0, int count, int seed, int size) {
        Random r = new Random(seed);
        for (int i = 0; i < count; i++) {
            int x = x0 + i * (size + 8) + r.nextInt(14);
            int height = (int) (size * (2.0 + r.nextDouble() * 1.4));
            int top = y0 + 190 - height;
            g.setColor(new Color(14 + r.nextInt(8), 34 + r.nextInt(14), 28 + r.nextInt(10)));
            for (int tier = 0; tier < 5; tier++) {
                int ty = top + tier * height / 6;
                int half = (int) (size * (0.28 + tier * 0.13));
                g.fill(new Polygon(new int[] {x, x - half, x + half}, new int[] {ty, ty + height / 3, ty + height / 3}, 3));
            }
            g.setColor(new Color(40, 28, 20));
            g.fillRect(x - 3, top + height - height / 6, 6, height / 6 + 4);
        }
    }

    // ---- scene 2: meadow with a farmhouse ----
    static BufferedImage meadow() {
        double[][][] px = new double[H][W][];
        double[] top = {80, 150, 225}, low = {205, 230, 250};
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                double t = Math.min(1, y / (H * 0.55));
                double[] c = lerp(top, low, t);
                double cloud = Math.max(0, Math.min(1, (fbm(x / 200.0, y / 70.0, 5) - 0.50) * 4.5));
                c = lerp(c, new double[] {255, 255, 255}, cloud * 0.85 * (1 - t * 0.6));
                double hill = H * 0.58 + 40 * Math.sin(x / 190.0 + 1) + 22 * Math.sin(x / 70.0);
                if (y > hill) {
                    double depth = (y - hill) / (H - hill);
                    double tex = fbm(x / 9.0, y / 5.0, 31);
                    double[] grass = lerp(new double[] {110, 160, 60}, new double[] {52, 110, 38}, depth);
                    c = new double[] {grass[0] * (0.78 + 0.44 * tex), grass[1] * (0.78 + 0.44 * tex), grass[2] * (0.78 + 0.44 * tex)};
                }
                px[y][x] = c;
            }
        }
        BufferedImage image = fromRgb(px);
        Graphics2D g = pen(image);
        // farmhouse
        g.setColor(new Color(238, 224, 196));
        g.fillRect(300, 330, 210, 120);
        g.setColor(new Color(150, 45, 38));
        g.fill(new Polygon(new int[] {280, 405, 530}, new int[] {332, 245, 332}, 3));
        g.setColor(new Color(92, 60, 40));
        g.fillRect(385, 380, 40, 70);
        g.setColor(new Color(120, 170, 210));
        g.fillRect(325, 360, 38, 34);
        g.fillRect(450, 360, 38, 34);
        g.setColor(new Color(60, 40, 30));
        g.setStroke(new BasicStroke(3));
        g.drawRect(325, 360, 38, 34);
        g.drawRect(450, 360, 38, 34);
        // trees
        Random r = new Random(9);
        for (int t = 0; t < 3; t++) {
            int tx = 120 + t * 640 / 2 + r.nextInt(30), ty = 360 + r.nextInt(30);
            g.setColor(new Color(70, 45, 28));
            g.fillRect(tx - 6, ty, 12, 90);
            for (int k = 0; k < 14; k++) {
                g.setColor(new Color(30 + r.nextInt(30), 90 + r.nextInt(50), 30 + r.nextInt(30)));
                double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 44;
                g.fill(new Ellipse2D.Double(tx + Math.cos(a) * d - 24, ty - 20 + Math.sin(a) * d * 0.8 - 24, 54, 48));
            }
        }
        // fence and flowers
        g.setColor(new Color(235, 230, 215));
        for (int x = 40; x < W - 20; x += 46) {
            g.fillRect(x, 520, 8, 58);
        }
        g.fillRect(36, 534, W - 60, 7);
        g.fillRect(36, 556, W - 60, 7);
        Color[] petals = {new Color(240, 90, 120), new Color(250, 215, 70), new Color(250, 250, 250), new Color(170, 110, 220)};
        for (int i = 0; i < 260; i++) {
            int x = r.nextInt(W), y = 585 + r.nextInt(H - 590);
            g.setColor(petals[r.nextInt(petals.length)]);
            g.fillOval(x, y, 5, 5);
        }
        g.dispose();
        grain(image, 2, 2.4, 1.2);
        return image;
    }

    // ---- edits ----
    static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(src, 0, 0, null);
        return out;
    }

    // Flat-colour hot-air balloon, drawn like a Paint user would (no noise).
    static BufferedImage balloon(BufferedImage src, int x, int y) {
        BufferedImage out = copy(src);
        Graphics2D g = pen(out);
        g.setColor(new Color(214, 40, 40));
        g.fill(new Ellipse2D.Double(x, y, 100, 120));
        g.setColor(new Color(250, 200, 40));
        g.fill(new Ellipse2D.Double(x + 30, y + 4, 40, 112));
        g.setColor(new Color(90, 60, 36));
        g.setStroke(new BasicStroke(2));
        g.drawLine(x + 30, y + 112, x + 38, y + 146);
        g.drawLine(x + 70, y + 112, x + 62, y + 146);
        g.fillRect(x + 36, y + 146, 28, 20);
        g.dispose();
        return out;
    }

    // Clone stamp: copy a block of pixels somewhere else (exactly what copy-move forgery is).
    static BufferedImage clone(BufferedImage src, int sx, int sy, int w, int h, int dx, int dy) {
        BufferedImage out = copy(src);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(dx + x, dy + y, src.getRGB(sx + x, sy + y));
            }
        }
        return out;
    }

    // Paste a soft-edged cut-out taken from ANOTHER image (a compositing job, like Photoshop).
    static BufferedImage paste(BufferedImage base, BufferedImage other, int sx, int sy, int w, int h, int dx, int dy) {
        BufferedImage out = copy(base);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double nx = (x - w / 2.0) / (w / 2.0), ny = (y - h / 2.0) / (h / 2.0);
                double d = Math.sqrt(nx * nx + ny * ny);
                double alpha = d >= 1 ? 0 : Math.min(1, (1 - d) * 5);
                int a = base.getRGB(dx + x, dy + y), b = other.getRGB(sx + x, sy + y);
                int rr = clamp(((a >> 16) & 255) * (1 - alpha) + ((b >> 16) & 255) * alpha);
                int gg = clamp(((a >> 8) & 255) * (1 - alpha) + ((b >> 8) & 255) * alpha);
                int bb = clamp((a & 255) * (1 - alpha) + (b & 255) * alpha);
                out.setRGB(dx + x, dy + y, (rr << 16) | (gg << 8) | bb);
            }
        }
        return out;
    }

    // Brighten a feathered patch (dodge tool).
    static BufferedImage dodge(BufferedImage src, int cx, int cy, int rx, int ry, double gain) {
        BufferedImage out = copy(src);
        for (int y = Math.max(0, cy - ry); y < Math.min(H, cy + ry); y++) {
            for (int x = Math.max(0, cx - rx); x < Math.min(W, cx + rx); x++) {
                double nx = (x - cx) / (double) rx, ny = (y - cy) / (double) ry;
                double d = Math.sqrt(nx * nx + ny * ny);
                if (d < 1) {
                    double k = 1 + gain * Math.min(1, (1 - d) * 3);
                    int c = src.getRGB(x, y);
                    out.setRGB(x, y, (clamp(((c >> 16) & 255) * k) << 16) | (clamp(((c >> 8) & 255) * k) << 8) | clamp((c & 255) * k));
                }
            }
        }
        return out;
    }

    static void save(BufferedImage image, File dir, String name, PrintWriter csv, String what) throws Exception {
        File f = new File(dir, name + ".png");
        ImageIO.write(image, "png", f);
        long t = System.currentTimeMillis();
        Analysis a = Analysis.run(LoadedImage.load(f).image, "PNG");
        long ms = System.currentTimeMillis() - t;
        TamperingAssessment s = a.assessment;
        String area = s.areaAvailable ? String.format(Locale.ROOT, "%.1f", s.areaPercent) : "N/A";
        csv.println(name + ".png," + what + "," + s.outcome + "," + area + "," + (s.local == null ? 0 : s.local.flaggedTiles) + ","
                + s.flatRegions.size() + "," + a.copyMove.matches.size() + "," + ms);
        System.out.println(String.format(Locale.ROOT, "%-34s %-30s area=%s flat=%d copyPairs=%d (%d ms)", name + ".png", s.title(), area,
                s.flatRegions.size(), a.copyMove.matches.size(), ms));
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args[0]);
        dir.mkdirs();
        PrintWriter csv = new PrintWriter(new File(dir, "gallery-results.csv"), "UTF-8");
        csv.println("file,what_was_done,assessment,anomalous_area_pct,flagged_tiles,flat_patches,copy_pairs,millis");
        BufferedImage lake = mountainLake(), farm = meadow();
        save(lake, dir, "lake-1-original", csv, "untouched");
        save(balloon(lake, 430, 110), dir, "lake-2-painted-balloon", csv, "flat-colour balloon painted in the sky");
        save(clone(lake, 592, 48, 168, 96, 64, 152), dir, "lake-3-cloned-cloud", csv, "cloud cloned (shift multiple of 8 px)");
        save(clone(lake, 595, 51, 168, 96, 70, 160), dir, "lake-4-cloned-cloud-offgrid", csv, "cloud cloned (shift of -525 and +109 px is NOT a multiple of 8)");
        save(paste(lake, farm, 120, 250, 150, 150, 760, 400), dir, "lake-5-pasted-object", csv, "soft-edged cut-out pasted from another image");
        save(dodge(lake, 270, 190, 150, 90, 0.7), dir, "lake-6-brightened-patch", csv, "feathered brightness patch");
        save(farm, dir, "meadow-1-original", csv, "untouched");
        save(balloon(farm, 640, 90), dir, "meadow-2-painted-balloon", csv, "flat-colour balloon painted in the sky");
        save(clone(farm, 280, 232, 256, 232, 560, 232), dir, "meadow-3-cloned-house", csv, "house cloned next to itself (shift multiple of 8 px)");
        save(paste(farm, lake, 790, 450, 150, 150, 120, 440), dir, "meadow-4-pasted-object", csv, "soft-edged cut-out pasted from another image");
        save(dodge(farm, 800, 470, 130, 80, 0.6), dir, "meadow-5-brightened-patch", csv, "feathered brightness patch");
        csv.close();
    }
}
