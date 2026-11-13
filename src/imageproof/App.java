package imageproof;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

// Main window: open one image, press Analyze, see the measurements.
// Slow work (loading, analysing) runs in SwingWorker threads so the window stays responsive.
public class App implements ActionListener {

    static final String NOTE = "These are measurements, not a verdict. "
            + "Unusual values do NOT prove an image was edited, and normal values do NOT prove it is authentic.";

    JFrame frame = new JFrame("Simple-Image-Proof");
    JButton openButton = new JButton("Open image...");
    JButton analyzeButton = new JButton("Analyze");
    JLabel status = new JLabel("Open a PNG or JPEG image.");
    ImagePanel imagePanel = new ImagePanel();
    HistogramPanel histogramPanel = new HistogramPanel("Gray histogram (whole image)");
    JTextArea results = new JTextArea();
    JFileChooser chooser = new JFileChooser();

    LoadedImage current;   // image shown now, null if none
    boolean busy = false;  // true while loading or analysing

    // Every new job gets a new number. A finished job whose number is old is ignored,
    // so a slow result from an old image can never be shown for a new one.
    int jobNumber = 0;

    App() {
        chooser.setFileFilter(new FileNameExtensionFilter("PNG or JPEG", "png", "jpg", "jpeg"));
        openButton.addActionListener(this);
        analyzeButton.addActionListener(this);
        analyzeButton.setEnabled(false);

        JPanel top = new JPanel();
        top.add(openButton);
        top.add(analyzeButton);
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
        }
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
        histogramPanel.setValues(null);
        results.setText(NOTE);
    }

    String describe(LoadedImage loaded) {
        return loaded.file.getName() + "  " + loaded.image.getWidth() + " x " + loaded.image.getHeight()
                + "  " + loaded.format;
    }

    void showResults(Analysis a) {
        histogramPanel.setValues(a.histogram);
        String text = "Image: " + describe(current) + "\n\n"
                + "Whole image\n"
                + String.format("  Mean gray:          %.2f%n", a.mean)
                + String.format("  Standard deviation: %.2f%n", a.standardDeviation)
                + String.format("  Entropy:            %.3f bits (0 to 8)%n", a.entropy)
                + "\n" + NOTE;
        results.setText(text);
        results.setCaretPosition(0);
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
