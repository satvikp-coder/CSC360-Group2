package imageproof;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileNameExtensionFilter;

// Main window: open one image, press Analyze, see the measurements.
// Click a tile on the image to see that tile's histogram and numbers.
// Slow work (loading, analysing) runs in SwingWorker threads so the window stays responsive.
public class App implements ActionListener, ChangeListener {

    static final String NOTE = "These are measurements, not a verdict. "
            + "Unusual values do NOT prove an image was edited, and normal values do NOT prove it is authentic.";

    // What the image area shows. The index is used in showView().
    static final String[] VIEWS = {
        "Regional variation",
        "Noise residual variance",
        "Edge strength (tile mean)",
        "Edge map (Sobel)"
    };

    JFrame frame = new JFrame("Simple-Image-Proof");
    JButton openButton = new JButton("Open image...");
    JButton analyzeButton = new JButton("Analyze");
    JComboBox<String> view = new JComboBox<String>(VIEWS);
    JCheckBox showHeat = new JCheckBox("Show heatmap", true);
    JSlider opacity = new JSlider(0, 100, 50);
    JLabel status = new JLabel("Open a PNG or JPEG image.");
    ImagePanel imagePanel = new ImagePanel();
    HistogramPanel histogramPanel = new HistogramPanel("Gray histogram (whole image)");
    JTextArea results = new JTextArea();
    JFileChooser chooser = new JFileChooser();

    LoadedImage current;   // image shown now, null if none
    Analysis analysis;     // results for the current image, null if not analysed
    boolean busy = false;  // true while loading or analysing

    // Every new job gets a new number. A finished job whose number is old is ignored,
    // so a slow result from an old image can never be shown for a new one.
    int jobNumber = 0;

    App() {
        chooser.setFileFilter(new FileNameExtensionFilter("PNG or JPEG", "png", "jpg", "jpeg"));
        openButton.addActionListener(this);
        analyzeButton.addActionListener(this);
        analyzeButton.setEnabled(false);
        view.addActionListener(this);
        view.setEnabled(false);
        showHeat.addActionListener(this);
        opacity.addChangeListener(this);
        opacity.setPreferredSize(new Dimension(100, 20));
        imagePanel.addMouseListener(new ClickHandler());

        JPanel top = new JPanel();
        top.add(openButton);
        top.add(analyzeButton);
        top.add(view);
        top.add(showHeat);
        top.add(new JLabel("Opacity"));
        top.add(opacity);
        top.add(status);

        results.setEditable(false);
        results.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        results.setLineWrap(true);
        results.setWrapStyleWord(true);
        results.setText(NOTE);

        JPanel right = new JPanel(new GridLayout(2, 1, 5, 5));
        right.add(histogramPanel);
        right.add(new JScrollPane(results));
        right.setPreferredSize(new Dimension(420, 500));

        frame.setLayout(new BorderLayout(5, 5));
        frame.add(top, BorderLayout.NORTH);
        frame.add(imagePanel, BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(700, 450));
        frame.setSize(1000, 650);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == openButton) {
            openImage();
        } else if (e.getSource() == analyzeButton) {
            analyze();
        } else if (e.getSource() == view) {
            showView();
        } else if (e.getSource() == showHeat) {
            imagePanel.setShowHeat(showHeat.isSelected());
        }
    }

    // Opacity slider moved.
    public void stateChanged(ChangeEvent e) {
        imagePanel.setOpacity(opacity.getValue());
    }

    // Mouse click on the image: select the tile under the mouse.
    class ClickHandler extends MouseAdapter {
        public void mouseClicked(MouseEvent e) {
            if (analysis == null) {
                return;
            }
            Point p = imagePanel.toImagePoint(e.getX(), e.getY());
            if (p != null) {
                selectTile(p.y / analysis.regions.tileSize, p.x / analysis.regions.tileSize);
            }
        }
    }

    void selectTile(int row, int col) {
        Regions r = analysis.regions;
        Rectangle b = r.bounds(row, col);
        double[] h = r.histograms[row][col];
        imagePanel.setSelected(b);
        histogramPanel.setTitle("Gray histogram of tile row " + row + ", col " + col);
        histogramPanel.setValues(h);
        String text = "Selected tile: row " + row + ", col " + col + "\n"
                + "  Pixels x " + b.x + " to " + (b.x + b.width - 1) + ", y " + b.y + " to " + (b.y + b.height - 1)
                + "  (" + b.width + " x " + b.height + ")\n"
                + String.format("  Mean gray:          %.2f%n", Histogram.mean(h))
                + String.format("  Standard deviation: %.2f%n", Histogram.standardDeviation(h))
                + String.format("  Entropy:            %.3f bits%n", Histogram.entropy(h))
                + String.format("  Variation (TVD):    %.3f%n", r.variation[row][col])
                + String.format("  Noise variance:     %.2f%n", analysis.noise[row][col])
                + String.format("  Edge strength:      %.2f%n", analysis.edges[row][col])
                + "\n" + resultText(analysis);
        results.setText(text);
        results.setCaretPosition(0);
    }

    void openImage() {
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return; // cancelled: keep the current image
        }
        jobNumber++;
        busy = true;
        updateButtons();
        status.setText("Loading " + chooser.getSelectedFile().getName() + "...");
        new LoadWorker(chooser.getSelectedFile(), jobNumber).execute();
    }

    void analyze() {
        jobNumber++;
        busy = true;
        updateButtons();
        status.setText("Analysing...");
        new AnalyzeWorker(current, jobNumber).execute();
    }

    void updateButtons() {
        analyzeButton.setEnabled(current != null && !busy);
    }

    // Clear everything that belonged to the previous image.
    void clearResults() {
        analysis = null;
        view.setEnabled(false);
        imagePanel.setImage(current.image);
        imagePanel.setHeat(null, 0, "");
        imagePanel.setSelected(null);
        histogramPanel.setTitle("Gray histogram (whole image)");
        histogramPanel.setValues(null);
        results.setText(NOTE);
    }

    String describe(LoadedImage loaded) {
        return loaded.file.getName() + "  " + loaded.image.getWidth() + " x " + loaded.image.getHeight()
                + "  " + loaded.format;
    }

    void showResults(Analysis a) {
        analysis = a;
        histogramPanel.setTitle("Gray histogram (whole image)");
        histogramPanel.setValues(a.histogram);
        imagePanel.setSelected(null);
        view.setEnabled(true);
        showView();
        results.setText("Click a tile on the image to inspect it.\n\n" + resultText(a));
        results.setCaretPosition(0);
    }

    // Put the chosen measurement on the image.
    void showView() {
        if (analysis == null) {
            return;
        }
        int size = analysis.regions.tileSize;
        int choice = view.getSelectedIndex();
        imagePanel.setImage(current.image);
        if (choice == 0) {
            imagePanel.setHeat(analysis.regions.variation, size, "Regional variation (TVD to neighbour tiles)");
        } else if (choice == 1) {
            imagePanel.setHeat(analysis.noise, size, "Noise residual variance per tile");
        } else if (choice == 2) {
            imagePanel.setHeat(analysis.edges, size, "Mean Sobel edge strength per tile");
        } else if (choice == 3) {
            imagePanel.setImage(analysis.edgeMap);
            imagePanel.setHeat(null, 0, "");
        }
    }

    String resultText(Analysis a) {
        Regions r = a.regions;
        String text = "Image: " + describe(current) + "\n\n"
                + "Whole image\n"
                + String.format("  Mean gray:          %.2f%n", a.mean)
                + String.format("  Standard deviation: %.2f%n", a.standardDeviation)
                + String.format("  Entropy:            %.3f bits (0 to 8)%n", a.entropy)
                + "\nRegional variation, " + r.rows + " x " + r.cols + " tiles of " + r.tileSize + " px\n"
                + "(average TVD to the tiles above, below, left, right; 0 = same, 1 = no overlap)\n"
                + grid(r.variation, "%6.3f")
                + "\nNoise residual variance per tile\n"
                + "(gray minus 3 x 3 local mean; texture and edges also raise it)\n"
                + grid(a.noise, "%8.2f")
                + "\nMean Sobel edge strength per tile (0 to " + Math.round(Edges.MAX_STRENGTH) + ")\n"
                + "(normal object outlines are strong edges)\n"
                + grid(a.edges, "%8.2f")
                + "\n" + NOTE;
        return text;
    }

    // Numbers of a grid, one line per row.
    static String grid(double[][] values, String format) {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < values.length; row++) {
            for (int col = 0; col < values[row].length; col++) {
                text.append(String.format(format, values[row][col]));
            }
            text.append("\n");
        }
        return text.toString();
    }

    void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Problem", JOptionPane.ERROR_MESSAGE);
    }

    // Loads a file in the background.
    class LoadWorker extends SwingWorker<LoadedImage, Void> {
        File file;
        int myJob;

        LoadWorker(File file, int myJob) {
            this.file = file;
            this.myJob = myJob;
        }

        protected LoadedImage doInBackground() throws Exception {
            return LoadedImage.load(file);
        }

        // Runs on the Swing thread when loading is finished.
        protected void done() {
            if (myJob != jobNumber) {
                return; // a newer job started, ignore this one
            }
            busy = false;
            try {
                current = get();
                imagePanel.setImage(current.image);
                clearResults();
                status.setText(describe(current) + "  - press Analyze");
            } catch (Exception e) {
                // Bad file: keep the old image and its results.
                showError(errorText(e));
                if (current == null) {
                    status.setText("Open a PNG or JPEG image.");
                } else {
                    status.setText(describe(current));
                }
            }
            updateButtons();
        }
    }

    // Runs all measurements in the background.
    class AnalyzeWorker extends SwingWorker<Analysis, Void> {
        LoadedImage loaded;
        int myJob;

        AnalyzeWorker(LoadedImage loaded, int myJob) {
            this.loaded = loaded;
            this.myJob = myJob;
        }

        protected Analysis doInBackground() {
            return Analysis.run(loaded.image);
        }

        protected void done() {
            if (myJob != jobNumber) {
                return; // image was replaced meanwhile, ignore this result
            }
            busy = false;
            try {
                showResults(get());
                status.setText(describe(current) + "  - analysed");
            } catch (Exception e) {
                showError("Analysis failed: " + errorText(e));
                status.setText(describe(current));
            }
            updateButtons();
        }
    }

    // The useful message inside an exception from a worker.
    static String errorText(Exception e) {
        Throwable cause = e;
        if (e.getCause() != null) {
            cause = e.getCause();
        }
        return cause.getMessage();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new App();
            }
        });
    }
}
