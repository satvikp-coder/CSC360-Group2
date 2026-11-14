# Evaluation

Measured output of the real analysis on the synthetic samples. No threshold, probability or accuracy figure is claimed.

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

None of these values is used as a verdict.
