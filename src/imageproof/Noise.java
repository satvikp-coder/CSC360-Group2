package imageproof;

import java.awt.Rectangle;

// Noise diagnostic.
// Residual of a pixel = its gray value minus the average of its 3 x 3 neighbourhood.
// At the image border only the neighbours inside the image are averaged.
// For each tile we report the population variance of the residuals.
// Texture and edges also give high values; this says nothing about which camera took the photo.
public class Noise {

    // Gray value minus the 3 x 3 local mean.
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

    // Variance of the residuals inside one rectangle.
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

    // Residual variance of every tile, as [row][col].
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
