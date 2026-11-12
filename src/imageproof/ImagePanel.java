package imageproof;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.function.IntConsumer;
import javax.swing.JPanel;

/** One shared image transform for drawing, overlays and hit-testing. */
public final class ImagePanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private transient BufferedImage image;
    private transient AnalysisResult result;
    private int selected = -1;
    private boolean overlay = true;
    private float opacity = .45f;
    private String view = "Original";
    private transient IntConsumer selection = index -> {};

    public ImagePanel() {
        setBackground(new Color(235, 238, 242));
        setPreferredSize(new Dimension(620, 450));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                int index = regionAt(event.getPoint());
                if (index >= 0) { selected = index; selection.accept(index); repaint(); }
            }
        });
    }
    public void onSelection(IntConsumer listener) { selection = listener; }
    public void setImage(BufferedImage image) { this.image = image; result = null; selected = -1; repaint(); }
    public void setResult(AnalysisResult result) { this.result = result; selected = -1; repaint(); }
    public void setOverlay(boolean visible) { overlay = visible; repaint(); }
    public void setOpacity(float value) { opacity = Math.max(0, Math.min(1, value)); repaint(); }
    public void setView(String view) { this.view = view; repaint(); }
    public Rectangle imageBounds() {
        if (image == null) return new Rectangle();
        double scale = Math.min((double) getWidth() / image.getWidth(), (double) getHeight() / image.getHeight());
        int w = Math.max(1, (int) (image.getWidth() * scale)), h = Math.max(1, (int) (image.getHeight() * scale));
        return new Rectangle((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
    }
    public int regionAt(Point point) {
        if (image == null || result == null) return -1;
        Rectangle bounds = imageBounds();
        if (!bounds.contains(point)) return -1;
        int x = (int) ((long) (point.x - bounds.x) * image.getWidth() / bounds.width);
        int y = (int) ((long) (point.y - bounds.y) * image.getHeight() / bounds.height);
        return (y / result.regions().blockSize()) * result.regions().columns() + x / result.regions().blockSize();
    }
    public static Color heatColor(double value) {
        return Color.getHSBColor((float) (.66 * (1 - Math.max(0, Math.min(1, value)))), .85f, 1);
    }
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            if (image == null) { g.setColor(Color.DARK_GRAY); g.drawString("Upload one JPEG or PNG to begin", 24, 36); return; }
            Rectangle bounds = imageBounds();
            g.setColor(Color.WHITE); g.fill(bounds);
            BufferedImage displayed = result != null && view.equals("Sobel edges") ? result.edges() : image;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(displayed, bounds.x, bounds.y, bounds.width, bounds.height, null);
            if (result == null) return;
            g.clip(bounds); g.translate(bounds.x, bounds.y);
            double sx = (double) bounds.width / image.getWidth(), sy = (double) bounds.height / image.getHeight();
            g.scale(sx, sy);
            if (overlay && !view.equals("Sobel edges") && !view.equals("Copy candidates")) {
                double maximum = 0;
                if (view.equals("Noise residual")) for (double value : result.residualVariance()) maximum = Math.max(maximum, value);
                g.setComposite(AlphaComposite.SrcOver.derive(opacity));
                for (int i = 0; i < result.regions().regions().length; i++) {
                    RegionAnalyzer.Region region = result.regions().regions()[i];
                    double value = view.equals("Noise residual") ? (maximum == 0 ? 0 : result.residualVariance()[i] / maximum) : region.variation();
                    g.setColor(heatColor(value)); g.fillRect(region.x(), region.y(), region.width(), region.height());
                }
                g.setComposite(AlphaComposite.SrcOver);
            }
            g.setStroke(new BasicStroke((float) (2 / Math.max(.01, Math.min(sx, sy)))));
            if (view.equals("Copy candidates")) for (CopyMoveAnalyzer.Match match : result.copies().matches()) {
                g.setColor(Color.CYAN); g.drawRect(match.x1(), match.y1(), match.size(), match.size());
                g.setColor(Color.MAGENTA); g.drawRect(match.x2(), match.y2(), match.size(), match.size());
            }
            if (selected >= 0) {
                RegionAnalyzer.Region region = result.regions().regions()[selected];
                g.setColor(Color.BLACK); g.drawRect(region.x(), region.y(), region.width(), region.height());
                g.setStroke(new BasicStroke((float) (1 / Math.max(.01, Math.min(sx, sy)))));
                g.setColor(Color.WHITE); g.drawRect(region.x(), region.y(), region.width(), region.height());
            }
        } finally { g.dispose(); }
    }
}
