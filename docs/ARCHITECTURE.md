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
                                                    +--> TamperingAssessment (rule after all measurements finish)
Analysis --> App shows it in ImagePanel (heatmap / boxes) and HistogramPanel + text
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
| `TamperingAssessment` | Validates completed evidence; reports a limited assessment with reasons, copy footprints and separate descriptive measurements |
| `LoadedImage` | Real format check (PNG/JPEG only), header size check, readable errors |
| `App` | Window, buttons, view selector, `SwingWorker`s with a job number so stale results are ignored |
| `ImagePanel` | Letterboxed image, heatmap, legend, copy-move boxes, selected tile, screen <-> image mapping |
| `HistogramPanel` | Bar chart of 256 bins |

## Threads

`App` starts on the Swing thread (`SwingUtilities.invokeLater`). Loading and analysing run in `SwingWorker`s. Each job gets `jobNumber++`; `done()` only shows a result if its number is still the newest, so an old result can never appear for a newer image.

The assessment is calculated at the end of `Analysis.run`, on the same worker,
not during Swing rendering. The right-hand assessment box sits outside the
scrollable numeric tables. Successful image replacement and re-analysis clear
the previous conclusion. Cancel returns before changing a job; failed loads
retain the matching current result; failed analysis leaves no fabricated result.
Repeated Analyze while busy is ignored. A positive assessment selects the
existing candidate view; other views and tile selection remain available.

The [rule](EVALUATION.md#final-assessment-rule) uses no weighted score. Generic
map maxima and overlapping tile measurements are context, not independent
evidence. Only qualifying exact-patch geometry supports a possible-duplication
warning; no state establishes authenticity or a probability.

## Tests and tools

`tests/imageproof`: one test class per class above plus `SamplesTest`; `AllTests` runs them (`mvn test`). `tools/imageproof`: `SampleGenerator` (samples), `Evaluate` (results CSV), `Screenshots` (README images).
