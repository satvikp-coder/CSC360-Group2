package imageproof;

import java.util.*;

/** Bounded exact grayscale block matches; natural repeated texture can produce candidates. */
public final class CopyMoveAnalyzer {
    public static final int BLOCK_SIZE = 16, MAX_BLOCKS = 50000, MAX_COMPARISONS = 200000;
    private CopyMoveAnalyzer() {}
    public record Match(int x1, int y1, int x2, int y2, int size) {}
    public record Result(List<Match> matches, int blocksExamined, int comparisons, boolean truncated) {
        public Result { matches = List.copyOf(matches); }
    }
    private record Position(int x, int y) {}
    private record Displacement(int x, int y) {}
    public static Result analyze(int[] gray, int width, int height) {
        ImageAnalyzer.validatePixels(gray, width, height);
        Map<Long, List<Position>> buckets = new HashMap<>();
        Map<Displacement, List<Match>> groups = new LinkedHashMap<>();
        int blocks = 0, comparisons = 0, stored = 0; boolean truncated = false;
        scan: for (int y = 0; y + BLOCK_SIZE <= height; y += 8) {
            ImageAnalyzer.checkCancelled();
            for (int x = 0; x + BLOCK_SIZE <= width; x += 8) {
                if (blocks >= MAX_BLOCKS || comparisons >= MAX_COMPARISONS || stored >= 4096) { truncated = true; break scan; }
                blocks++;
                long hash = 0xcbf29ce484222325L; double sum = 0, square = 0;
                for (int dy = 0; dy < BLOCK_SIZE; dy++) for (int dx = 0; dx < BLOCK_SIZE; dx++) {
                    int value = gray[(y + dy) * width + x + dx];
                    hash = (hash ^ value) * 0x100000001b3L; sum += value; square += value * value;
                }
                if (square / 256 - (sum / 256) * (sum / 256) < 25) continue;
                List<Position> previous = buckets.computeIfAbsent(hash, key -> new ArrayList<>());
                for (Position position : previous) {
                    if (Math.abs(x - position.x()) < BLOCK_SIZE * 2 && Math.abs(y - position.y()) < BLOCK_SIZE * 2) continue;
                    if (comparisons >= MAX_COMPARISONS || stored >= 4096) { truncated = true; break scan; }
                    comparisons++;
                    if (same(gray, width, position.x(), position.y(), x, y)) {
                        groups.computeIfAbsent(new Displacement(x - position.x(), y - position.y()), key -> new ArrayList<>())
                                .add(new Match(position.x(), position.y(), x, y, BLOCK_SIZE)); stored++;
                    }
                }
                if (previous.size() < 16) previous.add(new Position(x, y)); else truncated = true;
            }
        }
        List<List<Match>> coherent = new ArrayList<>();
        for (List<Match> group : groups.values()) if (group.size() >= 3) coherent.add(group);
        coherent.sort((a, b) -> Integer.compare(b.size(), a.size()));
        List<Match> matches = new ArrayList<>();
        for (List<Match> group : coherent) for (Match match : group) {
            if (matches.size() == 256) { truncated = true; break; }
            matches.add(match);
        }
        return new Result(matches, blocks, comparisons, truncated);
    }
    private static boolean same(int[] gray, int width, int x1, int y1, int x2, int y2) {
        for (int dy = 0; dy < BLOCK_SIZE; dy++) for (int dx = 0; dx < BLOCK_SIZE; dx++)
            if (gray[(y1 + dy) * width + x1 + dx] != gray[(y2 + dy) * width + x2 + dx]) return false;
        return true;
    }
}
