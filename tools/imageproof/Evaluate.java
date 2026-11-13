package imageproof;

import java.io.File;

// Runs the real analysis on every sample and prints one CSV line per file.
// Run: java -cp target/classes:target/test-classes imageproof.Evaluate samples > docs/RESULTS.csv
// These are measurements only: there is no threshold and no "edited / not edited" answer.
public class Evaluate {

    public static void main(String[] args) throws Exception {
        String folder = "samples";
        if (args.length > 0) {
            folder = args[0];
        }
        System.out.println("file,width,height,format,mean,std,entropy,max_variation,max_variation_tile,"
                + "max_noise,max_noise_tile,max_edge,max_edge_tile,copy_move_pairs,copy_move_shift,truncated");
        for (int i = 0; i < SampleGenerator.FILES.length; i++) {
            String name = SampleGenerator.FILES[i];
            LoadedImage loaded;
            try {
                loaded = LoadedImage.load(new File(folder, name));
            } catch (Exception e) {
                System.out.println(name + ",load error: " + e.getMessage().replace(",", ";"));
                continue;
            }
            Analysis a = Analysis.run(loaded.image);
            String shift = "";
            if (a.copyMove.matches.size() > 0) {
                CopyMove.Match m = a.copyMove.matches.get(0);
                shift = (m.x2 - m.x1) + " " + (m.y2 - m.y1);
            }
            System.out.println(name + "," + a.width + "," + a.height + "," + loaded.format + ","
                    + String.format("%.2f,%.2f,%.3f,", a.mean, a.standardDeviation, a.entropy)
                    + max(a.regions.variation, "%.3f") + "," + max(a.noise, "%.2f") + "," + max(a.edges, "%.2f") + ","
                    + a.copyMove.matches.size() + "," + shift + "," + a.copyMove.truncated);
        }
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
