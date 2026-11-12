package imageproof;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Integration test for the real visible App; run separately from headless Maven tests. */
public final class AppDesktopTest {
    private static App app;
    private static void waitFor(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45);
        while (System.nanoTime() < deadline) {
            AtomicBoolean ready = new AtomicBoolean();
            SwingUtilities.invokeAndWait(() -> ready.set(condition.getAsBoolean()));
            if (ready.get()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Timed out waiting for App worker state");
    }
    public static void main(String[] args) throws Exception {
        if (java.awt.GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Graphical desktop required");
        Path scratch = Files.createTempDirectory("imageproof-app-test-");
        try {
            Path large = scratch.resolve("large.png"), small = scratch.resolve("small.png");
            ImageIO.write(new BufferedImage(4000, 3000, BufferedImage.TYPE_INT_RGB), "png", large.toFile());
            ImageIO.write(AnalyzerTest.copiedTexture(), "png", small.toFile());
            SwingUtilities.invokeAndWait(() -> { app = new App(); app.loadFile(large.toFile()); });
            waitFor(() -> app.image != null && app.image.getWidth() == 4000 && app.analyze.isEnabled());
            long started = System.nanoTime();
            CountDownLatch heartbeat = new CountDownLatch(1);
            SwingUtilities.invokeAndWait(() -> { app.analyzeImage(); SwingUtilities.invokeLater(heartbeat::countDown); });
            if (!heartbeat.await(2, TimeUnit.SECONDS)) throw new AssertionError("EDT blocked by image analysis");
            long heartbeatMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            waitFor(() -> app.result != null);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            SwingUtilities.invokeAndWait(() -> { app.analyzeImage(); app.loadFile(small.toFile()); });
            waitFor(() -> app.image.getWidth() == 96 && app.analyze.isEnabled());
            SwingUtilities.invokeAndWait(() -> {
                if (app.result != null || !app.grid.getText().isEmpty()) throw new AssertionError("Stale result after replacement");
                app.analyzeImage();
            });
            waitFor(() -> app.result != null);
            SwingUtilities.invokeAndWait(() -> {
                if (app.result.copies().matches().size() != 9) throw new AssertionError("Wrong image analysis published");
                if (!app.details.getText().contains("96 × 96")) throw new AssertionError("Stale image dimensions");
                app.frame.setSize(940, 650);
            });
            System.out.println("AppDesktopTest OK: 12 MP analysis " + elapsedMs + " ms; EDT heartbeat " + heartbeatMs
                    + " ms; replacement cleared old results; newer image published 9 pairs; minimum-size window applied.");
        } finally {
            if (app != null) SwingUtilities.invokeAndWait(() -> app.frame.dispose());
            try (var paths = Files.walk(scratch)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
