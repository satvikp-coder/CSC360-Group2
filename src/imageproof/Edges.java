package imageproof;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * Sobel edge diagnostic.
 *
 * <p>For each pixel, using a 3 x 3 neighbourhood:</p>
 * <ul>
 * <li>{@code gx} = (right column) - (left column), with weights 1, 2, 1</li>
 * <li>{@code gy} = (bottom row) - (top row), with weights 1, 2, 1</li>
 * <li>{@code strength = sqrt(gx * gx + gy * gy)}</li>
 * </ul>
 *
 * <p>At the border, pixels outside the image use the nearest edge pixel
 * (clamping), so a flat image gives 0 everywhere, borders included.
 * Normal object outlines give strong edges. This is not a sign of editing.
 * {@link LocalAnomaly} uses the per-tile values to explain tile boundaries.</p>
 */
public class Edges {

    /** Biggest possible strength: {@code sqrt(2) * 4 * 255}. */
    public static final double MAX_STRENGTH = Math.sqrt(2) * 4 * 255;

    /** Gray value with the coordinates clamped into the image. */
    static int at(int[][] gray, int x, int y) {
        int height = gray.length;
        int width = gray[0].length;
        x = Math.max(0, Math.min(width - 1, x));
        y = Math.max(0, Math.min(height - 1, y));
        return gray[y][x];
    }

    /**
     * Horizontal Sobel gradient {@code gx} (finds vertical edges).
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x    pixel column
     * @param y    pixel row
     * @return right column sum minus left column sum
     */
    public static int gradientX(int[][] gray, int x, int y) {
        int right = at(gray, x + 1, y - 1) + 2 * at(gray, x + 1, y) + at(gray, x + 1, y + 1);
        int left = at(gray, x - 1, y - 1) + 2 * at(gray, x - 1, y) + at(gray, x - 1, y + 1);
        return right - left;
    }

    /**
     * Vertical Sobel gradient {@code gy} (finds horizontal edges).
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x    pixel column
     * @param y    pixel row
     * @return bottom row sum minus top row sum
     */
    public static int gradientY(int[][] gray, int x, int y) {
        int bottom = at(gray, x - 1, y + 1) + 2 * at(gray, x, y + 1) + at(gray, x + 1, y + 1);
        int top = at(gray, x - 1, y - 1) + 2 * at(gray, x, y - 1) + at(gray, x + 1, y - 1);
        return bottom - top;
    }

    /**
     * Edge strength of one pixel: {@code sqrt(gx * gx + gy * gy)}.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param x    pixel column
     * @param y    pixel row
     * @return the strength, from 0 to {@link #MAX_STRENGTH}
     */
    public static double strength(int[][] gray, int x, int y) {
        int gx = gradientX(gray, x, y);
        int gy = gradientY(gray, x, y);
        return Math.sqrt(gx * gx + gy * gy);
    }

    /**
     * Average edge strength inside one rectangle.
     *
     * @param gray gray values as {@code gray[y][x]}
     * @param r    the rectangle, in pixels; it must be inside the image
     * @return the mean strength
     */
    public static double meanStrength(int[][] gray, Rectangle r) {
        double sum = 0;
        for (int y = r.y; y < r.y + r.height; y++) {
            for (int x = r.x; x < r.x + r.width; x++) {
                sum = sum + strength(gray, x, y);
            }
        }
        return sum / (r.width * r.height);
    }

    /**
     * Average edge strength of every tile.
     *
     * @param gray    gray values as {@code gray[y][x]}
     * @param regions the tile grid
     * @return the mean strength of each tile, as {@code [row][col]}
     */
    public static double[][] tileStrength(int[][] gray, Regions regions) {
        double[][] result = new double[regions.rows][regions.cols];
        for (int row = 0; row < regions.rows; row++) {
            for (int col = 0; col < regions.cols; col++) {
                result[row][col] = meanStrength(gray, regions.bounds(row, col));
            }
        }
        return result;
    }

    /**
     * Draws the edges as a gray picture.
     * Black means no edge. White means the strongest possible edge
     * ({@link #MAX_STRENGTH}).
     *
     * @param gray gray values as {@code gray[y][x]}
     * @return a gray image of the same size
     */
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
