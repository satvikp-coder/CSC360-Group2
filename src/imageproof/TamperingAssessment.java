package imageproof;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

/** A limited exact-duplication heuristic, never a probability or authenticity verdict. */
public final class TamperingAssessment {
    public enum Outcome { POSSIBLE_TAMPERING, NO_CLEAR_INDICATORS, INCONCLUSIVE }

    public final Outcome outcome;
    public final List<String> reasons;
    public final String decisionEvidence;
    public final String descriptiveMeasurements;
    private final Rectangle source;
    private final Rectangle destination;
    public static final String LIMITATIONS = "Heuristic, not proof. Natural repeated objects can match. "
            + "Insertions, shuffling, blur, rotated/off-grid or recompressed copies can be missed. "
            + "No clear indicators does not mean original, unedited or authentic. No probability is calculated.";

    private TamperingAssessment(Outcome outcome, List<String> reasons, String evidence,
            String measurements, Rectangle source, Rectangle destination) {
        this.outcome = outcome;
        this.reasons = List.copyOf(reasons);
        this.decisionEvidence = evidence;
        this.descriptiveMeasurements = measurements;
        this.source = source == null ? null : new Rectangle(source);
        this.destination = destination == null ? null : new Rectangle(destination);
    }

    public String title() {
        if (outcome == Outcome.POSSIBLE_TAMPERING) return "Possible tampering detected";
        if (outcome == Outcome.NO_CLEAR_INDICATORS) return "No clear tampering indicators";
        return "Inconclusive";
    }

    public Rectangle sourceBounds() { return source == null ? null : new Rectangle(source); }
    public Rectangle destinationBounds() { return destination == null ? null : new Rectangle(destination); }

    public String details() {
        StringBuilder text = new StringBuilder("FINAL IMAGE ASSESSMENT\n" + title() + "\n\nWHY THIS RESULT?\n");
        for (String reason : reasons) text.append("- ").append(reason).append('\n');
        return text + "\nDECISION EVIDENCE\n" + decisionEvidence
                + "\n\nDESCRIPTIVE CONTEXT (not decision votes)\n" + descriptiveMeasurements
                + "\n\nLIMITATIONS\n" + LIMITATIONS + "\n";
    }

    public static TamperingAssessment evaluate(Analysis a) {
        if (!valid(a)) return new TamperingAssessment(Outcome.INCONCLUSIVE,
                List.of("Measurements are missing, inconsistent or nonfinite; no decision can be supported."),
                "No valid evidence used.", "Unavailable; do not interpret missing values as zero.", null, null);
        String measurements = context(a);
        String evidence = "Retained candidate pairs: " + a.copyMove.matches.size()
                + "; blocks checked: " + a.copyMove.blocksChecked + "; comparisons: " + a.copyMove.comparisons
                + "\nConfigured search completed without reaching a cap: " + (a.copyMove.truncated ? "NO" : "YES")
                + " (exact grayscale 16 px blocks on the 8 px grid only).";
        if (a.copyMove.truncated) return inconclusive("Search/output limits were reached; candidates are incomplete. "
                + "Many matches can come from a repeated pattern.", evidence, measurements);

        // Two-dimensional patches need two block positions on each axis; separation is inherited from CopyMove.
        int patch = CopyMove.BLOCK + CopyMove.STEP;
        int span = patch + 2 * CopyMove.BLOCK;
        if (a.width < patch || a.height < patch || (a.width < span && a.height < span))
            return inconclusive("The image is too small to hold two separated two-dimensional patches under this rule.", evidence, measurements);
        if (a.standardDeviation == 0)
            return inconclusive("The image has no intensity texture; the exact textured-block method cannot assess duplication here.", evidence, measurements);
        if (a.copyMove.matches.isEmpty()) return new TamperingAssessment(Outcome.NO_CLEAR_INDICATORS,
                List.of("No retained exact-copy group was found in the completed configured search.",
                        "This is a limited search result, not a clean-image verdict. Insertions and shuffled pixels can be missed.",
                        "Histogram, noise and edge values describe content; they do not independently establish editing."),
                evidence, measurements, null, null);

        List<CopyMove.Match> pairs = a.copyMove.matches;
        if (pairs.size() < CopyMove.MIN_GROUP)
            return inconclusive("Too few retained pairs support a coherent duplication relationship.", evidence, measurements);
        HashSet<Long> sources = new HashSet<Long>(), destinations = new HashSet<Long>();
        Rectangle from = null, to = null;
        int dx = 0, dy = 0;
        for (int i = 0; i < pairs.size(); i++) {
            CopyMove.Match m = pairs.get(i);
            if (!validPair(a, m)) return inconclusive("Candidate coordinates, texture or exact pixel equality could not be verified.", evidence, measurements);
            int shiftX = m.x2 - m.x1, shiftY = m.y2 - m.y1;
            if (i == 0) { dx = shiftX; dy = shiftY; }
            if (shiftX != dx || shiftY != dy)
                return inconclusive("Candidates have multiple displacements; repeated content or several explanations remain possible.", evidence, measurements);
            if (!sources.add(key(m.x1, m.y1)) || !destinations.add(key(m.x2, m.y2)))
                return inconclusive("A block has more than one matching partner; the duplication relationship is ambiguous.", evidence, measurements);
            Rectangle first = new Rectangle(m.x1, m.y1, CopyMove.BLOCK, CopyMove.BLOCK);
            Rectangle second = new Rectangle(m.x2, m.y2, CopyMove.BLOCK, CopyMove.BLOCK);
            from = from == null ? first : from.union(first);
            to = to == null ? second : to.union(second);
        }
        if (from.intersects(to) || from.width < patch || from.height < patch || !connected(pairs))
            return inconclusive("Matches do not form one connected two-dimensional source patch and a separate destination patch.", evidence, measurements);
        evidence += "\nOne displacement: (" + dx + ", " + dy + "); " + pairs.size() + " unique textured pairs."
                + "\nSource " + bounds(from) + " -> destination " + bounds(to)
                + "\nFootprints may include gaps. These are equal patches, not verified source/copy chronology.";
        measurements += footprintContext(a, from, "First footprint") + footprintContext(a, to, "Second footprint");
        return new TamperingAssessment(Outcome.POSSIBLE_TAMPERING,
                List.of("Exact grayscale matches form one connected textured patch with a unique partner and consistent shift.",
                        "The two patch footprints are separate; the configured search reached no cap.",
                        "This supports possible duplication. Naturally repeated objects can produce the same evidence."),
                evidence, measurements, from, to);
    }

    private static TamperingAssessment inconclusive(String reason, String evidence, String measurements) {
        return new TamperingAssessment(Outcome.INCONCLUSIVE, List.of(reason,
                "Generic brightness, variation, noise and edge values cannot resolve this uncertainty."), evidence, measurements, null, null);
    }

    private static long key(int x, int y) { return ((long) y << 32) | (x & 0xffffffffL); }

    private static boolean validPair(Analysis a, CopyMove.Match m) {
        if (m == null || m.x1 < 0 || m.y1 < 0 || m.x2 < 0 || m.y2 < 0
                || m.x1 > a.width - CopyMove.BLOCK || m.x2 > a.width - CopyMove.BLOCK
                || m.y1 > a.height - CopyMove.BLOCK || m.y2 > a.height - CopyMove.BLOCK
                || m.x1 % CopyMove.STEP != 0 || m.y1 % CopyMove.STEP != 0
                || m.x2 % CopyMove.STEP != 0 || m.y2 % CopyMove.STEP != 0) return false;
        if (Math.abs(m.x2 - m.x1) < 2 * CopyMove.BLOCK && Math.abs(m.y2 - m.y1) < 2 * CopyMove.BLOCK) return false;
        return CopyMove.variance(a.gray, m.x1, m.y1) >= CopyMove.MIN_VARIANCE
                && CopyMove.variance(a.gray, m.x2, m.y2) >= CopyMove.MIN_VARIANCE
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
