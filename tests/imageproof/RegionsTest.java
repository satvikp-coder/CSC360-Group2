package imageproof;

import java.awt.Rectangle;

// Tests the tile grid and the regional variation numbers.
public class RegionsTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static int[][] filled(int width, int height, int value) {
        int[][] gray = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gray[y][x] = value;
            }
        }
        return gray;
    }

    public static void main(String[] args) {
        // 150 x 70 with 64 px tiles -> 3 cols (64, 64, 22) and 2 rows (64, 6).
        Regions r = Regions.analyze(filled(150, 70, 9), 64);
        check(r.rows == 2 && r.cols == 3, "grid size");
        check(r.bounds(1, 2).equals(new Rectangle(128, 64, 22, 6)), "partial corner tile");

        // Every pixel is in exactly one tile.
        int[][] covered = new int[70][150];
        for (int row = 0; row < r.rows; row++) {
            for (int col = 0; col < r.cols; col++) {
                Rectangle b = r.bounds(row, col);
                for (int y = b.y; y < b.y + b.height; y++) {
                    for (int x = b.x; x < b.x + b.width; x++) {
                        covered[y][x]++;
                    }
                }
            }
        }
        for (int y = 0; y < 70; y++) {
            for (int x = 0; x < 150; x++) {
                check(covered[y][x] == 1, "pixel covered once");
            }
        }

        // Same colour everywhere -> variation 0 for every tile.
        for (int row = 0; row < r.rows; row++) {
            for (int col = 0; col < r.cols; col++) {
                check(r.variation[row][col] == 0, "uniform -> 0");
            }
        }

        // Left tile black, right tile white -> no overlap -> variation 1.
        int[][] two = filled(20, 10, 0);
        for (int y = 0; y < 10; y++) {
            for (int x = 10; x < 20; x++) {
                two[y][x] = 255;
            }
        }
        Regions t = Regions.analyze(two, 10);
        check(t.variation[0][0] == 1 && t.variation[0][1] == 1, "disjoint -> 1");

        // Row of 4 tiles: black, white, black, black.
        // Tile 0: only neighbour is white -> 1. Tile 2: white and black -> (1 + 0) / 2.
        // Tile 3: only neighbour is black -> 0.
        int[][] row = filled(40, 10, 0);
        for (int y = 0; y < 10; y++) {
            for (int x = 10; x < 20; x++) {
                row[y][x] = 255;
            }
        }
        Regions u = Regions.analyze(row, 10);
        check(u.variation[0][0] == 1, "tile 0: one different neighbour");
        check(u.variation[0][2] == 0.5, "tile 2: one different, one same -> 0.5");
        check(u.variation[0][3] == 0, "tile 3: same as neighbour");

        // Tiny image: one 1 x 1 tile, no neighbours.
        Regions tiny = Regions.analyze(filled(1, 1, 50), 64);
        check(tiny.rows == 1 && tiny.cols == 1 && tiny.variation[0][0] == 0, "1 x 1 image");

        System.out.println("RegionsTest OK");
    }
}
