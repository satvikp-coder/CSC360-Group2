# Simple-Image-Proof

**Single-Image Forensic Analyzer · CSC360 Group 2 · Ahmedabad University**

Upload **one JPEG or PNG** and inspect its brightness statistics, regional
histogram variation, blur-residual variance, Sobel edges and exact copy-move
candidates. Everything runs offline on your computer using Java standard APIs.
No ML, learned models, OpenCV, external analysis services, accounts or backend.

These are descriptive measurements. Normal object boundaries, texture, JPEG
compression and repeated patterns can respond strongly. The program produces
no editing probability, forgery verdict or authenticity guarantee.

## Build and run

Install a **JDK 17 or later** and Maven, then from the repository root:

```sh
mvn -B verify
java -jar target/simple-image-proof-0.1.0.jar
```

Or use `launcher.bat` on Windows / `bash launcher.sh` on macOS or Linux. Launchers
check prerequisites and let Maven rebuild changes, skipping test execution only
for startup. Run `mvn test` separately to verify changes. Maven's first build
downloads build plugins; after packaging, the JAR analyzes images without any
network connection. No image is uploaded to an external service.

## Use

1. Click **Upload image**, select one JPEG/PNG, then **Analyze**.
2. Inspect dimensions/format, histogram and global mean, standard deviation,
   and entropy. PNG transparency is composited onto white.
3. Toggle the heatmap and opacity; click a tile for its histogram and measurements.
   Blue-to-red means smaller-to-larger mean neighboring histogram difference.
4. Select Noise residual, Sobel edges or Copy candidates from **View**.
   Noise colors are relative to this image's maximum tile variance. Copy pairs
   are outlined in cyan/magenta. Repeated patterns can produce false matches.

Default tiles are 64×64; 32×32 is also available. Partial edge tiles are included.
Processing runs in background workers; replacement clears results and rejects
stale workers. Cancel preserves input. Files exceeding **16 megapixels** are
rejected before decoding; resize them first. Copy search/output caps are stated
in the UI. Near-exact, rotated, scaled and off-grid copies may be missed.

## Verification and project status

The migration branch implements all six required modules and the one-image UI.
See [test and GUI evidence](docs/MANUAL_TEST.md), [actual evaluation](docs/EVALUATION.md)
and [roadmap](docs/ROADMAP.md). Standalone verification does not mean the change
is merged or teammate-reviewed; check the linked PR's current state.

Tests are plain Java main programs run explicitly by Maven's exec plugin;
assertion failures return a failing build. Desktop harnesses are compiled but
run separately. Java 17 CI covers Windows and Ubuntu, including `mvn -f` from
outside the checkout. See [architecture](docs/ARCHITECTURE.md) and
[scope](docs/SINGLE_IMAGE_BLUEPRINT.md) for formulas and limitations.

Historical two-image code remains in `LegacyComparisonApp`, `Comparison` and
`EvaluateDataset`, with its tests and [archived evaluation](docs/LEGACY_EVALUATION.md).
The default JAR entry point is the single-image application.

Team: Satvik Patel, Varun, Garv and Shambhavee. Topic assigned by Dhruvee Sheth.
