package imageproof;

import java.awt.Color;
import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.SwingUtilities;

/**
 * Records short GIF demos of the REAL application window (needs a desktop, not headless).
 * It moves the real mouse and clicks with {@link Robot}, grabs the window about 8 times a second,
 * draws the mouse pointer on each frame and writes the GIF with the JDK's own ImageIO.
 * Nothing here is a runtime dependency of the application.
 *
 * <p>Run: {@code java -cp target/classes;target/test-classes imageproof.DemoGifs <sampleFolder> <outputFolder>}
 * where the sample folder holds copies of the samples in a neutral path (it is shown in the file chooser).
 */
public class DemoGifs {

    static final int WIDTH = 760;      // GIF width in pixels
    static final int FRAME_MS = 120;   // time between captures

    static App app;
    static Robot robot;
    static File samples;
    static File output;
    static Recorder recorder;

    // ---------------------------------------------------------------- recording

    /** Grabs the window in a background thread until stopped. */
    static class Recorder extends Thread {
        final ArrayList<int[]> pixels = new ArrayList<int[]>();
        final ArrayList<Long> times = new ArrayList<Long>();
        int width;
        int height;
        volatile boolean running = true;

        public void run() {
            while (running) {
                try {
                    Rectangle area = windowArea();
                    BufferedImage shot = robot.createScreenCapture(area);
                    drawPointer(shot, area);
                    BufferedImage small = scale(shot, WIDTH);
                    width = small.getWidth();
                    height = small.getHeight();
                    synchronized (pixels) {
                        pixels.add(small.getRGB(0, 0, width, height, null, 0, width));
                        times.add(Long.valueOf(System.currentTimeMillis()));
                    }
                    Thread.sleep(FRAME_MS);
                } catch (Exception e) {
                    return;
                }
            }
        }
    }

    static Rectangle windowArea() throws Exception {
        Rectangle[] box = new Rectangle[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                box[0] = new Rectangle(app.frame.getLocationOnScreen(), app.frame.getSize());
            }
        });
        return box[0];
    }

    // Robot screenshots have no mouse pointer, so draw a simple arrow where the pointer is.
    static void drawPointer(BufferedImage shot, Rectangle area) {
        Point p = MouseInfo.getPointerInfo().getLocation();
        int x = p.x - area.x;
        int y = p.y - area.y;
        if (x < 0 || y < 0 || x >= shot.getWidth() || y >= shot.getHeight()) {
            return;
        }
        java.awt.Graphics2D g = shot.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Polygon arrow = new Polygon(new int[] {x, x, x + 5, x + 8, x + 11, x + 8, x + 14},
                new int[] {y, y + 19, y + 14, y + 22, y + 21, y + 13, y + 13}, 7);
        g.setColor(Color.WHITE);
        g.fill(arrow);
        g.setColor(Color.BLACK);
        g.draw(arrow);
        g.dispose();
    }

    static BufferedImage scale(BufferedImage src, int width) {
        int height = src.getHeight() * width / src.getWidth();
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, width, height, null);
        g.dispose();
        return out;
    }

    static void startRecording() {
        recorder = new Recorder();
        recorder.start();
    }

    static void stopRecording(String name) throws Exception {
        recorder.running = false;
        recorder.join();
        writeGif(recorder, new File(output, name));
    }

    // ---------------------------------------------------------------- GIF writing

    // Picks the 255 most common colours of all frames, so interface text stays sharp.
    static IndexColorModel palette(ArrayList<int[]> frames) {
        int[] count = new int[32768];
        for (int[] frame : frames) {
            for (int i = 0; i < frame.length; i += 3) {
                int c = frame[i];
                count[((c >> 19) & 31) << 10 | ((c >> 11) & 31) << 5 | ((c >> 3) & 31)]++;
            }
        }
        byte[] r = new byte[256];
        byte[] g = new byte[256];
        byte[] b = new byte[256];
        for (int k = 0; k < 256; k++) {
            int best = 0;
            for (int c = 1; c < count.length; c++) {
                if (count[c] > count[best]) {
                    best = c;
                }
            }
            r[k] = (byte) (((best >> 10) & 31) * 255 / 31);
            g[k] = (byte) (((best >> 5) & 31) * 255 / 31);
            b[k] = (byte) ((best & 31) * 255 / 31);
            count[best] = -1; // used
        }
        return new IndexColorModel(8, 256, r, g, b);
    }

    static void writeGif(Recorder rec, File file) throws Exception {
        // Merge frames that look identical (a still window) into one longer frame.
        ArrayList<int[]> frames = new ArrayList<int[]>();
        ArrayList<Integer> delays = new ArrayList<Integer>();
        for (int i = 0; i < rec.pixels.size(); i++) {
            long next = i + 1 < rec.times.size() ? rec.times.get(i + 1).longValue() : rec.times.get(i).longValue() + 1500;
            int delay = (int) Math.max(1, (next - rec.times.get(i).longValue()) / 10);
            if (!frames.isEmpty() && Arrays.equals(frames.get(frames.size() - 1), rec.pixels.get(i))) {
                delays.set(delays.size() - 1, Integer.valueOf(delays.get(delays.size() - 1).intValue() + delay));
            } else {
                frames.add(rec.pixels.get(i));
                delays.add(Integer.valueOf(delay));
            }
        }
        IndexColorModel model = palette(frames);
        int[] nearest = new int[32768];
        Arrays.fill(nearest, -1);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ImageOutputStream out = ImageIO.createImageOutputStream(file);
        writer.setOutput(out);
        writer.prepareWriteSequence(null);
        for (int f = 0; f < frames.size(); f++) {
            BufferedImage indexed = new BufferedImage(rec.width, rec.height, BufferedImage.TYPE_BYTE_INDEXED, model);
            byte[] data = ((java.awt.image.DataBufferByte) indexed.getRaster().getDataBuffer()).getData();
            int[] px = frames.get(f);
            for (int i = 0; i < px.length; i++) {
                int c = px[i];
                int key = ((c >> 19) & 31) << 10 | ((c >> 11) & 31) << 5 | ((c >> 3) & 31);
                if (nearest[key] < 0) {
                    nearest[key] = closest(model, (c >> 16) & 255, (c >> 8) & 255, c & 255);
                }
                data[i] = (byte) nearest[key];
            }
            IIOMetadata meta = writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(indexed), null);
            String format = meta.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) meta.getAsTree(format);
            // Write our own palette into the frame (the writer would otherwise use a default one).
            for (String old : new String[] {"GlobalColorTable", "LocalColorTable"}) {
                for (int i = root.getLength() - 1; i >= 0; i--) {
                    if (root.item(i).getNodeName().equals(old)) {
                        root.removeChild(root.item(i));
                    }
                }
            }
            IIOMetadataNode table = new IIOMetadataNode("LocalColorTable");
            table.setAttribute("sizeOfLocalColorTable", "256");
            table.setAttribute("sortFlag", "FALSE");
            for (int i = 0; i < 256; i++) {
                IIOMetadataNode entry = new IIOMetadataNode("ColorTableEntry");
                entry.setAttribute("index", String.valueOf(i));
                entry.setAttribute("red", String.valueOf(model.getRed(i)));
                entry.setAttribute("green", String.valueOf(model.getGreen(i)));
                entry.setAttribute("blue", String.valueOf(model.getBlue(i)));
                table.appendChild(entry);
            }
            root.insertBefore(table, root.getFirstChild() == null ? null : child(root, "ImageDescriptor").getNextSibling());
            IIOMetadataNode control = child(root, "GraphicControlExtension");
            control.setAttribute("disposalMethod", "none");
            control.setAttribute("userInputFlag", "FALSE");
            control.setAttribute("transparentColorFlag", "FALSE");
            control.setAttribute("delayTime", String.valueOf(delays.get(f)));
            control.setAttribute("transparentColorIndex", "0");
            if (f == 0) {
                IIOMetadataNode apps = child(root, "ApplicationExtensions");
                IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                loop.setAttribute("applicationID", "NETSCAPE");
                loop.setAttribute("authenticationCode", "2.0");
                loop.setUserObject(new byte[] {1, 0, 0}); // loop forever
                apps.appendChild(loop);
            }
            meta.setFromTree(format, root);
            writer.writeToSequence(new IIOImage(indexed, null, meta), null);
        }
        writer.endWriteSequence();
        out.close();
        writer.dispose();
        long total = 0;
        for (Integer d : delays) {
            total += d.intValue();
        }
        System.out.println(file.getName() + ": " + frames.size() + " frames, " + rec.width + "x" + rec.height + ", "
                + String.format("%.1f", total / 100.0) + " s, " + file.length() / 1024 + " KB");
    }

    static int closest(IndexColorModel m, int r, int g, int b) {
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < m.getMapSize(); i++) {
            long dr = m.getRed(i) - r, dg = m.getGreen(i) - g, db = m.getBlue(i) - b;
            long d = dr * dr * 3 + dg * dg * 4 + db * db * 2;
            if (d < bestDistance) {
                bestDistance = d;
                best = i;
            }
        }
        return best;
    }

    static IIOMetadataNode child(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            if (root.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) root.item(i);
            }
        }
        IIOMetadataNode node = new IIOMetadataNode(name);
        root.appendChild(node);
        return node;
    }

    // ---------------------------------------------------------------- driving the real window

    static void on(Runnable r) throws Exception {
        SwingUtilities.invokeAndWait(r);
    }

    static void pause(int ms) throws Exception {
        Thread.sleep(ms);
    }

    static void waitIdle() throws Exception {
        while (true) {
            AtomicBoolean busy = new AtomicBoolean();
            on(new Runnable() {
                public void run() {
                    busy.set(app.busy);
                }
            });
            if (!busy.get()) {
                return;
            }
            pause(50);
        }
    }

    // Moves the real mouse to a point on screen in small steps, like a hand would.
    static void moveTo(int x, int y) throws Exception {
        Point from = MouseInfo.getPointerInfo().getLocation();
        int steps = 22;
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            t = t * t * (3 - 2 * t);
            robot.mouseMove((int) (from.x + (x - from.x) * t), (int) (from.y + (y - from.y) * t));
            pause(16);
        }
    }

    static Point centerOf(Component c) throws Exception {
        Point[] p = new Point[1];
        on(new Runnable() {
            public void run() {
                p[0] = c.getLocationOnScreen();
                p[0].translate(c.getWidth() / 2, c.getHeight() / 2);
            }
        });
        return p[0];
    }

    static void clickAt(Point p) throws Exception {
        moveTo(p.x, p.y);
        pause(250);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        pause(80);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        pause(250);
    }

    static void click(Component c) throws Exception {
        clickAt(centerOf(c));
    }

    static void clickTile(int row, int col) throws Exception {
        Point[] p = new Point[1];
        on(new Runnable() {
            public void run() {
                Rectangle r = app.imagePanel.toPanel(app.analysis.regions.bounds(row, col));
                p[0] = new Point(r.x + r.width / 2, r.y + r.height / 2);
                SwingUtilities.convertPointToScreen(p[0], app.imagePanel);
            }
        });
        clickAt(p[0]);
    }

    static void key(int code) throws Exception {
        robot.keyPress(code);
        pause(40);
        robot.keyRelease(code);
        pause(120);
    }

    // Opens the real file chooser, pastes the file path and presses Enter.
    static void chooseFile(String name) throws Exception {
        on(new Runnable() {
            public void run() {
                app.chooser.setCurrentDirectory(samples);
            }
        });
        click(app.openButton);
        pause(1400);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(new File(samples, name).getAbsolutePath()), null);
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);
        pause(900);
        key(KeyEvent.VK_ENTER);
        pause(300);
        waitIdle();
        pause(700);
    }

    static void analyzeWithMouse() throws Exception {
        click(app.analyzeButton);
        waitIdle();
        pause(900);
    }

    // Chooses an entry of the view list with the mouse and keyboard.
    static void chooseView(int index) throws Exception {
        click(app.view);
        pause(200);
        int current = app.view.getSelectedIndex();
        int steps = index - current;
        for (int i = 0; i < Math.abs(steps); i++) {
            key(steps > 0 ? KeyEvent.VK_DOWN : KeyEvent.VK_UP);
        }
        pause(120);
        key(KeyEvent.VK_ENTER);
        pause(600);
    }

    static void dragOpacity(int target) throws Exception {
        Point[] thumb = new Point[1];
        Point[] goal = new Point[1];
        on(new Runnable() {
            public void run() {
                int left = 8;
                int span = app.opacity.getWidth() - 16;
                int y = app.opacity.getHeight() / 2;
                thumb[0] = new Point(left + span * app.opacity.getValue() / 100, y);
                goal[0] = new Point(left + span * target / 100, y);
                SwingUtilities.convertPointToScreen(thumb[0], app.opacity);
                SwingUtilities.convertPointToScreen(goal[0], app.opacity);
            }
        });
        moveTo(thumb[0].x, thumb[0].y);
        pause(200);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        pause(100);
        moveTo(goal[0].x, goal[0].y);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        pause(400);
    }

    static void loadAndAnalyzeQuietly(String name) throws Exception {
        on(new Runnable() {
            public void run() {
                app.loadImage(new File(samples, name));
            }
        });
        waitIdle();
        on(new Runnable() {
            public void run() {
                app.analyze();
            }
        });
        waitIdle();
        pause(500);
    }

    public static void main(String[] args) throws Exception {
        samples = new File(args[0]).getAbsoluteFile();
        output = new File(args[1]).getAbsoluteFile();
        output.mkdirs();
        robot = new Robot();
        on(new Runnable() {
            public void run() {
                app = new App();
                app.frame.setAlwaysOnTop(true);
            }
        });
        pause(1200);
        Toolkit.getDefaultToolkit().getSystemClipboard(); // used by chooseFile

        // GIF 1: open a sample, analyze, read the result.
        robot.mouseMove(40, 40);
        startRecording();
        pause(1200);
        chooseFile("copied.png");
        analyzeWithMouse();
        pause(3500);
        stopRecording("upload-and-analyze.gif");

        // GIF 2: whole-image histogram, click a region, back to the whole image.
        on(new Runnable() {
            public void run() {
                app.view.setSelectedIndex(1);
            }
        });
        pause(500);
        startRecording();
        pause(1800);
        clickTile(1, 1);
        pause(2200);
        clickTile(2, 3);
        pause(2200);
        click(app.wholeButton);
        pause(2500);
        stopRecording("histogram-and-regions.gif");

        // GIF 3: forensic views and the opacity slider.
        on(new Runnable() {
            public void run() {
                app.view.setSelectedIndex(0);
                app.showWholeImage();
            }
        });
        pause(500);
        startRecording();
        pause(500);
        chooseView(1);
        dragOpacity(85);
        dragOpacity(25);
        chooseView(2);
        pause(700);
        chooseView(4);
        pause(700);
        chooseView(5);
        pause(1800);
        stopRecording("forensic-views.gif");

        on(new Runnable() {
            public void run() {
                app.frame.dispose();
            }
        });
        System.exit(0);
    }
}
