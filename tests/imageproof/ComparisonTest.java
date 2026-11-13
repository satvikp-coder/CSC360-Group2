package imageproof;

// Plain tests, no library. A failed check throws an error.
public class ComparisonTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    // All pixels in one bin.
    static double[] spike(int bin) {
        double[] p = new double[256];
        p[bin] = 1;
        return p;
    }

    // Every bin equally common.
    static double[] uniform() {
        double[] p = new double[256];
        for (int i = 0; i < 256; i++) {
            p[i] = 1.0 / 256;
        }
        return p;
    }

    static void rejects(double[] p, String message) {
        try {
            Comparison.difference(p, uniform());
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("should reject: " + message);
    }

    public static void main(String[] args) {
        check(Comparison.difference(uniform(), uniform()) == 0, "same -> 0");
        check(Comparison.difference(spike(0), spike(255)) == 1, "no overlap -> 1");

        // p = (0.5, 0.5, 0, ...), q = (0.5, 0, 0.5, ...): 0.5 * (0 + 0.5 + 0.5) = 0.5
        double[] p = new double[256];
        double[] q = new double[256];
        p[0] = 0.5;
        p[1] = 0.5;
        q[0] = 0.5;
        q[2] = 0.5;
        check(Math.abs(Comparison.difference(p, q) - 0.5) < 1e-12, "hand result 0.5");
        check(Comparison.difference(p, q) == Comparison.difference(q, p), "symmetric");

        rejects(null, "null");
        rejects(new double[255], "wrong length");
        rejects(new double[256], "all zero");
        double[] nan = uniform();
        nan[0] = Double.NaN;
        rejects(nan, "NaN");
        double[] inf = uniform();
        inf[0] = Double.POSITIVE_INFINITY;
        rejects(inf, "infinity");
        double[] negative = spike(0);
        negative[1] = -0.5;
        negative[2] = 0.5;
        rejects(negative, "negative");
        double[] notOne = uniform();
        notOne[0] = 0.5;
        rejects(notOne, "does not add to 1");

        System.out.println("ComparisonTest OK");
    }
}
