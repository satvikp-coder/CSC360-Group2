package imageproof;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

/** One-image desktop workflow. All I/O and image mathematics run outside the EDT. */
public final class App {
    final JFrame frame = new JFrame("Simple-Image-Proof — Single-Image Forensic Analyzer");
    final ImagePanel preview = new ImagePanel();
    final JButton analyze = new JButton("Analyze");
    final JLabel status = new JLabel("Upload one JPEG or PNG to begin.");
    final JLabel details = new JLabel("No image selected");
    final JTextArea overview = textArea(), grid = textArea(), evidence = textArea();
    private final JLabel selectedDetails = new JLabel("Click a region in the image to inspect it.");
    private final HistogramPanel global = new HistogramPanel("Global grayscale histogram");
    private final HistogramPanel selected = new HistogramPanel("Selected region histogram");
    private final JComboBox<String> view = new JComboBox<>(new String[]{"Original", "Noise residual", "Sobel edges", "Copy candidates"});
    private final JComboBox<Integer> tileSize = new JComboBox<>(new Integer[]{64, 32});
    private final JCheckBox overlay = new JCheckBox("Heatmap", true);
    private final JSlider opacity = new JSlider(0, 100, 45);
    private final WorkerTickets tickets = new WorkerTickets();
    private SwingWorker<?, ?> loadWorker, analysisWorker;
    private File lastDirectory;
    private boolean loading, analyzing;
    BufferedImage image;
    AnalysisResult result;

    App() {
        JButton upload = new JButton("Upload image…");
        upload.addActionListener(event -> chooseFile());
        analyze.setEnabled(false); analyze.addActionListener(event -> analyzeImage());
        JPanel heading = new JPanel(new BorderLayout(8, 4));
        JLabel title = new JLabel("Simple-Image-Proof");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 22));
        JPanel name = new JPanel(new GridLayout(2, 1));
        name.add(title); name.add(new JLabel("Single-Image Forensic Analyzer • Offline • Java 17"));
        heading.add(name, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(upload); actions.add(analyze); heading.add(actions, BorderLayout.EAST);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("View:")); controls.add(view); controls.add(overlay);
        opacity.setPreferredSize(new Dimension(100, 28));
        controls.add(new JLabel("Opacity")); controls.add(opacity);
        controls.add(new JLabel("Tile px:")); controls.add(tileSize);
        JPanel imageArea = new JPanel(new BorderLayout(4, 4));
        imageArea.add(controls, BorderLayout.NORTH); imageArea.add(preview, BorderLayout.CENTER);
        imageArea.add(details, BorderLayout.SOUTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Overview", new JScrollPane(overview));
        JPanel histograms = new JPanel(new GridLayout(2, 1, 4, 4)); histograms.add(global); histograms.add(selected);
        tabs.addTab("Histogram", histograms);
        JPanel regional = new JPanel(new BorderLayout(4, 4)); regional.add(new JScrollPane(grid), BorderLayout.CENTER);
        regional.add(selectedDetails, BorderLayout.SOUTH); tabs.addTab("Region map", regional);
        tabs.addTab("Evidence", new JScrollPane(evidence));
        tabs.setPreferredSize(new Dimension(410, 520));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, imageArea, tabs);
        split.setResizeWeight(.62); split.setDividerLocation(650);
        JPanel bottom = new JPanel(new GridLayout(3, 1, 2, 2)); bottom.add(status);
        bottom.add(new JLabel("Heatmap: blue = smaller, red = larger mean neighbor TVD (0–1). Noise view uses relative variance."));
        bottom.add(new JLabel("Texture, object boundaries and repeated patterns are normal. These measurements do not prove editing or authenticity."));
        JPanel root = new JPanel(new BorderLayout(8, 8)); root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        root.add(heading, BorderLayout.NORTH); root.add(split, BorderLayout.CENTER); root.add(bottom, BorderLayout.SOUTH);
        frame.setContentPane(root); frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setMinimumSize(new Dimension(940, 650)); frame.setSize(1180, 800); frame.setLocationRelativeTo(null);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) { cancelWorkers(); tickets.nextLoad(); }
        });
        view.addActionListener(event -> preview.setView((String) view.getSelectedItem()));
        overlay.addActionListener(event -> preview.setOverlay(overlay.isSelected()));
        opacity.addChangeListener(event -> preview.setOpacity(opacity.getValue() / 100f));
        tileSize.addActionListener(event -> {
            tickets.nextAnalysis(); if (analysisWorker != null) analysisWorker.cancel(true);
            analyzing = false; clearResults(); refreshStatus();
        });
        preview.onSelection(this::inspectRegion);
        overview.setText("Upload one JPEG or PNG, then Analyze.\n\nAll analysis stays on this computer.\nNo models, network services or editing probabilities.\n\nImages up to 16 megapixels are supported.\nLarger images should be resized before loading.");
        frame.setVisible(true);
    }
    private static JTextArea textArea() {
        JTextArea text = new JTextArea(); text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setMargin(new Insets(12, 12, 12, 12)); text.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14)); return text;
    }
    private void chooseFile() {
        JFileChooser chooser = new JFileChooser(lastDirectory);
        chooser.setFileFilter(new FileNameExtensionFilter("JPEG or PNG images", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) loadFile(chooser.getSelectedFile());
    }
    void loadFile(File file) {
        long ticket = tickets.nextLoad(); cancelWorkers();
        loading = true; analyzing = false; refreshStatus();
        lastDirectory = file.getParentFile();
        loadWorker = new SwingWorker<ImageAnalyzer.Loaded, Void>() {
            @Override protected ImageAnalyzer.Loaded doInBackground() throws Exception { return ImageAnalyzer.load(file); }
            @Override protected void done() {
                if (!tickets.currentLoad(ticket)) return;
                try {
                    ImageAnalyzer.Loaded loaded = get(); image = loaded.image(); clearResults(); preview.setImage(image);
                    details.setText(file.getName() + " • " + image.getWidth() + " × " + image.getHeight() + " • " + loaded.format());
                    overview.setText("Image loaded. Press Analyze to calculate measurements.\n\nAlpha is composited onto white. Encoded gray samples retain their 8-bit intensity (16-bit scaled).\nNo network is used.");
                } catch (CancellationException ignored) {
                    // A newer selection owns status and results.
                } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                catch (ExecutionException | RuntimeException exception) {
                    if (image != null && result == null) overview.setText("Current image retained. Press Analyze to calculate measurements again.");
                    showError("Cannot load image", exception);
                }
                loading = false; refreshStatus();
            }
        };
        loadWorker.execute();
    }
    private void cancelWorkers() {
        if (loadWorker != null) loadWorker.cancel(true);
        if (analysisWorker != null) analysisWorker.cancel(true);
    }
    private void clearResults() {
        result = null; preview.setResult(null); global.clearData(); selected.clearData();
        overview.setText("Press Analyze to calculate measurements for the current image and tile size.");
        grid.setText(""); evidence.setText(""); selectedDetails.setText("Click a region after analysis.");
    }
    void analyzeImage() {
        if (image == null || loading || analyzing) return;
        BufferedImage input = image; int size = (Integer) tileSize.getSelectedItem();
        long ticket = tickets.nextAnalysis(); analyzing = true; clearResults(); refreshStatus();
        overview.setText("Analyzing on this computer… You can upload a replacement image at any time.");
        analysisWorker = new SwingWorker<AnalysisResult, Void>() {
            @Override protected AnalysisResult doInBackground() { return ImageAnalyzer.analyze(input, size); }
            @Override protected void done() {
                if (!tickets.currentAnalysis(ticket)) return;
                try { result = get(); displayResults(); }
                catch (CancellationException ignored) {}
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                catch (ExecutionException | RuntimeException exception) {
                    overview.setText("Analysis failed. The image is retained; you may retry or upload another image.");
                    showError("Analysis failed", exception);
                }
                analyzing = false; refreshStatus();
            }
        };
        analysisWorker.execute();
    }
    private void displayResults() {
        preview.setResult(result); scaleHistograms(null); global.setValues(result.histogram());
        Histogram.Statistics statistics = result.statistics();
        double maximum = 0;
        for (RegionAnalyzer.Region region : result.regions().regions()) maximum = Math.max(maximum, region.variation());
        overview.setText(String.format(Locale.ROOT,
                "GLOBAL BRIGHTNESS\nMean: %.3f / 255\nPopulation standard deviation: %.3f\nEntropy: %.3f bits\n\nLOCAL VARIATION\n%d × %d tiles, %d px default grid\nLargest mean neighbor TVD: %.4f\n\nCOPY CANDIDATES\n%d matched block pairs; %d blocks examined\n%s\n\nInspect the image to select a region. Use the view selector for noise, Sobel edges or copy candidates.\n\nMeasurements describe image structure. Natural boundaries, texture and repeated patterns can respond strongly. No editing probability or authenticity verdict is calculated.",
                statistics.mean(), statistics.standardDeviation(), statistics.entropy(), result.regions().columns(), result.regions().rows(),
                result.regions().blockSize(), maximum, result.copies().matches().size(), result.copies().blocksExamined(),
                result.copies().truncated() ? "Search/output cap reached; candidates are incomplete." : "Exact aligned 16×16 grayscale blocks only; other transformations may be missed."));
        StringBuilder values = new StringBuilder("Mean TVD to existing side-neighbors (0–1)\nClick a tile for its histogram and residual variance.\n\n");
        for (int row = 0; row < result.regions().rows(); row++) {
            for (int column = 0; column < result.regions().columns(); column++) values.append(String.format(Locale.ROOT, "%.3f  ", result.regions().at(row, column).variation()));
            values.append('\n');
        }
        grid.setText(values.toString()); grid.setCaretPosition(0);
        evidence.setText("METHODS\n\nHistogram: rounded 0.299R + 0.587G + 0.114B; 256 normalized bins. Alpha is composited onto white.\n\nRegional heatmap: mean total variation distance to four existing side-neighbors. Hot colors can mark ordinary scene boundaries.\n\nNoise residual: gray minus available 3×3 mean; tile population residual variance. Texture and edges contribute. This is not sensor identification. Noise colors are scaled to this image's maximum tile variance.\n\nSobel: clamped borders, strength = hypot(gx, gy)/(4√2), clipped to 255. Strong edges are normal.\n\nCopy candidates: exact textured 16×16 blocks sampled every 8 pixels; distant matches need at least three pairs with the same displacement. Cyan and magenta show paired areas. Repeated patterns cause false positives. Near-exact, rotated, scaled or off-grid copies may be missed.\n\nNo result proves whether an image was edited, forged or authentic.");
    }
    private void inspectRegion(int index) {
        if (result == null) return;
        RegionAnalyzer.Region region = result.regions().regions()[index];
        scaleHistograms(region.histogram()); selected.setValues(region.histogram());
        String text = String.format(Locale.ROOT, "Region (%d,%d) %d×%d • TVD %.4f • mean %.2f • residual variance %.2f",
                region.x(), region.y(), region.width(), region.height(), region.variation(), region.statistics().mean(), result.residualVariance()[index]);
        selectedDetails.setText(text); status.setText(text);
    }
    private void scaleHistograms(double[] regional) {
        double maximum = 0;
        for (double value : result.histogram()) maximum = Math.max(maximum, value);
        if (regional != null) for (double value : regional) maximum = Math.max(maximum, value);
        global.setMaximumFrequency(maximum); selected.setMaximumFrequency(maximum);
    }
    private void refreshStatus() {
        analyze.setEnabled(image != null && !loading && !analyzing);
        status.setText(loading ? "Loading image…" : analyzing ? "Analyzing…" : result != null ? "Analysis complete — click a region to inspect." : image != null ? "Ready — press Analyze." : "Upload one JPEG or PNG to begin.");
    }
    private void showError(String title, Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        JOptionPane.showMessageDialog(frame, cause.getMessage(), title, JOptionPane.ERROR_MESSAGE);
    }
    public static void main(String[] args) { SwingUtilities.invokeLater(App::new); }
}
