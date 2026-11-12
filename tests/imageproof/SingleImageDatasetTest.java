package imageproof;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

public final class SingleImageDatasetTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("imageproof-single-fixtures-");
        try {
            SingleImageDataset.generate(scratch);
            Path baseline = Path.of(System.getProperty("imageproof.basedir", "."), "samples", "single-image");
            for (String name : SingleImageDataset.FILES) {
                if (name.equals("corrupt.png")) continue;
                BufferedImage fresh = ImageIO.read(scratch.resolve(name).toFile()), committed = ImageIO.read(baseline.resolve(name).toFile());
                check(fresh.getWidth() == committed.getWidth() && fresh.getHeight() == committed.getHeight(), "fixture dimensions");
                check(Arrays.equals(Histogram.grayPixels(fresh), Histogram.grayPixels(committed)), "fixture decoded intensities: " + name);
            }
            BufferedImage copy = ImageIO.read(scratch.resolve("copied-texture.png").toFile());
            for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++)
                check(copy.getRGB(x, y) == copy.getRGB(x + 64, y + 64), "copy ground truth");
            AnalysisResult result = ImageAnalyzer.analyze(copy, 64);
            check(result.copies().matches().stream().filter(m -> m.x2() - m.x1() == 64 && m.y2() - m.y1() == 64).count() >= 3, "copied region found");
            BufferedImage repeated = ImageIO.read(scratch.resolve("repeated-pattern.png").toFile());
            check(!ImageAnalyzer.analyze(repeated, 64).copies().matches().isEmpty(), "record repeated-pattern false positives");
            BufferedImage inserted = ImageIO.read(scratch.resolve("inserted-object.png").toFile());
            check((inserted.getRGB(40, 32) & 0xffffff) == 0xffcc33 && (inserted.getRGB(71, 55) & 0xffffff) == 0xffcc33, "inserted ground truth");
            try { ImageAnalyzer.load(scratch.resolve("corrupt.png").toFile()); throw new AssertionError("Corrupt fixture accepted"); }
            catch (java.io.IOException expected) {}
            System.out.println("SingleImageDatasetTest OK: regeneration, 1024 copied pixels, candidate ground truth and repeated-pattern limitation");
        } finally {
            try (var paths = Files.walk(scratch)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
