package imageproof;

import java.awt.image.BufferedImage;

/** PLACEHOLDER matching docs/ARCHITECTURE.md so App compiles; superseded by issue #1. */
public final class Histogram {
    private Histogram() {}

    public static int toGray(int rgb) {
        return (int) Math.round(0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255));
    }

    public static int[] compute(BufferedImage img) {
        int[] counts = new int[256];
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) counts[toGray(img.getRGB(x, y))]++;
        return counts;
    }

    public static double[] normalize(int[] counts) {
        long total = 0;
        for (int c : counts) total += c;
        double[] p = new double[counts.length];
        for (int i = 0; i < p.length; i++) p[i] = (double) counts[i] / total;
        return p;
    }
}
