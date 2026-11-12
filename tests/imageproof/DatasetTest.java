package imageproof;

import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.imageio.ImageIO;

public final class DatasetTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }
    public static void main(String[] args) throws Exception {
        Path scratch = Files.createTempDirectory("imageproof-dataset-test-");
        try {
            Path first = scratch.resolve("first"), second = scratch.resolve("second");
            DatasetGenerator.generate(first); DatasetGenerator.generate(second);
            for (String name : DatasetGenerator.FILES) {
                check(Files.mismatch(first.resolve(name), second.resolve(name)) == -1,
                        "Nondeterministic output: " + name);
                check(Files.mismatch(first.resolve(name), Path.of("samples/generated", name)) == -1,
                        "Committed dataset differs: " + name);
            }
            check(Files.mismatch(first.resolve("SHA256SUMS"), second.resolve("SHA256SUMS")) == -1,
                    "Manifest differs");
            check(Files.mismatch(first.resolve("SHA256SUMS"),
                    Path.of("samples/generated/SHA256SUMS")) == -1, "Committed manifest differs");
            List<String> manifest = Files.readAllLines(Path.of("samples/generated/SHA256SUMS"));
            check(manifest.size() == DatasetGenerator.FILES.size(), "Wrong manifest length");
            for (int i = 0; i < manifest.size(); i++) {
                String name = DatasetGenerator.FILES.get(i);
                String actualHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(Path.of("samples/generated", name))));
                check(manifest.get(i).equals(actualHash + "  " + name), "Bad committed hash: " + name);
            }
            BufferedImage original = ImageIO.read(first.resolve("original.png").toFile());
            int[] before = pixels(original);
            int[] shuffled = pixels(ImageIO.read(first.resolve("shuffled.png").toFile()));
            check(!Arrays.equals(before, shuffled), "Shuffle did not move pixels");
            Arrays.sort(before); Arrays.sort(shuffled);
            check(Arrays.equals(before, shuffled), "Shuffle changed RGB multiset");
            check(Arrays.equals(pixels(original), pixels(ImageIO.read(first.resolve("identical.png").toFile()))),
                    "Identical pixels differ");
            for (String name : DatasetGenerator.FILES) {
                BufferedImage loaded = ImageIO.read(first.resolve(name).toFile());
                if (name.endsWith(".txt")) { check(loaded == null, "Unsupported input decoded"); continue; }
                check(loaded != null, "Image unreadable: " + name);
                try (var stream = ImageIO.createImageInputStream(first.resolve(name).toFile())) {
                    var readers = ImageIO.getImageReaders(stream);
                    check(readers.hasNext(), "No format reader: " + name);
                    var reader = readers.next();
                    try {
                        check(reader.getFormatName().equalsIgnoreCase(name.endsWith(".jpg") ? "JPEG" : "PNG"),
                                "Actual format differs from extension: " + name);
                    } finally { reader.dispose(); }
                }
                check(loaded.getWidth() == (name.equals("crop.png") ? 96 : 192)
                        && loaded.getHeight() == (name.equals("crop.png") ? 96 : 128), "Wrong dimensions");
            }
            BufferedImage local = ImageIO.read(first.resolve("localized.png").toFile());
            BufferedImage crop = ImageIO.read(first.resolve("crop.png").toFile());
            int changed = 0;
            for (int y = 0; y < 128; y++) for (int x = 0; x < 192; x++) {
                if (original.getRGB(x, y) != local.getRGB(x, y)) changed++;
                if (x < 96 && y < 96) check(original.getRGB(x, y) == crop.getRGB(x, y), "Incorrect crop");
            }
            check(changed == 64, "Localized edit must change 64 pixels");
            check(local.getRGB(88, 60) == 0xffffffff && local.getRGB(95, 67) == 0xffffffff,
                    "Localized patch corners must be white");
            check(local.getRGB(87, 60) == original.getRGB(87, 60)
                    && local.getRGB(96, 67) == original.getRGB(96, 67), "Patch exceeded bounds");
            // Independent fixed RGB fixtures from documented transformation settings.
            BufferedImage bright = ImageIO.read(first.resolve("brightness.png").toFile());
            BufferedImage contrast = ImageIO.read(first.resolve("contrast.png").toFile());
            check((bright.getRGB(0, 0) & 0xffffff) == 0x1e1e4e, "Brightness dark fixture");
            check((bright.getRGB(191, 127) & 0xffffff) == 0xffff4e, "Brightness saturation fixture");
            check((bright.getRGB(96, 64) & 0xffffff) == 0x9e9e4e, "Brightness midtone fixture");
            check((contrast.getRGB(0, 0) & 0xffffff) == 0x000010, "Contrast dark fixture");
            check((contrast.getRGB(191, 127) & 0xffffff) == 0xffff10, "Contrast saturation fixture");
            check((contrast.getRGB(96, 64) & 0xffffff) == 0x808010, "Contrast midtone fixture");
            check((contrast.getRGB(12, 0) & 0xffffff) == 0x0000f0, "Contrast blue fixture");
            System.out.println("DatasetTest: " + checks + " checks passed");
        } finally {
            // Only delete the explicitly created temporary directory, children first.
            try (var paths = Files.walk(scratch)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
