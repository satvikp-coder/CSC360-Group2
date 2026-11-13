package imageproof;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;

// Turns an image into gray values (0-255) and a 256-bin histogram.
// Rules:
// - gray = round(0.299 R + 0.587 G + 0.114 B)
// - transparent pixels are mixed onto a white background first
// - gray images use their stored sample directly (16-bit is scaled to 0-255)
public class Histogram {

    public static final int BINS = 256;

    // Biggest image we accept (16 megapixels), keeps memory use small.
    public static final int MAX_PIXELS = 16000000;

    // Gray value of one RGB colour.
    public static int toGray(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }

    // Mix one colour channel onto white using its alpha (0 = see-through, 255 = solid).
    public static int onWhite(int value, int alpha) {
        return (value * alpha + 255 * (255 - alpha) + 127) / 255;
    }

    // Gray value of every pixel, as gray[y][x].
    public static int[][] grayPixels(BufferedImage image) {
        if (image == null) {
            throw new IllegalArgumentException("No image");
        }
        int width = image.getWidth();
        int height = image.getHeight();
        if ((long) width * height > MAX_PIXELS) {
            throw new IllegalArgumentException("Image is bigger than 16 megapixels");
        }
        int[][] gray = new int[height][width];
        boolean isGrayImage = image.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY;
        if (isGrayImage) {
            readGrayImage(image, gray);
        } else {
            readColourImage(image, gray);
        }
        return gray;
    }

    // Colour images: read one row at a time with getRGB, then mix alpha onto white.
    private static void readColourImage(BufferedImage image, int[][] gray) {
        int width = image.getWidth();
        int[] row = new int[width];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                int a = (argb >> 24) & 255;
                int r = onWhite((argb >> 16) & 255, a);
                int g = onWhite((argb >> 8) & 255, a);
                int b = onWhite(argb & 255, a);
                gray[y][x] = toGray(r, g, b);
            }
        }
    }

    // Gray images: getRGB would change the values (colour space conversion),
    // so read the stored sample instead. Band 0 = gray, band 1 = alpha (if any).
    private static void readGrayImage(BufferedImage image, int[][] gray) {
        Raster raster = image.getRaster();
        int bits = raster.getSampleModel().getSampleSize(0);
        int max = (1 << bits) - 1;
        boolean hasAlpha = image.getColorModel().hasAlpha() && raster.getNumBands() > 1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int value = scale(raster.getSample(x, y, 0), max);
                int alpha = 255;
                if (hasAlpha) {
                    alpha = scale(raster.getSample(x, y, 1), max);
                }
                gray[y][x] = onWhite(value, alpha);
            }
        }
    }

    // Scale a sample from 0..max to 0..255 with rounding.
    private static int scale(int sample, int max) {
        return (sample * 255 + max / 2) / max;
    }

    // Count how many pixels have each gray value.
    public static int[] count(int[][] gray) {
        int[] counts = new int[BINS];
        for (int y = 0; y < gray.length; y++) {
            for (int x = 0; x < gray[y].length; x++) {
                counts[gray[y][x]]++;
            }
        }
        return counts;
    }

    // Histogram of a whole image.
    public static int[] compute(BufferedImage image) {
        return count(grayPixels(image));
    }

    // Divide every count by the total so the bins add up to 1.
    public static double[] normalize(int[] counts) {
        if (counts == null || counts.length != BINS) {
            throw new IllegalArgumentException("Need 256 counts");
        }
        long total = 0;
        for (int i = 0; i < BINS; i++) {
            if (counts[i] < 0) {
                throw new IllegalArgumentException("Negative count");
            }
            total = total + counts[i];
        }
        if (total == 0) {
            throw new IllegalArgumentException("Histogram has no pixels");
        }
        double[] p = new double[BINS];
        for (int i = 0; i < BINS; i++) {
            p[i] = (double) counts[i] / total;
        }
        return p;
    }

    // Average gray value.
    public static double mean(double[] p) {
        double sum = 0;
        for (int i = 0; i < BINS; i++) {
            sum = sum + i * p[i];
        }
        return sum;
    }

    // Population standard deviation of the gray values.
    public static double standardDeviation(double[] p) {
        double m = mean(p);
        double sum = 0;
        for (int i = 0; i < BINS; i++) {
            sum = sum + (i - m) * (i - m) * p[i];
        }
        return Math.sqrt(sum);
    }

    // Shannon entropy in bits: 0 = one gray value, 8 = all 256 values equally common.
    public static double entropy(double[] p) {
        double sum = 0;
        for (int i = 0; i < BINS; i++) {
            if (p[i] > 0) {
                sum = sum - p[i] * Math.log(p[i]) / Math.log(2);
            }
        }
        return sum;
    }
}
