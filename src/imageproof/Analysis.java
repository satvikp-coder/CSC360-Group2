package imageproof;

import java.awt.image.BufferedImage;

// All measurements for one image. No Swing here, so it can be tested headless.
public class Analysis {

    public int width;
    public int height;
    public int[][] gray;
    public double[] histogram;
    public double mean;
    public double standardDeviation;
    public double entropy;
    public Regions regions;

    public static Analysis run(BufferedImage image) {
        Analysis a = new Analysis();
        a.width = image.getWidth();
        a.height = image.getHeight();
        a.gray = Histogram.grayPixels(image);
        a.histogram = Histogram.normalize(Histogram.count(a.gray));
        a.mean = Histogram.mean(a.histogram);
        a.standardDeviation = Histogram.standardDeviation(a.histogram);
        a.entropy = Histogram.entropy(a.histogram);
        a.regions = Regions.analyze(a.gray, Regions.TILE);
        return a;
    }
}
