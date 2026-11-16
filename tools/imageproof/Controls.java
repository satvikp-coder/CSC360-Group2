package imageproof;

import java.awt.image.BufferedImage;
import java.util.Random;

// Deterministic extra images for evaluating the local anomaly rule (all pixels made by this code).
// BENIGN images contain strong edges, text, shadows or texture but NO edit.
// MANIPULATED images contain a constructed change at the known rectangle PATCH (x, y, w, h).
// They were written down independently of the thresholds in LocalAnomaly.
public class Controls {

    public static final String[] BENIGN = {
        "benign-noise", "benign-gradient", "benign-forest-sky", "benign-textured-blob", "benign-text", "benign-vignette-shadow"
    };
    public static final String[] MANIPULATED = {"patch-less-noise", "patch-brighter"};
    public static final int[] PATCH = {200, 100, 140, 130};
    public static final int WIDTH = 512;
    public static final int HEIGHT = 384;

    interface Pixel {
        double value(int x, int y, Random random);
    }

    static boolean inPatch(int x, int y) {
        return x >= PATCH[0] && x < PATCH[0] + PATCH[2] && y >= PATCH[1] && y < PATCH[1] + PATCH[3];
    }

    static boolean inBlob(int x, int y) {
        return (x - 250) * (x - 250) + (y - 190) * (y - 190) < 90 * 90;
    }

    static BufferedImage make(long seed, Pixel pixel) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int v = (int) Math.max(0, Math.min(255, Math.round(pixel.value(x, y, random))));
                image.setRGB(x, y, v * 0x010101);
            }
        }
        return image;
    }

    public static BufferedImage image(String name) {
        if (name.equals("benign-noise")) {
            return make(11, (x, y, r) -> 128 + r.nextGaussian() * 20);
        } else if (name.equals("benign-gradient")) {
            return make(12, (x, y, r) -> x / 2 + y / 3 + r.nextGaussian() * 3);
        } else if (name.equals("benign-forest-sky")) {
            return make(31, (x, y, r) -> (y > 150 ? 90 : 200) + r.nextGaussian() * (y > 150 ? 35 : 2));
        } else if (name.equals("benign-textured-blob")) {
            return make(32, (x, y, r) -> (inBlob(x, y) ? 80 : 190) + r.nextGaussian() * (inBlob(x, y) ? 30 : 2));
        } else if (name.equals("benign-text")) {
            return make(7, (x, y, r) -> ((y / 12) % 2 == 0 && (x / 5) % 3 != 0 && x > 40 && x < 470 && y > 30 && y < 340 ? 30 : 230)
                    + r.nextGaussian() * 2);
        } else if (name.equals("benign-vignette-shadow")) {
            return make(33, (x, y, r) -> 220 - 120 * Math.pow(Math.hypot(x - 256, y - 192) / 300, 2)
                    - (y > 250 && y < 300 ? 50 : 0) + r.nextGaussian() * 4);
        } else if (name.equals("patch-less-noise")) {
            return make(21, (x, y, r) -> 128 + r.nextGaussian() * (inPatch(x, y) ? 3 : 20));
        } else if (name.equals("patch-brighter")) {
            return make(22, (x, y, r) -> 128 + (inPatch(x, y) ? 40 : 0) + r.nextGaussian() * 20);
        }
        throw new IllegalArgumentException("Unknown control " + name);
    }
}
