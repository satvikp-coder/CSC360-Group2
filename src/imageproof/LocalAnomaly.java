package imageproof;

import java.awt.Rectangle;
import java.util.Arrays;

/**
 * Local anomaly rule for ONE image. It only re-uses numbers that already exist:
 * tile TVD from {@link Regions}, residual variance from {@link Noise} and
 * Sobel means from {@link Edges}.
 *
 * <p>A tile is "flagged" when ALL of these hold:</p>
 * <ol>
 * <li>Histogram: its neighbour TVD is a high outlier compared with the other
 * tiles of THIS image (modified z-score at least {@link #Z_LIMIT}, from
 * Iglewicz and Hoaglin) and the TVD is at least {@link #MIN_TVD}.</li>
 * <li>Noise: its residual variance differs by a factor of {@link #NOISE_RATIO}
 * or more (larger or smaller) from the median of its neighbouring tiles
 * (up to 8). At least {@link #MIN_NEIGHBOURS} neighbours are needed, and the
 * comparison is skipped when both variances are below {@link #NOISE_FLOOR}.</li>
 * <li>Not explained by a boundary: its own edge strength is not itself a high
 * outlier (modified z-score at least {@link #Z_LIMIT}).</li>
 * <li>Agreement: at least one of its 8 neighbours also meets rules 1 to 3.
 * Isolated tiles are ignored.</li>
 * </ol>
 *
 * <p>Edge strength is NOT a vote. It only explains boundaries (rule 3).</p>
 *
 * <p>The result is a MEASURED AREA of unusual tiles. It is not a probability
 * of editing or forgery. Tiles can be unusual for normal reasons, and an image
 * with no flagged tiles is not proven to be authentic.
 * {@link TamperingAssessment} turns this area into a cautious summary.</p>
 */
public class LocalAnomaly {

    /** Creates an empty result; use {@link #analyze(Analysis)} to measure an image. */
    public LocalAnomaly() {}

    /** Modified z-score cut-off (3.5). */
    public static final double Z_LIMIT = 3.5;
    /** Smallest neighbour TVD (0.10) that can count; tiny absolute differences are ignored. */
    public static final double MIN_TVD = 0.10;
    /** Noise must be at least this many times larger or smaller (2) than the neighbour median. */
    public static final double NOISE_RATIO = 2.0;
    /** Variance below this (std below 2 gray levels) is not compared. */
    public static final double NOISE_FLOOR = 4.0;
    /** Neighbours needed for a noise comparison (3). */
    public static final int MIN_NEIGHBOURS = 3;
    /** Fewer tiles than this (9) cannot give "nearby" context. */
    public static final int MIN_TILES = 9;

    /** True if the rule could run on this image. */
    public boolean available;
    /** Why the rule could not run (empty if it ran). */
    public String unavailableReason = "";
    /** Rule 1 result per tile, as {@code [row][col]}. */
    public boolean[][] histogramUnusual;
    /** Rule 2 result per tile, as {@code [row][col]}. */
    public boolean[][] noiseUnusual;
    /** True for tiles whose edge strength is a high outlier (rule 3 fails), as {@code [row][col]}. */
    public boolean[][] boundaryExplained;
    /** True for tiles that meet all four rules, as {@code [row][col]}. */
    public boolean[][] flagged;
    /** Number of tiles meeting rules 1 to 3 before the agreement check. */
    public int candidateTiles;
    /** Number of flagged tiles. */
    public int flaggedTiles;
    /** Actual pixel area of the flagged tiles (edge tiles are smaller). */
    public long flaggedPixels;

    /**
     * Applies the rules to a finished analysis.
     *
     * @param a an analysis with regions, noise and edges filled in
     * @return the per-tile results; {@link #available} is false (with a reason)
     *         if the image has fewer than {@link #MIN_TILES} tiles or the tiles
     *         are almost identical
     */
    public static LocalAnomaly analyze(Analysis a) {
        LocalAnomaly result = new LocalAnomaly();
        Regions r = a.regions;
        result.histogramUnusual = new boolean[r.rows][r.cols];
        result.noiseUnusual = new boolean[r.rows][r.cols];
        result.boundaryExplained = new boolean[r.rows][r.cols];
        result.flagged = new boolean[r.rows][r.cols];
        if (r.rows * r.cols < MIN_TILES) {
            result.unavailableReason = "Limited analysis: the image is too small (fewer than " + MIN_TILES + " tiles) to compare regions.";
            return result;
        }
        double[] variation = robust(r.variation);
        double[] edge = robust(a.edges);
        if (variation[1] == 0) {
            result.unavailableReason = "The tiles are almost identical, so there is no local contrast to compare.";
            return result;
        }
        result.available = true;

        boolean[][] candidate = new boolean[r.rows][r.cols];
        for (int row = 0; row < r.rows; row++) {
            for (int col = 0; col < r.cols; col++) {
                double v = r.variation[row][col];
                result.histogramUnusual[row][col] = v >= MIN_TVD && z(v, variation) >= Z_LIMIT;
                result.noiseUnusual[row][col] = noiseUnusual(a, row, col);
                result.boundaryExplained[row][col] = edge[1] > 0 && z(a.edges[row][col], edge) >= Z_LIMIT;
                candidate[row][col] = result.histogramUnusual[row][col] && result.noiseUnusual[row][col]
                        && !result.boundaryExplained[row][col];
                if (candidate[row][col]) {
                    result.candidateTiles++;
                }
            }
        }
        for (int row = 0; row < r.rows; row++) {
            for (int col = 0; col < r.cols; col++) {
                if (candidate[row][col] && hasCandidateNeighbour(candidate, row, col)) {
                    result.flagged[row][col] = true;
                    result.flaggedTiles++;
                    Rectangle b = r.bounds(row, col);
                    result.flaggedPixels += (long) b.width * b.height;
                }
            }
        }
        return result;
    }

    // Rule 2: compare the tile's noise with the median noise of its neighbours.
    private static boolean noiseUnusual(Analysis a, int row, int col) {
        double[] others = new double[8];
        int n = 0;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr;
                int c = col + dc;
                boolean inside = r >= 0 && r < a.regions.rows && c >= 0 && c < a.regions.cols;
                if ((dr != 0 || dc != 0) && inside) {
                    others[n] = a.noise[r][c];
                    n++;
                }
            }
        }
        if (n < MIN_NEIGHBOURS) {
            return false; // Not enough neighbours, so make no claim.
        }
        double typical = median(Arrays.copyOf(others, n));
        double mine = a.noise[row][col];
        if (Math.max(mine, typical) < NOISE_FLOOR) {
            return false;
        }
        double ratio = (mine + 1) / (typical + 1);
        return ratio >= NOISE_RATIO || ratio <= 1 / NOISE_RATIO;
    }

    private static boolean hasCandidateNeighbour(boolean[][] candidate, int row, int col) {
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                int r = row + dr;
                int c = col + dc;
                boolean inside = r >= 0 && r < candidate.length && c >= 0 && c < candidate[0].length;
                if ((dr != 0 || dc != 0) && inside && candidate[r][c]) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Median and robust scale of all values in a grid.
     * The scale is {@code 1.4826 * MAD} (median absolute deviation), or
     * {@code 1.253314 * mean absolute deviation} if the MAD is 0.
     * A scale of 0 means all values are the same.
     *
     * @param grid a non-empty rectangular grid of values
     * @return an array {@code {median, scale}}
     */
    static double[] robust(double[][] grid) {
        int count = grid.length * grid[0].length;
        double[] values = new double[count];
        int i = 0;
        for (double[] row : grid) {
            for (double value : row) {
                values[i] = value;
                i++;
            }
        }
        double middle = median(values.clone());
        double[] deviation = new double[count];
        double sum = 0;
        for (int k = 0; k < count; k++) {
            deviation[k] = Math.abs(values[k] - middle);
            sum = sum + deviation[k];
        }
        double mad = median(deviation);
        double scale = 1.4826 * mad;
        if (mad == 0) {
            scale = 1.253314 * sum / count;
        }
        return new double[]{middle, scale};
    }

    /**
     * Modified z-score: {@code (value - median) / scale}.
     *
     * @param value the value to score
     * @param stats {@code {median, scale}} from {@link #robust(double[][])}
     * @return the score
     */
    static double z(double value, double[] stats) {
        return (value - stats[0]) / stats[1];
    }

    /**
     * Median of the values. The array is sorted in place.
     *
     * @param values a non-empty array
     * @return the middle value (or the mean of the two middle values)
     */
    static double median(double[] values) {
        Arrays.sort(values);
        int n = values.length;
        if (n % 2 == 1) {
            return values[n / 2];
        }
        return (values[n / 2 - 1] + values[n / 2]) / 2;
    }
}
