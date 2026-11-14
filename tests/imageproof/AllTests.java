package imageproof;

// Runs every test. "mvn test" calls this. Any failure stops with an error,
// so Maven (and CI) report a failed build.
public class AllTests {

    public static void main(String[] args) throws Exception {
        HistogramTest.main(args);
        ComparisonTest.main(args);
        AnalysisTest.main(args);
        HistogramPanelTest.main(args);
        RegionsTest.main(args);
        NoiseEdgesTest.main(args);
        CopyMoveTest.main(args);
        ImagePanelTest.main(args);
        SamplesTest.main(args);
        System.out.println("All tests passed");
    }
}
