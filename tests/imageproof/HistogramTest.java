package imageproof;

import java.awt.image.BufferedImage;

/** No test framework required; failures throw AssertionError. */
public final class HistogramTest {
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }

    public static void main(String[] args) {
        check(Histogram.toGray(0x000000) == 0 && Histogram.toGray(0xFFFFFF) == 255, "gray extremes");
        BufferedImage a = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB); // all black
        BufferedImage b = new BufferedImage(40, 20, BufferedImage.TYPE_INT_RGB); // bigger, all black
        int[] counts = Histogram.compute(a);
        check(counts[0] == 8, "8 black pixels");
        double[] p = Histogram.normalize(counts);
        double sum = 0;
        for (double v : p) sum += v;
        check(Math.abs(sum - 1) < 1e-12, "normalized sums to 1");
        // different dimensions, same content -> zero distance (size independent)
        check(Comparison.difference(p, Histogram.normalize(Histogram.compute(b))) == 0, "size independent");
        // black vs white -> 1
        for (int x = 0; x < 4; x++) for (int y = 0; y < 2; y++) a.setRGB(x, y, 0xFFFFFF);
        check(Comparison.difference(p, Histogram.normalize(Histogram.compute(a))) == 1, "black vs white");
        System.out.println("HistogramTest OK");
    }
}
