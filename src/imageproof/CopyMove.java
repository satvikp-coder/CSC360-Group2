package imageproof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * Looks for copy-move candidates: two 16 x 16 blocks with exactly the same gray values.
 *
 * <p>Steps:</p>
 * <ol>
 * <li>Take blocks every 8 pixels over the whole image. Skip blocks that are
 * flat (gray variance below {@link #MIN_VARIANCE}, about 2 gray levels of
 * standard deviation) or have almost no fine detail (3 x 3 residual variance
 * below {@link #MIN_DETAIL}), because smooth areas repeat by themselves.
 * Noisy areas are kept, because noise makes each block unique.</li>
 * <li>Give every kept block a hash of its pixels. Only blocks with the same
 * hash are compared pixel by pixel. So the work grows with the number of
 * blocks, not with its square. Pairs that are closer than two block sizes
 * apart (32 pixels) in both directions are skipped, because nearby blocks are
 * naturally similar.</li>
 * <li>Group the matches by their shift {@code (dx, dy)}. A copied object gives
 * many matches with the same shift. Keep only groups with at least
 * {@link #MIN_GROUP} matches.</li>
 * </ol>
 *
 * <p>Limits keep the work bounded. When a limit is hit, {@link #truncated} is
 * set and {@link #coveragePercent()} says how much of the block grid was
 * searched.</p>
 *
 * <p>Repeated patterns (tiles, bricks) also match, so a candidate is NOT proof
 * of editing. No candidates does NOT mean the image is authentic. The search
 * only finds copies moved by a multiple of 8 pixels, without rotation or
 * scaling. It uses the gray values from {@link Histogram} and
 * {@link Noise#variance}.</p>
 */
public class CopyMove {

    /** Creates an empty result; use {@link #analyze(int[][])} to search an image. */
    public CopyMove() {}

    /** Block side length in pixels (16). */
    public static final int BLOCK = 16;
    /** Distance in pixels between block positions (8). */
    public static final int STEP = 8;
    /** Blocks with a gray variance below this value (std below 2 gray levels) are skipped. */
    public static final double MIN_VARIANCE = 4;
    /** Blocks with a 3 x 3 residual variance below this are skipped, because smooth ramps repeat exactly by themselves. */
    public static final double MIN_DETAIL = 1;
    /** Number of matches with the same shift needed to keep a group (3). */
    public static final int MIN_GROUP = 3;
    /** Most grid positions that are visited (a 16 MP image has about 250000). */
    public static final int MAX_BLOCKS = 300000;
    /** Most full pixel-by-pixel block comparisons. */
    public static final int MAX_COMPARISONS = 200000;
    /** Most candidates that are returned (a 256 x 232 clone has 868). */
    public static final int MAX_MATCHES = 2000;

    /** One candidate pair: the block at (x1, y1) has the same pixels as the block at (x2, y2). */
    public static class Match {
        /** Left edge of the first block. */
        public int x1;
        /** Top edge of the first block. */
        public int y1;
        /** Left edge of the second block. */
        public int x2;
        /** Top edge of the second block. */
        public int y2;

        /**
         * Creates a match.
         *
         * @param x1 left edge of the first block
         * @param y1 top edge of the first block
         * @param x2 left edge of the second block
         * @param y2 top edge of the second block
         */
        public Match(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    /** The candidate pairs that belong to a group of at least {@link #MIN_GROUP}. */
    public ArrayList<Match> matches = new ArrayList<Match>();
    /** Grid positions visited. */
    public int blocksChecked = 0;
    /** Grid positions in the whole image. */
    public int blocksTotal = 0;
    /** Number of full pixel-by-pixel comparisons made. */
    public int comparisons = 0;
    /** True if a limit was hit, so not everything was searched. */
    public boolean truncated = false;

    /**
     * Share of the block grid that was searched.
     *
     * @return a percentage from 0 to 100 (100 if the image has no block positions)
     */
    public double coveragePercent() {
        if (blocksTotal == 0) {
            return 100;
        }
        return 100.0 * blocksChecked / blocksTotal;
    }

    /**
     * Searches the image for copy-move candidates.
     *
     * @param gray gray values as {@code gray[y][x]}; must not be empty
     * @return the candidates and the search statistics
     */
    public static CopyMove analyze(int[][] gray) {
        CopyMove result = new CopyMove();
        int height = gray.length;
        int width = gray[0].length;
        if (width >= BLOCK && height >= BLOCK) {
            result.blocksTotal = ((height - BLOCK) / STEP + 1) * ((width - BLOCK) / STEP + 1);
        }

        // Step 1: keep blocks with detail and group them by the hash of their pixels.
        ArrayList<int[]> blocks = new ArrayList<int[]>(); // each entry: {x, y}
        LinkedHashMap<Long, ArrayList<Integer>> buckets = new LinkedHashMap<Long, ArrayList<Integer>>();
        for (int y = 0; y + BLOCK <= height && !result.truncated; y = y + STEP) {
            for (int x = 0; x + BLOCK <= width; x = x + STEP) {
                if (result.blocksChecked == MAX_BLOCKS) {
                    result.truncated = true; // The rest of the image is not checked.
                    break;
                }
                result.blocksChecked++;
                if (textured(gray, x, y)) {
                    Long key = Long.valueOf(hash(gray, x, y));
                    if (!buckets.containsKey(key)) {
                        buckets.put(key, new ArrayList<Integer>());
                    }
                    buckets.get(key).add(blocks.size());
                    blocks.add(new int[] {x, y});
                }
            }
        }

        // Step 2: compare blocks with the same hash. A pair is {i, j} with i < j (block order).
        ArrayList<int[]> pairs = new ArrayList<int[]>();
        boolean stop = false;
        for (ArrayList<Integer> bucket : buckets.values()) {
            for (int p = 0; p < bucket.size() && !stop; p++) {
                for (int q = p + 1; q < bucket.size(); q++) {
                    int[] a = blocks.get(bucket.get(p));
                    int[] b = blocks.get(bucket.get(q));
                    int dx = b[0] - a[0];
                    int dy = b[1] - a[1];
                    // Skip blocks closer than 32 pixels in both directions: they are naturally similar.
                    if (Math.abs(dx) < 2 * BLOCK && Math.abs(dy) < 2 * BLOCK) {
                        continue;
                    }
                    if (result.comparisons == MAX_COMPARISONS) {
                        result.truncated = true;
                        stop = true;
                        break;
                    }
                    result.comparisons++;
                    if (same(gray, a[0], a[1], b[0], b[1])) {
                        pairs.add(new int[] {bucket.get(p), bucket.get(q)});
                    }
                }
            }
        }
        // Sort the pairs in image scan order.
        Collections.sort(pairs, new Comparator<int[]>() {
            public int compare(int[] u, int[] v) {
                if (u[0] != v[0]) {
                    return Integer.compare(u[0], v[0]);
                }
                return Integer.compare(u[1], v[1]);
            }
        });

        // Step 3: group pairs by shift and keep groups with enough matches.
        HashMap<String, ArrayList<Match>> groups = new HashMap<String, ArrayList<Match>>();
        ArrayList<String> order = new ArrayList<String>(); // Shifts in the order first found.
        for (int[] pair : pairs) {
            int[] a = blocks.get(pair[0]);
            int[] b = blocks.get(pair[1]);
            String shift = (b[0] - a[0]) + "," + (b[1] - a[1]);
            if (!groups.containsKey(shift)) {
                groups.put(shift, new ArrayList<Match>());
                order.add(shift);
            }
            groups.get(shift).add(new Match(a[0], a[1], b[0], b[1]));
        }
        for (int i = 0; i < order.size(); i++) {
            ArrayList<Match> group = groups.get(order.get(i));
            if (group.size() >= MIN_GROUP) {
                for (int k = 0; k < group.size(); k++) {
                    if (result.matches.size() == MAX_MATCHES) {
                        result.truncated = true;
                        return result;
                    }
                    result.matches.add(group.get(k));
                }
            }
        }
        return result;
    }

    /**
     * Hash of the 256 gray values of one block. Equal blocks always have equal hashes.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x    left edge of the block
     * @param y    top edge of the block
     * @return a 64-bit hash
     */
    static long hash(int[][] gray, int x, int y) {
        long h = 1469598103934665603L;
        for (int dy = 0; dy < BLOCK; dy++) {
            for (int dx = 0; dx < BLOCK; dx++) {
                h = (h ^ gray[y + dy][x + dx]) * 1099511628211L;
            }
        }
        return h;
    }

    /**
     * Tells if a block has enough detail to be compared.
     * A block is textured when its gray variance is at least {@link #MIN_VARIANCE}
     * and its 3 x 3 residual variance (see {@link Noise}) is at least
     * {@link #MIN_DETAIL}. Exact equality of a smooth ramp is not evidence of copying.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x    left edge of the block
     * @param y    top edge of the block
     * @return true if the block is textured
     */
    static boolean textured(int[][] gray, int x, int y) {
        return variance(gray, x, y) >= MIN_VARIANCE
                && Noise.variance(gray, new java.awt.Rectangle(x, y, BLOCK, BLOCK)) >= MIN_DETAIL;
    }

    /**
     * Population variance of the gray values in one block.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x    left edge of the block
     * @param y    top edge of the block
     * @return the variance
     */
    static double variance(int[][] gray, int x, int y) {
        double total = 0;
        double squares = 0;
        for (int dy = 0; dy < BLOCK; dy++) {
            for (int dx = 0; dx < BLOCK; dx++) {
                int v = gray[y + dy][x + dx];
                total = total + v;
                squares = squares + v * v;
            }
        }
        int n = BLOCK * BLOCK;
        double mean = total / n;
        return squares / n - mean * mean;
    }

    /**
     * Tells if two blocks have exactly the same gray values.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x1   left edge of the first block
     * @param y1   top edge of the first block
     * @param x2   left edge of the second block
     * @param y2   top edge of the second block
     * @return true if all 256 pixels are equal
     */
    static boolean same(int[][] gray, int x1, int y1, int x2, int y2) {
        for (int dy = 0; dy < BLOCK; dy++) {
            for (int dx = 0; dx < BLOCK; dx++) {
                if (gray[y1 + dy][x1 + dx] != gray[y2 + dy][x2 + dx]) {
                    return false;
                }
            }
        }
        return true;
    }
}
