package imageproof;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;

/** Group-created synthetic fixtures; coordinates and seed are part of the evaluation contract. */
public final class SingleImageDataset {
    public static final java.util.List<String> FILES = java.util.List.of("copied-texture.png", "inserted-object.png",
            "repeated-pattern.png", "transparent.png", "grayscale.png", "blurred.png", "resized.png", "corrupt.png");
    private SingleImageDataset() {}
    private static BufferedImage texture() {
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(360);
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) image.setRGB(x, y, random.nextInt(256) * 0x010101);
        return image;
    }
    public static void generate(Path directory) throws Exception {
        Files.createDirectories(directory);
        BufferedImage original = texture(), copy = texture(), inserted = texture();
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) copy.setRGB(x + 64, y + 64, original.getRGB(x, y));
        for (int y = 32; y < 56; y++) for (int x = 40; x < 72; x++) inserted.setRGB(x, y, 0xffcc33);
        BufferedImage repeated = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) repeated.setRGB(x, y, ((x % 8) * 20 + (y % 8) * 10) * 0x010101);
        BufferedImage transparent = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        BufferedImage gray = new BufferedImage(32, 32, BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            transparent.setRGB(x, y, x < 16 ? 0x00ffffff : 0x80000000);
            gray.getRaster().setSample(x, y, 0, x * 8);
        }
        BufferedImage blur = new BufferedImage(96, 96, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 96; y++) for (int x = 0; x < 96; x++) {
            int sum = 0, count = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++)
                if (x + dx >= 0 && x + dx < 96 && y + dy >= 0 && y + dy < 96) { sum += original.getRGB(x + dx, y + dy) & 255; count++; }
            blur.setRGB(x, y, ((sum + count / 2) / count) * 0x010101);
        }
        BufferedImage resized = new BufferedImage(48, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(original, 0, 0, 48, 48, null); graphics.dispose();
        BufferedImage[] images = {copy, inserted, repeated, transparent, gray, blur, resized};
        for (int i = 0; i < images.length; i++) ImageIO.write(images[i], "png", directory.resolve(FILES.get(i)).toFile());
        Files.write(directory.resolve("corrupt.png"), new byte[]{(byte) 137, 80, 78, 71, 13, 10});
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: SingleImageDataset directory");
        generate(Path.of(args[0]));
    }
}
