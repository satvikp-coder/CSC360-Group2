package imageproof;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageOutputStream;

/** Creates original synthetic RGB images; contains no histogram/scoring algorithm. */
public final class DatasetGenerator {
    public static final long SEED = 3602026L;
    public static final List<String> FILES = List.of("original.png", "identical.png",
            "brightness.png", "contrast.png", "crop.png", "recompressed.jpg",
            "shuffled.png", "localized.png", "unsupported.txt");

    private static BufferedImage image(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    }
    private static int clamp(int value) { return Math.max(0, Math.min(255, value)); }
    private static void png(BufferedImage image, Path path) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) throw new IOException("No PNG writer");
    }
    public static void generate(Path directory) throws Exception {
        Files.createDirectories(directory);
        int w = 192, h = 128;
        BufferedImage original = image(w, h), bright = image(w, h), contrast = image(w, h);
        BufferedImage local = image(w, h), shuffle = image(w, h), crop = image(96, 96);
        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int r = x * 255 / (w - 1), g = y * 255 / (h - 1);
            int b = ((x / 12 + y / 16) % 2 == 0) ? 48 : 208;
            int rgb = (r << 16) | (g << 8) | b;
            original.setRGB(x, y, rgb); pixels[y * w + x] = rgb;
            bright.setRGB(x, y, (clamp(r + 30) << 16) | (clamp(g + 30) << 8) | clamp(b + 30));
            contrast.setRGB(x, y, (clamp((int) Math.round(128 + 1.4 * (r - 128))) << 16)
                    | (clamp((int) Math.round(128 + 1.4 * (g - 128))) << 8)
                    | clamp((int) Math.round(128 + 1.4 * (b - 128))));
            local.setRGB(x, y, x >= 88 && x < 96 && y >= 60 && y < 68 ? 0xffffff : rgb);
            if (x < 96 && y < 96) crop.setRGB(x, y, rgb);
        }
        Random random = new Random(SEED);
        for (int i = pixels.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1), temporary = pixels[i];
            pixels[i] = pixels[j]; pixels[j] = temporary;
        }
        shuffle.setRGB(0, 0, w, h, pixels, 0, w);
        png(original, directory.resolve("original.png"));
        png(original, directory.resolve("identical.png"));
        png(bright, directory.resolve("brightness.png"));
        png(contrast, directory.resolve("contrast.png"));
        png(crop, directory.resolve("crop.png"));
        png(shuffle, directory.resolve("shuffled.png"));
        png(local, directory.resolve("localized.png"));
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("No JPEG writer");
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(
                directory.resolve("recompressed.jpg").toFile())) {
            writer.setOutput(output);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(0.65f);
            writer.write(null, new IIOImage(original, null, null), parameters);
        } finally { writer.dispose(); }
        Files.writeString(directory.resolve("unsupported.txt"),
                "Deliberately unsupported image input. CSC360 Group 2 synthetic dataset.\n",
                StandardCharsets.UTF_8);
        StringBuilder manifest = new StringBuilder();
        for (String name : FILES) {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    Files.readAllBytes(directory.resolve(name)));
            manifest.append(HexFormat.of().formatHex(digest)).append("  ").append(name).append('\n');
        }
        Files.writeString(directory.resolve("SHA256SUMS"), manifest, StandardCharsets.UTF_8);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Usage: DatasetGenerator output-directory");
        generate(Path.of(args[0]));
        System.out.println("Generated " + FILES.size() + " inputs and SHA256SUMS in " + args[0]);
    }
}
