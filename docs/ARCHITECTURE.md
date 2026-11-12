# Single-image architecture

Current scope: [blueprint](SINGLE_IMAGE_BLUEPRINT.md). The earlier
[two-image architecture](LEGACY_ARCHITECTURE.md) is retained as history.

```text
one JPEG/PNG -> ImageAnalyzer.load -> App preview
Analyze -> background ImageAnalyzer -> Histogram.grayPixels
    -> global histogram + mean / population SD / entropy
    -> RegionAnalyzer -> per-tile histograms + neighboring TVD
    -> NoiseAnalyzer -> tile residual variance
    -> EdgeAnalyzer -> Sobel strength image
    -> CopyMoveAnalyzer -> bounded coherent exact-block candidates
AnalysisResult -> Swing histogram / image overlay / numeric evidence
```

`App` owns Swing state. `ImagePanel` uses one aspect-preserving transform for
the displayed image, tile overlays, click mapping and candidate rectangles.
`WorkerTickets` rejects late load/analysis completions. Only background workers
decode files or run the analysis modules. Mathematical classes do not use Swing.
The old `LegacyComparisonApp` and TVD tests are preserved, outside the primary UI.

## Numerical definitions

- Gray: round(0.299R + 0.587G + 0.114B). Alpha channels are composited onto white
  with `(channel*alpha + 255*(255-alpha) + 127)/255`, integer division.
  Gray color-space raster samples retain encoded intensity, including gray-alpha;
  16-bit values are rounded to 0–255. Premultiplied gray is unpremultiplied first.
  This convention avoids ImageIO's gray-to-sRGB brightness conversion. It is not
  calibrated physical luminance; preview color management may look different.
- Histogram: exactly 256 counts; normalization uses a long total and rejects
  null/wrong length/negative/zero-total arrays. Fully transparent images become
  white rather than empty. Inputs are bounded to 16 million pixels.
- Statistics: mean = sum(i*p[i]); population variance = sum((i-mean)^2*p[i]);
  entropy = -sum(p[i]*log2(p[i])) for nonzero bins.
- Tiles: 64×64 by default, partial edge tiles kept. Variation = arithmetic mean
  of `0.5*sum(abs(p-q))` to existing up/down/left/right neighbors; no neighbors = 0.
- Noise: gray minus the available 3×3 mean (smaller neighborhood at borders).
  Welford's population residual variance is calculated in each tile. The map
  uses variance/max variance in this image; values are not calibrated noise scores.
- Sobel: kernels [-1,0,1; -2,0,2; -1,0,1] and its transpose; border coordinates
  clamp to the nearest pixel. Display = round(hypot(gx,gy)/(4*sqrt(2))), max 255.
- Copy candidates: 16×16 grayscale blocks on an 8-pixel grid; variance <25 is
  ignored. A 64-bit content hash finds candidates, then all pixels are checked.
  Exclude pairs within 32 pixels in both axes. Require at least three pairs
  sharing one displacement. This is displacement coherence, not object recognition.

## Bounds and interpretation

Copy search examines at most 50,000 blocks / 200,000 comparisons, keeps at most
16 positions per hash bucket / 4,096 raw matches and returns at most 256 pairs.
Truncation is visible. More/larger/rotated/scaled/off-grid or recompressed copies
can be missed. Repeated natural texture can match. Module loops check interruption.
The file loader inspects dimensions before decoding and accepts JPEG/PNG only.

Hot tiles can be normal dark/bright boundaries. Residuals include texture and
edges; no camera-sensor identity is inferred. No measurements are combined into
an editing probability, automatic verdict or anomaly index. Evaluation records
real failure cases in [EVALUATION.md](EVALUATION.md).
