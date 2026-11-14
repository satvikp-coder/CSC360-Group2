package imageproof;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

// Sobel edge diagnostic.
// gx = (right column) - (left column), weights 1, 2, 1
// gy = (bottom row) - (top row), weights 1, 2, 1
// strength = sqrt(gx * gx + gy * gy)
// At the border, pixels outside the image use the nearest edge pixel (clamping),
// so a flat image gives 0 everywhere, borders included.
// Normal object outlines give strong edges; this is not a sign of editing.
public class Edges {

    // Biggest possible strength: 4 * 255 in both directions.
    public static final double MAX_STRENGTH = Math.sqrt(2) * 4 * 255;

    // Gray value with coordinates clamped into the image.
    static int at(int[][] gray, int x, int y) {
        int height = gray.length;
        int width = gray[0].length;
        x = Math.max(0, Math.min(width - 1, x));
        y = Math.max(0, Math.min(height - 1, y));
        return gray[y][x];
    }

    // Horizontal gradient (finds vertical edges).
    public static int gradientX(int[][] gray, int x, int y) {
        int right = at(gray, x + 1, y - 1) + 2 * at(gray, x + 1, y) + at(gray, x + 1, y + 1);
        int left = at(gray, x - 1, y - 1) + 2 * at(gray, x - 1, y) + at(gray, x - 1, y + 1);
        return right - left;
    }

    // Vertical gradient (finds horizontal edges).
    public static int gradientY(int[][] gray, int x, int y) {
        int bottom = at(gray, x - 1, y + 1) + 2 * at(gray, x, y + 1) + at(gray, x + 1, y + 1);
        int top = at(gray, x - 1, y - 1) + 2 * at(gray, x, y - 1) + at(gray, x + 1, y - 1);
        return bottom - top;
    }

    public static double strength(int[][] gray, int x, int y) {
        int gx = gradientX(gray, x, y);
        int gy = gradientY(gray, x, y);
        return Math.sqrt(gx * gx + gy * gy);
    }

    // Average edge strength inside one rectangle.
    public static double meanStrength(int[][] gray, Rectangle r) {
        double sum = 0;
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                sum = sum + strength(gray, x, y);
            }
        }
        return sum / (r.width * r.height);
    }

    // Average edge strength of every tile, as [row][col].
    public static double[][] tileStrength(int[][] gray, Regions regions) {
        double[][] result = new double[regions.rows][regions.cols];
        for (int row = 0; row < regions.rows; row++) {
            for (int col = 0; col < regions.cols; col++) {
                result[row][col] = meanStrength(gray, regions.bounds(row, col));
            }
        }
        return result;
    }

    // Gray picture of the edges: black = no edge, white = strongest possible edge.
    public static BufferedImage map(int[][] gray) {
        int height = gray.length;
        int width = gray[0].length;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int value = (int) Math.round(strength(gray, x, y) * 255 / MAX_STRENGTH);
                image.getRaster().setSample(x, y, 0, value);
            }
        }
        return image;
    }
}
