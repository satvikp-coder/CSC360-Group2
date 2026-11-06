package imageproof;

import java.awt.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Captures only the visible demo content, never the surrounding desktop.
 * Screenshots require visual inspection; successful capture alone is not a pass.
 */
public final class HistogramPanelDesktopCheck {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: HistogramPanelDesktopCheck output-directory");
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Interactive desktop required");
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        JFrame[] window = new JFrame[1];
        HistogramPanel[] panels = new HistogramPanel[2];
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                JFrame frame = new JFrame("Histogram visual verification");
                window[0] = frame;
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setLayout(new GridLayout(1, 2));
                panels[0] = new HistogramPanel("Original image");
                panels[1] = new HistogramPanel("Comparison image");
                double[] p = new double[256], q = new double[256];
                p[0] = .25; p[128] = .5; p[255] = .25;
                q[32] = .5; q[192] = .5;
                for (HistogramPanel panel : panels) {
                    panel.setMaximumFrequency(.5); frame.add(panel);
                }
                panels[0].setValues(p); panels[1].setValues(q);
                frame.setLocation(40, 40);
                frame.setAlwaysOnTop(true);
                frame.pack(); frame.setVisible(true);
            });
            capture(window[0], robot, output, "normal", 840, 260);
            capture(window[0], robot, output, "small", 640, 220);
            capture(window[0], robot, output, "narrow", 400, 200);
            SwingUtilities.invokeAndWait(() -> {
                panels[0].clearData(); panels[1].setValues(null);
            });
            capture(window[0], robot, output, "cleared", 840, 260);
        } finally {
            if (window[0] != null) SwingUtilities.invokeAndWait(window[0]::dispose);
        }
        System.out.println("Captured normal/small/narrow/cleared desktop views; inspect before recording outcomes.");
    }

    private static void capture(JFrame frame, Robot robot, Path output, String name,
                                int width, int height) throws Exception {
        Rectangle[] content = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> {
            Insets insets = frame.getInsets();
            frame.setSize(width + insets.left + insets.right, height + insets.top + insets.bottom);
            frame.validate(); frame.toFront();
        });
        // Keep the pointer away from panels to avoid a tooltip covering the plot.
        robot.mouseMove(5, 5);
        robot.waitForIdle();
        robot.delay(500); // Allow the native window manager to finish resizing.
        SwingUtilities.invokeAndWait(() -> content[0] = new Rectangle(
                frame.getContentPane().getLocationOnScreen(), frame.getContentPane().getSize()));
        if (content[0].width != width || content[0].height != height)
            throw new IllegalStateException("Window manager did not apply requested content size: " + content[0]);
        ImageIO.write(robot.createScreenCapture(content[0]), "png", output.resolve(name + ".png").toFile());
    }
}
