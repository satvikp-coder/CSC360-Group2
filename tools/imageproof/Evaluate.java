package imageproof;

import java.io.File;

// Runs the real analysis on every sample and prints one CSV line per file.
// Run: java -cp target/classes:target/test-classes imageproof.Evaluate samples > docs/RESULTS.csv
// Also evaluates the deterministic extra controls (see Controls.java), printed as control:<name>.
// anomalous_area_pct is measured area (see LocalAnomaly); it is not a probability of editing.
public class Evaluate {

    public static void main(String[] args) throws Exception {
        String folder = "samples";
        if (args.length > 0) {
            folder = args[0];
        }
        System.out.println("file,width,height,format,mean,std,entropy,max_variation,max_variation_tile,"
                + "max_noise,max_noise_tile,max_edge,max_edge_tile,flagged_tiles,anomalous_area_pct,copy_move_pairs,copy_move_shift,truncated,assessment,assessment_reason");
        for (int i = 0; i < SampleGenerator.FILES.length; i++) {
            String name = SampleGenerator.FILES[i];
            LoadedImage loaded;
            try {
                loaded = LoadedImage.load(new File(folder, name));
            } catch (Exception e) {
                System.out.println(name + ",load error: " + e.getMessage().replace(",", ";"));
                continue;
            }
            print(name, loaded.format, Analysis.run(loaded.image));
        }
        for (int i = 0; i < Controls.BENIGN.length + Controls.MANIPULATED.length; i++) {
            String name = i < Controls.BENIGN.length ? Controls.BENIGN[i] : Controls.MANIPULATED[i - Controls.BENIGN.length];
            print("control:" + name, "generated", Analysis.run(Controls.image(name)));
        }
    }

    static void print(String name, String format, Analysis a) {
        String shift = "";
        if (a.copyMove.matches.size() > 0) {
            CopyMove.Match m = a.copyMove.matches.get(0);
            shift = (m.x2 - m.x1) + " " + (m.y2 - m.y1);
        }
        TamperingAssessment t = a.assessment;
        String area = t.areaAvailable ? String.format("%.1f", t.areaPercent) : "N/A";
        int tiles = t.local == null ? 0 : t.local.flaggedTiles;
        System.out.println(name + "," + a.width + "," + a.height + "," + format + ","
                + String.format("%.2f,%.2f,%.3f,", a.mean, a.standardDeviation, a.entropy)
                + max(a.regions.variation, "%.3f") + "," + max(a.noise, "%.2f") + "," + max(a.edges, "%.2f") + ","
                + tiles + "," + area + ","
                + a.copyMove.matches.size() + "," + shift + "," + a.copyMove.truncated + "," + t.outcome
                + ",\"" + t.reasons.get(0).replace("\"", "\"\"") + "\"");
    }

    // Largest value in a grid and where it is: "value,row col".
    static String max(double[][] grid, String format) {
        int bestRow = 0;
        int bestCol = 0;
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < grid[row].length; col++) {
                if (grid[row][col] > grid[bestRow][bestCol]) {
                    bestRow = row;
                    bestCol = col;
                }
            }
        }
        return String.format(format, grid[bestRow][bestCol]) + ",r" + bestRow + " c" + bestCol;
    }
}
