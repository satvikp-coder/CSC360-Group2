package imageproof;

// Total variation distance (TVD) between two normalized histograms.
// TVD = 0.5 * sum(|p[i] - q[i]|)
// 0 = same distribution, 1 = no overlap at all.
// It is NOT the percentage of edited pixels.
public class Comparison {

    // How far the sum of a histogram may be from 1 (rounding error).
    public static final double TOLERANCE = 1e-9;

    public static double difference(double[] p, double[] q) {
        check(p);
        check(q);
        double sum = 0;
        for (int i = 0; i < Histogram.BINS; i++) {
            sum = sum + Math.abs(p[i] - q[i]);
        }
        double tvd = 0.5 * sum;
        // Keep the answer inside 0..1 even with tiny rounding errors.
        if (tvd > 1) {
            tvd = 1;
        }
        return tvd;
    }

    // A valid histogram has 256 finite, non-negative values that add up to 1.
    private static void check(double[] p) {
        if (p == null || p.length != Histogram.BINS) {
            throw new IllegalArgumentException("Need 256 bins");
        }
        double total = 0;
        for (int i = 0; i < p.length; i++) {
            if (Double.isNaN(p[i]) || Double.isInfinite(p[i]) || p[i] < 0) {
                throw new IllegalArgumentException("Bad bin value at " + i);
            }
            total = total + p[i];
        }
        if (Math.abs(total - 1) > TOLERANCE) {
            throw new IllegalArgumentException("Bins must add up to 1");
        }
    }
}
