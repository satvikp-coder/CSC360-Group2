package imageproof;

import java.awt.*;
import java.util.Locale;
import javax.swing.JPanel;

/** Displays normalized bins. Construct and update on the Swing event dispatch thread. */
public final class HistogramPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    static final Color BAR = new Color(35, 105, 175);
    private final String title;
    private double[] values;
    private double maximumFrequency = 1.0;

    /** Creates an empty plot with the given title; null uses "Histogram". */
    public HistogramPanel(String title) {
        this.title = title == null ? "Histogram" : title;
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(420, 260));
        setToolTipText("Normalized frequency = bin pixel count / total image pixels");
        getAccessibleContext().setAccessibleName(this.title);
    }

    /** Null clears. Invalid input clears stale data and throws IllegalArgumentException.
     * All-zero input represents an empty result; otherwise the sum must be 1 +/- 1e-9.
     */
    public void setValues(double[] input) {
        clearData();
        if (input == null) return;
        if (input.length != 256) throw new IllegalArgumentException("Expected 256 bins");
        double[] copy = input.clone();
        double sum = 0;
        for (double value : copy) {
            if (!Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Bins must be finite frequencies in [0, 1]");
            sum += value;
        }
        if (sum == 0) return;
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("Frequencies must sum to 1");
        values = copy;
        repaint();
    }

    /** Clears the plotted values and schedules a repaint, retaining the frequency scale. */
    public void clearData() {
        values = null;
        repaint();
    }

    /** Supply the SAME positive maximum to both panels. Values above it are clipped.
     * Default 1.0 is always comparable and covers every valid normalized histogram.
     */
    public void setMaximumFrequency(double maximum) {
        if (!Double.isFinite(maximum) || maximum <= 0 || maximum > 1)
            throw new IllegalArgumentException("Scale must be finite and in (0, 1]");
        maximumFrequency = maximum;
        repaint();
    }

    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            Insets insets = getInsets();
            g.clipRect(insets.left, insets.top,
                    Math.max(0, getWidth() - insets.left - insets.right),
                    Math.max(0, getHeight() - insets.top - insets.bottom));
            int left = insets.left + 48, top = insets.top + 56;
            int width = getWidth() - left - insets.right - 14;
            int height = getHeight() - top - insets.bottom - 46;
            g.setColor(Color.DARK_GRAY);
            g.drawString(title, insets.left + 8, insets.top + 18);
            g.drawString(String.format(Locale.ROOT, "Normalized frequency (0 to %.4g)",
                    maximumFrequency), insets.left + 8, insets.top + 36);
            if (width <= 0 || height <= 0) return;
            g.drawRect(left - 1, top, width + 1, height);
            g.drawString("0", left, top + height + 16);
            g.drawString("255", left + Math.max(0, width - 24), top + height + 16);
            g.drawString("Grayscale intensity", left, top + height + 34);
            if (values == null) {
                g.drawString("No histogram loaded", left + 4, top + Math.min(height, 20));
                return;
            }
            g.setColor(BAR);
            // Overlapping bins share pixels when narrow: drawing every positive bin
            // retains the tallest spike instead of sampling and losing narrow peaks.
            for (int bin = 0; bin < 256; bin++) {
                if (values[bin] == 0) continue;
                int x0 = (int) ((long) bin * width / 256);
                int x1 = (int) ((long) (bin + 1) * width / 256);
                int barHeight = Math.max(1, (int) Math.ceil(
                        Math.min(1, values[bin] / maximumFrequency) * height));
                g.fillRect(left + x0, top + height - barHeight,
                        Math.min(width - x0, Math.max(1, x1 - x0)), barHeight);
            }
        } finally {
            g.dispose();
        }
    }
}
