package imageproof;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/**
 * Final assessment of ONE image. It combines the local anomaly rule (LocalAnomaly: regional histogram,
 * noise and edge measurements that agree in the same place) with the exact copy-move detector.
 * The anomalous-area percentage is measured area, never a probability of editing.
 */
public final class TamperingAssessment {
    public enum Outcome { POSSIBLE_TAMPERING, NO_CLEAR_INDICATORS, INCONCLUSIVE }

    public final Outcome outcome;
    public final List<String> reasons;       // at most three short sentences
    public final String decisionEvidence;    // technical text for the Advanced details view
    public final String descriptiveMeasurements;
    public final LocalAnomaly local;         // null if the measurements were invalid
    public final List<Rectangle> flatRegions; // solid-colour patches (bounding boxes), may be empty
    public final boolean areaAvailable;
    public final double areaPercent;         // 0..100, only meaningful if areaAvailable
    public final long flaggedPixels;
    public final long analyzedPixels;
    private final Rectangle source;
    private final Rectangle destination;
    public static final String NOTE = "These findings suggest areas worth inspecting. They do not confirm editing.";
    public static final String LIMITATIONS = "Heuristic, not proof. Natural textures, shadows, edges and repeated objects can look unusual. "
            + "Insertions, shuffling, blur, rotated/off-grid or recompressed copies can be missed. "
            + "No notable indicators does not mean original, unedited or authentic. No probability of editing is calculated.";

    private TamperingAssessment(Outcome outcome, List<String> reasons, String evidence, String measurements,
            Rectangle source, Rectangle destination, LocalAnomaly local, FlatPatch flat, long flaggedPixels, long analyzedPixels) {
        this.outcome = outcome;
        this.reasons = List.copyOf(reasons.size() > 3 ? reasons.subList(0, 3) : reasons);
        this.decisionEvidence = evidence;
        this.descriptiveMeasurements = measurements;
        this.source = source == null ? null : new Rectangle(source);
        this.destination = destination == null ? null : new Rectangle(destination);
        this.local = local;
        this.flatRegions = flat == null ? List.of() : List.copyOf(flat.regions);
        this.analyzedPixels = analyzedPixels;
        boolean ok = local != null && analyzedPixels > 0 && flaggedPixels >= 0 && flaggedPixels <= analyzedPixels
                && (local.available || source != null || (flat != null && flat.pixels > 0));
        this.areaAvailable = ok;
        this.flaggedPixels = ok ? flaggedPixels : 0;
        this.areaPercent = ok ? Math.max(0, Math.min(100, 100.0 * flaggedPixels / analyzedPixels)) : 0;
    }

    public String title() {
        if (outcome == Outcome.POSSIBLE_TAMPERING) return "Possible tampering indicators";
        if (outcome == Outcome.NO_CLEAR_INDICATORS) return "No notable indicators";
        return "Inconclusive";
    }

    /** "12.5% of analyzed image area" or "N/A (...)". */
    public String areaText() {
        if (!areaAvailable) return "N/A (not enough reliable information to measure an area)";
        return String.format(Locale.ROOT, "%.1f%% of analyzed image area", areaPercent);
    }

    public Rectangle sourceBounds() { return source == null ? null : new Rectangle(source); }
    public Rectangle destinationBounds() { return destination == null ? null : new Rectangle(destination); }

    /** The short default result: about ten lines, no tables. */
    public String summary() {
        StringBuilder text = new StringBuilder("FINAL IMAGE ASSESSMENT\n" + title() + "\n\nANOMALOUS AREA\n" + areaText() + "\n\nWHY?\n");
        for (String reason : reasons) text.append("- ").append(reason).append('\n');
        return text + "\n" + NOTE;
    }

    /** Full technical text for the Advanced details view. */
    public String details() {
        return summary() + "\n\nDECISION EVIDENCE\n" + decisionEvidence
                + "\n\nDESCRIPTIVE CONTEXT (not decision votes)\n" + descriptiveMeasurements
                + "\n\nLIMITATIONS\n" + LIMITATIONS + "\n";
    }

    public static TamperingAssessment evaluate(Analysis a) {
        if (!valid(a)) return new TamperingAssessment(Outcome.INCONCLUSIVE,
                List.of("Measurements are missing or invalid, so no decision can be supported."),
                "No valid evidence used.", "Unavailable; do not interpret missing values as zero.", null, null, null, null, 0, 0);
        String measurements = context(a);
        LocalAnomaly local = LocalAnomaly.analyze(a);
        CopyResult copy = judgeCopy(a);
        FlatPatch flatEvidence = FlatPatch.analyze(a.gray, a.jpeg);
        String evidence = localEvidence(a, local) + "\n" + flatText(flatEvidence) + "\n" + copy.evidence;
        long total = (long) a.width * a.height;
        boolean localFlags = local.available && local.flaggedTiles > 0;
        boolean copySupported = copy.kind == Kind.SUPPORTED;
        FlatPatch flat = FlatPatch.analyze(a.gray, a.jpeg);
        boolean flatFlags = flat.available && flat.pixels > 0;
        if (copySupported) measurements += footprintContext(a, copy.from, "First footprint")
                + footprintContext(a, copy.to, "Second footprint");

        long flaggedPixels = flaggedArea(a, local, copySupported ? copy.pairs : null, flatFlags ? flat : null);
        List<String> reasons = new ArrayList<String>();
        Outcome outcome;
        if (localFlags || copySupported || flatFlags) {
            outcome = Outcome.POSSIBLE_TAMPERING;
            if (flatFlags) reasons.add("Some areas are perfectly flat (no camera noise) in an otherwise noisy image, like a solid paint fill.");
            if (localFlags) {
                reasons.add("Some neighboring regions have unusually different brightness distributions.");
                reasons.add("The noise level in the same regions differs from their surroundings.");
            }
            if (copySupported) reasons.add("Two separate patches contain identical pixels, which can happen after copy-and-paste.");
            else if (copy.kind == Kind.INCOMPLETE || copy.kind == Kind.AMBIGUOUS) reasons.add("Copy search was not conclusive: " + copy.reason);
            if (reasons.size() < 3) reasons.add("These regions are highlighted on the image.");
        } else if (!local.available) {
            outcome = Outcome.INCONCLUSIVE;
            reasons.add(local.unavailableReason);
            reasons.add(copy.kind == Kind.NONE ? "The exact-copy search found nothing, but it only covers identical copied blocks."
                    : "Copy search: " + copy.reason);
        } else if (copy.kind == Kind.INCOMPLETE || copy.kind == Kind.AMBIGUOUS) {
            outcome = Outcome.INCONCLUSIVE;
            reasons.add("No group of neighboring regions stood out, but the copy search was not conclusive.");
            reasons.add("Copy search: " + copy.reason);
        } else {
            outcome = Outcome.NO_CLEAR_INDICATORS;
            reasons.add("No group of neighboring regions stood out in both brightness distribution and noise.");
            reasons.add("Strong edges and textures were treated as normal variation.");
            reasons.add("No identical copied patch was found. This does not prove the image is original.");
        }
        return new TamperingAssessment(outcome, reasons, evidence, measurements,
                copySupported ? copy.from : null, copySupported ? copy.to : null, local, flatFlags ? flat : null, flaggedPixels, total);
    }

    // Pixels covered by flagged tiles and/or supported copy blocks, each pixel counted once.
    private static long flaggedArea(Analysis a, LocalAnomaly local, List<CopyMove.Match> pairs, FlatPatch flat) {
        if (pairs == null && flat == null) return local.available ? local.flaggedPixels : 0;
        boolean[][] covered = new boolean[a.height][a.width];
        if (local.available) {
            for (int r = 0; r < a.regions.rows; r++) for (int c = 0; c < a.regions.cols; c++)
                if (local.flagged[r][c]) paint(covered, a.regions.bounds(r, c));
        }
        if (flat != null) for (Rectangle b : flat.blocks) paint(covered, b);
        if (pairs != null) for (CopyMove.Match m : pairs) {
            paint(covered, new Rectangle(m.x1, m.y1, CopyMove.BLOCK, CopyMove.BLOCK));
            paint(covered, new Rectangle(m.x2, m.y2, CopyMove.BLOCK, CopyMove.BLOCK));
        }
        long count = 0;
        for (boolean[] row : covered) for (boolean value : row) if (value) count++;
        return count;
    }

    private static void paint(boolean[][] covered, Rectangle r) {
        for (int y = r.y; y < r.y + r.height && y < covered.length; y++)
            for (int x = r.x; x < r.x + r.width && x < covered[0].length; x++) covered[y][x] = true;
    }

    private static String localEvidence(Analysis a, LocalAnomaly l) {
        if (!l.available) return "Local rule: unavailable. " + l.unavailableReason;
        return "Local rule (tile " + a.regions.tileSize + " px): histogram-unusual " + count(l.histogramUnusual)
                + ", noise-unusual " + count(l.noiseUnusual) + ", boundary-explained " + count(l.boundaryExplained)
                + ", candidates " + l.candidateTiles + ", flagged (with a flagged neighbour) " + l.flaggedTiles
                + String.format(Locale.ROOT, "%nRule: TVD modified z >= %.1f and >= %.2f; noise ratio >= %.1f vs neighbour median (floor %.0f); "
                + "not a high edge outlier; needs a neighbouring candidate.", LocalAnomaly.Z_LIMIT, LocalAnomaly.MIN_TVD,
                LocalAnomaly.NOISE_RATIO, LocalAnomaly.NOISE_FLOOR);
    }

    private static String flatText(FlatPatch f) {
        if (!f.available) return "Flat-patch rule: unavailable. " + f.unavailableReason;
        return String.format(Locale.ROOT, "Flat-patch rule: %d solid patch(es), %d px (noisy blocks %.0f%%; needs >= %d connected equal 4x4 blocks with a 3x3 core, value not 0/255).",
                f.regions.size(), f.pixels, 100 * f.noisyShare, FlatPatch.MIN_BLOCKS);
    }

    private static int count(boolean[][] grid) {
        int n = 0;
        for (boolean[] row : grid) for (boolean value : row) if (value) n++;
        return n;
    }

    private enum Kind { SUPPORTED, NONE, UNAVAILABLE, INCOMPLETE, AMBIGUOUS }

    private static final class CopyResult {
        final Kind kind; final String reason; String evidence; Rectangle from, to; List<CopyMove.Match> pairs;
        CopyResult(Kind kind, String reason, String evidence) { this.kind = kind; this.reason = reason; this.evidence = evidence; }
    }

    // The earlier exact-copy rule, now only one specialised input of the final assessment.
    private static CopyResult judgeCopy(Analysis a) {
        String evidence = "Retained candidate pairs: " + a.copyMove.matches.size()
                + "; blocks checked: " + a.copyMove.blocksChecked + " of " + a.copyMove.blocksTotal
                + String.format(Locale.ROOT, " (%.0f%% of the block grid)", a.copyMove.coveragePercent())
                + "; comparisons: " + a.copyMove.comparisons
                + "\nConfigured search completed without reaching a cap: " + (a.copyMove.truncated ? "NO" : "YES")
                + " (exact grayscale 16 px blocks on the 8 px grid only).";
        if (a.copyMove.truncated) {
            boolean manyMatches = a.copyMove.blocksChecked == a.copyMove.blocksTotal;
            return new CopyResult(Kind.INCOMPLETE, manyMatches
                    ? "many identical blocks were found (a repeated pattern is likely) and the search hit its limit."
                    : String.format(Locale.ROOT, "the copy search covered only %.0f%% of the image.", a.copyMove.coveragePercent()), evidence);
        }
        int patch = CopyMove.BLOCK + CopyMove.STEP;
        int span = patch + 2 * CopyMove.BLOCK;
        if (a.width < patch || a.height < patch || (a.width < span && a.height < span))
            return new CopyResult(Kind.UNAVAILABLE, "the image is too small for the copy search.", evidence);
        if (a.standardDeviation == 0) return new CopyResult(Kind.UNAVAILABLE, "the image has no texture for the copy search.", evidence);
        if (a.copyMove.matches.isEmpty()) return new CopyResult(Kind.NONE, "no identical blocks found.", evidence);
        List<CopyMove.Match> pairs = a.copyMove.matches;
        if (pairs.size() < CopyMove.MIN_GROUP)
            return new CopyResult(Kind.AMBIGUOUS, "too few identical pairs to show a copied patch.", evidence);
        HashSet<Long> sources = new HashSet<Long>(), destinations = new HashSet<Long>();
        Rectangle from = null, to = null;
        int dx = 0, dy = 0;
        for (int i = 0; i < pairs.size(); i++) {
            CopyMove.Match m = pairs.get(i);
            if (!validPair(a, m)) return new CopyResult(Kind.AMBIGUOUS, "candidate blocks could not be verified.", evidence);
            int shiftX = m.x2 - m.x1, shiftY = m.y2 - m.y1;
            if (i == 0) { dx = shiftX; dy = shiftY; }
            if (shiftX != dx || shiftY != dy)
                return new CopyResult(Kind.AMBIGUOUS, "identical blocks have several different shifts (repeated content is likely).", evidence);
            if (!sources.add(key(m.x1, m.y1)) || !destinations.add(key(m.x2, m.y2)))
                return new CopyResult(Kind.AMBIGUOUS, "a block has more than one identical partner.", evidence);
            Rectangle first = new Rectangle(m.x1, m.y1, CopyMove.BLOCK, CopyMove.BLOCK);
            Rectangle second = new Rectangle(m.x2, m.y2, CopyMove.BLOCK, CopyMove.BLOCK);
            from = from == null ? first : from.union(first);
            to = to == null ? second : to.union(second);
        }
        if (from.intersects(to) || from.width < patch || from.height < patch || !connected(pairs))
            return new CopyResult(Kind.AMBIGUOUS, "identical blocks do not form one source patch and one separate copy.", evidence);
        evidence += "\nOne displacement: (" + dx + ", " + dy + "); " + pairs.size() + " unique textured pairs."
                + "\nSource " + bounds(from) + " -> destination " + bounds(to)
                + "\nFootprints may include gaps. These are equal patches, not verified source/copy chronology.";
        CopyResult result = new CopyResult(Kind.SUPPORTED, "", evidence);
        result.from = from; result.to = to; result.pairs = pairs;
        return result;
    }

    private static long key(int x, int y) { return ((long) y << 32) | (x & 0xffffffffL); }

    private static boolean validPair(Analysis a, CopyMove.Match m) {
        if (m == null || m.x1 < 0 || m.y1 < 0 || m.x2 < 0 || m.y2 < 0
                || m.x1 > a.width - CopyMove.BLOCK || m.x2 > a.width - CopyMove.BLOCK
                || m.y1 > a.height - CopyMove.BLOCK || m.y2 > a.height - CopyMove.BLOCK
                || m.x1 % CopyMove.STEP != 0 || m.y1 % CopyMove.STEP != 0
                || m.x2 % CopyMove.STEP != 0 || m.y2 % CopyMove.STEP != 0) return false;
        if (Math.abs(m.x2 - m.x1) < 2 * CopyMove.BLOCK && Math.abs(m.y2 - m.y1) < 2 * CopyMove.BLOCK) return false;
        return CopyMove.textured(a.gray, m.x1, m.y1) && CopyMove.textured(a.gray, m.x2, m.y2)
                && CopyMove.same(a.gray, m.x1, m.y1, m.x2, m.y2);
    }

    // Side-connected source positions on the existing sampling grid, rather than a raw-count vote.
    private static boolean connected(List<CopyMove.Match> pairs) {
        boolean[] seen = new boolean[pairs.size()];
        ArrayList<Integer> queue = new ArrayList<Integer>(); queue.add(0); seen[0] = true;
        for (int next = 0; next < queue.size(); next++) {
            CopyMove.Match a = pairs.get(queue.get(next));
            for (int i = 0; i < pairs.size(); i++) {
                CopyMove.Match b = pairs.get(i);
                if (!seen[i] && Math.abs(a.x1 - b.x1) + Math.abs(a.y1 - b.y1) == CopyMove.STEP) {
                    seen[i] = true; queue.add(i);
                }
            }
        }
        return queue.size() == pairs.size();
    }

    private static boolean valid(Analysis a) {
        if (a == null || a.width < 1 || a.height < 1 || (long) a.width * a.height > Histogram.MAX_PIXELS
                || a.gray == null || a.gray.length != a.height || a.regions == null || a.copyMove == null
                || a.copyMove.matches == null || a.copyMove.matches.size() > CopyMove.MAX_MATCHES
                || a.copyMove.blocksChecked < 0 || a.copyMove.comparisons < a.copyMove.matches.size()
                || a.copyMove.comparisons > CopyMove.MAX_COMPARISONS) return false;
        if (a.width >= CopyMove.BLOCK && a.height >= CopyMove.BLOCK && a.copyMove.blocksChecked == 0) return false;
        for (int[] row : a.gray) {
            if (row == null || row.length != a.width) return false;
            for (int value : row) if (value < 0 || value > 255) return false;
        }
        try { Comparison.difference(a.histogram, a.histogram); }
        catch (IllegalArgumentException e) { return false; }
        if (!Double.isFinite(a.mean) || !Double.isFinite(a.standardDeviation) || !Double.isFinite(a.entropy)
                || Math.abs(a.mean - Histogram.mean(a.histogram)) > 1e-9
                || Math.abs(a.standardDeviation - Histogram.standardDeviation(a.histogram)) > 1e-9
                || Math.abs(a.entropy - Histogram.entropy(a.histogram)) > 1e-9) return false;
        Regions r = a.regions;
        if (r.width != a.width || r.height != a.height || r.tileSize <= 0
                || r.rows != (a.height - 1) / r.tileSize + 1 || r.cols != (a.width - 1) / r.tileSize + 1
                || r.histograms == null || r.histograms.length != r.rows) return false;
        for (double[][] row : r.histograms) {
            if (row == null || row.length != r.cols) return false;
            for (double[] histogram : row) {
                try { Comparison.difference(histogram, histogram); }
                catch (IllegalArgumentException e) { return false; }
            }
        }
        return validGrid(r.variation, r.rows, r.cols, 1) && validGrid(a.noise, r.rows, r.cols, 65025)
                && validGrid(a.edges, r.rows, r.cols, Edges.MAX_STRENGTH);
    }

    private static boolean validGrid(double[][] grid, int rows, int cols, double maximum) {
        if (grid == null || grid.length != rows) return false;
        for (double[] row : grid) {
            if (row == null || row.length != cols) return false;
            for (double value : row) if (!Double.isFinite(value) || value < 0 || value > maximum + 1e-9) return false;
        }
        return true;
    }

    private static String context(Analysis a) {
        return String.format(Locale.ROOT, "Mean %.2f; standard deviation %.2f; entropy %.3f bits.%n", a.mean, a.standardDeviation, a.entropy)
                + maximum("Highest neighbor TVD", a.regions.variation) + "\n"
                + maximum("Highest residual variance", a.noise) + "\n" + maximum("Highest Sobel tile mean", a.edges)
                + "\nThese can reflect normal boundaries/texture and are not counted as independent evidence.";
    }

    private static String maximum(String name, double[][] grid) {
        int row = 0, col = 0;
        for (int r = 0; r < grid.length; r++) for (int c = 0; c < grid[r].length; c++)
            if (grid[r][c] > grid[row][col]) { row = r; col = c; }
        return String.format(Locale.ROOT, "%s: %.4f at tile r%d c%d", name, grid[row][col], row, col);
    }

    private static String footprintContext(Analysis a, Rectangle footprint, String name) {
        StringBuilder text = new StringBuilder("\n" + name + " overlapping tiles (context only):");
        for (int r = 0; r < a.regions.rows; r++) for (int c = 0; c < a.regions.cols; c++) {
            if (footprint.intersects(a.regions.bounds(r, c))) text.append(String.format(Locale.ROOT,
                    "%n  r%d c%d: TVD %.4f, residual variance %.2f, Sobel mean %.2f", r, c,
                    a.regions.variation[r][c], a.noise[r][c], a.edges[r][c]));
        }
        return text.toString();
    }

    static String bounds(Rectangle r) { return "(x=" + r.x + ", y=" + r.y + ", w=" + r.width + ", h=" + r.height + ")"; }
}
