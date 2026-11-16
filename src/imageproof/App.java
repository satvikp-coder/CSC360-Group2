package imageproof;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
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

/**
 * Main window of the program. It is the last step of the flow
 * {@link Histogram} to {@link Comparison} to {@link Regions} to {@link Noise}
 * and {@link Edges} to {@link CopyMove} and {@link FlatPatch} to
 * {@link LocalAnomaly} to {@link TamperingAssessment}. This class only shows
 * the results.
 *
 * <p>How it works:</p>
 * <ul>
 * <li><b>Loading:</b> "Open image..." lets the user choose a PNG or JPEG file.
 * {@link LoadedImage#load(File)} reads it in a {@link SwingWorker} background job.</li>
 * <li><b>Analyzing:</b> "Analyze" runs {@link Analysis#run(java.awt.image.BufferedImage, String)}
 * in another background job, so the window stays responsive. The result is a
 * SHORT final assessment: the outcome, the measured anomalous area and up to three reasons.</li>
 * <li><b>Job number:</b> every new job gets a new number ({@link #jobNumber}).
 * When a job finishes, its number is compared with the current one. An old
 * number means a newer job started, so the old result is ignored. This way a
 * slow result from an old image is never shown for a new image.</li>
 * <li><b>Histogram selection:</b> clicking a tile on the image selects it.
 * The histogram panel then shows that tile's histogram, and a short summary
 * of the selected region is shown (position, size, three measurements and one
 * sentence). The assessment does not change. "Whole-image histogram" goes back.</li>
 * <li><b>Views:</b> a drop-down list chooses what is drawn on the image
 * (flagged regions, heatmaps, edge map or copy-move candidates).</li>
 * <li><b>Advanced details:</b> a separate read-only window with the full text:
 * rules, tables for every tile and copy-move counts.</li>
 * </ul>
 *
 * <p>The numbers are measurements, not a verdict. The anomalous-area
 * percentage is a measured area, not a probability of editing. Unusual values
 * do not prove editing, and normal values do not prove that an image is
 * authentic. This program is for learning. It is not forensic proof.</p>
 */
public class App implements ActionListener, ChangeListener {

    /** Reminder text: the numbers are measurements, not a verdict. */
    static final String NOTE = "These are measurements, not a verdict. "
            + "Unusual values do NOT prove an image was edited, and normal values do NOT prove it is authentic.";

    // What the image area shows. The index is used in showView().
    static final String[] VIEWS = {
        "Flagged regions (summary)",
        "Regional variation",
        "Noise residual variance",
        "Edge strength (tile mean)",
        "Edge map (Sobel)",
        "Copy-move candidates"
    };

    JFrame frame = new JFrame("Simple-Image-Proof");
    JButton openButton = new JButton("Open image...");
    JButton analyzeButton = new JButton("Analyze");
    JComboBox<String> view = new JComboBox<String>(VIEWS);
    JCheckBox showHeat = new JCheckBox("Show overlay", true);
    JSlider opacity = new JSlider(0, 100, 50);
    JLabel status = new JLabel("Open a PNG or JPEG image.");
    ImagePanel imagePanel = new ImagePanel();
    HistogramPanel histogramPanel = new HistogramPanel("Gray histogram (whole image)");
    JTextArea regionSummary = new JTextArea(10, 30);
    JPanel assessmentPanel = new JPanel(new BorderLayout(4, 4));
    JLabel assessmentStatus = new JLabel("Not analyzed");
    JLabel areaLabel = new JLabel(" ");
    JLabel statsLabel = new JLabel(" ");
    JTextArea assessmentReasons = new JTextArea(6, 30);
    JButton wholeButton = new JButton("Whole-image histogram");
    JButton advancedButton = new JButton("Advanced details...");
    JFileChooser chooser = new JFileChooser();
    JDialog advancedDialog;

    LoadedImage current;   // image shown now, null if none
    Analysis analysis;     // results for the current image, null if not analysed
    boolean busy = false;  // true while loading or analysing

    /**
     * Number of the newest background job. Every new job gets a new number.
     * A finished job whose number is old is ignored, so a slow result from
     * an old image can never be shown for a new one.
     */
    int jobNumber = 0;

    /** Builds the window, connects the controls and shows the window. Call it on the Swing thread. */
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
        wholeButton.addActionListener(this);
        wholeButton.setEnabled(false);
        advancedButton.addActionListener(this);
        advancedButton.setEnabled(false);
        imagePanel.addMouseListener(new ClickHandler());
        nameControls();

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        top.add(openButton);
        top.add(analyzeButton);
        top.add(view);
        top.add(showHeat);
        top.add(new JLabel("Opacity"));
        top.add(opacity);

        regionSummary.setEditable(false);
        regionSummary.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        regionSummary.setLineWrap(true);
        regionSummary.setWrapStyleWord(true);
        regionSummary.setText(NOTE);

        assessmentPanel.setBorder(BorderFactory.createTitledBorder("FINAL IMAGE ASSESSMENT"));
        assessmentStatus.setFont(assessmentStatus.getFont().deriveFont(Font.BOLD, 15f));
        areaLabel.setFont(areaLabel.getFont().deriveFont(Font.BOLD, 12f));
        assessmentReasons.setEditable(false);
        assessmentReasons.setLineWrap(true);
        assessmentReasons.setWrapStyleWord(true);
        assessmentReasons.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        JPanel heading = new JPanel(new GridLayout(2, 1));
        heading.setOpaque(false);
        heading.add(assessmentStatus);
        heading.add(areaLabel);
        JPanel foot = new JPanel(new GridLayout(3, 1));
        foot.setOpaque(false);
        foot.add(statsLabel);
        foot.add(new JLabel("<html>Area is measured, not a probability of editing.</html>"));
        assessmentPanel.add(heading, BorderLayout.NORTH);
        assessmentPanel.add(assessmentReasons, BorderLayout.CENTER);
        assessmentPanel.add(foot, BorderLayout.SOUTH);
        showAssessment(null);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        buttons.add(wholeButton);
        buttons.add(advancedButton);
        JPanel regionPanel = new JPanel(new BorderLayout(3, 3));
        regionPanel.setBorder(BorderFactory.createTitledBorder("SELECTED REGION"));
        regionPanel.add(new JScrollPane(regionSummary), BorderLayout.CENTER);
        regionPanel.add(buttons, BorderLayout.SOUTH);

        JPanel right = new JPanel(new BorderLayout(5, 5));
        right.add(assessmentPanel, BorderLayout.NORTH);
        right.add(histogramPanel, BorderLayout.CENTER);
        right.add(regionPanel, BorderLayout.SOUTH);
        right.setPreferredSize(new Dimension(450, 600));

        frame.setLayout(new BorderLayout(5, 5));
        frame.add(top, BorderLayout.NORTH);
        frame.add(imagePanel, BorderLayout.CENTER);
        frame.add(right, BorderLayout.EAST);
        frame.add(status, BorderLayout.SOUTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(920, 700));
        frame.setSize(1120, 800);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    // Names and tool tips for keyboard and assistive-technology users.
    private void nameControls() {
        openButton.getAccessibleContext().setAccessibleName("Open image");
        analyzeButton.getAccessibleContext().setAccessibleName("Analyze image");
        view.getAccessibleContext().setAccessibleName("Analysis view");
        showHeat.getAccessibleContext().setAccessibleName("Show overlay");
        opacity.getAccessibleContext().setAccessibleName("Overlay opacity");
        wholeButton.getAccessibleContext().setAccessibleName("Show whole-image histogram");
        advancedButton.getAccessibleContext().setAccessibleName("Advanced details");
        regionSummary.getAccessibleContext().setAccessibleName("Selected region summary");
        assessmentReasons.getAccessibleContext().setAccessibleName("Reasons for the assessment");
        assessmentStatus.getAccessibleContext().setAccessibleName("Final assessment");
        areaLabel.getAccessibleContext().setAccessibleName("Anomalous area");
        imagePanel.getAccessibleContext().setAccessibleName("Image with overlay; click to select a region");
        opacity.setToolTipText("Opacity of the overlay, 0 to 100 percent");
        advancedButton.setToolTipText("Full tables, thresholds and copy-search counts");
    }

    /**
     * Handles the buttons, the view list and the overlay check box.
     *
     * @param e the event; its source tells which control was used
     */
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == openButton) {
            openImage();
        } else if (e.getSource() == analyzeButton) {
            analyze();
        } else if (e.getSource() == view) {
            showView();
        } else if (e.getSource() == showHeat) {
            imagePanel.setShowHeat(showHeat.isSelected());
        } else if (e.getSource() == wholeButton) {
            showWholeImage();
        } else if (e.getSource() == advancedButton) {
            showAdvanced();
        }
    }

    /**
     * Handles the opacity slider: passes its value to the image panel.
     *
     * @param e the slider event
     */
    public void stateChanged(ChangeEvent e) {
        imagePanel.setOpacity(opacity.getValue());
    }

    /** Mouse click on the image: selects the tile under the mouse. */
    class ClickHandler extends MouseAdapter {
        /**
         * Selects the tile under the mouse. It does nothing before the analysis is done
         * or when the click is outside the image.
         *
         * @param e the mouse event
         */
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

    /**
     * Selects one tile. Only the histogram and the small region summary change.
     * The assessment stays as it is. An analysis must exist.
     *
     * @param row tile row, from 0
     * @param col tile column, from 0
     */
    void selectTile(int row, int col) {
        Regions r = analysis.regions;
        imagePanel.setSelected(r.bounds(row, col));
        histogramPanel.setTitle("Gray histogram of tile row " + row + ", col " + col);
        histogramPanel.setValues(r.histograms[row][col]);
        regionSummary.setText(regionText(analysis, row, col));
        regionSummary.setCaretPosition(0);
        wholeButton.setEnabled(true);
    }

    /** Removes the tile selection and shows the whole-image histogram again. */
    void showWholeImage() {
        imagePanel.setSelected(null);
        wholeButton.setEnabled(false);
        if (analysis == null) {
            return;
        }
        histogramPanel.setTitle("Gray histogram (whole image)");
        histogramPanel.setValues(analysis.histogram);
        regionSummary.setText("Click a tile on the image to inspect it.");
    }

    /**
     * Short summary of one tile: its position and size, whether the local rule
     * flagged it, three measurements (histogram difference, noise variation,
     * edge strength) and one sentence of interpretation.
     * The sentence describes measurements only. It does not say the tile was edited.
     *
     * @param a   the finished analysis
     * @param row tile row, from 0
     * @param col tile column, from 0
     * @return the summary text
     */
    static String regionText(Analysis a, int row, int col) {
        Regions r = a.regions;
        Rectangle b = r.bounds(row, col);
        LocalAnomaly local = a.assessment == null ? null : a.assessment.local;
        boolean known = local != null && local.available;
        String meaning;
        if (!known) {
            meaning = "There is not enough nearby context to compare this region.";
        } else if (local.flagged[row][col]) {
            meaning = "This region and a neighbour differ from nearby content in brightness distribution and noise. "
                    + "Normal textures and object boundaries can cause this.";
        } else if (local.boundaryExplained[row][col]) {
            meaning = "Strong edges here explain much of the difference. This looks like an ordinary boundary or texture.";
        } else if (local.histogramUnusual[row][col] || local.noiseUnusual[row][col]) {
            meaning = "One measurement differs from nearby regions, but the others do not support it.";
        } else {
            meaning = "Nothing here stood out from nearby regions.";
        }
        String flagText = !known ? "not available" : (local.flagged[row][col] ? "yes" : "no");
        return String.format(Locale.ROOT, "Position: (%d, %d)   Size: %d × %d\nFlagged by the rule: %s\n"
                + "Histogram difference: %.2f\nNoise variation: %.1f\nEdge strength: %.1f\n\nInterpretation: %s",
                b.x, b.y, b.width, b.height, flagText, r.variation[row][col], a.noise[row][col], a.edges[row][col], meaning);
    }

    /** Lets the user choose a file and loads it. If the user cancels, the current image stays. */
    void openImage() {
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return; // The user cancelled.
        }
        loadImage(chooser.getSelectedFile());
    }

    /**
     * Starts a background job that loads a file. It gets a new job number.
     * No file work runs on the Swing thread. It is shared by the file chooser
     * and the desktop integration test.
     *
     * @param file the PNG or JPEG file to load
     */
    void loadImage(File file) {
        jobNumber++;
        busy = true;
        showAssessment(null);
        updateButtons();
        status.setText("Loading " + file.getName() + "...");
        new LoadWorker(file, jobNumber).execute();
    }

    /**
     * Starts a background job that analyzes the current image. It gets a new
     * job number. It does nothing if no image is loaded or a job is running.
     */
    void analyze() {
        if (current == null || busy) return;
        clearResults();
        jobNumber++;
        busy = true;
        updateButtons();
        status.setText("Analysing...");
        new AnalyzeWorker(current, jobNumber).execute();
    }

    /** Enables the Analyze button only when an image is loaded and no job is running. */
    void updateButtons() {
        analyzeButton.setEnabled(current != null && !busy);
    }

    /** Clears everything that belonged to the previous image and shows the new image without overlays. */
    void clearResults() {
        analysis = null;
        showAssessment(null);
        view.setEnabled(false);
        wholeButton.setEnabled(false);
        advancedButton.setEnabled(false);
        if (advancedDialog != null) {
            advancedDialog.dispose();
            advancedDialog = null;
        }
        imagePanel.setImage(current.image);
        imagePanel.setHeat(null, 0, "");
        imagePanel.setPairs(null);
        imagePanel.setFlags(null, 0);
        imagePanel.setRegions(null);
        imagePanel.setSelected(null);
        histogramPanel.setTitle("Gray histogram (whole image)");
        histogramPanel.setValues(null);
        regionSummary.setText(NOTE);
    }

    /**
     * One line describing a loaded image.
     *
     * @param loaded the loaded image
     * @return the file name, the size and the format
     */
    String describe(LoadedImage loaded) {
        return loaded.file.getName() + "  " + loaded.image.getWidth() + " x " + loaded.image.getHeight()
                + "  " + loaded.format;
    }

    /**
     * Shows a finished analysis: the assessment, the whole-image histogram
     * and the summary view (flagged regions).
     *
     * @param a the finished analysis of the current image
     */
    void showResults(Analysis a) {
        analysis = a;
        showAssessment(a.assessment);
        histogramPanel.setTitle("Gray histogram (whole image)");
        histogramPanel.setValues(a.histogram);
        imagePanel.setSelected(null);
        view.setEnabled(true);
        advancedButton.setEnabled(true);
        wholeButton.setEnabled(false);
        view.setSelectedIndex(0); // View 0 is the summary of flagged regions.
        showView();
        regionSummary.setText("Click a tile on the image to inspect it.");
    }

    /** Draws the view chosen in the list ({@link #VIEWS}) on the image. It does nothing before the analysis is done. */
    void showView() {
        if (analysis == null) {
            return;
        }
        int size = analysis.regions.tileSize;
        int choice = view.getSelectedIndex();
        imagePanel.setImage(current.image);
        imagePanel.setPairs(null);
        imagePanel.setFlags(null, 0);
        imagePanel.setRegions(null);
        imagePanel.setHeat(null, 0, "");
        TamperingAssessment t = analysis.assessment;
        if (choice == 0) {
            if (t != null && t.local != null && t.local.available) {
                imagePanel.setFlags(t.local.flagged, size);
            }
            if (t != null && t.sourceBounds() != null) {
                imagePanel.setPairs(analysis.copyMove.matches);
            }
            if (t != null) {
                imagePanel.setRegions(t.flatRegions);
            }
        } else if (choice == 1) {
            imagePanel.setHeat(analysis.regions.variation, size, "Regional variation (TVD to neighbour tiles)");
        } else if (choice == 2) {
            imagePanel.setHeat(analysis.noise, size, "Noise residual variance per tile");
        } else if (choice == 3) {
            imagePanel.setHeat(analysis.edges, size, "Mean Sobel edge strength per tile");
        } else if (choice == 4) {
            imagePanel.setImage(analysis.edgeMap);
        } else if (choice == 5) {
            imagePanel.setPairs(analysis.copyMove.matches);
        }
        // The three heatmaps also mark the flagged tiles, so they can be compared.
        if ((choice == 1 || choice == 2 || choice == 3) && t != null && t.local != null && t.local.available) {
            imagePanel.setFlags(t.local.flagged, size);
        }
    }

    /**
     * Short statistics line with numbers that were really calculated.
     *
     * @param a the finished analysis
     * @return mean gray value, standard deviation, entropy and the highest regional difference
     */
    static String statsText(Analysis a) {
        double highest = 0;
        for (double[] row : a.regions.variation) {
            for (double value : row) {
                highest = Math.max(highest, value);
            }
        }
        return String.format(Locale.ROOT, "Brightness %.1f / 255 | Contrast (std) %.1f | Entropy %.2f bits | Highest regional difference %.2f",
                a.mean, a.standardDeviation, a.entropy, highest);
    }

    /**
     * Shows the final assessment (outcome, measured area, reasons and statistics).
     * The background colour follows the outcome.
     *
     * @param assessment the assessment, or {@code null} to show "Not analyzed"
     */
    void showAssessment(TamperingAssessment assessment) {
        Color background = new Color(236, 238, 240);
        assessmentStatus.setText(assessment == null ? "Not analyzed" : assessment.title());
        areaLabel.setText(assessment == null ? " " : "ANOMALOUS AREA: " + assessment.areaText());
        StringBuilder explanation = new StringBuilder("WHY?\n");
        if (assessment == null) {
            explanation.append("Analyze the current image to calculate its assessment.");
            statsLabel.setText(" ");
        } else {
            if (assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING) background = new Color(255, 239, 207);
            if (assessment.outcome == TamperingAssessment.Outcome.NO_CLEAR_INDICATORS) background = new Color(248, 248, 248);
            for (String reason : assessment.reasons) explanation.append("- ").append(reason).append('\n');
            explanation.append('\n').append(TamperingAssessment.NOTE);
            statsLabel.setText(analysis == null ? " " : "<html>" + statsText(analysis).replace(" | Entropy", "<br>Entropy") + "</html>");
        }
        assessmentPanel.setBackground(background);
        assessmentReasons.setBackground(background);
        assessmentReasons.setText(explanation.toString());
        assessmentReasons.setCaretPosition(0);
    }

    /**
     * Opens the Advanced details window: everything in full (assessment,
     * rules, tables, copy-move counts) as read-only text. An older details
     * window is closed first.
     */
    void showAdvanced() {
        if (analysis == null) {
            return;
        }
        if (advancedDialog != null) {
            advancedDialog.dispose();
        }
        JTextArea text = new JTextArea(advancedText(analysis));
        text.setEditable(false);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setCaretPosition(0);
        text.getAccessibleContext().setAccessibleName("Advanced details");
        advancedDialog = new JDialog(frame, "Advanced details", false);
        advancedDialog.add(new JScrollPane(text));
        advancedDialog.setSize(760, 600);
        advancedDialog.setLocationRelativeTo(frame);
        advancedDialog.setVisible(true);
    }

    /**
     * Builds the text of the Advanced details window.
     *
     * @param a the finished analysis
     * @return the assessment details, whole-image numbers, the tile tables and the copy-move counts
     */
    String advancedText(Analysis a) {
        Regions r = a.regions;
        return (a.assessment == null ? "" : a.assessment.details() + "\n")
                + "Image: " + describe(current) + "\n\n"
                + "Whole image\n"
                + String.format(Locale.ROOT, "  Mean gray:          %.2f%n", a.mean)
                + String.format(Locale.ROOT, "  Standard deviation: %.2f%n", a.standardDeviation)
                + String.format(Locale.ROOT, "  Entropy:            %.3f bits (0 to 8)%n", a.entropy)
                + "\nRegional variation, " + r.rows + " x " + r.cols + " tiles of " + r.tileSize + " px\n"
                + "(average TVD to the tiles above, below, left, right; 0 = same, 1 = no overlap)\n"
                + grid(r.variation, "%6.3f")
                + "\nNoise residual variance per tile\n"
                + "(gray minus 3 x 3 local mean; texture and edges also raise it)\n"
                + grid(a.noise, "%8.2f")
                + "\nMean Sobel edge strength per tile (0 to " + Math.round(Edges.MAX_STRENGTH) + ")\n"
                + "(normal object outlines are strong edges)\n"
                + grid(a.edges, "%8.2f")
                + "\n" + copyMoveText(a.copyMove)
                + "\n" + NOTE;
    }

    /**
     * Text about the copy-move search: counts, a warning if a limit was reached,
     * and the first 10 candidate pairs.
     *
     * @param c the copy-move result
     * @return the text
     */
    static String copyMoveText(CopyMove c) {
        String text = "Copy-move candidates (exact 16 x 16 blocks, same shift at least "
                + CopyMove.MIN_GROUP + " times)\n"
                + "  Blocks checked: " + c.blocksChecked + " of " + c.blocksTotal
                + String.format(Locale.ROOT, " (%.0f%% of the image)", c.coveragePercent())
                + ", comparisons: " + c.comparisons + "\n"
                + "  Candidate pairs: " + c.matches.size() + "\n";
        if (c.truncated) {
            text = text + "  LIMIT REACHED (limits " + CopyMove.MAX_BLOCKS + " blocks, " + CopyMove.MAX_COMPARISONS
                    + " comparisons, " + CopyMove.MAX_MATCHES + " pairs shown): the search is incomplete.\n";
        }
        for (int i = 0; i < c.matches.size() && i < 10; i++) {
            CopyMove.Match m = c.matches.get(i);
            text = text + "  (" + m.x1 + ", " + m.y1 + ") = (" + m.x2 + ", " + m.y2 + ")\n";
        }
        if (c.matches.size() > 10) {
            text = text + "  ... " + (c.matches.size() - 10) + " more\n";
        }
        text = text + "  Repeated patterns also match. No candidates does not mean authentic.\n";
        return text;
    }

    /**
     * Writes the numbers of a grid, one line per row.
     *
     * @param values the grid as {@code [row][col]}
     * @param format a {@link String#format} pattern for one number
     * @return the text
     */
    static String grid(double[][] values, String format) {
        StringBuilder text = new StringBuilder();
        for (int row = 0; row < values.length; row++) {
            for (int col = 0; col < values[row].length; col++) {
                text.append(String.format(Locale.ROOT, format, values[row][col]));
            }
            text.append("\n");
        }
        return text.toString();
    }

    /**
     * Shows an error dialog.
     *
     * @param message the text to show
     */
    void showError(String message) {
        JOptionPane.showMessageDialog(frame, message, "Problem", JOptionPane.ERROR_MESSAGE);
    }

    /** Background job that loads a file. It carries the job number it was started with. */
    class LoadWorker extends SwingWorker<LoadedImage, Void> {
        File file;
        int myJob;

        /**
         * Creates the job.
         *
         * @param file  the file to load
         * @param myJob the job number of this job
         */
        LoadWorker(File file, int myJob) {
            this.file = file;
            this.myJob = myJob;
        }

        /**
         * Loads the file on a background thread.
         *
         * @return the loaded image
         * @throws Exception if the file cannot be loaded (see {@link LoadedImage#load(File)})
         */
        protected LoadedImage doInBackground() throws Exception {
            return LoadedImage.load(file);
        }

        /**
         * Runs on the Swing thread when loading is finished.
         * The result is ignored if this job is not the newest one.
         */
        protected void done() {
            if (myJob != jobNumber) {
                return; // A newer job started, so ignore this one.
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
                showAssessment(analysis == null ? null : analysis.assessment);
                if (current == null) {
                    status.setText("Open a PNG or JPEG image.");
                } else {
                    status.setText(describe(current));
                }
            }
            updateButtons();
        }
    }

    /** Background job that runs all measurements. It carries the job number it was started with. */
    class AnalyzeWorker extends SwingWorker<Analysis, Void> {
        LoadedImage loaded;
        int myJob;

        /**
         * Creates the job.
         *
         * @param loaded the image to analyze
         * @param myJob  the job number of this job
         */
        AnalyzeWorker(LoadedImage loaded, int myJob) {
            this.loaded = loaded;
            this.myJob = myJob;
        }

        /**
         * Runs the analysis on a background thread.
         *
         * @return the finished analysis
         */
        protected Analysis doInBackground() {
            return Analysis.run(loaded.image, loaded.format);
        }

        /**
         * Runs on the Swing thread when the analysis is finished.
         * The result is ignored if this job is not the newest one.
         */
        protected void done() {
            if (myJob != jobNumber) {
                return; // The image was replaced meanwhile, so ignore this result.
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

    /**
     * Finds the useful message inside an exception from a worker.
     *
     * @param e the exception
     * @return the message of the cause if there is one, otherwise the message of {@code e}
     */
    static String errorText(Exception e) {
        Throwable cause = e;
        if (e.getCause() != null) {
            cause = e.getCause();
        }
        return cause.getMessage();
    }

    /**
     * Starts the program: creates the window on the Swing thread.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new App();
            }
        });
    }
}
