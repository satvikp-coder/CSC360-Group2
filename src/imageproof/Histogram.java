package imageproof;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;

/**
 * Turns an image into gray values (0 to 255) and a 256-bin histogram.
 * This is the first step of the analysis.
 *
 * <p>Rules:</p>
 * <ul>
 * <li>{@code gray = round(0.299 R + 0.587 G + 0.114 B)}</li>
 * <li>transparent pixels are mixed onto a white background first</li>
 * <li>gray images use their stored sample directly (16-bit is scaled to 0-255)</li>
 * </ul>
 *
 * <p>A histogram counts how many pixels have each gray value. It is
 * normalized by dividing each count by the total, so the 256 bins add up to 1.
 * {@link Comparison} compares two such histograms.</p>
 */
public class Histogram {

    /** Number of histogram bins: one for each gray value 0 to 255. */
    public static final int BINS = 256;

    /** Biggest image we accept: 16 megapixels (16,000,000 pixels). It keeps memory use small. */
    public static final int MAX_PIXELS = 16000000;

    /**
     * Gray value of one RGB colour: {@code round(0.299 R + 0.587 G + 0.114 B)}.
     *
     * @param r red, 0 to 255
     * @param g green, 0 to 255
     * @param b blue, 0 to 255
     * @return the gray value, 0 to 255
     */
    public static int toGray(int r, int g, int b) {
        return (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);
    }

    /**
     * Mixes one colour channel onto a white background using its alpha.
     *
     * @param value the channel value, 0 to 255
     * @param alpha 0 = see-through, 255 = solid
     * @return the mixed value, 0 to 255
     */
    public static int onWhite(int value, int alpha) {
        return (value * alpha + 255 * (255 - alpha) + 127) / 255;
    }

    /**
     * Gray value of every pixel.
     *
     * @param image the image to convert
     * @return the gray values, as {@code gray[y][x]}
     * @throws IllegalArgumentException if the image is {@code null} or has
     *         more than {@link #MAX_PIXELS} pixels
     */
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

    /**
     * Counts how many pixels have each gray value.
     *
     * @param gray gray values as {@code gray[y][x]}, each 0 to 255
     * @return 256 counts; index {@code i} counts the pixels with gray value {@code i}
     */
    public static int[] count(int[][] gray) {
        int[] counts = new int[BINS];
        for (int y = 0; y < gray.length; y++) {
            for (int x = 0; x < gray[y].length; x++) {
                counts[gray[y][x]]++;
            }
        }
        return counts;
    }

    /**
     * Histogram counts of a whole image.
     *
     * @param image the image
     * @return 256 counts of gray values
     * @throws IllegalArgumentException if the image is {@code null} or too big
     */
    public static int[] compute(BufferedImage image) {
        return count(grayPixels(image));
    }

    /**
     * Divides every count by the total so the bins add up to 1.
     *
     * @param counts 256 pixel counts
     * @return the normalized histogram
     * @throws IllegalArgumentException if {@code counts} is {@code null}, does
     *         not have 256 values, has a negative value, or the total is 0
     */
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

    /**
     * Average gray value: {@code sum(i * p[i])}.
     *
     * @param p normalized histogram (256 values)
     * @return the mean, 0 to 255
     */
    public static double mean(double[] p) {
        double sum = 0;
        for (int i = 0; i < BINS; i++) {
            sum = sum + i * p[i];
        }
        return sum;
    }

    /**
     * Population standard deviation of the gray values.
     *
     * @param p normalized histogram (256 values)
     * @return the standard deviation
     */
    public static double standardDeviation(double[] p) {
        double m = mean(p);
        double sum = 0;
        for (int i = 0; i < BINS; i++) {
            sum = sum + (i - m) * (i - m) * p[i];
        }
        return Math.sqrt(sum);
    }

    /**
     * Shannon entropy in bits: {@code -sum(p[i] * log2(p[i]))}.
     * 0 means one gray value only. 8 means all 256 values are equally common.
     *
     * @param p normalized histogram (256 values)
     * @return the entropy, 0 to 8
     */
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
