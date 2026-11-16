# Evaluation

Measured output of the real analysis on synthetic samples. A limited exact-patch
heuristic is evaluated below; no calibrated probability or accuracy figure is claimed.

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

Global brightness and regional TVD/noise/edge values remain descriptive context,
not independent votes for editing. The final assessment uses the specific
exact-duplication relationship below.

## Final assessment rule

This is a cautious heuristic for **possible duplication**, not a general tampering
classifier, verified source/destination chronology or probability model.

Apply these rules in order:

1. Missing, inconsistent or nonfinite measurements -> **INCONCLUSIVE**. Check
   normalized global/tile histograms, finite bounded maps/statistics, dimensions,
   gray values and candidate-search metadata. Numeric validation tolerance is
   1e-9, the existing histogram convention; it is not a detection threshold.
2. A truncated search/output -> **INCONCLUSIVE**, regardless of match count.
3. Insufficient geometry or constant intensity -> **INCONCLUSIVE**. Two source
   positions on each axis need a 24×24 footprint (16 px block + 8 px step).
   CopyMove's minimum separated shift is 32 px on at least one axis. Thus a
   qualifying pair needs width >=56 and height >=24, or the transposed dimensions.
4. No retained candidates after a completed configured search -> **NO CLEAR
   TAMPERING INDICATORS**, explicitly limited to these tests. This is not a clean
   image verdict: the known inserted and shuffled manipulations receive it too.
5. A possible warning requires **all retained pairs** to have one displacement,
   unique source/destination positions, exact pixel equality, texture variance
   >=25 and the existing grid/separation rules. There must be at least the
   existing MIN_GROUP=3 pairs. Source positions must form one side-connected
   component with 8 px steps, span both axes, and have a bounding footprint
   disjoint from its translated partner. Otherwise -> **INCONCLUSIVE**.
6. If those specific conditions hold -> **POSSIBLE TAMPERING DETECTED**. This says
   an exact duplication relationship was observed; natural repeated objects can
   give identical evidence. Bounding footprints may contain unmatched gaps.

No new weights, brightness/noise thresholds, anomaly index or confidence score
are introduced. MIN_GROUP and texture filtering are inherited CopyMove settings,
not validated forensic cutoffs. Regional maxima and values overlapping the
candidate footprints are displayed with their tile coordinates as **context**;
correlated texture/edge/noise values are not counted as additional proof.

## Actual assessment results

Reproduced with Temurin Java 17; full measurements and the first decision reason
are appended to [RESULTS.csv](RESULTS.csv).

| Case | Pairs / cap | Assessment | Interpretation |
|---|---|---|---|
| Original | 0 / no | No clear indicators | No authenticity claim |
| Copied PNG | 25 / no | Possible tampering | Connected 48×48 footprints, (16,16) -> (168,112), shift (152,96) |
| Copied JPEG | 15 / no | Possible tampering | 40×40 footprints (24,24) -> (176,120), shift (152,96); not evidence of JPEG robustness in general |
| Repeated pattern | 200 / yes | Inconclusive | Raw count is insufficient; incomplete/ambiguous pattern evidence |
| Inserted object | 0 / no | No clear indicators | **Missed manipulation**; generic edge/noise peaks do not rescue the exact-copy rule |
| Brightness / blur | 0 / no | No clear indicators | No automatic malicious-edit label from generic statistics |
| Shuffled pixels | 0 / no | No clear indicators | **Missed manipulation** despite changed spatial diagnostics |
| Grayscale / transparency | 0 / no | No clear indicators | Input policy retained; no authenticity claim |
| Tiny / constant image | 0 / no | Inconclusive | Insufficient qualifying geometry/texture |
| Corrupt / unsupported | load rejected | No assessment | The GUI retains only a valid result for the unchanged current image |

Separately seeded sanity checks use seeds 2026111601–2026111603 in 128×128
random-texture images: a 32×32 patch from (8,8) to (72,64) gives 9 pairs and a
possible warning for each; the corresponding unmodified controls give no clear
indicators. These examples were not used to choose the rule, but they are still
synthetic checks, not a representative independent accuracy benchmark.

An additional control (seed 2026111699) constructs the same formula-based motif
twice without copying pixels. It also gives a possible warning: a **known false
warning**. Two naturally identical motifs and a copied motif can be observationally
indistinguishable. Periodic repeats, competing shifts, disconnected matches,
overlapping footprints and search caps are conservatively inconclusive, but this
cannot eliminate every false warning. Near-exact, off-grid, rotated, scaled and
recompressed copies can be missed. No accuracy percentage is claimed.

## Reproduce assessment tests

`mvn -B clean test` runs the nine preserved test programs plus
`TamperingAssessmentTest`. It covers every supplied category, independent
synthetic checks, numeric validity, determinism, inherited texture/group and
derived geometry boundaries, spatial ambiguity and explanations. Production
assessment code never examines filenames.

For the separate real-App worker/state test on a graphical desktop:

```powershell
java -cp 'target/classes;target/test-classes' imageproof.AssessmentGuiTest
```

It forces out-of-order load/analysis completion with latches and tests replacement,
invalid load, repeated Analyze, failure and EDT responsiveness. It substitutes
error capture for modal dialogs; actual visual inspection is recorded separately
in MANUAL_TEST.md. On Unix use `:` in the classpath.
