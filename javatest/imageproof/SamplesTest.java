package imageproof;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

// Checks the committed sample images in samples/ and the known results on them.
// Works from any folder: Maven passes the project folder as "project.dir".
// PNGs are compared by decoded pixels, not file bytes, because encoders differ between JDKs.
public class SamplesTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    static Analysis analyse(File folder, String name) throws Exception {
        return Analysis.run(LoadedImage.load(new File(folder, name)).image);
    }

    static void loadFails(File file) {
        try {
            LoadedImage.load(file);
        } catch (IOException expected) {
            return;
        }
        throw new AssertionError("should not load: " + file.getName());
    }

    public static void main(String[] args) throws Exception {
        File committed = new File(System.getProperty("project.dir", "."), "samples");
        File fresh = Files.createTempDirectory("imageproof-samples-").toFile();
        SampleGenerator.generate(fresh);

        // 1. Regenerated PNGs have exactly the committed pixels.
        for (int i = 0; i < SampleGenerator.FILES.length; i++) {
            String name = SampleGenerator.FILES[i];
            if (name.endsWith(".png") && !name.equals("corrupt.png")) {
                BufferedImage a = LoadedImage.load(new File(fresh, name)).image;
                BufferedImage b = LoadedImage.load(new File(committed, name)).image;
                check(Arrays.equals(pixels(a), pixels(b)), "pixels of " + name);
            }
        }

        // 2. Copied object is found with the known shift; the original has no candidates.
        Analysis copied = analyse(committed, "copied.png");
        int shiftX = SampleGenerator.COPY_TO[0] - SampleGenerator.COPY_FROM[0];
        int shiftY = SampleGenerator.COPY_TO[1] - SampleGenerator.COPY_FROM[1];
        check(copied.copyMove.matches.size() == 25, "25 copy-move pairs");
        CopyMove.Match m = copied.copyMove.matches.get(0);
        check(m.x2 - m.x1 == shiftX && m.y2 - m.y1 == shiftY, "copy shift");
        check(analyse(committed, "original.png").copyMove.matches.size() == 0, "original has none");

        // 3. Repeated pattern gives candidates although nothing was edited (false positive).
        check(analyse(committed, "repeated.png").copyMove.matches.size() > 0, "repeated pattern false positive");

        // 4. Shuffled pixels: same global histogram as the original (histograms ignore position).
        Analysis original = analyse(committed, "original.png");
        Analysis shuffled = analyse(committed, "shuffled.png");
        check(Comparison.difference(original.histogram, shuffled.histogram) == 0, "shuffle keeps histogram");

        // 5. Grayscale PNG gives the same gray values as the colour original.
        check(Comparison.difference(original.histogram, analyse(committed, "grayscale.png").histogram) == 0, "gray");

        // 6. Transparent half counts as white (255), hidden black ignored.
        Analysis transparent = analyse(committed, "transparent.png");
        check(transparent.gray[0][0] == 255 && transparent.gray[100][10] == 255, "transparent is white");

        // 7. Broken and non-image files are rejected.
        loadFails(new File(committed, "corrupt.png"));
        loadFails(new File(committed, "unsupported.txt"));

        File[] files = fresh.listFiles();
        for (int i = 0; i < files.length; i++) {
            files[i].delete();
        }
        fresh.delete();
        System.out.println("SamplesTest OK");
    }
}
