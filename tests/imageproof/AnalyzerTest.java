package imageproof;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Deterministic mathematical and input fixtures; no graphical display needed. */
public final class AnalyzerTest {
    private static int checks;
    static void check(boolean condition, String message) {
        checks++; if (!condition) throw new AssertionError(message);
    }
    static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-9, message + ": " + actual); }
    static void rejects(Runnable operation) {
        try { operation.run(); throw new AssertionError("Expected invalid input rejection"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    public static BufferedImage copiedTexture() {
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(360);
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) image.setRGB(x, y, random.nextInt(256) * 0x010101);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) image.setRGB(x + 64, y + 64, image.getRGB(x, y));
        return image;
    }
    public static void main(String[] args) throws Exception {
        check(Histogram.toGray(0xff0000) == 76 && Histogram.toGray(0x00ff00) == 150
                && Histogram.toGray(0x0000ff) == 29, "primary rounding");
        rejects(() -> Histogram.compute(null)); rejects(() -> Histogram.normalize(null));
        rejects(() -> Histogram.normalize(new int[255])); rejects(() -> Histogram.normalize(new int[256]));
        int[] bad = new int[256]; bad[0] = -1; rejects(() -> Histogram.normalize(bad));
        int[] huge = new int[256]; huge[0] = huge[255] = Integer.MAX_VALUE;
        double[] binary = Histogram.normalize(huge);
        near(binary[0], .5, "long total");
        Histogram.Statistics stats = Histogram.statistics(binary);
        near(stats.mean(), 127.5, "mean"); near(stats.standardDeviation(), 127.5, "population sd"); near(stats.entropy(), 1, "entropy bits");
        for (int type : new int[]{BufferedImage.TYPE_BYTE_GRAY, BufferedImage.TYPE_USHORT_GRAY}) {
            BufferedImage image = new BufferedImage(1, 1, type);
            image.getRaster().setSample(0, 0, 0, type == BufferedImage.TYPE_BYTE_GRAY ? 100 : 25700);
            check(Histogram.compute(image)[100] == 1, "encoded gray convention");
            BufferedImage rgb = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB); rgb.setRGB(0, 0, 0x646464);
            near(Comparison.difference(Histogram.normalize(Histogram.compute(image)), Histogram.normalize(Histogram.compute(rgb))), 0, "gray RGB equivalence");
        }
        BufferedImage alpha = new BufferedImage(3, 1, BufferedImage.TYPE_INT_ARGB);
        java.awt.image.ComponentColorModel grayAlphaModel = new java.awt.image.ComponentColorModel(
                java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY), true, false,
                java.awt.Transparency.TRANSLUCENT, java.awt.image.DataBuffer.TYPE_BYTE);
        java.awt.image.WritableRaster grayAlphaRaster = java.awt.image.Raster.createInterleavedRaster(
                java.awt.image.DataBuffer.TYPE_BYTE, 1, 1, 2, null);
        grayAlphaRaster.setPixel(0, 0, new int[]{100, 128});
        BufferedImage grayAlpha = new BufferedImage(grayAlphaModel, grayAlphaRaster, false, null);
        check(Histogram.grayPixels(grayAlpha)[0] == 177, "gray-alpha uses encoded sample before white composition");
        alpha.setRGB(0, 0, 0x00ffffff); alpha.setRGB(1, 0, 0x00000000); alpha.setRGB(2, 0, 0x80000000);
        int[] gray = Histogram.grayPixels(alpha);
        check(gray[0] == 255 && gray[1] == 255 && gray[2] == 127, "white alpha composition");
        for (int type : new int[]{BufferedImage.TYPE_INT_ARGB_PRE, BufferedImage.TYPE_4BYTE_ABGR}) {
            BufferedImage alternate = new BufferedImage(3, 1, type);
            alternate.setRGB(0, 0, 0x00ffffff); alternate.setRGB(1, 0, 0x00000000); alternate.setRGB(2, 0, 0x80000000);
            check(Arrays.equals(gray, Histogram.grayPixels(alternate)), "premultiplied alpha");
        }
        int[] tiles = new int[32 * 16];
        for (int y = 0; y < 16; y++) Arrays.fill(tiles, y * 32 + 16, y * 32 + 32, 255);
        RegionAnalyzer.Result regions = RegionAnalyzer.analyze(tiles, 32, 16, 16);
        near(regions.at(0, 0).variation(), 1, "disjoint neighbors"); near(regions.at(0, 1).variation(), 1, "symmetric neighbors");
        regions = RegionAnalyzer.analyze(new int[65 * 65], 65, 65, 64);
        check(regions.columns() == 2 && regions.rows() == 2 && regions.at(1, 1).width() == 1, "partial edges");
        int pixels = 0;
        for (RegionAnalyzer.Region region : regions.regions()) { pixels += region.width() * region.height(); near(region.variation(), 0, "same neighboring distribution"); }
        check(pixels == 65 * 65, "all pixels counted");
        rejects(() -> RegionAnalyzer.analyze(new int[1], 1, 2, 64));
        rejects(() -> RegionAnalyzer.analyze(new int[1], 1, 1, 0));
        regions = RegionAnalyzer.analyze(new int[]{0, 255}, 2, 1, 64);
        near(NoiseAnalyzer.analyze(new int[]{0, 255}, 2, 1, regions)[0], 16256.25, "two-pixel residual variance");
        int[] ramp = {0, 10, 20, 0, 10, 20, 0, 10, 20};
        int[] gradient = EdgeAnalyzer.gradient(ramp, 3, 3, 1, 1);
        check(gradient[0] == 80 && gradient[1] == 0, "hand-calculated Sobel");
        AnalysisResult tiny = ImageAnalyzer.analyze(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), 64);
        near(tiny.residualVariance()[0], 0, "tiny residual");
        check((tiny.edges().getRGB(0, 0) & 0xffffff) == 0, "tiny edge");
        near(tiny.regions().regions()[0].variation(), 0, "no neighbors");
        check(tiny.copies().matches().isEmpty(), "tiny copies");
        BufferedImage copied = copiedTexture();
        CopyMoveAnalyzer.Result copies = CopyMoveAnalyzer.analyze(Histogram.grayPixels(copied), 96, 96);
        check(copies.matches().stream().filter(m -> m.x2() - m.x1() == 64 && m.y2() - m.y1() == 64).count() >= 3,
                "coherent known copied object");
        check(CopyMoveAnalyzer.analyze(new int[128 * 128], 128, 128).matches().isEmpty(), "ignore flat blocks");
        int[] repeated = new int[128 * 128];
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) repeated[y * 128 + x] = (x % 8) * 20 + (y % 8) * 10;
        copies = CopyMoveAnalyzer.analyze(repeated, 128, 128);
        check(!copies.matches().isEmpty(), "repeated-pattern false positives are real");
        check(copies.matches().size() <= 256 && copies.comparisons() <= CopyMoveAnalyzer.MAX_COMPARISONS, "bounded results");
        for (CopyMoveAnalyzer.Match match : copies.matches())
            check(Math.abs(match.x2() - match.x1()) >= 32 || Math.abs(match.y2() - match.y1()) >= 32, "exclude neighbors");
        Path invalid = Files.createTempFile("imageproof-invalid-", ".png");
        try {
            Files.writeString(invalid, "not an image");
            try { ImageAnalyzer.load(invalid.toFile()); throw new AssertionError("Corrupt decoded"); } catch (java.io.IOException expected) { checks++; }
            try { ImageAnalyzer.load(invalid.resolveSibling("missing-image.png").toFile()); throw new AssertionError("Missing decoded"); } catch (java.io.IOException expected) { checks++; }
            javax.imageio.ImageIO.write(alpha, "png", invalid.toFile());
            ImageAnalyzer.Loaded loaded = ImageAnalyzer.load(invalid.toFile());
            check(loaded.format().equals("PNG") && loaded.image().getWidth() == 3, "PNG decoded");
        } finally { Files.delete(invalid); }
        Thread.currentThread().interrupt();
        try { ImageAnalyzer.analyze(copied, 64); throw new AssertionError("Cancellation ignored"); }
        catch (java.util.concurrent.CancellationException expected) { checks++; }
        finally { Thread.interrupted(); }
        System.out.println("AnalyzerTest: " + checks + " checks passed");
    }
}
