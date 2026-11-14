package imageproof;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

// Hand-calculated checks for the noise residual and Sobel edges.
public class NoiseEdgesTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static boolean near(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    static int[][] filled(int width, int height, int value) {
        int[][] gray = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                gray[y][x] = value;
            }
        }
        return gray;
    }

    public static void main(String[] args) {
        // Uniform image: no residual and no edges anywhere, borders included.
        int[][] flat = filled(70, 70, 120);
        Regions r = Regions.analyze(flat, 64);
        double[][] noise = Noise.tileVariance(flat, r);
        double[][] edges = Edges.tileStrength(flat, r);
        for (int row = 0; row < r.rows; row++) {
            for (int col = 0; col < r.cols; col++) {
                check(noise[row][col] == 0, "flat noise 0");
                check(edges[row][col] == 0, "flat edges 0");
            }
        }
        check(Edges.strength(flat, 0, 0) == 0 && Edges.strength(flat, 69, 69) == 0, "flat corners");

        // One bright pixel (9) in the middle of a 3 x 3 black image.
        int[][] dot = filled(3, 3, 0);
        dot[1][1] = 9;
        check(near(Noise.residual(dot, 1, 1), 8), "centre: 9 - 9/9 = 8");
        check(near(Noise.residual(dot, 0, 0), -2.25), "corner: 0 - 9/4");
        check(near(Noise.residual(dot, 1, 0), -1.5), "side: 0 - 9/6");
        // Residuals: one 8, four -2.25, four -1.5 -> variance 790.25 / 81.
        check(near(Noise.variance(dot, new Rectangle(0, 0, 3, 3)), 790.25 / 81), "dot variance");

        // Ramp: gray = 10 * x. Inside, gx = 4 * 10 * 2 = 80 and gy = 0.
        int[][] ramp = new int[3][5];
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 5; x++) {
                ramp[y][x] = 10 * x;
            }
        }
        check(Edges.gradientX(ramp, 2, 1) == 80, "ramp gx");
        check(Edges.gradientY(ramp, 2, 1) == 0, "ramp gy");
        // Left border: the missing left column copies x = 0, so gx = 4 * 10 - 4 * 0 = 40.
        check(Edges.gradientX(ramp, 0, 1) == 40, "ramp border gx");

        // Step: columns 0-1 are 0, columns 2-3 are 100 -> gx = 400 next to the step, 0 far away.
        int[][] step = filled(4, 4, 0);
        for (int y = 0; y < 4; y++) {
            step[y][2] = 100;
            step[y][3] = 100;
        }
        check(Edges.gradientX(step, 1, 1) == 400 && Edges.gradientX(step, 2, 1) == 400, "step edge");
        check(Edges.gradientX(step, 0, 1) == 0, "far from step");
        check(near(Edges.strength(step, 1, 0), 400), "step strength");

        // Horizontal step (top 0, bottom 100) -> gy = 400.
        int[][] hstep = filled(4, 4, 0);
        for (int x = 0; x < 4; x++) {
            hstep[2][x] = 100;
            hstep[3][x] = 100;
        }
        check(Edges.gradientY(hstep, 1, 1) == 400 && Edges.gradientX(hstep, 1, 1) == 0, "horizontal step");

        // 1 x 1 image: finite zero results.
        int[][] one = filled(1, 1, 200);
        check(Noise.residual(one, 0, 0) == 0 && Edges.strength(one, 0, 0) == 0, "1 x 1");

        // Edge map: black where flat, brighter at the step.
        BufferedImage map = Edges.map(step);
        check(map.getRaster().getSample(0, 0, 0) < map.getRaster().getSample(1, 0, 0), "edge map");

        System.out.println("NoiseEdgesTest OK");
    }
}
