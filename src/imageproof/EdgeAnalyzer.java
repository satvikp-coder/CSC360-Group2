package imageproof;

import java.awt.image.BufferedImage;

/** Sobel with clamped coordinates at borders. Strong normal object boundaries are expected. */
public final class EdgeAnalyzer {
    private EdgeAnalyzer() {}
    public static int[] gradient(int[] gray, int width, int height, int x, int y) {
        int gx = 0, gy = 0;
        for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
            int value = gray[Math.max(0, Math.min(height - 1, y + dy)) * width
                    + Math.max(0, Math.min(width - 1, x + dx))];
            gx += value * dx * (dy == 0 ? 2 : 1); gy += value * dy * (dx == 0 ? 2 : 1);
        }
        return new int[]{gx, gy};
    }
    public static BufferedImage analyze(int[] gray, int width, int height) {
        ImageAnalyzer.validatePixels(gray, width, height);
        BufferedImage map = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            ImageAnalyzer.checkCancelled();
            for (int x = 0; x < width; x++) {
                int[] gradient = gradient(gray, width, height, x, y);
                int value = (int) Math.round(Math.min(255, Math.hypot(gradient[0], gradient[1]) / (4 * Math.sqrt(2))));
                map.setRGB(x, y, value * 0x010101);
            }
        }
        return map;
    }
}
