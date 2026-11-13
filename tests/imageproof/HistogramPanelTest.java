package imageproof;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

// Draws the panel into an image (no window needed) and counts the bar pixels.
public class HistogramPanelTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static BufferedImage render(HistogramPanel panel, int width, int height) {
        panel.setSize(width, height);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        panel.paint(g);
        g.dispose();
        return image;
    }

    static int barPixels(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == HistogramPanel.BAR.getRGB()) {
                    count++;
                }
            }
        }
        return count;
    }

    public static void main(String[] args) {
        HistogramPanel panel = new HistogramPanel("Test");
        // Plot area for a 532 x 255 panel: 512 wide (2 px per bin), 200 high.
        int width = 532;
        int height = 255;
        check(barPixels(render(panel, width, height)) == 0, "empty panel has no bars");

        // One full bin: 2 px wide and the full 200 px high.
        double[] spike = new double[256];
        spike[128] = 1;
        panel.setValues(spike);
        BufferedImage image = render(panel, width, height);
        check(barPixels(image) == 2 * 200, "one bar 2 x 200");
        check(image.getRGB(HistogramPanel.LEFT + 256, HistogramPanel.TOP) == HistogramPanel.BAR.getRGB(), "bar at bin 128");

        // Two bins, one twice as tall as the other.
        double[] two = new double[256];
        two[0] = 1.0 / 3;
        two[255] = 2.0 / 3;
        panel.setValues(two);
        check(barPixels(render(panel, width, height)) == 2 * 100 + 2 * 200, "half and full bars");

        // Every bin is still visible on a narrow panel.
        for (int bin = 0; bin < 256; bin++) {
            double[] one = new double[256];
            one[bin] = 1;
            panel.setValues(one);
            check(barPixels(render(panel, 60, 120)) > 0, "bin " + bin + " visible");
        }

        // Tiny panel does not crash; clearing removes the bars.
        render(panel, 1, 1);
        panel.setValues(null);
        check(barPixels(render(panel, width, height)) == 0, "cleared");

        try {
            panel.setValues(new double[10]);
            throw new AssertionError("wrong length accepted");
        } catch (IllegalArgumentException expected) {
        }
        System.out.println("HistogramPanelTest OK");
    }
}
