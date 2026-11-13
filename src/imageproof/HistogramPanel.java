package imageproof;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import javax.swing.JPanel;

// Draws a normalized 256-bin histogram as bars. The tallest bar fills the plot height.
public class HistogramPanel extends JPanel {

    public static final Color BAR = new Color(35, 105, 175);

    // Space around the plot for text.
    static final int LEFT = 10;
    static final int TOP = 30;
    static final int BOTTOM = 25;

    private String title;
    private double[] values;

    public HistogramPanel(String title) {
        this.title = title;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(400, 220));
    }

    // Show new values (256 bins adding to 1). null clears the plot.
    public void setValues(double[] p) {
        if (p != null && p.length != Histogram.BINS) {
            throw new IllegalArgumentException("Need 256 bins");
        }
        values = p;
        repaint();
    }

    public void setTitle(String title) {
        this.title = title;
        repaint();
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int plotWidth = getWidth() - 2 * LEFT;
        int plotHeight = getHeight() - TOP - BOTTOM;
        g.setColor(Color.DARK_GRAY);
        g.drawString(title, LEFT, 18);
        if (plotWidth <= 0 || plotHeight <= 0) {
            return;
        }
        g.drawRect(LEFT - 1, TOP - 1, plotWidth + 1, plotHeight + 1);
        g.drawString("0", LEFT, TOP + plotHeight + 16);
        g.drawString("gray value", LEFT + plotWidth / 2 - 25, TOP + plotHeight + 16);
        g.drawString("255", LEFT + plotWidth - 22, TOP + plotHeight + 16);
        if (values == null) {
            g.drawString("No histogram yet", LEFT + 5, TOP + 15);
            return;
        }
        double max = 0;
        for (int i = 0; i < Histogram.BINS; i++) {
            if (values[i] > max) {
                max = values[i];
            }
        }
        if (max == 0) {
            return;
        }
        g.setColor(BAR);
        for (int i = 0; i < Histogram.BINS; i++) {
            if (values[i] > 0) {
                // Bar i covers x from i/256 to (i+1)/256 of the width, at least 1 pixel.
                int x0 = i * plotWidth / 256;
                int x1 = (i + 1) * plotWidth / 256;
                int barWidth = Math.max(1, x1 - x0);
                int barHeight = Math.max(1, (int) Math.round(values[i] / max * plotHeight));
                g.fillRect(LEFT + x0, TOP + plotHeight - barHeight, barWidth, barHeight);
            }
        }
    }
}
