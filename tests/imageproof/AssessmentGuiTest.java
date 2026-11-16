package imageproof;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.swing.SwingUtilities;

/** Real App worker/state integration. Run separately on a desktop, never as a headless CI test. */
public class AssessmentGuiTest {
    private static TestApp app;
    private static int checks;
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
    private static class TestApp extends App {
        String lastError;
        @Override void showError(String message) { lastError = message; } // Test errors without blocking on a modal.
    }
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for App state");
    }

    public static void main(String[] args) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Desktop required");
        File samples = new File(System.getProperty("project.dir", "."), "samples");
        LoadedImage original = LoadedImage.load(new File(samples, "original.png"));
        LoadedImage copied = LoadedImage.load(new File(samples, "copied.png"));
        Analysis oldResult = Analysis.run(original.image);
        CountDownLatch releaseAnalysis = new CountDownLatch(1), releaseLoad = new CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(() -> {
                app = new TestApp(); app.frame.setTitle("Assessment integration verification");
                app.loadImage(original.file);
            });
            waitFor(() -> !app.busy && app.current != null);
            SwingUtilities.invokeAndWait(() -> {
                app.analyze(); int job = app.jobNumber; app.analyze();
                check(app.jobNumber == job && !app.analyzeButton.isEnabled(), "duplicate Analyze ignored while busy");
            });
            waitFor(() -> !app.busy && app.analysis != null);
            SwingUtilities.invokeAndWait(() -> check(app.assessmentStatus.getText().equals("No clear tampering indicators"), "original limited assessment visible"));
            SwingUtilities.invokeAndWait(() -> {
                app.loadImage(copied.file);
                check(app.assessmentStatus.getText().equals("Not analyzed"), "new selection immediately hides previous conclusion");
            });
            waitFor(() -> !app.busy && app.current.file.equals(copied.file));
            SwingUtilities.invokeAndWait(() -> {
                check(app.analysis == null && app.assessmentStatus.getText().equals("Not analyzed"), "replacement clears assessment");
                check(!app.results.getText().contains("FINAL IMAGE ASSESSMENT"), "old explanation removed");
                app.analyze();
            });
            waitFor(() -> !app.busy && app.analysis != null);
            SwingUtilities.invokeAndWait(() -> {
                check(app.assessmentStatus.getText().equals("Possible tampering detected"), "copy result visible");
                check(app.view.getSelectedIndex() == 4, "specific candidate areas are shown");
                app.lastError = null; app.loadImage(new File(samples, "corrupt.png"));
            });
            waitFor(() -> !app.busy && app.lastError != null);
            SwingUtilities.invokeAndWait(() -> {
                check(app.current.file.equals(copied.file), "invalid load preserves matching input");
                check(app.analysis != null && app.assessmentStatus.getText().equals("Possible tampering detected"), "invalid load preserves only valid current assessment");
            });

            CountDownLatch oldAnalysisDone = new CountDownLatch(1), entered = new CountDownLatch(1);
            SwingUtilities.invokeAndWait(() -> {
                app.busy = true; app.updateButtons();
                app.new AnalyzeWorker(original, ++app.jobNumber) {
                    @Override protected Analysis doInBackground() {
                        if (SwingUtilities.isEventDispatchThread()) throw new AssertionError("Heavy work on EDT");
                        entered.countDown();
                        try { releaseAnalysis.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return oldResult;
                    }
                    @Override protected void done() { super.done(); oldAnalysisDone.countDown(); }
                }.execute();
            });
            check(entered.await(5, TimeUnit.SECONDS), "background worker entered");
            CountDownLatch heartbeat = new CountDownLatch(1);
            SwingUtilities.invokeLater(heartbeat::countDown);
            check(heartbeat.await(2, TimeUnit.SECONDS), "EDT responds while analysis worker is blocked");
            SwingUtilities.invokeAndWait(() -> app.loadImage(new File(samples, "inserted.png")));
            waitFor(() -> !app.busy && app.current.file.getName().equals("inserted.png"));
            SwingUtilities.invokeAndWait(() -> app.analyze());
            waitFor(() -> !app.busy && app.analysis != null);
            Analysis[] newest = new Analysis[1];
            SwingUtilities.invokeAndWait(() -> newest[0] = app.analysis);
            releaseAnalysis.countDown(); check(oldAnalysisDone.await(5, TimeUnit.SECONDS), "old analysis done executed");
            SwingUtilities.invokeAndWait(() -> check(app.analysis == newest[0] && app.current.file.getName().equals("inserted.png"), "stale analysis cannot overwrite newer assessment"));

            CountDownLatch oldLoadDone = new CountDownLatch(1);
            SwingUtilities.invokeAndWait(() -> {
                app.new LoadWorker(copied.file, ++app.jobNumber) {
                    @Override protected LoadedImage doInBackground() {
                        try { releaseLoad.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                        return copied;
                    }
                    @Override protected void done() { super.done(); oldLoadDone.countDown(); }
                }.execute();
                app.loadImage(new File(samples, "grayscale.png"));
            });
            waitFor(() -> !app.busy && app.current.file.getName().equals("grayscale.png"));
            releaseLoad.countDown(); check(oldLoadDone.await(5, TimeUnit.SECONDS), "old load done executed");
            SwingUtilities.invokeAndWait(() -> {
                check(app.current.file.getName().equals("grayscale.png") && app.analysis == null, "stale load cannot restore prior conclusion");
                app.lastError = null; app.clearResults(); app.busy = true;
                app.new AnalyzeWorker(app.current, ++app.jobNumber) {
                    @Override protected Analysis doInBackground() { throw new IllegalStateException("Controlled analysis failure"); }
                }.execute();
            });
            waitFor(() -> !app.busy && app.lastError != null);
            SwingUtilities.invokeAndWait(() -> {
                check(app.lastError.contains("Analysis failed") && app.assessmentStatus.getText().equals("Not analyzed"), "failure does not fabricate a conclusion");
                check(app.analyzeButton.isEnabled(), "failure permits retry");
            });
            System.out.println("AssessmentGuiTest: " + checks + " checks passed; real App workers/EDT, stale load/analysis, replacement, invalid load and failure state");
        } finally {
            releaseAnalysis.countDown(); releaseLoad.countDown();
            if (app != null) SwingUtilities.invokeAndWait(() -> app.frame.dispose());
        }
    }
}
