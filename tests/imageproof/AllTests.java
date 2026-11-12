package imageproof;

/** One class loader for all plain-main checks, including both Swing headless tests on Java 17. */
public final class AllTests {
    public static void main(String[] args) throws Exception {
        ComparisonTest.main(args);
        HistogramTest.main(args);
        HistogramPanelTest.main(args);
        DatasetTest.main(args);
        AnalyzerTest.main(args);
        ImagePanelTest.main(args);
        SingleImageDatasetTest.main(args);
        System.out.println("AllTests: all seven test programs passed");
    }
}
