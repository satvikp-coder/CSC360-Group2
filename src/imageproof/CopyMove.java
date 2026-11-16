package imageproof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;

// Looks for copy-move candidates: two 16 x 16 blocks with exactly the same gray values.
// Steps:
// 1. Take blocks every 8 pixels over the WHOLE image. Skip perfectly flat blocks (std < 2 gray levels) because they match everywhere; noisy sky is kept, noise makes it unique.
// 2. Give every block a hash of its pixels. Only blocks with the same hash are compared pixel by pixel,
//    so the cost grows with the number of blocks, not with the square of it (big images are fully covered).
//    Blocks that overlap or touch are skipped: they are naturally similar.
// 3. Group matches by their shift (dx, dy). A copied object gives many matches with the same shift.
//    Keep only groups with at least MIN_GROUP matches.
// Limits keep the work bounded; when a limit is hit, "truncated" is set and coverage says how much was searched.
// Repeated patterns (tiles, bricks) also match: a candidate is NOT proof of editing,
// and no candidates does NOT mean the image is authentic.
// Only finds copies moved by a multiple of 8 pixels, without rotation or scaling.
public class CopyMove {

    public static final int BLOCK = 16;
    public static final int STEP = 8;
    public static final double MIN_VARIANCE = 4;    // blocks flatter than this (std < 2 gray levels) are skipped
    public static final double MIN_DETAIL = 1;      // residual variance: smooth ramps repeat exactly by themselves, so they are skipped
    public static final int MIN_GROUP = 3;          // matches needed with the same shift
    public static final int MAX_BLOCKS = 300000;    // grid positions (a 16 MP image has about 250000)
    public static final int MAX_COMPARISONS = 200000; // full pixel-by-pixel comparisons
    public static final int MAX_MATCHES = 2000;     // candidates we return (a 256 x 232 clone has 868)

    // One candidate pair: block at (x1, y1) has the same pixels as the block at (x2, y2).
    public static class Match {
        public int x1;
        public int y1;
        public int x2;
        public int y2;

        public Match(int x1, int y1, int x2, int y2) {
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }
    }

    public ArrayList<Match> matches = new ArrayList<Match>();
    public int blocksChecked = 0;   // grid positions visited
    public int blocksTotal = 0;     // grid positions in the whole image
    public int comparisons = 0;
    public boolean truncated = false;

    // Share of the block grid that was searched, 0 to 100.
    public double coveragePercent() {
        if (blocksTotal == 0) {
            return 100;
        }
        return 100.0 * blocksChecked / blocksTotal;
    }

    public static CopyMove analyze(int[][] gray) {
        CopyMove result = new CopyMove();
        int height = gray.length;
        int width = gray[0].length;
        if (width >= BLOCK && height >= BLOCK) {
            result.blocksTotal = ((height - BLOCK) / STEP + 1) * ((width - BLOCK) / STEP + 1);
        }

        // Step 1: textured blocks, bucketed by the hash of their pixels.
        ArrayList<int[]> blocks = new ArrayList<int[]>(); // each entry: {x, y}
        LinkedHashMap<Long, ArrayList<Integer>> buckets = new LinkedHashMap<Long, ArrayList<Integer>>();
        for (int y = 0; y + BLOCK <= height && !result.truncated; y = y + STEP) {
            for (int x = 0; x + BLOCK <= width; x = x + STEP) {
                if (result.blocksChecked == MAX_BLOCKS) {
                    result.truncated = true; // rest of the image is not checked
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

        // Step 2: compare blocks that share a hash. Each pair is {i, j} with i < j (block order).
        ArrayList<int[]> pairs = new ArrayList<int[]>();
        boolean stop = false;
        for (ArrayList<Integer> bucket : buckets.values()) {
            for (int p = 0; p < bucket.size() && !stop; p++) {
                for (int q = p + 1; q < bucket.size(); q++) {
                    int[] a = blocks.get(bucket.get(p));
                    int[] b = blocks.get(bucket.get(q));
                    int dx = b[0] - a[0];
                    int dy = b[1] - a[1];
                    // Skip blocks that overlap or touch: they are naturally similar.
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
        // Same order as scanning the image: first block, then second block.
        Collections.sort(pairs, new Comparator<int[]>() {
            public int compare(int[] u, int[] v) {
                if (u[0] != v[0]) {
                    return Integer.compare(u[0], v[0]);
                }
                return Integer.compare(u[1], v[1]);
            }
        });

        // Step 3: keep coherent groups (enough matches with the same shift).
        HashMap<String, ArrayList<Match>> groups = new HashMap<String, ArrayList<Match>>();
        ArrayList<String> order = new ArrayList<String>(); // shifts in the order first found
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

    // Hash of the 256 gray values of one block (equal blocks always have equal hashes).
    static long hash(int[][] gray, int x, int y) {
        long h = 1469598103934665603L;
        for (int dy = 0; dy < BLOCK; dy++) {
            for (int dx = 0; dx < BLOCK; dx++) {
                h = (h ^ gray[y + dy][x + dx]) * 1099511628211L;
            }
        }
        return h;
    }

    // A block counts as textured when it is not flat AND has fine random-looking detail
    // (3 x 3 residual variance, see Noise). Exact equality of a smooth ramp is not evidence of copying.
    static boolean textured(int[][] gray, int x, int y) {
        return variance(gray, x, y) >= MIN_VARIANCE
                && Noise.variance(gray, new java.awt.Rectangle(x, y, BLOCK, BLOCK)) >= MIN_DETAIL;
    }

    // Population variance of the gray values in one block.
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

    // True if two blocks have exactly the same gray values.
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
