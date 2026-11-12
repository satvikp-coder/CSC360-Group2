package imageproof;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.concurrent.CancellationException;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Offline analysis without Swing; dimensions are checked before decoding files. */
public final class ImageAnalyzer {
    public static final int MAX_PIXELS = 16000000;
    private ImageAnalyzer() {}
    public record Loaded(BufferedImage image, String format) {}
    public static Loaded load(File file) throws IOException {
        if (file == null || !file.isFile()) throw new IOException("Choose an existing JPEG or PNG file");
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("Cannot read selected file");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported or corrupt image; choose JPEG or PNG");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase(java.util.Locale.ROOT);
                if (!format.equals("JPEG") && !format.equals("PNG")) throw new IOException("Only JPEG and PNG are supported");
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS)
                    throw new IOException("Image must contain 1–16 million pixels; resize larger images first");
                checkCancelled();
                BufferedImage image = reader.read(0);
                if (image == null) throw new IOException("Image could not be decoded");
                return new Loaded(image, format);
            } finally { reader.dispose(); }
        }
    }
    public static AnalysisResult analyze(BufferedImage image, int blockSize) {
        int[] gray = Histogram.grayPixels(image);
        int width = image.getWidth(), height = image.getHeight();
        double[] histogram = Histogram.normalize(Histogram.counts(gray));
        RegionAnalyzer.Result regions = RegionAnalyzer.analyze(gray, width, height, blockSize);
        return new AnalysisResult(histogram, Histogram.statistics(histogram), regions,
                NoiseAnalyzer.analyze(gray, width, height, regions), EdgeAnalyzer.analyze(gray, width, height),
                CopyMoveAnalyzer.analyze(gray, width, height));
    }
    static void validatePixels(int[] gray, int width, int height) {
        if (gray == null || width < 1 || height < 1 || (long) width * height > MAX_PIXELS
                || (long) width * height != gray.length) throw new IllegalArgumentException("Invalid pixel dimensions");
        for (int value : gray) if (value < 0 || value > 255) throw new IllegalArgumentException("Gray values must be 0–255");
    }
    static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Analysis cancelled");
    }
}
