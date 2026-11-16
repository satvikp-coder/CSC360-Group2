# Architecture

Plain Java 17, Swing for the window, ImageIO for files. No outside libraries. All maths classes have no Swing code, so they are tested headless.

## Flow

```text
File --LoadedImage.load--> BufferedImage --Analysis.run--> Analysis
                                                    |
          Histogram.grayPixels -> gray[y][x] -------+--> Histogram  (global bins, mean, std, entropy)
                                                    +--> Regions    (64 px tiles, tile histograms, TVD to neighbours)
                                                    +--> Noise      (3x3 residual variance per tile)
                                                    +--> Edges      (Sobel strength per tile + edge map)
                                                    +--> CopyMove   (exact block pairs grouped by shift)
                                                    +--> TamperingAssessment (LocalAnomaly rule + copy-move, then area %)
Analysis --> App shows a short assessment, ImagePanel (flags / heatmap / boxes), HistogramPanel,
             a short selected-region text; tables only in the Advanced details window
```

Diagrams: [pixel to histogram](images/pixel-to-histogram.svg), [TVD between two histograms](images/histogram-compare.svg) (16 bins shown; the app uses 256).

## Classes (`src/imageproof`)

| Class | Responsibility |
| --- | --- |
| `Histogram` | `grayPixels` (colour: `getRGB` rows, alpha onto white; gray images: raster sample, 16-bit scaled), `count`, `normalize`, `mean`, `standardDeviation`, `entropy`. 16 MP limit. |
| `Comparison` | `difference(p, q)` = TVD, rejects invalid histograms |
| `Regions` | Tile grid incl. partial edge tiles, `bounds(row, col)`, tile histograms, variation |
| `Noise` | `residual(x, y)`, `variance(rect)`, `tileVariance` |
| `Edges` | `gradientX`, `gradientY`, `strength`, `tileStrength`, `map`; borders clamped |
| `CopyMove` | Block search with limits, `matches`, `truncated` |
| `Analysis` | Runs all of the above, holds the results in public fields |
| `LocalAnomaly` | Per-tile flags: histogram outlier (modified z-score) + noise ratio vs neighbours, boundary explanation from edges, neighbour agreement |
| `TamperingAssessment` | Validates evidence, runs `LocalAnomaly`, judges the copy-move patch, counts flagged pixels once (union) and builds the status, at most three reasons and the area percentage |
| `LoadedImage` | Real format check (PNG/JPEG only), header size check, readable errors |
| `App` | Window, short assessment, selected-region text, Advanced details window, `SwingWorker`s with a job number so stale results are ignored |
| `ImagePanel` | Letterboxed image, heatmap, flagged tiles, legend, copy-move boxes, selected tile, screen <-> image mapping |
| `HistogramPanel` | Bar chart of 256 bins |

## Threads

`App` starts on the Swing thread (`SwingUtilities.invokeLater`). Loading and analysing run in `SwingWorker`s. Each job gets `jobNumber++`; `done()` only shows a result if its number is still the newest, so an old result can never appear for a newer image.

The assessment is calculated at the end of `Analysis.run`, on the same worker,
not during Swing rendering. Successful image replacement and re-analysis clear
the previous conclusion, area, histogram and Advanced window. Cancel returns
before changing a job; failed loads retain the matching current result; failed
analysis leaves no fabricated result. Repeated Analyze while busy is ignored.
Selecting a tile changes only the histogram, the blue outline and the short
region text; the assessment stays.

The [rule](EVALUATION.md#final-assessment-rule) uses no weighted score. Edge
strength only explains boundaries; histogram, noise and edge values come from
the same pixels and are not independent proofs. The percentage is measured area
(`100 * flagged pixels / analyzed pixels`), not a probability of editing; no
state establishes authenticity.

## Tests and tools

`javatest/imageproof`: one test class per class above plus `SamplesTest`; `AllTests` runs them (`mvn test`); `AssessmentGuiTest` is a separate desktop test. `tools/imageproof`: `SampleGenerator` (samples), `Controls` (extra deterministic benign/manipulated images), `Evaluate` (results CSV), `Screenshots` (README images).
