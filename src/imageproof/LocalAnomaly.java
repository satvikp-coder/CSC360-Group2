package imageproof;

import java.awt.Rectangle;
import java.util.Arrays;

// Local anomaly rule for ONE image. It only re-uses numbers that already exist
// (tile TVD from Regions, residual variance from Noise, Sobel means from Edges).
// A tile is "flagged" when ALL of these hold:
//   1. Histogram: its neighbour TVD is a high outlier compared with the other tiles of THIS image
//      (modified z-score >= 3.5, Iglewicz and Hoaglin) and at least MIN_TVD.
//   2. Noise: its residual variance differs by a factor of NOISE_RATIO or more from the median of
//      its neighbouring tiles (up to 8).
//   3. Not explained by a boundary: its own edge strength is not itself a high outlier (z >= 3.5).
//   4. Agreement: at least one of its 8 neighbours also meets 1-3 (isolated tiles are ignored).
// Edge strength is NOT a vote; it only explains boundaries (rule 3).
// The result is a measured area, not a probability of editing.
public class LocalAnomaly {

    public static final double Z_LIMIT = 3.5;       // standard modified z-score cut-off
    public static final double MIN_TVD = 0.10;      // ignore tiny absolute histogram differences
    public static final double NOISE_RATIO = 2.0;   // noise at least 2x larger or smaller than the neighbour median
    public static final double NOISE_FLOOR = 4.0;   // variance below this (std < 2 gray levels) is not compared
    public static final int MIN_NEIGHBOURS = 3;     // neighbours needed for a noise comparison
    public static final int MIN_TILES = 9;          // fewer tiles cannot give "nearby" context

    public boolean available;
    public String unavailableReason = "";
    public boolean[][] histogramUnusual;
    public boolean[][] noiseUnusual;
    public boolean[][] boundaryExplained;
    public boolean[][] flagged;
    public int candidateTiles;  // tiles meeting rules 1-3 before the agreement check
    public int flaggedTiles;
    public long flaggedPixels;  // actual pixel area of flagged tiles (edge tiles are smaller)

    public static LocalAnomaly analyze(Analysis a) {
        LocalAnomaly result = new LocalAnomaly();
        Regions r = a.regions;
        result.histogramUnusual = new boolean[r.rows][r.cols];
        result.noiseUnusual = new boolean[r.rows][r.cols];
        result.boundaryExplained = new boolean[r.rows][r.cols];
        result.flagged = new boolean[r.rows][r.cols];
        if (r.rows * r.cols < MIN_TILES) {
            result.unavailableReason = "The image has fewer than " + MIN_TILES + " tiles, so there is not enough nearby context.";
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
            return false; // not enough neighbours: no claim
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

    // {median, scale}. scale = 1.4826 * MAD, or 1.253314 * mean absolute deviation if MAD is 0.
    // Scale 0 means all tiles are the same.
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

    static double z(double value, double[] stats) {
        return (value - stats[0]) / stats[1];
    }

    static double median(double[] values) {
        Arrays.sort(values);
        int n = values.length;
        if (n % 2 == 1) {
            return values[n / 2];
        }
        return (values[n / 2 - 1] + values[n / 2]) / 2;
    }
}
