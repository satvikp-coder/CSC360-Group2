package imageproof;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;

public class TamperingAssessmentTest {
    private static int checks;
    static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }

    static Analysis sample(String name) throws Exception {
        File folder = new File(System.getProperty("project.dir", "."), "samples");
        return Analysis.run(LoadedImage.load(new File(folder, name)).image);
    }

    static Analysis image(int[][] gray) {
        BufferedImage image = new BufferedImage(gray[0].length, gray.length, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < gray.length; y++) for (int x = 0; x < gray[y].length; x++) image.setRGB(x, y, gray[y][x] * 0x010101);
        return Analysis.run(image);
    }

    static Analysis copy(long seed, int size) {
        int[][] gray = CopyMoveTest.noise(128, 128, seed);
        CopyMoveTest.copy(gray, 8, 8, 72, 64, size);
        return image(gray);
    }

    static Analysis pairs(Analysis a, int dx, int dy, int[][] positions) {
        a.copyMove = new CopyMove();
        for (int[] p : positions) a.copyMove.matches.add(new CopyMove.Match(p[0], p[1], p[0] + dx, p[1] + dy));
        a.copyMove.comparisons = positions.length; a.copyMove.blocksChecked = positions.length * 2;
        return a;
    }

    private static void invalid(Consumer<Analysis> change, String message) {
        Analysis a = copy(9001, 32); change.accept(a);
        TamperingAssessment result = TamperingAssessment.evaluate(a);
        check(result.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, message);
        check(!result.details().contains("NaN") && !result.details().contains("Infinity"), "invalid values are not presented as measurements");
    }

    public static void main(String[] args) throws Exception {
        for (String name : new String[]{"original.png", "brightness.png", "blurred.png", "shuffled.png", "grayscale.png", "transparent.png"}) {
            Analysis a = sample(name);
            check(a.assessment.outcome == TamperingAssessment.Outcome.NO_CLEAR_INDICATORS, "limited no-indicator result: " + name);
            check(a.assessment.details().contains("does not prove the image is original"), "known misses never certify authenticity");
            check(a.assessment.areaAvailable && a.assessment.areaPercent == 0, "measured area is 0 when nothing is flagged: " + name);
            check(a.assessment.sourceBounds() == null, "no fabricated flagged area");
            check(a.assessment.details().equals(TamperingAssessment.evaluate(a).details()), "deterministic explanation");
        }
        // The inserted square is a solid fill: the flat-patch indicator finds it, inside the known rectangle.
        Analysis inserted = sample("inserted.png");
        Rectangle known = new Rectangle(SampleGenerator.INSERTED[0], SampleGenerator.INSERTED[1], SampleGenerator.INSERTED[2], SampleGenerator.INSERTED[3]);
        check(inserted.assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING && !inserted.assessment.flatRegions.isEmpty(), "solid inserted square flagged");
        for (Rectangle region : inserted.assessment.flatRegions) check(known.contains(region), "flat region lies inside the inserted square: " + region);
        check(inserted.assessment.areaAvailable && inserted.assessment.areaPercent > 0 && inserted.assessment.areaPercent < 5, "flat area is measured");
        check(inserted.assessment.reasons.get(0).contains("perfectly flat"), "reason names the flat patch");
        // The same pixels flagged as a JPEG are not trusted: compression creates flat areas by itself.
        check(!FlatPatch.analyze(inserted.gray, true).available, "flat patches ignored for JPEG input");
        Analysis copied = sample("copied.png"), jpeg = sample("copied.jpg");
        check(jpeg.assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING && jpeg.copyMove.matches.size() == 15,
                "JPEG preserves a smaller qualifying patch in this fixture");
        check(copied.assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING, "actual copied PNG evidence");
        check(copied.assessment.sourceBounds().equals(new Rectangle(16, 16, 48, 48)), "known source footprint");
        check(copied.assessment.destinationBounds().equals(new Rectangle(168, 112, 48, 48)), "known destination footprint");
        check(copied.assessment.decisionEvidence.contains("(152, 96)"), "actual displacement reported");
        check(copied.assessment.areaAvailable && copied.assessment.areaPercent > 0 && copied.assessment.areaPercent < 100, "copy area measured");
        System.out.println("JPEG assessment: " + jpeg.assessment.outcome + "; " + jpeg.assessment.reasons.get(0));
        System.out.println("JPEG footprints: " + TamperingAssessment.bounds(jpeg.assessment.sourceBounds()) + " -> " + TamperingAssessment.bounds(jpeg.assessment.destinationBounds()));
        Analysis repeated = sample("repeated.png");
        check(repeated.copyMove.matches.size() == CopyMove.MAX_MATCHES && repeated.copyMove.truncated, "known repeated-pattern fixture");
        // A large genuine clone (529 pairs, more than the old cap of 200) is supported, not mistaken for a repeated pattern.
        int[][] bigClone = CopyMoveTest.noise(512, 384, 4242);
        CopyMoveTest.copy(bigClone, 32, 32, 280, 152, 192);
        Analysis cloneResult = image(bigClone);
        check(cloneResult.copyMove.matches.size() == 529 && !cloneResult.copyMove.truncated, "large clone fully reported: " + cloneResult.copyMove.matches.size());
        check(cloneResult.assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING, "large clone is possible tampering");
        check(repeated.assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "raw count cannot trigger a warning");
        check(repeated.assessment.decisionEvidence.contains("cap: NO"), "incomplete search disclosed");
        check(repeated.assessment.sourceBounds() == null, "ambiguous matches are not designated supported patches");
        check(!repeated.assessment.areaAvailable && repeated.assessment.areaText().startsWith("N/A"), "no area is invented for identical tiles");
        repeated.copyMove.truncated = false;
        check(TamperingAssessment.evaluate(repeated).outcome == TamperingAssessment.Outcome.INCONCLUSIVE,
                "repeated footprint remains ambiguous even without the cap flag");
        check(copied.assessment.descriptiveMeasurements.contains("not counted as independent evidence"), "correlation caveat");
        check(copied.assessment.details().contains("No probability of editing is calculated"), "no calibrated confidence claim");
        check(sample("original.png").assessment.descriptiveMeasurements.contains("at tile r1 c3"), "actual variation maximum location");
        check(sample("original.png").assessment.descriptiveMeasurements.contains("at tile r1 c1"), "actual residual maximum location");
        Rectangle returned = copied.assessment.sourceBounds(); returned.x = 999;
        check(copied.assessment.sourceBounds().x == 16, "flagged bounds cannot be mutated externally");

        // These separately seeded fixtures were not used to choose the rule.
        for (long seed : new long[]{2026111601L, 2026111602L, 2026111603L}) {
            Analysis separateCopy = copy(seed, 32);
            check(separateCopy.assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING, "separate copied fixture " + seed);
            check(separateCopy.copyMove.matches.size() == 9, "separate copy has 9 pairs");
            check(image(CopyMoveTest.noise(128, 128, seed)).assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "small control has too few tiles for local context " + seed);
        }

        int[][] lShape = {{8, 8}, {16, 8}, {8, 16}};
        check(TamperingAssessment.evaluate(pairs(copy(801, 32), 64, 56, lShape)).outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING, "existing MIN_GROUP boundary with 2D connectivity");
        check(TamperingAssessment.evaluate(pairs(copy(801, 32), 64, 56, new int[][]{{8, 8}, {16, 8}})).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "below MIN_GROUP");
        check(TamperingAssessment.evaluate(pairs(copy(801, 32), 64, 56, new int[][]{{8, 8}, {16, 8}, {24, 8}})).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "a line is not a 2D patch");
        check(TamperingAssessment.evaluate(pairs(copy(801, 32), 64, 56, new int[][]{{8, 8}, {16, 16}, {24, 24}})).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "diagonal points are not side connected");
        check(TamperingAssessment.evaluate(pairs(copy(801, 32), 64, 56, new int[][]{{8, 8}, {8, 8}, {16, 8}})).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "duplicate pair count is not unique evidence");
        Analysis missing = copy(801, 32); missing.copyMove.matches.get(0).x2++;
        check(TamperingAssessment.evaluate(missing).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "off-grid or unequal candidate rejected");
        Analysis truncated = copy(801, 32); truncated.copyMove.truncated = true;
        check(TamperingAssessment.evaluate(truncated).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "truncation overrides positive evidence");

        int[][] minimum = CopyMoveTest.noise(56, 24, 800);
        CopyMoveTest.copy(minimum, 0, 0, 32, 0, 24);
        check(image(minimum).assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING, "derived minimum geometry boundary");
        check(image(CopyMoveTest.noise(55, 24, 800)).assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "below derived geometry boundary");
        check(image(CopyMoveTest.noise(56, 23, 800)).assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "too little patch height");
        check(image(new int[1][1]).assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "tiny image");
        check(image(new int[128][128]).assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "uniform image has no textured evidence");
        int[][] multiple = CopyMoveTest.noise(128, 128, 809);
        CopyMoveTest.copy(multiple, 8, 8, 64, 8, 24); CopyMoveTest.copy(multiple, 8, 8, 64, 64, 24);
        check(image(multiple).assessment.outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "multiple actual displacements are ambiguous");
        for (int high : new int[]{3, 4}) {
            int[][] texture = new int[24][56];
            for (int y = 0; y < 24; y++) for (int x = 0; x < 56; x++) texture[y][x] = (x + y) % 2 * high;
            Analysis threshold = pairs(image(texture), 32, 0, new int[][]{{0, 0}, {8, 0}, {0, 8}});
            check(TamperingAssessment.evaluate(threshold).outcome == (high == 4 ? TamperingAssessment.Outcome.POSSIBLE_TAMPERING : TamperingAssessment.Outcome.INCONCLUSIVE),
                    "inherited texture variance 4 boundary (alternating 0/high has variance (high/2)^2), high=" + high);
        }
        int[][] overlap = new int[40][80];
        for (int y = 0; y < 40; y++) for (int x = 0; x < 80; x++) overlap[y][x] = (x + y) % 2 * 200;
        check(TamperingAssessment.evaluate(pairs(image(overlap), 32, 0, new int[][]{{0, 0}, {8, 0}, {16, 0}, {24, 0}, {32, 0}, {0, 8}})).outcome
                == TamperingAssessment.Outcome.INCONCLUSIVE, "overlapping patch footprints are ambiguous");

        // A repeated motif constructed twice, without copying pixels, is observationally indistinguishable.
        int[][] motifs = CopyMoveTest.noise(128, 128, 2026111699L);
        for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
            int value = (x * 29 + y * 47 + x * y * 3) % 256;
            motifs[y + 8][x + 8] = value; motifs[y + 64][x + 72] = value;
        }
        Analysis pairedMotifs = image(motifs);
        check(pairedMotifs.assessment.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING, "explicit paired-motif false-warning limitation");
        System.out.println("Separately seeded copies: 9 pairs each; paired-motif control: " + pairedMotifs.copyMove.matches.size() + " pairs, " + pairedMotifs.assessment.outcome);
        // Local rule: extreme values in ONE tile are not enough; two adjacent agreeing tiles are; strong edges explain them away.
        Analysis single = sample("original.png");
        single.regions.variation[1][1] = 0.95; single.noise[1][1] = 4000;
        check(TamperingAssessment.evaluate(single).outcome == TamperingAssessment.Outcome.NO_CLEAR_INDICATORS, "one isolated tile is not flagged");
        Analysis pair = sample("original.png");
        for (int c = 1; c <= 2; c++) { pair.regions.variation[1][c] = 0.95; pair.noise[1][c] = 4000; }
        TamperingAssessment pairResult = TamperingAssessment.evaluate(pair);
        check(pairResult.outcome == TamperingAssessment.Outcome.POSSIBLE_TAMPERING && pairResult.local.flaggedTiles == 2, "two adjacent agreeing tiles are flagged");
        check(pairResult.flaggedPixels == 2 * 64 * 64 && Math.abs(pairResult.areaPercent - 100.0 * 2 * 64 * 64 / (256 * 192)) < 1e-9, "exact tile-area percentage");
        for (int c = 1; c <= 2; c++) pair.edges[1][c] = Edges.MAX_STRENGTH;
        check(TamperingAssessment.evaluate(pair).outcome == TamperingAssessment.Outcome.NO_CLEAR_INDICATORS, "strong boundary explains the difference");
        check(TamperingAssessment.evaluate(null).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "null evidence");
        check(TamperingAssessment.evaluate(new Analysis()).outcome == TamperingAssessment.Outcome.INCONCLUSIVE, "empty evidence");
        for (double value : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            invalid(a -> a.mean = value, "nonfinite mean"); invalid(a -> a.entropy = value, "nonfinite entropy");
            invalid(a -> a.standardDeviation = value, "nonfinite deviation"); invalid(a -> a.noise[0][0] = value, "nonfinite noise");
            invalid(a -> a.edges[0][0] = value, "nonfinite edges"); invalid(a -> a.regions.variation[0][0] = value, "nonfinite TVD");
            invalid(a -> a.histogram[0] = value, "nonfinite histogram");
        }
        invalid(a -> a.histogram = null, "missing histogram"); invalid(a -> a.gray[0] = null, "missing pixel row");
        invalid(a -> a.gray[0][0] = -1, "invalid intensity"); invalid(a -> a.regions.histograms[0][0] = null, "missing region histogram");
        invalid(a -> a.copyMove.matches = null, "missing candidates"); invalid(a -> a.regions.tileSize = 0, "invalid tile geometry");
        invalid(a -> a.copyMove.blocksChecked = 0, "search was not performed");
        invalid(a -> a.copyMove.matches.get(0).x1 = Integer.MAX_VALUE, "invalid candidate coordinates");

        File folder = new File(System.getProperty("project.dir", "."), "samples");
        for (String name : new String[]{"corrupt.png", "unsupported.txt"}) {
            try { LoadedImage.load(new File(folder, name)); throw new AssertionError("Invalid file accepted"); }
            catch (IOException expected) { checks++; }
        }
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRANCE);
            check(copied.assessment.details().equals(TamperingAssessment.evaluate(copied).details()), "locale independent evidence");
        } finally { Locale.setDefault(before); }
        System.out.println("TamperingAssessmentTest: " + checks + " checks passed");
    }
}
