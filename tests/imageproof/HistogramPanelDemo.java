package imageproof;

import java.awt.GridLayout;
import javax.swing.*;

/** Synthetic visual harness, not a replacement for App. */
public final class HistogramPanelDemo {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Histogram panel visual check");
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLayout(new GridLayout(1, 2));
            HistogramPanel original = new HistogramPanel("Original image");
            HistogramPanel comparison = new HistogramPanel("Comparison image");
            double[] p = new double[256], q = new double[256];
            p[0] = .25; p[128] = .5; p[255] = .25;
            q[32] = .5; q[192] = .5;
            original.setMaximumFrequency(.5); comparison.setMaximumFrequency(.5);
            original.setValues(p); comparison.setValues(q);
            frame.add(original); frame.add(comparison);
            frame.pack(); frame.setVisible(true);
        });
    }
}
