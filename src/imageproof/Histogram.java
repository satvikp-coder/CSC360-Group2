package imageproof;

import java.awt.image.BufferedImage;

/** Encoded intensity statistics; transparency is composited onto white. */
public final class Histogram {
    private Histogram() {}

    public static int toGray(int rgb) {
        return (int) Math.round(0.299 * ((rgb >> 16) & 255) + 0.587 * ((rgb >> 8) & 255) + 0.114 * (rgb & 255));
    }

    public static int[] compute(BufferedImage img) {
        return counts(grayPixels(img));
    }

    public static double[] normalize(int[] counts) {
        if (counts == null || counts.length != 256) throw new IllegalArgumentException("Expected 256 bins");
        long total = 0;
        for (int c : counts) {
            if (c < 0) throw new IllegalArgumentException("Negative count");
            total += c;
        }
        if (total == 0) throw new IllegalArgumentException("Histogram must contain pixels");
        double[] p = new double[counts.length];
        for (int i = 0; i < p.length; i++) p[i] = (double) counts[i] / total;
        return p;
    }

    /** Round weighted RGB; explicit gray raster samples retain their encoded intensity. */
    public static int[] grayPixels(BufferedImage image) {
        if (image == null) throw new IllegalArgumentException("Image is required");
        int width = image.getWidth(), height = image.getHeight();
        if ((long) width * height > ImageAnalyzer.MAX_PIXELS)
            throw new IllegalArgumentException("Image exceeds the 16 megapixel limit");
        int[] gray = new int[width * height];
        boolean plainGray = image.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY
                && image.getColorModel().getNumColorComponents() == 1;
        int[] row = new int[width];
        int[] alphaRow = new int[width];
        for (int y = 0; y < height; y++) {
            ImageAnalyzer.checkCancelled();
            if (plainGray) {
                int bits = image.getSampleModel().getSampleSize(0);
                if (bits != 8 && bits != 16) throw new IllegalArgumentException("Use 8/16-bit grayscale");
                int maximum = bits == 16 ? 65535 : 255;
                image.getRaster().getSamples(0, y, width, 1, 0, row);
                if (image.getColorModel().hasAlpha()) image.getRaster().getSamples(0, y, width, 1, 1, alphaRow);
                for (int x = 0; x < width; x++) {
                    int value = (row[x] * 255 + maximum / 2) / maximum;
                    int alpha = image.getColorModel().hasAlpha() ? (alphaRow[x] * 255 + maximum / 2) / maximum : 255;
                    if (image.isAlphaPremultiplied() && alpha > 0) value = Math.min(255, (value * 255 + alpha / 2) / alpha);
                    gray[y * width + x] = composite(value, alpha);
                }
            } else {
                image.getRGB(0, y, width, 1, row, 0, width);
                for (int x = 0; x < width; x++) {
                    int rgb = row[x], alpha = rgb >>> 24;
                    int r = composite((rgb >>> 16) & 255, alpha), g = composite((rgb >>> 8) & 255, alpha);
                    int b = composite(rgb & 255, alpha);
                    gray[y * width + x] = toGray((r << 16) | (g << 8) | b);
                }
            }
        }
        return gray;
    }

    private static int composite(int value, int alpha) {
        return (value * alpha + 255 * (255 - alpha) + 127) / 255;
    }

    static int[] counts(int[] gray) {
        int[] counts = new int[256];
        for (int value : gray) counts[value]++;
        return counts;
    }

    public record Statistics(double mean, double standardDeviation, double entropy) {}

    public static Statistics statistics(double[] histogram) {
        Comparison.difference(histogram, histogram);
        double mean = 0, variance = 0, entropy = 0;
        for (int i = 0; i < 256; i++) mean += i * histogram[i];
        for (int i = 0; i < 256; i++) {
            variance += (i - mean) * (i - mean) * histogram[i];
            if (histogram[i] > 0) entropy -= histogram[i] * Math.log(histogram[i]) / Math.log(2);
        }
        return new Statistics(mean, Math.sqrt(variance), entropy);
    }
}
