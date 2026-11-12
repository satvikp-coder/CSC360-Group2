package imageproof;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.swing.*;

/**
 * Swing window for the histogram comparison workflow.
 *
 * <p>The user loads a reference and a comparison image (any size), then presses Compare to see both
 * normalized grayscale histograms and the total variation distance. Cancelling a file chooser or
 * loading an unreadable file leaves the existing inputs untouched; loading a new image clears the
 * previous plots and score so stale results are never shown. The score does not prove editing.
 */
public final class App {
    private final JFrame frame = new JFrame("Simple-Image-Proof");
    private final BufferedImage[] images = new BufferedImage[2];
    private final JLabel[] previews = {new JLabel("No image", SwingConstants.CENTER), new JLabel("No image", SwingConstants.CENTER)};
    private final HistogramPanel[] panels = {new HistogramPanel("Reference"), new HistogramPanel("Comparison")};
    private final JButton compare = new JButton("Compare");
    private final JLabel result = new JLabel("Choose both images, then press Compare.", SwingConstants.CENTER);
    private File lastDir;

    private App() {
        JPanel top = new JPanel(new GridLayout(1, 3, 8, 8));
        top.add(button("Load reference…", 0));
        top.add(button("Load comparison…", 1));
        compare.setEnabled(false);
        compare.addActionListener(e -> compare());
        top.add(compare);

        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        for (JLabel l : previews) { l.setBorder(BorderFactory.createLineBorder(Color.GRAY)); grid.add(new JScrollPane(l)); }
        for (HistogramPanel p : panels) grid.add(p);

        result.setFont(result.getFont().deriveFont(Font.BOLD, 16f));
        JLabel note = new JLabel("<html><center>Score 0 = same brightness distribution, 1 = no overlap. It is NOT the % of edited pixels "
                + "and does not prove editing or authenticity.</center></html>", SwingConstants.CENTER);
        JPanel bottom = new JPanel(new GridLayout(2, 1));
        bottom.add(result);
        bottom.add(note);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        root.add(top, BorderLayout.NORTH);
        root.add(grid, BorderLayout.CENTER);
        root.add(bottom, BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(600, 500));
        frame.setSize(950, 750);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private JButton button(String text, int slot) {
        JButton b = new JButton(text);
        b.addActionListener(e -> load(slot));
        return b;
    }

    /** Asks for a file and, if it is a readable image, stores it in {@code slot} (0 = reference, 1 = comparison). */
    private void load(int slot) {
        JFileChooser fc = new JFileChooser(lastDir);
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return; // cancel keeps current state
        File f = fc.getSelectedFile();
        lastDir = f.getParentFile();
        BufferedImage img;
        try {
            img = ImageIO.read(f);
        } catch (IOException | RuntimeException ex) {
            error("Could not read \"" + f.getName() + "\": " + ex.getMessage());
            return;
        }
        if (img == null) { error("\"" + f.getName() + "\" is not a supported image (try PNG, JPG, GIF or BMP)."); return; }
        images[slot] = img;
        previews[slot].setText(null);
        previews[slot].setIcon(new ImageIcon(thumb(img)));
        previews[slot].setToolTipText(f.getName() + " — " + img.getWidth() + "×" + img.getHeight());
        invalidateResult(); // old score/plots no longer match the inputs
        compare.setEnabled(images[0] != null && images[1] != null);
    }

    /** Clears both plots and the score because the inputs no longer match them. */
    private void invalidateResult() {
        for (HistogramPanel p : panels) p.clearData();
        result.setText("Inputs changed — press Compare.");
    }

    /** Computes both normalized histograms and their distance, and displays them. */
    private void compare() {
        double[] p = Histogram.normalize(Histogram.compute(images[0]));
        double[] q = Histogram.normalize(Histogram.compute(images[1]));
        double score = Comparison.difference(p, q);
        panels[0].setValues(p);
        panels[1].setValues(q);
        result.setText(String.format("Histogram difference: %.4f", score));
    }

    private static Image thumb(BufferedImage img) {
        double s = Math.min(1.0, 400.0 / Math.max(img.getWidth(), img.getHeight()));
        return img.getScaledInstance(Math.max(1, (int) (img.getWidth() * s)), Math.max(1, (int) (img.getHeight() * s)), Image.SCALE_SMOOTH);
    }

    private void error(String msg) { JOptionPane.showMessageDialog(frame, msg, "Cannot load image", JOptionPane.ERROR_MESSAGE); }

    /** Starts the UI on the event dispatch thread. */
    public static void main(String[] args) { SwingUtilities.invokeLater(App::new); }
}
