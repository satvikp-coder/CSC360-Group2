# Evaluation

Measured output of the real analysis on synthetic samples. A local anomaly rule plus an
exact-copy check are evaluated below; no calibrated probability or accuracy figure is claimed.

## Samples

All files are 256 x 192 and made by `tools/imageproof/SampleGenerator.java` with seed `3602026`. No outside images, so the group may share them.

| File | How it is made | Known edit (x, y, w, h) |
| --- | --- | --- |
| original.png | red = x, green = 4y/3, blue = 32 px checkerboard (60/180), each plus noise -20..20 | none |
| copied.png | original with 48 x 48 copied | from (16, 16, 48, 48) to (168, 112, 48, 48), shift (152, 96) |
| copied.jpg | copied.png saved as JPEG, quality 0.65 | same as copied.png |
| inserted.png | red square with black frame | (120, 40, 40, 40) |
| brightness.png | every channel + 40 (clamped) | whole image |
| blurred.png | 3 x 3 box blur, border unchanged | whole image |
| shuffled.png | original pixels in random order (Fisher-Yates, same seed) | whole image |
| repeated.png | one 32 x 32 piece of the original repeated | none (pattern only) |
| grayscale.png | 8-bit gray PNG of the original's gray values | none |
| transparent.png | left half alpha 0 hiding black, right half original | none |
| corrupt.png | first half of original.png's bytes | broken file |
| unsupported.txt | text | not an image |

## Reproduce

```bash
mvn -q test-compile
```

```bash
java -cp target/classes:target/test-classes imageproof.SampleGenerator samples
```

```bash
java -Djava.awt.headless=true -cp target/classes:target/test-classes imageproof.Evaluate samples
```

(On Windows use `;` instead of `:` in the class path.) `SamplesTest`, part of `mvn test`, regenerates the samples and checks the decoded PNG pixels equal the committed ones, plus the facts below.

## Measured results

Full table: [RESULTS.csv](RESULTS.csv). Tiles are written `r<row> c<col>` (64 px tiles, 3 rows x 4 cols). Measured on OpenJDK 21 (compiled for Java 17).

| File | Mean | Std | Entropy | Max variation (tile) | Max noise var. (tile) | Max edge (tile) | Copy-move pairs |
| --- | --- | --- | --- | --- | --- | --- | --- |
| original.png | 126.35 | 50.22 | 7.650 | 0.631 (r1 c3) | 127.98 (r1 c1) | 54.71 (r1 c0) | 0 |
| copied.png | 120.67 | 51.04 | 7.664 | 0.640 (r1 c3) | 168.72 (r2 c2) | 65.47 (r2 c2) | 25, shift (152, 96) |
| copied.jpg | 120.66 | 50.65 | 7.651 | 0.656 (r1 c3) | 129.63 (r2 c3) | 64.60 (r2 c2) | 15, shift (152, 96) |
| inserted.png | 125.13 | 51.68 | 7.625 | 0.631 (r1 c3) | 164.01 (r1 c2) | 64.49 (r1 c2) | 0 |
| brightness.png | 163.43 | 47.01 | 7.504 | 0.667 (r2 c0) | 127.98 (r1 c1) | 54.71 (r1 c0) | 0 |
| blurred.png | 126.35 | 49.00 | 7.603 | 0.723 (r1 c3) | 6.58 (r0 c3) | 24.18 (r1 c0) | 0 |
| shuffled.png | 126.35 | 50.22 | 7.650 | 0.137 (r0 c3) | 2324.54 (r1 c2) | 226.02 (r0 c0) | 0 |
| repeated.png | 24.21 | 12.92 | 5.455 | 0.000 (r0 c0) | 106.18 (r1 c3) | 49.53 (r1 c1) | 200 (limit reached), shift (32, 0) |
| grayscale.png | 126.35 | 50.22 | 7.650 | 0.631 (r1 c3) | 127.98 (r1 c1) | 54.71 (r1 c0) | 0 |
| transparent.png | 200.19 | 63.92 | 4.749 | 0.717 (r1 c2) | 176.41 (r0 c2) | 63.49 (r0 c2) | 0 |
| corrupt.png | load error: "Error reading PNG metadata" | | | | | | |
| unsupported.txt | load error: "not a PNG or JPEG image" | | | | | | |

## What the numbers show

- **Copy-move works on the exact copy**: 25 pairs, all with the known shift (152, 96), green/magenta boxes on the known rectangles ([screenshot](images/app-copy-move.png)).
- **JPEG did not remove all matches here**: 15 of 25 pairs survive because the shift is a multiple of 8, the JPEG block size, so copied blocks are often encoded the same. With other shifts or re-saving, exact matching would usually fail.
- **False positive**: `repeated.png` has no edit but gives 200 candidates (the limit) because the pattern repeats. Candidates are not proof ([screenshot](images/app-repeated.png)).
- **Missed change**: `inserted.png` gives no copy-move candidates (the square is flat and not copied). Its tile r1 c2 does have the highest noise and edge values, but so do normal object edges; the largest regional variation tile (r1 c3) is the same as in the original.
- **Global histogram ignores position**: `shuffled.png` has exactly the same mean, std and entropy as the original although it looks like noise. Its regional variation drops (all tiles look alike) while noise and edge values jump.
- **Normal edits change numbers**: brightness moves the mean by about 37; blur cuts the noise variance from 127.98 to 6.58.
- **Input rules**: `grayscale.png` gives exactly the original's numbers (gray samples read directly, issue #22). The transparent half counts as white, not the hidden black (issue #23), so its mean is high.
- **Bad files** are rejected with a message and do not produce results.

The final assessment combines the local anomaly rule and the copy-move
detector (below). Brightness and the regional maxima in the table above remain
descriptive context; they are not votes for editing.

## Final assessment rule

Everything is calculated from the ONE uploaded image. No reference image,
database, machine learning or weighted score is used. The result is a measured
area and a cautious status, never a probability of editing.

### Local anomaly rule (`LocalAnomaly`)

Using values the program already computes for each 64 x 64 tile (partial edge
tiles at the right and bottom are smaller):

1. **Histogram.** The tile's neighbour TVD is a high outlier compared with the
   other tiles of the same image: modified z-score `0.6745 * (v - median) / MAD`
   >= 3.5 (Iglewicz and Hoaglin; if MAD is 0 the mean absolute deviation is
   used), and the TVD is at least 0.10 so tiny absolute differences never count.
2. **Noise.** Its residual variance differs by a factor of at least 2 (larger or
   smaller) from the median of its up to 8 neighbouring tiles, using
   `(variance + 1)` ratios. At least 3 neighbours are needed and the larger of
   the two variances must be at least 4 (std of about 2 gray levels).
3. **Edges explain, they do not vote.** If the tile's own mean Sobel strength is
   a high outlier (modified z >= 3.5) the difference is attributed to an object
   boundary or texture and the tile is not flagged. Many strong edges never
   raise the assessment.
4. **Spatial agreement.** A tile meeting 1-3 is flagged only if at least one of
   its 8 neighbours also meets 1-3. Isolated tiles are ignored. Histogram, noise
   and edge values come from the same pixels, so they are one piece of
   co-located evidence, not three independent proofs.

The rule is unavailable (status Inconclusive, area N/A) for images with fewer
than 9 tiles, and for images whose tiles are almost identical (no local contrast).

### Anomalous area percentage

```text
anomalousAreaPercentage = 100 * flaggedPixelArea / totalAnalyzedPixelArea
```

`flaggedPixelArea` is the number of pixels covered by flagged tiles (using each
tile's real width x height) plus the 16 x 16 blocks of a supported copy-move
patch pair, with every pixel counted once even where they overlap. The result
is clamped to 0..100. **It is the share of the image that met the rule. It is not
a probability of forgery or editing and not a percentage of forged pixels.**
It shows `N/A` when the local rule is unavailable and there is no supported
copy patch, or when the measurements are invalid.

### Copy-move input (`TamperingAssessment`)

The earlier exact-copy rule is kept as one specialised input. Supported when all
retained pairs share one displacement, positions are unique, pixels are exactly
equal, textured (variance >= 4 and residual variance >= 1), there are at least 3 pairs forming one
side-connected source patch, and the footprint is disjoint from its partner.
Search limits, several shifts, weak or ambiguous matches are *not conclusive*
(not tampering evidence, but they stop a "no notable indicators" answer).

### Status

| Status | When |
| --- | --- |
| Possible tampering indicators | flagged local tiles, or a supported copy-move patch pair |
| Inconclusive | invalid measurements; local rule unavailable (too few tiles / homogeneous); or nothing flagged but the copy search was truncated or ambiguous (repeated pattern, hit a comparison limit) |
| No notable indicators | local rule available and nothing flagged, copy search completed without a supported patch. Not a statement that the image is original |

### Threshold justification and validation (honest summary)

* 3.5 is the standard cut-off for modified z-scores. 2x noise ratio, the 0.10
  TVD floor, the noise floor of 4 and 3 neighbours were chosen **before**
  measuring, as simple round values; they are not tuned to the samples.
* They are validated only against small synthetic sets: 9 benign images produce
  **0 flagged tiles** (original, grayscale, transparent samples, plus 6
  controls with strong boundaries, text, shadows, a vignette, a textured blob
  next to flat sky and plain noise, see `tools/imageproof/Controls.java`).
* **Design history:** a first version compared noise only with neighbours of
  similar edge strength; it flagged nothing on the manipulated control because
  noise and Sobel strength move together. The edge-similarity filter was then
  removed, and edges were kept only as the "boundary explanation" in rule 3.
  The `patch-less-noise` control was written before that change but the change
  was made after seeing it, so it is **not** an independent test of the final
  rule.
* There is no calibration data, so **no probability of editing is available**.
  The numbers say where the rule fires, not how likely an edit is.

## Actual assessment results

Reproduce with `Evaluate` (see below). Full columns: [RESULTS.csv](RESULTS.csv).

| Case | Flagged tiles | Anomalous area | Status | Reading |
|---|---|---|---|---|
| original.png | 0 | 0.0% | No notable indicators | Correct (no edit) |
| copied.png | 0 | 9.4% | Possible tampering | **Detected** by copy-move (25 pairs, shift (152,96)); local rule does not fire |
| copied.jpg | 0 | 6.3% | Possible tampering | **Detected** by copy-move (15 pairs) |
| inserted.png | 0 | 0.0% | No notable indicators | **Missed manipulation** |
| brightness.png | 0 | 0.0% | No notable indicators | Global change; **missed** (not local) |
| blurred.png | 0 | 0.0% | No notable indicators | Global change; **missed** (not local) |
| shuffled.png | 0 | 0.0% | No notable indicators | **Missed manipulation** |
| repeated.png | 0 | N/A | Inconclusive | Benign repeated pattern: tiles identical (no local contrast) and copy search hit its limit. Not called tampering |
| grayscale.png, transparent.png | 0 | 0.0% | No notable indicators | Correct (no edit) |
| corrupt.png, unsupported.txt | - | - | load rejected | No assessment is fabricated |
| 6 benign controls (strong edges, text, shadow, texture, vignette) | 0 | 0.0% | No notable indicators | No false positives |
| control: patch with less noise | 2 | 4.2% | Possible tampering | **Detected** (2 of 12 flagged tiles touch the patch) |
| control: brighter patch (+40) | 0 | 0.0% | No notable indicators | **Missed**; histogram shifts are not flagged without a noise change |
| 4000 x 3000 noise image | 0 | 0.0% | No notable indicators | Earlier versions covered only the first 20000 blocks and were Inconclusive; the whole grid is now searched |

**False positives:** none of the 9 benign images above; a constructed pair of
identical motifs (not copied) still gives a *copy-move* warning (seed
2026111699). Natural repeated objects, shadows with noise changes or photos
processed differently in parts can fire the local rule.
**Missed manipulations:** the inserted object, shuffled pixels, global
brightness and blur, and a brighter patch. The local rule mainly sees regions
whose histogram **and** noise both differ from their surroundings.
**Inconclusive / N/A:** the repeated pattern, the 12 MP image and images with
fewer than 9 tiles (smaller than about 192 x 192).
No accuracy percentage is claimed.

## Benchmark with development and held-out scenes

`tools/imageproof/Benchmark.java` builds the scenes in code (**synthetic only, no
photographs**): sky and buildings, forest, indoor text with a shadow, portrait,
smooth sky, repeated bricks, noise-free graphic, and (held-out only) a night city
and fabric. Each scene has its own seed and belongs to exactly one group, and
every edited version is made from its own scene, so nothing leaks between
groups. Development seeds are 1000-1006; held-out seeds are 5000-5008 and two
families are never used in development. Sizes range from 160 x 120 to 640 x 480.
Each scene gives: the original, JPEG q75 and q40, a gray-type file, a file with a
transparent corner, an inserted foreign object, brightness patches (+45 and -45),
copy-moves (on the 8 px grid and off-grid), global brightness, global blur, and a
small 160 x 120 version with two local edits.

Rules were examined **only on the development set**. The held-out set was run
once, with the final code. Results (rows are ground truth, columns the status):

| Group | Truth | Possible | No notable | Inconclusive |
|---|---|---|---|---|
| dev | genuine (42) | 0 | 33 | 9 |
| dev | local edit (35) | 0 | 21 | 14 |
| dev | copy-move (14) | 5 | 8 | 1 |
| dev | global edit (14) | 0 | 14 | 0 |
| held-out | genuine (54) | 0 | 43 | 11 |
| held-out | local edit (45) | 0 | 27 | 18 |
| held-out | copy-move (18) | 7 | 10 | 1 |
| held-out | global edit (18) | 0 | 18 | 0 |

Per-image rows (assessment, area, coverage, milliseconds):
[BENCHMARK_DEV.csv](BENCHMARK_DEV.csv), [BENCHMARK_HELDOUT.csv](BENCHMARK_HELDOUT.csv).
Reproduce: `java -cp target/classes:target/test-classes imageproof.Benchmark held-out docs/BENCHMARK_HELDOUT.csv`.

**Reading the numbers honestly**
- **No false warnings** on 96 genuine images (JPEG re-saves, gray, transparent,
  natural repeats, smooth sky, text, noise-free art). Inconclusive on 20 of
  them: JPEG q40 (many identical blocks), small images (too few tiles) and some
  copy searches.
- **The local rule detected none of the 80 local edits** (inserted objects and
  brightness patches). It is not a usable local-edit detector on these scenes.
  Its only detections remain constructed noise-level patches (see above).
- **Copy-move detected 12 of 32.** Misses are all off-grid copies (cannot be found
  by design), copies of perfectly flat regions, and a few JPEG/forest cases.
- **Global brightness and blur are never flagged** (0 of 32). These are not local.
- Detected copies report 2.7% to 10.7% of the area, taken from the matched blocks.
- Every image had 100% copy-search coverage; the slowest analysis took about
  0.55 s (640 x 480).
- These synthetic results say nothing about real-world accuracy.

## Limitations investigated

| Limitation | Class | What was tested and decided |
|---|---|---|
| Large images only partly searched | A (fixed) | Block hashing replaces pairing every block with every other: the whole block grid is searched (a copy in the last rows of a 1300 x 1300 image is found), and the coverage percentage is reported. Comparison/match limits still apply and are reported. |
| Copies of noisy "flat" regions (sky) were skipped | B (improved) | Block variance floor 25 -> 4 (std 2). Development: copy-move detections 2 -> 5, genuine false warnings 0 -> 0. A smooth noise-free ramp then repeated exactly by itself (a false warning on a small clean graphic), so blocks must also have residual variance >= 1 (a rounded ramp has about 0.08). |
| Inserted object, brightness patch | C (not fixed) | Genuine scenes already have as many noise-unusual tiles as edited ones (for example 8 vs 8 in the forest scene); a brightness patch is indistinguishable from a shadow. Tried on development: z 2.0 (+1 false warning, no gain), histogram only (3/18 detected, 1/30 false warning, and no supporting evidence required), histogram OR noise (flags 18 of 30 genuine). **Rejected; the rule is unchanged.** |
| Global brightness / blur | C | A single image has no reference; a darker or blurrier photo is valid. |
| Shuffled pixels | C for this method | Histogram and neighbour statistics are not designed for it; unchanged. |
| Off-grid, rotated, scaled copies | B, rejected | Indexing every pixel offset costs about 16x more hashing for a 16 MP image and a heavier method; not worth it for this project. Documented as a miss. |
| Identical natural motifs | C | Pixel-identical patches look the same whether copied or naturally repeated. Many matches, several shifts or a cap give "Inconclusive" (preserved). |
| Small images | Inconclusive kept | A 32 px tile (adaptive) removed most "Inconclusive" results on development but detected 0 of 14 small edits, so it would only replace an honest "Inconclusive" with a misleading "No notable indicators". **Rejected.** The message now starts with "Limited analysis". |
| Uniform images | C | No local contrast to compare. |

## Reproduce assessment tests

`mvn -B clean test` runs the test programs: the nine preserved ones,
`TamperingAssessmentTest` (copy evidence, validity, boundaries) and
`LocalAnomalyTest` (benign controls, area formula, partial tiles, overlap,
unavailable cases, short texts). Production code never examines filenames.
Regenerate the CSV with:

```bash
java -Djava.awt.headless=true -cp target/classes:target/test-classes imageproof.Evaluate samples > docs/RESULTS.csv
```

For the separate real-App worker/state test on a graphical desktop:

```powershell
java -cp 'target/classes;target/test-classes' imageproof.AssessmentGuiTest
```

It forces out-of-order load/analysis completion with latches and tests replacement,
invalid load, repeated Analyze, failure, the short result and tile summaries and
EDT responsiveness. On Unix use `:` in the classpath.
