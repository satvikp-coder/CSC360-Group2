package imageproof;

import java.awt.Rectangle;

// Splits the image into square tiles (64 x 64 by default) and measures each tile.
// Edge tiles can be smaller when the size is not a multiple of 64.
// Variation of a tile = average TVD between its histogram and the histograms
// of its neighbours above, below, left and right.
// High variation means the tile looks different from its neighbours. Normal object
// edges do this too, so it is NOT a sign of editing by itself.
public class Regions {

    public static final int TILE = 64;

    public int tileSize;
    public int rows;
    public int cols;
    public int width;
    public int height;
    public double[][][] histograms; // [row][col][bin]
    public double[][] variation;    // [row][col], 0 to 1

    public static Regions analyze(int[][] gray, int tileSize) {
        Regions r = new Regions();
        r.tileSize = tileSize;
        r.height = gray.length;
        r.width = gray[0].length;
        // Round up so partial tiles at the right and bottom are included.
        r.rows = (r.height + tileSize - 1) / tileSize;
        r.cols = (r.width + tileSize - 1) / tileSize;

        r.histograms = new double[r.rows][r.cols][];
        for (int row = 0; row < r.rows; row++) {
            for (int col = 0; col < r.cols; col++) {
                r.histograms[row][col] = r.tileHistogram(gray, row, col);
            }
        }

        r.variation = new double[r.rows][r.cols];
        for (int row = 0; row < r.rows; row++) {
            for (int col = 0; col < r.cols; col++) {
                r.variation[row][col] = r.neighbourDifference(row, col);
            }
        }
        return r;
    }

    // Pixel rectangle covered by a tile.
    public Rectangle bounds(int row, int col) {
        int x = col * tileSize;
        int y = row * tileSize;
        int w = Math.min(tileSize, width - x);
        int h = Math.min(tileSize, height - y);
        return new Rectangle(x, y, w, h);
    }

    private double[] tileHistogram(int[][] gray, int row, int col) {
        Rectangle b = bounds(row, col);
        int[] counts = new int[Histogram.BINS];
        for (int y = b.y; y < b.y + b.height; y++) {
            for (int x = b.x; x < b.x + b.width; x++) {
                counts[gray[y][x]]++;
            }
        }
        return Histogram.normalize(counts);
    }

    // Average TVD to the neighbours that exist. A single tile has no neighbours -> 0.
    private double neighbourDifference(int row, int col) {
        double sum = 0;
        int neighbours = 0;
        int[] rowStep = {-1, 1, 0, 0};
        int[] colStep = {0, 0, -1, 1};
        for (int i = 0; i < 4; i++) {
            int r = row + rowStep[i];
            int c = col + colStep[i];
            if (r >= 0 && r < rows && c >= 0 && c < cols) {
                sum = sum + Comparison.difference(histograms[row][col], histograms[r][c]);
                neighbours++;
            }
        }
        if (neighbours == 0) {
            return 0;
        }
        return sum / neighbours;
    }
}
