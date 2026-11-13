package imageproof;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.Random;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

// Makes the sample images in samples/. All pixels are made by this code (no outside photos),
// so the group may share them. Same seed -> same pixels every time.
// Run: java -cp target/classes:target/test-classes imageproof.SampleGenerator samples
public class SampleGenerator {

    public static final long SEED = 3602026L;
    public static final int WIDTH = 256;
    public static final int HEIGHT = 192;

    // Known edits (ground truth), in pixels: x, y, width, height.
    public static final int[] COPY_FROM = {16, 16, 48, 48};
    public static final int[] COPY_TO = {168, 112, 48, 48};
    public static final int[] INSERTED = {120, 40, 40, 40};

    public static final String[] FILES = {
        "original.png", "copied.png", "copied.jpg", "inserted.png", "brightness.png",
        "blurred.png", "shuffled.png", "repeated.png", "grayscale.png", "transparent.png",
        "corrupt.png", "unsupported.txt"
    };

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    static int rgb(int r, int g, int b) {
        return (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
    }

    static BufferedImage newImage() {
        return new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
    }

    // Base picture: colour gradients + checkerboard + seeded noise (so blocks are textured).
    static BufferedImage original() {
        Random random = new Random(SEED);
        BufferedImage image = newImage();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int noise = random.nextInt(41) - 20; // -20 to 20
                int checker = 180;
                if ((x / 32 + y / 32) % 2 == 0) {
                    checker = 60;
                }
                image.setRGB(x, y, rgb(x + noise, (y * 4) / 3 + noise, checker + noise));
            }
        }
        return image;
    }

    static BufferedImage copyOf(BufferedImage source) {
        BufferedImage image = newImage();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                image.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return image;
    }

    public static void generate(File folder) throws Exception {
        folder.mkdirs();
        BufferedImage original = original();
        save(original, folder, "original.png");

        // Copy-move: a 48 x 48 piece pasted somewhere else in the same image.
        BufferedImage copied = copyOf(original);
        for (int dy = 0; dy < COPY_FROM[3]; dy++) {
            for (int dx = 0; dx < COPY_FROM[2]; dx++) {
                copied.setRGB(COPY_TO[0] + dx, COPY_TO[1] + dy, original.getRGB(COPY_FROM[0] + dx, COPY_FROM[1] + dy));
            }
        }
        save(copied, folder, "copied.png");
        saveJpeg(copied, new File(folder, "copied.jpg"), 0.65f);

        // Inserted object: flat red square with a black frame.
        BufferedImage inserted = copyOf(original);
        for (int dy = 0; dy < INSERTED[3]; dy++) {
            for (int dx = 0; dx < INSERTED[2]; dx++) {
                boolean frame = dx < 3 || dy < 3 || dx >= INSERTED[2] - 3 || dy >= INSERTED[3] - 3;
                int colour = 0xDD2222;
                if (frame) {
                    colour = 0x000000;
                }
                inserted.setRGB(INSERTED[0] + dx, INSERTED[1] + dy, colour);
            }
        }
        save(inserted, folder, "inserted.png");

        // Whole image brighter by 40 (a normal, harmless edit).
        BufferedImage bright = newImage();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int p = original.getRGB(x, y);
                bright.setRGB(x, y, rgb(((p >> 16) & 255) + 40, ((p >> 8) & 255) + 40, (p & 255) + 40));
            }
        }
        save(bright, folder, "brightness.png");

        // 3 x 3 box blur (border pixels copied unchanged).
        BufferedImage blurred = copyOf(original);
        for (int y = 1; y < HEIGHT - 1; y++) {
            for (int x = 1; x < WIDTH - 1; x++) {
                int r = 0;
                int g = 0;
                int b = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int p = original.getRGB(x + dx, y + dy);
                        r = r + ((p >> 16) & 255);
                        g = g + ((p >> 8) & 255);
                        b = b + (p & 255);
                    }
                }
                blurred.setRGB(x, y, rgb((r + 4) / 9, (g + 4) / 9, (b + 4) / 9));
            }
        }
        save(blurred, folder, "blurred.png");

        // Same pixels in random places: same global histogram, totally different picture.
        int[] pixels = original.getRGB(0, 0, WIDTH, HEIGHT, null, 0, WIDTH);
        Random random = new Random(SEED);
        for (int i = pixels.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int temp = pixels[i];
            pixels[i] = pixels[j];
            pixels[j] = temp;
        }
        BufferedImage shuffled = newImage();
        shuffled.setRGB(0, 0, WIDTH, HEIGHT, pixels, 0, WIDTH);
        save(shuffled, folder, "shuffled.png");

        // Repeated pattern: one 32 x 32 tile of the original repeated (nothing edited).
        BufferedImage repeated = newImage();
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                repeated.setRGB(x, y, original.getRGB(x % 32, y % 32));
            }
        }
        save(repeated, folder, "repeated.png");

        // 8-bit grayscale PNG with the same gray values the analysis gets from the original.
        int[][] gray = Histogram.grayPixels(original);
        BufferedImage grayImage = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                grayImage.getRaster().setSample(x, y, 0, gray[y][x]);
            }
        }
        save(grayImage, folder, "grayscale.png");

        // Transparent PNG: left half fully transparent but hides black pixels; right half solid.
        BufferedImage transparent = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                if (x < WIDTH / 2) {
                    transparent.setRGB(x, y, 0x00000000);
                } else {
                    transparent.setRGB(x, y, 0xFF000000 | original.getRGB(x, y));
                }
            }
        }
        save(transparent, folder, "transparent.png");

        // Broken PNG (first half of the file only) and a text file that is not an image.
        byte[] bytes = Files.readAllBytes(new File(folder, "original.png").toPath());
        byte[] half = new byte[bytes.length / 2];
        System.arraycopy(bytes, 0, half, 0, half.length);
        Files.write(new File(folder, "corrupt.png").toPath(), half);
        Files.writeString(new File(folder, "unsupported.txt").toPath(), "This is not an image. CSC360 Group 2 sample.\n");
    }

    static void save(BufferedImage image, File folder, String name) throws Exception {
        ImageIO.write(image, "png", new File(folder, name));
    }

    static void saveJpeg(BufferedImage image, File file, float quality) throws Exception {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageOutputStream output = ImageIO.createImageOutputStream(file);
        writer.setOutput(output);
        ImageWriteParam settings = writer.getDefaultWriteParam();
        settings.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        settings.setCompressionQuality(quality);
        writer.write(null, new IIOImage(image, null, null), settings);
        output.close();
        writer.dispose();
    }

    public static void main(String[] args) throws Exception {
        String folder = "samples";
        if (args.length > 0) {
            folder = args[0];
        }
        generate(new File(folder));
        System.out.println("Wrote " + FILES.length + " files to " + folder);
    }
}
