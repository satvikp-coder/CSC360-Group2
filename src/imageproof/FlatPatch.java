package imageproof;

import java.awt.Rectangle;
import java.util.ArrayList;

/**
 * Finds solid-colour patches (like a Paint fill) in an otherwise noisy image.
 *
 * <p>Camera noise makes real photos almost never contain a 4 x 4 block of
 * exactly equal gray values, but a solid fill does. The rules are:</p>
 * <ul>
 * <li>A block is flat when all 16 pixels are equal and the value is not 0 or
 * 255 (clipped shadows and highlights are naturally constant).</li>
 * <li>A patch is a group of at least {@link #MIN_BLOCKS} connected flat blocks
 * with the same value. Blocks touch if they are next to each other, also
 * diagonally. The patch must contain a solid 3 x 3 block core (12 x 12
 * pixels), so thin text bars and strokes are ignored.</li>
 * <li>The test only runs when at least {@link #MIN_NOISY_SHARE} of the blocks
 * are not flat, because noise-free art, screenshots and heavily smoothed JPEGs
 * contain flat areas naturally. It does not run on JPEG files at all, or on
 * images with fewer than {@link #MIN_GRID} blocks.</li>
 * </ul>
 *
 * <p>Limits: text and thin strokes are ignored. A fill that was blurred,
 * noised or re-compressed afterwards is not flat any more. A patch is a
 * measurement, not proof of editing. {@link TamperingAssessment} uses the
 * result.</p>
 */
public class FlatPatch {

    /** Block side length in pixels (4). */
    public static final int BLOCK = 4;
    /** Smallest patch: 6 blocks, which is 96 pixels of one solid value. */
    public static final int MIN_BLOCKS = 6;
    /** Smallest share (0.6) of blocks that must have real pixel variation for the test to run. */
    public static final double MIN_NOISY_SHARE = 0.6;
    /** Smallest block grid for the test: 100 blocks, otherwise there is no baseline. */
    public static final int MIN_GRID = 100;

    /** True if the test could run on this image. */
    public boolean available;
    /** Why the test could not run (empty if it ran). */
    public String unavailableReason = "";
    /** The flat blocks of the qualifying patches. */
    public ArrayList<Rectangle> blocks = new ArrayList<Rectangle>();
    /** The bounding box of each patch. */
    public ArrayList<Rectangle> regions = new ArrayList<Rectangle>();
    /** Area in pixels of the flat blocks. */
    public long pixels;
    /** Share (0 to 1) of blocks that are not flat. */
    public double noisyShare;

    /**
     * Looks for solid-colour patches.
     *
     * @param gray gray values as {@code gray[y][x]}; must not be empty
     * @param jpeg true if the file was a JPEG; the test is then not available
     * @return the patches, or a result with {@link #available} false and a reason
     */
    public static FlatPatch analyze(int[][] gray, boolean jpeg) {
        FlatPatch result = new FlatPatch();
        if (jpeg) {
            result.unavailableReason = "JPEG compression creates flat areas by itself, so flat patches are not reliable evidence here.";
            return result;
        }
        int bh = gray.length / BLOCK;
        int bw = gray[0].length / BLOCK;
        if (bw * bh < MIN_GRID) {
            result.unavailableReason = "The image is too small to tell flat patches from normal content.";
            return result;
        }
        int[][] value = new int[bh][bw]; // Gray value of a flat block, or -1 if not flat.
        int noisy = 0;
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                int first = gray[by * BLOCK][bx * BLOCK];
                boolean same = true;
                for (int y = 0; y < BLOCK && same; y++) {
                    for (int x = 0; x < BLOCK; x++) {
                        if (gray[by * BLOCK + y][bx * BLOCK + x] != first) {
                            same = false;
                            break;
                        }
                    }
                }
                if (!same) {
                    noisy++;
                    value[by][bx] = -1;
                } else if (first == 0 || first == 255) {
                    value[by][bx] = -1; // Clipped, so naturally constant.
                } else {
                    value[by][bx] = first;
                }
            }
        }
        result.noisyShare = (double) noisy / (bw * bh);
        if (result.noisyShare < MIN_NOISY_SHARE) {
            result.unavailableReason = "Much of the image is naturally flat, so flat patches are not unusual here.";
            return result;
        }
        result.available = true;
        boolean[][] seen = new boolean[bh][bw];
        for (int by = 0; by < bh; by++) {
            for (int bx = 0; bx < bw; bx++) {
                if (value[by][bx] >= 0 && !seen[by][bx]) {
                    collect(value, seen, bx, by, result);
                }
            }
        }
        return result;
    }

    // True if some flat block has all 8 neighbours flat with the same value.
    // That is a solid area of at least 12 x 12 pixels. Thin bars and text never have one.
    private static boolean hasSolidCore(ArrayList<int[]> group, int[][] value, int target) {
        for (int[] b : group) {
            boolean core = true;
            for (int dy = -1; dy <= 1 && core; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = b[0] + dx;
                    int ny = b[1] + dy;
                    if (nx < 0 || ny < 0 || ny >= value.length || nx >= value[0].length || value[ny][nx] != target) {
                        core = false;
                        break;
                    }
                }
            }
            if (core) {
                return true;
            }
        }
        return false;
    }

    // Flood fill over touching blocks with the same value. Keep the group if it is big enough.
    private static void collect(int[][] value, boolean[][] seen, int startX, int startY, FlatPatch result) {
        ArrayList<int[]> queue = new ArrayList<int[]>();
        queue.add(new int[] {startX, startY});
        seen[startY][startX] = true;
        int target = value[startY][startX];
        Rectangle box = null;
        for (int i = 0; i < queue.size(); i++) {
            int[] b = queue.get(i);
            Rectangle r = new Rectangle(b[0] * BLOCK, b[1] * BLOCK, BLOCK, BLOCK);
            box = box == null ? r : box.union(r);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = b[0] + dx;
                    int ny = b[1] + dy;
                    if (nx >= 0 && ny >= 0 && ny < value.length && nx < value[0].length
                            && !seen[ny][nx] && value[ny][nx] == target) {
                        seen[ny][nx] = true;
                        queue.add(new int[] {nx, ny});
                    }
                }
            }
        }
        if (queue.size() >= MIN_BLOCKS && hasSolidCore(queue, value, target)) {
            for (int[] b : queue) {
                result.blocks.add(new Rectangle(b[0] * BLOCK, b[1] * BLOCK, BLOCK, BLOCK));
            }
            result.regions.add(box);
            result.pixels += (long) queue.size() * BLOCK * BLOCK;
        }
    }
}
