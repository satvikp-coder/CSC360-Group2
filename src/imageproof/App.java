package imageproof;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
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
    /** Incremented per selection / input change so slow background results can be recognised as stale. */
    private final int[] loadTickets = new int[2];
    private int resultTicket;
    // Status is derived from this state (never from saved label text), so it cannot go stale.
    private int loading;            // loads in progress
    private boolean comparing;      // comparison in progress
    private Double score;           // score currently shown, null if none
    private boolean inputsChanged;  // an input was replaced since the last result

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

    /** Result of decoding a file in the background. */
    private record Loaded(BufferedImage image, BufferedImage thumb) {}

    /**
     * Asks for a file and decodes it on a background thread so the window stays responsive. The image is stored
     * in {@code slot} (0 = reference, 1 = comparison) only if it is readable and no newer selection was made for
     * that slot meanwhile.
     */
    private void load(int slot) {
        JFileChooser fc = new JFileChooser(lastDir);
        if (fc.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return; // cancel keeps current state
        File f = fc.getSelectedFile();
        lastDir = f.getParentFile();
        int ticket = ++loadTickets[slot]; // a later choice for this slot makes this result stale
        loading++;
        refreshStatus();
        new SwingWorker<Loaded, Void>() {
            @Override protected Loaded doInBackground() throws IOException {
                BufferedImage img = ImageIO.read(f);
                return img == null ? null : new Loaded(img, thumb(img));
            }

            @Override protected void done() {
                loading--;
                if (ticket != loadTickets[slot]) { refreshStatus(); return; } // stale: superseded by a newer selection
                try {
                    Loaded l = get();
                    if (l == null) { error("\"" + f.getName() + "\" is not a supported image (try PNG, JPG, GIF or BMP)."); }
                    else {
                        images[slot] = l.image();
                        previews[slot].setText(null);
                        previews[slot].setIcon(new ImageIcon(l.thumb()));
                        previews[slot].setToolTipText(f.getName() + " — " + l.image().getWidth() + "×" + l.image().getHeight());
                        invalidateResult(); // old score/plots no longer match the inputs
                        return; // invalidateResult refreshes the status
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | RuntimeException ex) {
                    Throwable c = ex.getCause() == null ? ex : ex.getCause();
                    error("Could not read \"" + f.getName() + "\": " + c.getMessage());
                }
                refreshStatus(); // a failed load leaves inputs, and so the current result, unchanged
            }
        }.execute();
    }

    /** Sets the status line from current state only. */
    private void refreshStatus() {
        if (loading > 0) result.setText("Loading…");
        else if (comparing) result.setText("Comparing…");
        else if (score != null) result.setText(String.format("Histogram difference: %.4f", score));
        else if (inputsChanged) result.setText("Inputs changed — press Compare.");
        else result.setText("Choose both images, then press Compare.");
    }

    /** Clears both plots and the score because the inputs no longer match them. */
    private void invalidateResult() {
        resultTicket++; // discards any comparison still running
        comparing = false;
        score = null;
        inputsChanged = true;
        for (HistogramPanel p : panels) p.clearData();
        compare.setEnabled(images[0] != null && images[1] != null);
        refreshStatus();
    }

    /** Computes both normalized histograms and their distance on a background thread, then displays them. */
    private void compare() {
        BufferedImage ref = images[0], cmp = images[1];
        int ticket = ++resultTicket;
        compare.setEnabled(false);
        comparing = true;
        refreshStatus();
        new SwingWorker<double[][], Void>() {
            @Override protected double[][] doInBackground() {
                return new double[][] {Histogram.normalize(Histogram.compute(ref)), Histogram.normalize(Histogram.compute(cmp))};
            }

            @Override protected void done() {
                if (ticket != resultTicket) return; // inputs changed while computing: do not show a stale score
                try {
                    double[][] h = get();
                    score = Comparison.difference(h[0], h[1]);
                    inputsChanged = false;
                    panels[0].setValues(h[0]);
                    panels[1].setValues(h[1]);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException | RuntimeException ex) {
                    JOptionPane.showMessageDialog(frame, "Comparison failed: " + (ex.getCause() == null ? ex : ex.getCause()).getMessage(),
                            "Cannot compare", JOptionPane.ERROR_MESSAGE);
                }
                comparing = false;
                compare.setEnabled(true);
                refreshStatus();
            }
        }.execute();
    }

    /** Scaled copy no larger than 400 px on its longest side; runs off the event thread. */
    private static BufferedImage thumb(BufferedImage img) {
        double s = Math.min(1.0, 400.0 / Math.max(img.getWidth(), img.getHeight()));
        int w = Math.max(1, (int) (img.getWidth() * s)), h = Math.max(1, (int) (img.getHeight() * s));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** Must be called on the event dispatch thread. */
    private void error(String msg) { JOptionPane.showMessageDialog(frame, msg, "Cannot load image", JOptionPane.ERROR_MESSAGE); }

    /** Starts the UI on the event dispatch thread. */
    public static void main(String[] args) { SwingUtilities.invokeLater(App::new); }
}
