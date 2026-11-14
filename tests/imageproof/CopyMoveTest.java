package imageproof;

import java.util.Random;

// Tests the copy-move candidate search on made-up images.
public class CopyMoveTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    // Random gray noise: textured everywhere, so no natural exact matches.
    static int[][] noise(int width, int height, long seed) {
        Random random = new Random(seed);
        int[][] gray = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gray[y][x] = random.nextInt(256);
            }
        }
        return gray;
    }

    // Copy a size x size square from (fromX, fromY) to (toX, toY).
    static void copy(int[][] gray, int fromX, int fromY, int toX, int toY, int size) {
        for (int dy = 0; dy < size; dy++) {
            for (int dx = 0; dx < size; dx++) {
                gray[toY + dy][toX + dx] = gray[fromY + dy][fromX + dx];
            }
        }
    }

    public static void main(String[] args) {
        // 1. Copied 48 x 48 object from (8, 8) to (72, 64).
        // Blocks fit at 5 x 5 positions inside it -> 25 matches, all with shift (64, 56).
        int[][] copied = noise(128, 128, 1);
        copy(copied, 8, 8, 72, 64, 48);
        CopyMove c = CopyMove.analyze(copied);
        check(c.matches.size() == 25, "25 matches, got " + c.matches.size());
        for (int i = 0; i < c.matches.size(); i++) {
            CopyMove.Match m = c.matches.get(i);
            check(m.x2 - m.x1 == 64 && m.y2 - m.y1 == 56, "same shift");
            check(m.x1 >= 8 && m.x1 + 16 <= 56 && m.y1 >= 8 && m.y1 + 16 <= 56, "source inside the object");
        }
        check(!c.truncated, "small image not truncated");

        // 2. No copy -> no candidates.
        check(CopyMove.analyze(noise(128, 128, 2)).matches.size() == 0, "plain noise");

        // 3. Flat image: every block is skipped.
        int[][] flat = new int[128][128];
        CopyMove f = CopyMove.analyze(flat);
        check(f.matches.size() == 0 && f.comparisons == 0, "flat blocks skipped");

        // 4. Copy right next to the source (touching) is ignored.
        int[][] touching = noise(128, 128, 3);
        copy(touching, 0, 0, 16, 0, 16);
        copy(touching, 0, 16, 16, 16, 16);
        copy(touching, 0, 32, 16, 32, 16);
        check(CopyMove.analyze(touching).matches.size() == 0, "adjacent blocks excluded");

        // 5. Repeated pattern: a 32 px tile repeated -> many matches (known false positive).
        int[][] tile = noise(32, 32, 4);
        int[][] repeated = new int[128][128];
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                repeated[y][x] = tile[y % 32][x % 32];
            }
        }
        CopyMove r = CopyMove.analyze(repeated);
        check(r.matches.size() > 25, "repeated pattern gives many candidates");

        // 6. Tiny image: no full block fits, nothing breaks.
        check(CopyMove.analyze(noise(10, 10, 5)).matches.size() == 0, "tiny image");

        // 7. Big image: work stops at the limit and says so.
        CopyMove big = CopyMove.analyze(noise(1300, 1300, 6));
        check(big.truncated, "big image truncated");
        check(big.comparisons <= CopyMove.MAX_COMPARISONS, "comparison limit kept");

        System.out.println("CopyMoveTest OK");
    }
}
