package imageproof;
import java.awt.image.BufferedImage;

/** Completed measurements, published to Swing only when the worker is still current. */
public record AnalysisResult(double[] histogram, Histogram.Statistics statistics,
        RegionAnalyzer.Result regions, double[] residualVariance,
        BufferedImage edges, CopyMoveAnalyzer.Result copies) {}
