package imageproof;

import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Calls actual application math only; no fallback scoring implementation. */
public final class EvaluateDataset {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: EvaluateDataset dataset-directory");
        Class<?> histogram, comparison;
        try {
            histogram = Class.forName("imageproof.Histogram");
            comparison = Class.forName("imageproof.Comparison");
        } catch (ClassNotFoundException missing) {
            System.err.println("BLOCKED: real Histogram and Comparison implementations required; no scores produced.");
            System.exit(2); return;
        }
        // Planned architecture API. Adapt this adapter if teammates finalize a different API.
        Method compute = histogram.getMethod("compute", BufferedImage.class);
        Method normalize = histogram.getMethod("normalize", int[].class);
        Method difference = comparison.getMethod("difference", double[].class, double[].class);
        Path directory = Path.of(args[0]);
        double[] reference = normalized(directory.resolve("original.png"), compute, normalize);
        StringBuilder results = new StringBuilder("case,distance\n");
        for (String name : DatasetGenerator.FILES) {
            if (name.equals("original.png") || name.endsWith(".txt")) continue;
            double[] values = normalized(directory.resolve(name), compute, normalize);
            double score = ((Number) difference.invoke(null, reference, values)).doubleValue();
            if (!Double.isFinite(score) || score < -1e-9 || score > 1 + 1e-9)
                throw new AssertionError("Invalid distance: " + name);
            double reverse = ((Number) difference.invoke(null, values, reference)).doubleValue();
            if (Math.abs(score - reverse) > 1e-9) throw new AssertionError("Asymmetric distance");
            if ((name.equals("identical.png") || name.equals("shuffled.png")) && Math.abs(score) > 1e-12)
                throw new AssertionError("Expected zero for " + name);
            if (name.equals("shuffled.png") && !Arrays.equals(reference, values))
                throw new AssertionError("Shuffled histogram differs");
            results.append(name).append(',').append(String.format(Locale.ROOT, "%.17g", score)).append('\n');
        }
        if (ImageIO.read(directory.resolve("unsupported.txt").toFile()) != null)
            throw new AssertionError("Unsupported input unexpectedly decoded");
        // Print only after every invariant succeeds; redirect stdout to preserve actual measurements.
        System.out.print(results);
    }
    private static double[] normalized(Path path, Method compute, Method normalize) throws Exception {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) throw new IllegalArgumentException("Unsupported image: " + path);
        double[] bins = (double[]) normalize.invoke(null, compute.invoke(null, image));
        if (bins.length != 256) throw new AssertionError("Expected 256 bins");
        double sum = 0;
        for (double value : bins) {
            if (!Double.isFinite(value) || value < 0) throw new AssertionError("Invalid bin");
            sum += value;
        }
        if (Math.abs(sum - 1) > 1e-9) throw new AssertionError("Not normalized");
        return bins;
    }
}
