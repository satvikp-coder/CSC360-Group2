package imageproof;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

/** No test framework required; failures throw AssertionError. */
public final class HistogramPanelTest {
    private static int checks;
    private static void check(boolean condition) {
        checks++;
        if (!condition) throw new AssertionError("Check " + checks);
    }
    private static BufferedImage render(HistogramPanel panel, int w, int h) {
        panel.setSize(w, h);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        panel.paint(graphics);
        graphics.dispose();
        return image;
    }
    private static int bars(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++)
                if (image.getRGB(x, y) == HistogramPanel.BAR.getRGB()) count++;
        return count;
    }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            HistogramPanel p = new HistogramPanel("Original image");
            check(bars(render(p, 574, 302)) == 0);
            p.setValues(null);
            check(bars(render(p, 574, 302)) == 0);
            double[] v = new double[256]; v[128] = 1;
            p.setValues(v); v[128] = 0;
            BufferedImage spike = render(p, 574, 302);
            check(spike.getRGB(304, 56) == HistogramPanel.BAR.getRGB());
            check(spike.getRGB(303, 56) != HistogramPanel.BAR.getRGB());
            check(bars(spike) == 400);
            check(bars(render(p, 90, 150)) > 0);
            check(bars(render(p, 1, 1)) == 0);
            check(bars(render(p, 574, 302)) == 400);
            v[0] = .25; v[255] = .75; p.setValues(v);
            BufferedImage multi = render(p, 574, 302);
            check(multi.getRGB(48, 206) == HistogramPanel.BAR.getRGB());
            check(multi.getRGB(558, 106) == HistogramPanel.BAR.getRGB());
            HistogramPanel q = new HistogramPanel("Comparison image");
            q.setValues(v); p.setMaximumFrequency(.75); q.setMaximumFrequency(.75);
            check(bars(render(p, 574, 302)) == bars(render(q, 574, 302)));
            check(bars(render(p, 574, 302)) > bars(multi));
            p.clearData(); check(bars(render(p, 574, 302)) == 0);
            p.setValues(new double[256]); check(bars(render(p, 574, 302)) == 0);
            double[][] invalid = {new double[1], new double[256], new double[256],
                    new double[256], new double[256], new double[256]};
            invalid[1][0] = -1; invalid[2][0] = Double.NaN;
            invalid[3][0] = Double.POSITIVE_INFINITY; invalid[4][0] = 2;
            invalid[5][0] = .5;
            for (double[] bad : invalid) {
                p.setValues(v);
                try { p.setValues(bad); throw new AssertionError("Accepted invalid input"); }
                catch (IllegalArgumentException expected) { check(bars(render(p, 574, 302)) == 0); }
            }
            for (double bad : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 2}) {
                try { p.setMaximumFrequency(bad); throw new AssertionError("Accepted bad scale"); }
                catch (IllegalArgumentException expected) { check(true); }
            }
            // Every bin survives at a graph width of just 28 pixels.
            for (int bin = 0; bin < 256; bin++) {
                double[] single = new double[256]; single[bin] = 1; p.setValues(single);
                check(bars(render(p, 90, 150)) > 0);
                BufferedImage full = render(p, 574, 302);
                check(full.getRGB(48 + 2 * bin, 56) == HistogramPanel.BAR.getRGB());
                check(bars(full) == 400);
            }
            // Common scale must preserve equal frequencies even with different peaks.
            double[] a = new double[256], b = new double[256];
            a[10] = .25; a[20] = .75;
            b[10] = .25; b[30] = .25; b[40] = .5;
            p.setValues(a); q.setValues(b);
            p.setMaximumFrequency(1); q.setMaximumFrequency(1);
            BufferedImage first = render(p, 574, 302), second = render(q, 574, 302);
            for (int y = 56; y < 256; y++)
                check(first.getRGB(68, y) == second.getRGB(68, y));
            p.setMaximumFrequency(Double.MIN_VALUE);
            check(bars(render(p, 574, 302)) == 800); // Safe clipping, even for tiny scale.
            p.setValues(null); check(bars(render(p, 574, 302)) == 0);
            p.setValues(a); p.setValues(new double[256]);
            check(bars(render(p, 574, 302)) == 0); // Zero replacement removes old bars.
            p.setMaximumFrequency(1);
            p.setValues(a);
            p.setBorder(javax.swing.BorderFactory.createEmptyBorder(7, 11, 13, 17));
            BufferedImage inset = render(p, 574, 302);
            for (int y = 0; y < 302; y++) for (int x = 0; x < 574; x++) {
                if (inset.getRGB(x, y) == HistogramPanel.BAR.getRGB())
                    check(x >= 11 && x < 557 && y >= 7 && y < 289);
            }
        });
        System.out.println("HistogramPanelTest: " + checks + " checks passed");
    }
}
