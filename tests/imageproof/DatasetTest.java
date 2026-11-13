package imageproof;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;

// Checks the committed sample images in samples/generated.
// Works from any folder: Maven passes the project folder as "project.dir".
// Compares decoded pixels, not file bytes, because PNG/JPEG encoders differ between JDKs.
public class DatasetTest {

    static int checks = 0;

    static void check(boolean ok, String message) {
        checks++;
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    static BufferedImage read(Path file) throws Exception {
        return ImageIO.read(file.toFile());
    }

    // Hex SHA-256 of a file.
    static String sha256(Path file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < digest.length; i++) {
            text.append(String.format("%02x", digest[i]));
        }
        return text.toString();
    }

    // Biggest difference of any colour channel between two images of the same size.
    static int maxChannelDifference(BufferedImage a, BufferedImage b) {
        int max = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                int p = a.getRGB(x, y);
                int q = b.getRGB(x, y);
                for (int shift = 0; shift <= 16; shift = shift + 8) {
                    int d = Math.abs(((p >> shift) & 255) - ((q >> shift) & 255));
                    if (d > max) {
                        max = d;
                    }
                }
            }
        }
        return max;
    }

    public static void main(String[] args) throws Exception {
        Path project = Path.of(System.getProperty("project.dir", "."));
        Path committed = project.resolve("samples/generated");
        Path fresh = Files.createTempDirectory("imageproof-test-");
        DatasetGenerator.generate(fresh);

        // 1. Committed files still match their committed SHA-256 manifest.
        List<String> manifest = Files.readAllLines(committed.resolve("SHA256SUMS"));
        check(manifest.size() == DatasetGenerator.FILES.size(), "manifest length");
        for (int i = 0; i < manifest.size(); i++) {
            String name = DatasetGenerator.FILES.get(i);
            check(manifest.get(i).equals(sha256(committed.resolve(name)) + "  " + name), "hash of " + name);
        }

        // 2. Regenerated PNGs decode to exactly the committed pixels.
        for (String name : DatasetGenerator.FILES) {
            if (name.endsWith(".png")) {
                BufferedImage a = read(fresh.resolve(name));
                BufferedImage b = read(committed.resolve(name));
                check(Arrays.equals(pixels(a), pixels(b)), "pixels of " + name);
            }
        }

        // 3. JPEG: decodes, right size, close to the original (lossy, so not exact).
        BufferedImage original = read(committed.resolve("original.png"));
        BufferedImage jpeg = read(fresh.resolve("recompressed.jpg"));
        check(jpeg != null && jpeg.getWidth() == 192 && jpeg.getHeight() == 128, "jpeg size");
        check(maxChannelDifference(original, jpeg) <= 128, "jpeg close to original");
        check(maxChannelDifference(original, read(committed.resolve("recompressed.jpg"))) <= 128, "committed jpeg");

        // 4. The text file is not an image.
        check(read(committed.resolve("unsupported.txt")) == null, "text is not an image");

        // 5. Shuffled image has the same pixels in different places.
        int[] before = pixels(original);
        int[] after = pixels(read(committed.resolve("shuffled.png")));
        check(!Arrays.equals(before, after), "shuffle moved pixels");
        Arrays.sort(before);
        Arrays.sort(after);
        check(Arrays.equals(before, after), "shuffle kept the same colours");

        // 6. Localized edit changes exactly an 8x8 white square.
        BufferedImage local = read(committed.resolve("localized.png"));
        int changed = 0;
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 192; x++) {
                if (original.getRGB(x, y) != local.getRGB(x, y)) {
                    changed++;
                }
            }
        }
        check(changed == 64, "64 changed pixels");

        // Clean up the temporary folder.
        File[] files = fresh.toFile().listFiles();
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
        }
        fresh.toFile().delete();
        System.out.println("DatasetTest: " + checks + " checks passed");
    }
}
