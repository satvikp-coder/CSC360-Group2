package imageproof;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Random;

// Tests of the local anomaly rule and the anomalous-area percentage.
// The controls below are generated here with their own seeds; the thresholds in LocalAnomaly come from
// the literature (modified z-score 3.5) and simple ratios, not from these images.
public class LocalAnomalyTest {
    private static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    interface Pixel { double value(int x, int y, Random random); }

    static Analysis make(int width, int height, long seed, Pixel pixel) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(seed);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int v = (int) Math.max(0, Math.min(255, Math.round(pixel.value(x, y, random))));
            image.setRGB(x, y, v * 0x010101);
        }
        return Analysis.run(image);
    }

    static long flaggedPixelsFromGrid(Analysis a) {
        long sum = 0;
        for (int r = 0; r < a.regions.rows; r++) for (int c = 0; c < a.regions.cols; c++)
            if (a.assessment.local.flagged[r][c]) { Rectangle b = a.regions.bounds(r, c); sum += (long) b.width * b.height; }
        return sum;
    }

    public static void main(String[] args) {
        // Benign controls: plain noise, texture next to flat sky, a textured blob, text-like stripes, a vignette with a shadow band.
        Analysis[] benign = new Analysis[Controls.BENIGN.length];
        for (int i = 0; i < benign.length; i++) benign[i] = Analysis.run(Controls.image(Controls.BENIGN[i]));
        for (int i = 0; i < benign.length; i++) {
            TamperingAssessment t = benign[i].assessment;
            check(t.local.available && t.local.flaggedTiles == 0 && t.areaPercent == 0, "benign control " + Controls.BENIGN[i] + " has no flagged area");
            check(t.outcome == TamperingAssessment.Outcome.NO_CLEAR_INDICATORS, "benign control " + Controls.BENIGN[i] + " outcome");
        }

        // A patch whose noise level differs from everything around it (a constructed manipulation).
        Analysis patch = Analysis.run(Controls.image("patch-less-noise"));
        TamperingAssessment p = patch.assessment;
        check(p.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING && p.local.flaggedTiles > 0, "noise-level patch is flagged");
        check(p.flaggedPixels == flaggedPixelsFromGrid(patch) && p.flaggedPixels == p.local.flaggedPixels, "flagged pixels equal summed tile areas");
        check(Math.abs(p.areaPercent - 100.0 * p.flaggedPixels / (512.0 * 384)) < 1e-9 && p.areaPercent > 0 && p.areaPercent <= 100, "percentage formula");
        // The flagged tiles must touch the manipulated rectangle.
        Rectangle manipulated = new Rectangle(Controls.PATCH[0], Controls.PATCH[1], Controls.PATCH[2], Controls.PATCH[3]);
        boolean touches = false;
        for (int r = 0; r < patch.regions.rows; r++) for (int c = 0; c < patch.regions.cols; c++)
            if (p.local.flagged[r][c] && manipulated.intersects(patch.regions.bounds(r, c))) touches = true;
        check(touches, "flagged tiles overlap the manipulated region");
        check(p.summary().split("\n").length <= 12 && p.reasons.size() <= 3, "short summary");
        check(!p.summary().toLowerCase().contains("probability") && !p.summary().toLowerCase().contains("confirmed"), "no probability or certainty claim in the summary");
        check(p.areaText().equals(String.format(Locale.ROOT, "%.1f%% of analyzed image area", p.areaPercent)), "area text");

        // Partial edge tiles use their real sizes: 200 x 150 has 8-pixel-wide and 22-pixel-high edge tiles.
        Analysis edge = make(200, 150, 5, (x, y, r) -> 128 + r.nextGaussian() * 15);
        edge.regions.variation[2][2] = 0.9; edge.regions.variation[2][3] = 0.9;
        edge.noise[2][2] = 5000; edge.noise[2][3] = 5000;
        TamperingAssessment e = TamperingAssessment.evaluate(edge);
        check(e.local.flaggedTiles == 2 && e.flaggedPixels == 64 * 22 + 8 * 22, "partial tiles counted with real area");
        check(Math.abs(e.areaPercent - 100.0 * (64 * 22 + 8 * 22) / (200 * 150)) < 1e-9, "partial-tile percentage");

        // Overlapping evidence is counted once: flag tiles that also contain a supported copied patch.
        int[][] gray = CopyMoveTest.noise(256, 192, 77);
        CopyMoveTest.copy(gray, 16, 16, 168, 112, 48);
        Analysis both = TamperingAssessmentTest.image(gray);
        check(both.assessment.sourceBounds() != null, "copy evidence present");
        both.regions.variation[1][2] = 0.9; both.regions.variation[1][3] = 0.9; both.noise[1][2] = 5000; both.noise[1][3] = 5000;
        both.regions.variation[2][2] = 0.9; both.regions.variation[2][3] = 0.9; both.noise[2][2] = 5000; both.noise[2][3] = 5000;
        TamperingAssessment union = TamperingAssessment.evaluate(both);
        boolean[][] mask = new boolean[192][256];
        for (int r = 0; r < both.regions.rows; r++) for (int c = 0; c < both.regions.cols; c++)
            if (union.local.flagged[r][c]) { Rectangle b = both.regions.bounds(r, c); for (int y = b.y; y < b.y + b.height; y++) for (int x = b.x; x < b.x + b.width; x++) mask[y][x] = true; }
        for (CopyMove.Match m : both.copyMove.matches) for (int k = 0; k < 2; k++) {
            int mx = k == 0 ? m.x1 : m.x2, my = k == 0 ? m.y1 : m.y2;
            for (int y = my; y < my + 16; y++) for (int x = mx; x < mx + 16; x++) mask[y][x] = true;
        }
        long expected = 0;
        for (boolean[] row : mask) for (boolean v : row) if (v) expected++;
        check(union.flaggedPixels == expected && union.flaggedPixels <= 256 * 192, "overlapping copy and tile areas counted once");
        check(union.local.flaggedPixels + 2 * both.copyMove.matches.size() * 256L > union.flaggedPixels, "overlap really existed");

        // Unavailable cases never invent a number.
        Analysis flat = make(256, 192, 1, (x, y, r) -> 100);
        check(!flat.assessment.areaAvailable && flat.assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "homogeneous image: area N/A");
        Analysis tiny = make(100, 100, 1, (x, y, r) -> 128 + r.nextGaussian() * 20);
        check(!tiny.assessment.areaAvailable && tiny.assessment.areaText().startsWith("N/A"), "too few tiles: area N/A");
        check(!TamperingAssessment.evaluate(null).areaAvailable && !TamperingAssessment.evaluate(new Analysis()).areaAvailable, "invalid input: area N/A");

        // Same pixels give the same answer.
        Analysis again = Analysis.run(Controls.image("patch-less-noise"));
        check(again.assessment.details().equals(patch.assessment.details()), "deterministic");

        // Short UI texts.
        String region = App.regionText(patch, 0, 0);
        check(region.split("\n").length <= 8 && region.startsWith("Position: (0, 0)") && region.contains("Size: 64 × 64"), "short region text");
        check(region.contains("Histogram difference: ") && region.contains("Noise variation: ") && region.contains("Edge strength: "), "three measurements");
        check(region.matches("(?s).*Histogram difference: \\d\\.\\d\\d\\n.*"), "two decimals for the histogram difference");
        String stats = App.statsText(patch);
        check(stats.startsWith("Brightness ") && stats.contains("/ 255") && stats.contains("Entropy ") && stats.contains("Highest regional difference "), "stats line");
        check(stats.length() < 140 && !stats.contains("\n"), "stats stay short");
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            check(App.regionText(patch, 0, 0).equals(region) && App.statsText(patch).equals(stats), "numbers do not depend on the locale");
        } finally { Locale.setDefault(before); }
        System.out.println("LocalAnomalyTest: " + checks + " checks passed (patch flagged " + p.local.flaggedTiles
                + " tiles = " + String.format(Locale.ROOT, "%.1f", p.areaPercent) + "% of area; 6 benign controls flagged 0)");
    }
}
