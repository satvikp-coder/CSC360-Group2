package imageproof;

/** Grayscale minus available 3×3 mean; regional population variance in intensity squared. */
public final class NoiseAnalyzer {
    private NoiseAnalyzer() {}
    public static double[] analyze(int[] gray, int width, int height, RegionAnalyzer.Result regions) {
        ImageAnalyzer.validatePixels(gray, width, height);
        double[] variance = new double[regions.regions().length];
        for (int i = 0; i < variance.length; i++) {
            ImageAnalyzer.checkCancelled();
            RegionAnalyzer.Region region = regions.regions()[i];
            double mean = 0, squared = 0; int count = 0;
            for (int y = region.y(); y < region.y() + region.height(); y++)
                for (int x = region.x(); x < region.x() + region.width(); x++) {
                    int sum = 0, samples = 0;
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        int xx = x + dx, yy = y + dy;
                        if (xx >= 0 && yy >= 0 && xx < width && yy < height) { sum += gray[yy * width + xx]; samples++; }
                    }
                    double residual = gray[y * width + x] - (double) sum / samples;
                    // Welford population variance avoids a full residual image and cancellation error.
                    count++; double delta = residual - mean;
                    mean += delta / count; squared += delta * (residual - mean);
                }
            variance[i] = Math.max(0, squared / count);
        }
        return variance;
    }
}
