package imageproof;

// Runs every test. "mvn test" calls this. Any failure stops with an error,
// so Maven (and CI) report a failed build.
public class AllTests {

    public static void main(String[] args) throws Exception {
        HistogramTest.main(args);
        ComparisonTest.main(args);
        AnalysisTest.main(args);
        HistogramPanelTest.main(args);
        DatasetTest.main(args);
        System.out.println("All tests passed");
    }
}
