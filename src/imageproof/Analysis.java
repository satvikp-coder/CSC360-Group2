package imageproof;

import java.awt.image.BufferedImage;

/**
 * All measurements for one image, collected in one place.
 *
 * <p>{@link #run(BufferedImage, String)} runs the whole pipeline in this order:
 * {@link Histogram}, {@link Regions}, {@link Noise}, {@link Edges},
 * {@link CopyMove}, and last {@link TamperingAssessment}
 * (which uses {@link Comparison}, {@link LocalAnomaly} and {@link FlatPatch}
 * on the finished measurements). {@link App} then shows the result.</p>
 *
 * <p>This class has no Swing code, so it can be tested without a screen.
 * The results are measurements for study. They are not proof of editing
 * and not proof that an image is genuine.</p>
 */
public class Analysis {

    /** Image width in pixels. */
    public int width;
    /** Image height in pixels. */
    public int height;
    /** Gray value (0 to 255) of every pixel, stored as {@code gray[y][x]}. */
    public int[][] gray;
    /** Normalized 256-bin histogram of the gray values. The bins add up to 1. */
    public double[] histogram;
    /** Average gray value of the whole image. */
    public double mean;
    /** Population standard deviation of the gray values. */
    public double standardDeviation;
    /** Shannon entropy of the gray values in bits (0 to 8). */
    public double entropy;
    /** The tile grid with a histogram and a variation value for each tile. */
    public Regions regions;
    /** Noise residual variance per tile, as {@code [row][col]}. */
    public double[][] noise;
    /** Mean Sobel edge strength per tile, as {@code [row][col]}. */
    public double[][] edges;
    /** Sobel edge strength drawn as a gray picture. */
    public BufferedImage edgeMap;
    /** Copy-move candidates (repeated blocks that moved by the same shift). */
    public CopyMove copyMove;
    /** Cautious rule applied to the finished measurements. */
    public TamperingAssessment assessment;

    /** True if the file was a JPEG (flat areas are normal there). */
    public boolean jpeg;

    /**
     * Runs all measurements when the file format is not known.
     *
     * @param image the image to measure
     * @return the finished analysis
     */
    public static Analysis run(BufferedImage image) {
        return run(image, "");
    }

    /**
     * Runs all measurements on one image.
     *
     * @param image  the image to measure
     * @param format the file format name from {@link LoadedImage} ("PNG" or
     *               "JPEG"), or "" if unknown. A name starting with "JPEG"
     *               (any letter case) sets {@link #jpeg}.
     * @return the finished analysis
     * @throws IllegalArgumentException if the image is {@code null} or
     *         bigger than {@link Histogram#MAX_PIXELS}
     */
    public static Analysis run(BufferedImage image, String format) {
        Analysis a = new Analysis();
        a.jpeg = format != null && format.toUpperCase().startsWith("JPEG");
        a.width = image.getWidth();
        a.height = image.getHeight();
        a.gray = Histogram.grayPixels(image);
        a.histogram = Histogram.normalize(Histogram.count(a.gray));
        a.mean = Histogram.mean(a.histogram);
        a.standardDeviation = Histogram.standardDeviation(a.histogram);
        a.entropy = Histogram.entropy(a.histogram);
        a.regions = Regions.analyze(a.gray, Regions.TILE);
        a.noise = Noise.tileVariance(a.gray, a.regions);
        a.edges = Edges.tileStrength(a.gray, a.regions);
        a.edgeMap = Edges.map(a.gray);
        a.copyMove = CopyMove.analyze(a.gray);
        a.assessment = TamperingAssessment.evaluate(a);
        return a;
    }
}
