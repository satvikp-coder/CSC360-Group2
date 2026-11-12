# Simple-Image-Proof 2.0

**Single-Image Forensic Analyzer** — professor-directed revised scope, 2026-11-12.
This document replaces the two-image product plan. Earlier comparisons and tests
remain useful mathematical and evaluation tools, but the primary screen accepts
one JPEG or PNG, with no reference image required.

## Constraints and interpretation

Java 17, Swing, ImageIO and standard Java image APIs; Maven builds the desktop
application. Analysis runs entirely offline. No ML, learned/pretrained models,
OpenCV, external image-analysis libraries, AI APIs, servers or accounts.
Show measured values and descriptive findings. Neither unusual statistics nor
matching blocks prove editing, forgery or authenticity. No manipulation
probability or anomaly index is introduced. Natural boundaries, texture,
compression and repeated patterns can produce strong responses.

## Reuse and mathematical contract

- Preserve Histogram, Comparison, HistogramPanel and existing tests.
- Histogram uses round(0.299 R + 0.587 G + 0.114 B), 256 bins and normalized counts.
- Composite transparency onto white with integer rounding before analysis;
  fully transparent images therefore have a valid white histogram. Explicit
  grayscale raster samples use their encoded 8-bit values (16-bit scaled to
  0–255), consistently across all modules. This is an intensity convention,
  not calibrated luminance or camera-sensor analysis.
- Comparison's total variation distance (TVD) remains 0.5 sum(abs(p-q)).
- Keep the earlier DatasetGenerator, DatasetTest and EvaluateDataset as legacy
  regression/evaluation tools; extend rather than delete their useful work.

## Required modules

1. Global histogram, mean, population standard deviation and optional entropy.
2. Regional normalized histograms in default 64×64 tiles, including partial edge
   tiles. Each tile's variation is mean TVD to its existing four side-neighbors.
3. Image-aligned heatmap, legend, visibility/opacity control and selectable tiles.
   Hot colors mean larger local statistical variation, including normal boundaries.
4. Noise diagnostic: grayscale minus a local 3×3 mean; regional population
   residual variance. Texture/edges contribute; no sensor attribution.
5. Sobel horizontal/vertical gradients and a grayscale strength map, with safe
   border handling. Strong normal object boundaries are expected.
6. Bounded copy-move candidates: exact textured blocks, distant nonoverlapping
   matches grouped by displacement. Prefer coherent groups; repeated patterns
   cause false positives. Rotation/scaling detection is out of scope.

## Development phases and acceptance

1. Preserve baseline, fix alpha/grayscale/invalid inputs, dataset portability and
   launch reliability; verify Java 17 and repeatable headless tests.
2. One upload, preview/dimensions/format, Analyze and background processing.
   Successful replacement clears results; chooser cancellation preserves input;
   failed loads show useful errors; stale workers cannot publish results.
3. Deliver global statistics, regional grid, heatmap and region inspection end
   to end; test edge tiles, resizing and image/overlay coordinate mapping.
4. Add noise, Sobel and bounded copy-move independently with deterministic tests.
5. Extend synthetic fixtures, record real measurements and failure examples;
   document provenance, known modifications and ground-truth rectangles.
6. Finish README, screenshots, manual GUI results and Java 17 CI. Request review
   before merging; leave unverified delivery criteria open.

Every module handles invalid/small inputs and bounded work for large images.
Mathematics stays independent of Swing. Keep classes small and understandable.
Optional metadata/JPEG/RGB/report features wait until all required modules are
stable. No optional feature is required to run the core analyzer.
