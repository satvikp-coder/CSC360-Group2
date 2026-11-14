package imageproof;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import javax.imageio.ImageIO;

// Tests loading files and the whole-image statistics.
public class AnalysisTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    static void loadFails(File file, String message) {
        try {
            LoadedImage.load(file);
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("should not load: " + message);
    }

    public static void main(String[] args) throws Exception {
        // Left half black, right half white -> mean 127.5, std 127.5, entropy 1.
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 2; x < 4; x++) {
                image.setRGB(x, y, 0xFFFFFF);
            }
        }
        Analysis a = Analysis.run(image);
        check(a.width == 4 && a.height == 2, "size");
        check(near(a.mean, 127.5), "mean");
        check(near(a.standardDeviation, 127.5), "std");
        check(near(a.entropy, 1), "entropy");

        // Loading: PNG and JPEG work, everything else gives a clear error.
        File folder = Files.createTempDirectory("imageproof-load-").toFile();
        File png = new File(folder, "ok.png");
        File jpg = new File(folder, "ok.jpg");
        File gif = new File(folder, "image.gif");
        File text = new File(folder, "notes.png");
        File broken = new File(folder, "broken.png");
        ImageIO.write(image, "png", png);
        ImageIO.write(image, "jpeg", jpg);
        ImageIO.write(image, "gif", gif);
        Files.writeString(text.toPath(), "not an image");
        byte[] bytes = Files.readAllBytes(png.toPath());
        byte[] half = new byte[bytes.length / 2];
        System.arraycopy(bytes, 0, half, 0, half.length);
        Files.write(broken.toPath(), half);

        LoadedImage loaded = LoadedImage.load(png);
        check(loaded.format.equals("PNG") && loaded.image.getWidth() == 4, "png loads");
        check(LoadedImage.load(jpg).format.equals("JPEG"), "jpeg loads");
        loadFails(gif, "gif");
        loadFails(text, "text with .png name");
        loadFails(broken, "cut-off png");
        loadFails(new File(folder, "missing.png"), "missing file");

        File[] files = folder.listFiles();
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
        }
        folder.delete();
        System.out.println("AnalysisTest OK");
    }
}
