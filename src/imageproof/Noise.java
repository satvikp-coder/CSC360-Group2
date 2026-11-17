package imageproof;

import java.awt.Rectangle;

/**
 * Noise diagnostic.
 *
 * <p>The residual of a pixel is its gray value minus the average of its
 * 3 x 3 neighbourhood. At the image border only the neighbours inside the
 * image are averaged. For each tile we report the population variance of
 * the residuals.</p>
 *
 * <p>Texture and edges also give high values. This says nothing about which
 * camera took the photo. {@link LocalAnomaly} compares these values between
 * neighbouring tiles.</p>
 */
public class Noise {

    /** Creates an instance; noise measurements are provided by static methods. */
    public Noise() {}

    /**
     * Gray value of a pixel minus the mean of its 3 x 3 neighbourhood.
     * Neighbours outside the image are left out of the mean.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x    pixel column
     * @param y    pixel row
     * @return the residual (can be negative)
     */
    public static double residual(int[][] gray, int x, int y) {
        int height = gray.length;
        int width = gray[0].length;
        double sum = 0;
        int n = 0;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx;
                int ny = y + dy;
                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    sum = sum + gray[ny][nx];
                    n++;
                }
            }
        }
        return gray[y][x] - sum / n;
    }

    /**
     * Population variance of the residuals inside one rectangle.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param r    the rectangle, in pixels; it must be inside the image
     * @return the variance (never negative)
     */
    public static double variance(int[][] gray, Rectangle r) {
        double sum = 0;
        double sumSquares = 0;
        int n = r.width * r.height;
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                double value = residual(gray, x, y);
                sum = sum + value;
                sumSquares = sumSquares + value * value;
            }
        }
        double mean = sum / n;
        // Max with 0 removes tiny negative results from rounding.
        return Math.max(0, sumSquares / n - mean * mean);
    }

    /**
     * Residual variance of every tile.
     *
     * @param gray    gray values as {@code gray[y][x]}
     * @param regions the tile grid
     * @return the variance of each tile, as {@code [row][col]}
     */
    public static double[][] tileVariance(int[][] gray, Regions regions) {
        double[][] result = new double[regions.rows][regions.cols];
        for (int row = 0; row < regions.rows; row++) {
            for (int col = 0; col < regions.cols; col++) {
                result[row][col] = variance(gray, regions.bounds(row, col));
            }
        }
        return result;
    }
}
