package imageproof;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

// Plain tests, no library. A failed check throws an error.
public class HistogramTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    // Image of one colour (ARGB value).
    static BufferedImage filled(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    static int onlyBin(BufferedImage image) {
        int[] counts = Histogram.compute(image);
        for (int i = 0; i < 256; i++) {
            if (counts[i] > 0) {
                return i;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        // Gray conversion of black, white and the primary colours.
        check(Histogram.toGray(0, 0, 0) == 0, "black");
        check(Histogram.toGray(255, 255, 255) == 255, "white");
        check(Histogram.toGray(255, 0, 0) == 76, "red = round(76.245)");
        check(Histogram.toGray(0, 255, 0) == 150, "green = round(149.685)");
        check(Histogram.toGray(0, 0, 255) == 29, "blue = round(29.07)");

        // Counts add up to the number of pixels; normalized bins add up to 1.
        BufferedImage mixed = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        mixed.setRGB(0, 0, 0x000000);
        mixed.setRGB(1, 0, 0xFFFFFF);
        mixed.setRGB(0, 1, 0xFF0000);
        mixed.setRGB(1, 1, 0xFFFFFF);
        int[] counts = Histogram.compute(mixed);
        check(counts[0] == 1 && counts[255] == 2 && counts[76] == 1, "mixed counts");
        double[] p = Histogram.normalize(counts);
        double sum = 0;
        for (int i = 0; i < 256; i++) {
            sum = sum + p[i];
        }
        check(near(sum, 1), "sums to 1");
        check(near(p[255], 0.5), "half white");

        // Different sizes with the same content give the same histogram.
        double[] small = Histogram.normalize(Histogram.compute(filled(2, 2, 0xFF808080)));
        double[] big = Histogram.normalize(Histogram.compute(filled(50, 30, 0xFF808080)));
        check(Comparison.difference(small, big) == 0, "size independent");

        // Statistics: half 0 and half 255 -> mean 127.5, std 127.5, entropy 1 bit.
        int[] half = new int[256];
        half[0] = 5;
        half[255] = 5;
        double[] h = Histogram.normalize(half);
        check(near(Histogram.mean(h), 127.5), "mean");
        check(near(Histogram.standardDeviation(h), 127.5), "std");
        check(near(Histogram.entropy(h), 1), "entropy 1 bit");
        check(near(Histogram.entropy(small), 0), "entropy of one value is 0");

        // Bad input is rejected instead of giving a fake histogram.
        rejects(null);
        rejects(new int[10]);
        rejects(new int[256]);
        int[] negative = new int[256];
        negative[0] = -1;
        rejects(negative);
        try {
            Histogram.grayPixels(null);
            throw new AssertionError("null image accepted");
        } catch (IllegalArgumentException expected) {
        }

        // Issue #23: transparent pixels count as white, whatever RGB is hidden.
        check(onlyBin(filled(3, 3, 0x00000000)) == 255, "transparent black -> white");
        check(onlyBin(filled(3, 3, 0x00123456)) == 255, "transparent colour -> white");
        // Half transparent black on white -> 128.
        check(onlyBin(filled(1, 1, 0x80000000)) == 127, "half alpha black");

        // Issue #22: gray images keep their stored value (getRGB would give 168 for 100).
        BufferedImage gray8 = new BufferedImage(4, 4, BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster r8 = gray8.getRaster();
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                r8.setSample(x, y, 0, 100);
            }
        }
        check(onlyBin(gray8) == 100, "8-bit gray keeps 100");
        check(onlyBin(filled(4, 4, 0xFF646464)) == 100, "RGB(100,100,100) -> 100");
        double[] g = Histogram.normalize(Histogram.compute(gray8));
        double[] c = Histogram.normalize(Histogram.compute(filled(4, 4, 0xFF646464)));
        check(Comparison.difference(g, c) == 0, "gray and RGB copy match");

        BufferedImage gray16 = new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_GRAY);
        gray16.getRaster().setSample(0, 0, 0, 65535);
        check(onlyBin(gray16) == 255, "16-bit max -> 255");
        gray16.getRaster().setSample(0, 0, 0, 100 * 257);
        check(onlyBin(gray16) == 100, "16-bit scaled to 100");

        System.out.println("HistogramTest OK");
    }

    static void rejects(int[] counts) {
        try {
            Histogram.normalize(counts);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("bad counts accepted");
    }
}
