package imageproof;

/** No test framework required; failures throw AssertionError. */
public final class ComparisonTest {
    static double[] spike(int bin) { double[] d = new double[256]; d[bin] = 1; return d; }
    static double[] uniform() { double[] d = new double[256]; java.util.Arrays.fill(d, 1.0 / 256); return d; }
    static void check(boolean ok, String msg) { if (!ok) throw new AssertionError(msg); }
    static void rejects(double[] p, double[] q, String msg) {
        try { Comparison.difference(p, q); } catch (IllegalArgumentException e) { return; }
        throw new AssertionError("should reject: " + msg);
    }
    static double[] with(int i, double v) { double[] d = uniform(); d[i] = v; return d; }

    public static void main(String[] args) {
        check(Comparison.difference(uniform(), uniform()) == 0, "identical -> 0");
        check(Comparison.difference(spike(0), spike(255)) == 1, "disjoint -> 1");
        // p=(.5,.5,0..) q=(.5,0,.5,0..): 0.5*(0+.5+.5)=0.5
        double[] p = new double[256], q = new double[256];
        p[0] = .5; p[1] = .5; q[0] = .5; q[2] = .5;
        check(Math.abs(Comparison.difference(p, q) - 0.5) < 1e-12, "partial overlap -> 0.5");
        check(Comparison.difference(p, q) == Comparison.difference(q, p), "symmetric");
        double d = Comparison.difference(spike(10), uniform());
        check(d >= 0 && d <= 1, "in range");

        rejects(null, uniform(), "null");
        rejects(uniform(), new double[255], "length");
        rejects(with(0, Double.NaN), uniform(), "NaN");
        rejects(with(0, Double.POSITIVE_INFINITY), uniform(), "inf");
        double[] neg = spike(0); neg[1] = -0.5; neg[2] = 0.5;
        rejects(neg, uniform(), "negative");
        rejects(new double[256], uniform(), "all zero (sum 0)");
        rejects(with(0, 0.5), uniform(), "not normalized");
        System.out.println("ComparisonTest OK");
    }
}
