# Verification evidence — single-image migration

Environment: Windows desktop 1536×864; JDK 24.0.2 compiler with `--release 17`;
actual runtime Temurin 17.0.21+9. Evidence is agent verification, not teammate
approval. Desktop programs are separate from headless CI.

## Automated commands

```powershell
mvn -B verify
mvn -B -f C:\path\to\checkout\pom.xml test
javac --release 17 -Xlint:all -d out src/imageproof/*.java tools/imageproof/*.java tests/imageproof/*.java
java '-Djava.awt.headless=true' -cp out imageproof.AnalyzerTest
java '-Djava.awt.headless=true' -cp out imageproof.ImagePanelTest
java -cp out imageproof.AppDesktopTest
```

The second Maven command is tested from outside the checkout. Use an actual
Java 17 executable for compatibility verification. Headless tests exercise
preserved comparison/histogram/panel/dataset tests, grayscale/alpha/gray-alpha,
numerical statistics, partial tiles/TVD, residual/Sobel fixtures, copy ground
truth/repeated-pattern false positives, corrupt inputs and worker generations.

AppDesktopTest opened the real App, loaded a 4000×3000 PNG, ran analysis, then
replaced a running task with the 96×96 copy fixture. On the recorded run:
12 MP analysis completed in 4626 ms; queued EDT heartbeat in 63 ms; replacement
cleared old results; only the newer result (9 pairs) published. The frame was
also resized to its 940×650 minimum. Timings describe this run, not a guarantee.

## Real desktop inspection

Using the computer-use skill, the agent operated the packaged App on actual
Java 17: upload chooser → copied-texture PNG → preview/dimensions/format →
Analyze → numeric summary → Histogram tab → region selection → Copy candidates.
The known cyan source and magenta destination aligned with the image. Opening
the chooser and cancelling preserved the selected image and displayed result.
The first visual check exposed an unreadable fixed histogram scale; the code
was corrected to a shared global/selected data maximum for subsequent inspection.

## Delivery checklist

- [x] Headless geometry checks at multiple aspect ratios and narrow sizes.
- [x] Real one-image upload/analysis/region/copy workflow inspected.
- [x] Java 17 visible App worker integration test, including 12 MP response.
- [ ] Teammate demonstration/review and final course presentation approval.
- [ ] macOS launcher GUI inspection (no macOS desktop available here).

CI checks are separate evidence; consult the PR's live check status. Never
equate compiling GUI code with visual verification or a test screenshot with
human review.
