package imageproof;

import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;

public final class ImagePanelTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage image = new BufferedImage(65, 33, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 33; y++) for (int x = 32; x < 65; x++) image.setRGB(x, y, 0xffffff);
            AnalysisResult result = ImageAnalyzer.analyze(image, 32);
            ImagePanel panel = new ImagePanel(); panel.setImage(image); panel.setResult(result); panel.setOpacity(1);
            for (Dimension size : new Dimension[]{new Dimension(780, 440), new Dimension(200, 500), new Dimension(99, 63)}) {
                panel.setSize(size); Rectangle bounds = panel.imageBounds();
                BufferedImage rendered = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rendered.createGraphics(); panel.paint(graphics); graphics.dispose();
                for (int column = 0; column < 2; column++) {
                    int x = bounds.x + (int) ((column * 32 + 16.0) * bounds.width / 65);
                    int y = bounds.y + (int) (16.0 * bounds.height / 33);
                    check(panel.regionAt(new Point(x, y)) == column, "hit-test transform after resize");
                    check(rendered.getRGB(x, y) == ImagePanel.heatColor(result.regions().at(0, column).variation()).getRGB(), "overlay aligns with tile");
                }
                check(panel.regionAt(new Point(bounds.x - 1, bounds.y)) == -1, "letterbox is not a tile");
                check(panel.regionAt(new Point(bounds.x + bounds.width - 1, bounds.y + bounds.height - 1)) >= 0, "partial edge click valid");
            }
            panel.setResult(null); check(panel.regionAt(new Point(50, 50)) == -1, "no stale regions");
        });
        WorkerTickets tickets = new WorkerTickets();
        long oldLoad = tickets.nextLoad(), newLoad = tickets.nextLoad();
        check(!tickets.currentLoad(oldLoad) && tickets.currentLoad(newLoad), "late load cannot replace selection");
        long analysis = tickets.nextAnalysis(); tickets.nextLoad();
        check(!tickets.currentAnalysis(analysis), "loading replacement invalidates old analysis even if it fails");
        long first = tickets.nextAnalysis(), second = tickets.nextAnalysis();
        check(!tickets.currentAnalysis(first) && tickets.currentAnalysis(second), "only newest analysis can publish");
        System.out.println("ImagePanelTest OK: alignment, letterboxing, partial edges and stale-worker tickets");
    }
}
