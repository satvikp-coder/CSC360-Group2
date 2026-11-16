package imageproof;

import java.awt.Rectangle;

/**
 * Splits the image into square tiles (64 x 64 pixels by default) and measures each tile.
 *
 * <p>Tiles at the right and bottom edges can be smaller when the image size
 * is not a multiple of 64.
 * The variation of a tile is the average {@link Comparison#difference TVD}
 * between its histogram and the histograms of its neighbours above, below,
 * left and right.</p>
 *
 * <p>High variation means the tile looks different from its neighbours.
 * Normal object edges do this too, so it is NOT a sign of editing by itself.
 * {@link Noise}, {@link Edges} and {@link LocalAnomaly} use the same tile grid.</p>
 */
public class Regions {

    /** Default tile size in pixels (64). */
    public static final int TILE = 64;

    /** Tile side length in pixels. */
    public int tileSize;
    /** Number of tile rows. */
    public int rows;
    /** Number of tile columns. */
    public int cols;
    /** Image width in pixels. */
    public int width;
    /** Image height in pixels. */
    public int height;
    /** Normalized gray histogram of each tile, as {@code [row][col][bin]}. */
    public double[][][] histograms;
    /** Variation of each tile, as {@code [row][col]}, from 0 to 1. */
    public double[][] variation;

    /**
     * Builds the tile grid, the tile histograms and the tile variation values.
     *
     * @param gray     gray values as {@code gray[y][x]}; must not be empty
     * @param tileSize tile side length in pixels (usually {@link #TILE})
     * @return the measured tile grid
     */
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

    /**
     * Gives the pixel rectangle covered by one tile.
     * Tiles at the right and bottom edges can be smaller than the tile size.
     *
     * @param row tile row, from 0
     * @param col tile column, from 0
     * @return the rectangle in image pixels
     */
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
