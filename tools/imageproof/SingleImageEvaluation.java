package imageproof;

import java.nio.file.*;
import java.util.*;

/** Prints actual module measurements. There is deliberately no accuracy or editing probability. */
public final class SingleImageEvaluation {
    public static void main(String[] args) throws Exception {
        Path root = args.length == 0 ? Path.of(".") : Path.of(args[0]);
        System.out.println("file,width,height,mean,sd,entropy,max_neighbor_tvd,mean_residual_variance,copy_pairs,truncated");
        for (String directory : new String[]{"generated", "single-image"}) {
            java.util.List<String> files = directory.equals("generated") ? DatasetGenerator.FILES : SingleImageDataset.FILES;
            for (String name : files) {
                Path file = root.resolve("samples").resolve(directory).resolve(name);
                if (name.equals("corrupt.png") || name.equals("unsupported.txt")) {
                    try { ImageAnalyzer.load(file.toFile()); throw new AssertionError("Invalid fixture decoded"); }
                    catch (java.io.IOException expected) { System.out.println(name + ",REJECTED"); }
                    continue;
                }
                ImageAnalyzer.Loaded loaded = ImageAnalyzer.load(file.toFile());
                AnalysisResult result = ImageAnalyzer.analyze(loaded.image(), 64);
                double maximum = 0, residual = 0;
                for (RegionAnalyzer.Region region : result.regions().regions()) maximum = Math.max(maximum, region.variation());
                for (double value : result.residualVariance()) residual += value;
                System.out.printf(Locale.ROOT, "%s,%d,%d,%.6f,%.6f,%.6f,%.6f,%.6f,%d,%s%n", name,
                        loaded.image().getWidth(), loaded.image().getHeight(), result.statistics().mean(),
                        result.statistics().standardDeviation(), result.statistics().entropy(), maximum,
                        residual / result.residualVariance().length, result.copies().matches().size(), result.copies().truncated());
            }
        }
    }
}
