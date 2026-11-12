package imageproof;

/** Total variation distance between two normalized 256-bin distributions. No Swing, no threshold. */
public final class Comparison {
    public static final int BINS = 256;
    /** Allowed deviation of a distribution's sum from 1 (counts/total in doubles). */
    public static final double TOLERANCE = 1e-9;

    private Comparison() {}

    /**
     * Returns 0.5 * sum|p[i]-q[i]|, in [0, 1]. This is NOT a percentage of edited pixels.
     * @throws IllegalArgumentException if an array is null, not length 256, has a
     *         NaN/infinite/negative value, or does not sum to 1 within {@link #TOLERANCE}.
     */
    public static double difference(double[] p, double[] q) {
        validate(p, "p");
        validate(q, "q");
        double sum = 0;
        for (int i = 0; i < BINS; i++) sum += Math.abs(p[i] - q[i]);
        return Math.min(1.0, Math.max(0.0, 0.5 * sum)); // clamp rounding noise
    }

    private static void validate(double[] d, String name) {
        if (d == null) throw new IllegalArgumentException(name + " is null");
        if (d.length != BINS) throw new IllegalArgumentException(name + " must have " + BINS + " bins, got " + d.length);
        double total = 0;
        for (int i = 0; i < BINS; i++) {
            if (!Double.isFinite(d[i]) || d[i] < 0)
                throw new IllegalArgumentException(name + "[" + i + "] must be finite and >= 0, got " + d[i]);
            total += d[i];
        }
        if (Math.abs(total - 1.0) > TOLERANCE)
            throw new IllegalArgumentException(name + " must sum to 1 (+/-" + TOLERANCE + "), got " + total);
    }
}
