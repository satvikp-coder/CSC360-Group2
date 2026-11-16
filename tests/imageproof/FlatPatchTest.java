package imageproof;

import java.awt.Rectangle;
import java.util.Random;

public class FlatPatchTest {
    private static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    static int[][] noisy(int w, int h, long seed) {
        int[][] gray = new int[h][w];
        Random random = new Random(seed);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) gray[y][x] = (int) Math.max(1, Math.min(254, 128 + random.nextGaussian() * 12));
        return gray;
    }

    static void fill(int[][] gray, int x, int y, int w, int h, int value) {
        for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) gray[j][i] = value;
    }

    public static void main(String[] args) {
        int[][] photo = noisy(256, 192, 1);
        check(FlatPatch.analyze(photo, false).available && FlatPatch.analyze(photo, false).pixels == 0, "noise alone has no flat patch");

        int[][] filled = noisy(256, 192, 1);
        fill(filled, 100, 60, 48, 32, 90);
        FlatPatch f = FlatPatch.analyze(filled, false);
        check(f.pixels == 48 * 32 && f.regions.size() == 1 && f.regions.get(0).equals(new Rectangle(100, 60, 48, 32)), "solid fill found exactly");

        int[][] unaligned = noisy(256, 192, 1);
        fill(unaligned, 101, 61, 48, 32, 90); // not on the 4 px grid: only whole 4 x 4 blocks inside count
        FlatPatch u = FlatPatch.analyze(unaligned, false);
        check(u.pixels > 0 && u.pixels <= 48 * 32 && u.pixels % 16 == 0 && new Rectangle(101, 61, 48, 32).contains(u.regions.get(0)), "unaligned fill: only full blocks inside");

        int[][] clipped = noisy(256, 192, 1);
        fill(clipped, 100, 60, 48, 32, 0);
        check(FlatPatch.analyze(clipped, false).pixels == 0, "black (clipped) areas are naturally flat");
        fill(clipped, 100, 60, 48, 32, 255);
        check(FlatPatch.analyze(clipped, false).pixels == 0, "white (clipped) areas are naturally flat");

        int[][] stroke = noisy(256, 192, 1);
        fill(stroke, 20, 20, 8, 120, 60); // 8 px wide line, like a thin brush stroke or text bar
        check(FlatPatch.analyze(stroke, false).pixels == 0, "thin strokes have no solid core");

        int[][] small = noisy(256, 192, 1);
        fill(small, 100, 60, 8, 8, 90); // 4 blocks, below MIN_BLOCKS
        check(FlatPatch.analyze(small, false).pixels == 0, "tiny patch ignored");
        int[][] twoValues = noisy(256, 192, 1);
        fill(twoValues, 100, 60, 24, 12, 90);
        fill(twoValues, 124, 60, 24, 12, 91); // touching patches with different values are separate and each is checked
        FlatPatch t = FlatPatch.analyze(twoValues, false);
        check(t.regions.size() == 2 && t.pixels == 2 * 24 * 12, "different values are separate patches");

        check(!FlatPatch.analyze(filled, true).available, "JPEG input: not reliable");
        int[][] art = new int[192][256];
        for (int y = 0; y < 192; y++) for (int x = 0; x < 256; x++) art[y][x] = 40 + (x / 64 + y / 64) % 2 * 30;
        check(!FlatPatch.analyze(art, false).available, "art made of flat plateaus: flat areas are normal, rule unavailable");
        int[][] ramp = new int[192][256];
        for (int y = 0; y < 192; y++) for (int x = 0; x < 256; x++) ramp[y][x] = 40 + x / 2 + y / 3;
        FlatPatch r = FlatPatch.analyze(ramp, false);
        check(r.available && r.pixels == 0, "a smooth ramp has no exactly flat blocks");
        check(!FlatPatch.analyze(noisy(36, 36, 2), false).available && FlatPatch.analyze(noisy(40, 40, 2), false).available, "36x36 (81 blocks) unavailable, 40x40 (100 blocks) available");

        // The assessment: area is the flat pixel count, counted once even with another overlapping rule.
        Analysis a = TamperingAssessmentTest.image(filled);
        check(a.assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING && a.assessment.flaggedPixels == 48 * 32, "assessment uses the flat area");
        check(Math.abs(a.assessment.areaPercent - 100.0 * 48 * 32 / (256 * 192)) < 1e-9, "flat area percentage");
        check(a.assessment.flatRegions.size() == 1, "overlay regions exposed");
        Analysis jpeg = Analysis.run(a.assessment == null ? null : toImage(filled), "JPEG");
        check(jpeg.assessment.flatRegions.isEmpty() && jpeg.assessment.outcome != TamperingAssessment.Outcome.POSSIBLE_TAMPERING, "JPEG file: fill not trusted");
        System.out.println("FlatPatchTest: " + checks + " checks passed");
    }

    static java.awt.image.BufferedImage toImage(int[][] gray) {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(gray[0].length, gray.length, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < gray.length; y++) for (int x = 0; x < gray[0].length; x++) image.setRGB(x, y, gray[y][x] * 0x010101);
        return image;
    }
}
