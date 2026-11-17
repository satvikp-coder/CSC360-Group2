package imageproof;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

// Verify format detection from the bytes rather than the file extension.
public class LoadedImageTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("imageproof-load-");
        try {
            BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
            File png = dir.resolve("actually-png.jpg").toFile();
            File jpeg = dir.resolve("actually-jpeg.png").toFile();
            File gif = dir.resolve("unsupported.png").toFile();
            check(ImageIO.write(image, "png", png), "PNG writer available");
            check(ImageIO.write(image, "jpeg", jpeg), "JPEG writer available");
            check(ImageIO.write(image, "gif", gif), "GIF writer available");

            LoadedImage loadedPng = LoadedImage.load(png);
            LoadedImage loadedJpeg = LoadedImage.load(jpeg);
            check(loadedPng.file.equals(png) && loadedPng.format.equals("PNG"), "PNG content detected");
            check(loadedJpeg.file.equals(jpeg) && loadedJpeg.format.equals("JPEG"), "JPEG content detected");
            check(loadedPng.image.getWidth() == 3 && loadedPng.image.getHeight() == 2, "PNG dimensions");
            check(loadedJpeg.image.getWidth() == 3 && loadedJpeg.image.getHeight() == 2, "JPEG dimensions");
            try {
                LoadedImage.load(gif);
                throw new AssertionError("GIF content should be rejected");
            } catch (IOException expected) {
                check(expected.getMessage().contains("Only PNG and JPEG"), "unsupported format message");
            }
            try {
                LoadedImage.load(dir.resolve("missing.png").toFile());
                throw new AssertionError("missing file should be rejected");
            } catch (IOException expected) {
                // A missing file cannot be opened.
            }
            System.out.println("LoadedImageTest OK");
        } finally {
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) Files.delete(file);
            }
            Files.delete(dir);
        }
    }
}
