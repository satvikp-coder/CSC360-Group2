package imageproof;

import java.util.ArrayList;
import java.util.HashMap;

// Looks for copy-move candidates: two 16 x 16 blocks with exactly the same gray values.
// Steps:
// 1. Take blocks every 8 pixels. Skip flat blocks (sky, walls) because they match everywhere.
// 2. Compare blocks that are not overlapping or touching. Exact equal pixels = a match.
// 3. Group matches by their shift (dx, dy). A copied object gives many matches with the same shift.
//    Keep only groups with at least MIN_GROUP matches.
// Limits keep the work bounded on big images; when a limit is hit, "truncated" is set.
// Repeated patterns (tiles, bricks) also match: a candidate is NOT proof of editing,
// and no candidates does NOT mean the image is authentic.
// Only finds copies moved by a multiple of 8 pixels, without rotation or scaling.
public class CopyMove {

    public static final int BLOCK = 16;
    public static final int STEP = 8;
    public static final double MIN_VARIANCE = 25;   // blocks flatter than this are skipped
    public static final int MIN_GROUP = 3;          // matches needed with the same shift
    public static final int MAX_BLOCKS = 20000;     // textured blocks we look at
    public static final int MAX_COMPARISONS = 200000; // full pixel-by-pixel comparisons
    public static final int MAX_MATCHES = 200;      // candidates we return

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
    public int blocksChecked = 0;
    public int comparisons = 0;
    public boolean truncated = false;

    public static CopyMove analyze(int[][] gray) {
        CopyMove result = new CopyMove();
        int height = gray.length;
        int width = gray[0].length;

        // Step 1: textured blocks. The pixel sum is a quick filter: equal blocks have equal sums.
        ArrayList<int[]> blocks = new ArrayList<int[]>(); // each entry: {x, y, sum}
        for (int y = 0; y + BLOCK <= height && !result.truncated; y = y + STEP) {
            for (int x = 0; x + BLOCK <= width; x = x + STEP) {
                if (blocks.size() == MAX_BLOCKS) {
                    result.truncated = true; // rest of the image is not checked
                    break;
                }
                result.blocksChecked++;
                if (variance(gray, x, y) >= MIN_VARIANCE) {
                    blocks.add(new int[] {x, y, sum(gray, x, y)});
                }
            }
        }

        // Step 2: compare pairs.
        HashMap<String, ArrayList<Match>> groups = new HashMap<String, ArrayList<Match>>();
        ArrayList<String> order = new ArrayList<String>(); // shifts in the order first found
        boolean stop = false;
        for (int i = 0; i < blocks.size() && !stop; i++) {
            for (int j = i + 1; j < blocks.size(); j++) {
                int[] a = blocks.get(i);
                int[] b = blocks.get(j);
                if (a[2] != b[2]) {
                    continue; // different sums, cannot be equal
                }
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
                    String shift = dx + "," + dy;
                    if (!groups.containsKey(shift)) {
                        groups.put(shift, new ArrayList<Match>());
                        order.add(shift);
                    }
                    groups.get(shift).add(new Match(a[0], a[1], b[0], b[1]));
                }
            }
        }

        // Step 3: keep coherent groups (enough matches with the same shift).
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

    static int sum(int[][] gray, int x, int y) {
        int total = 0;
        for (int dy = 0; dy < BLOCK; dy++) {
            for (int dx = 0; dx < BLOCK; dx++) {
                total = total + gray[y + dy][x + dx];
            }
        }
        return total;
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
