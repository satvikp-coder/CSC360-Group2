package imageproof;

/** Mean TVD to existing side-neighbors: local statistical variation, including normal boundaries. */
public final class RegionAnalyzer {
    private RegionAnalyzer() {}
    public record Region(int x, int y, int width, int height, double[] histogram,
                         Histogram.Statistics statistics, double variation) {}
    public record Result(int columns, int rows, int blockSize, Region[] regions) {
        public Region at(int row, int column) { return regions[row * columns + column]; }
    }

    public static Result analyze(int[] gray, int width, int height, int blockSize) {
        ImageAnalyzer.validatePixels(gray, width, height);
        if (blockSize < 16 || blockSize > 256) throw new IllegalArgumentException("Tile size must be 16–256");
        int columns = (width + blockSize - 1) / blockSize, rows = (height + blockSize - 1) / blockSize;
        Region[] regions = new Region[columns * rows];
        for (int row = 0; row < rows; row++) {
            ImageAnalyzer.checkCancelled();
            for (int column = 0; column < columns; column++) {
                int x = column * blockSize, y = row * blockSize;
                int w = Math.min(blockSize, width - x), h = Math.min(blockSize, height - y);
                int[] counts = new int[256];
                for (int dy = 0; dy < h; dy++) for (int dx = 0; dx < w; dx++) counts[gray[(y + dy) * width + x + dx]]++;
                double[] histogram = Histogram.normalize(counts);
                regions[row * columns + column] = new Region(x, y, w, h, histogram, Histogram.statistics(histogram), 0);
            }
        }
        int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
        Region[] measured = new Region[regions.length];
        for (int row = 0; row < rows; row++) for (int column = 0; column < columns; column++) {
            Region region = regions[row * columns + column];
            double sum = 0; int neighbors = 0;
            for (int i = 0; i < 4; i++) {
                int r = row + dr[i], c = column + dc[i];
                if (r >= 0 && r < rows && c >= 0 && c < columns) {
                    sum += Comparison.difference(region.histogram(), regions[r * columns + c].histogram()); neighbors++;
                }
            }
            measured[row * columns + column] = new Region(region.x(), region.y(), region.width(), region.height(),
                    region.histogram(), region.statistics(), neighbors == 0 ? 0 : sum / neighbors);
        }
        return new Result(columns, rows, blockSize, measured);
    }
}
