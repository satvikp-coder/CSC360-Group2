# Single-image evaluation

Actual measurements are in [SINGLE_IMAGE_RESULTS.csv](SINGLE_IMAGE_RESULTS.csv),
recorded on Windows with Temurin 17.0.21+9, compiled with JDK 24.0.2 release 17.
The CSV contains measurements, not expected values or editing probabilities.
Legacy pair generation/tests and [earlier report](LEGACY_EVALUATION.md) are retained.

## Reproduce

From the repository root, after `mvn test` (PowerShell classpath separator):

```powershell
java -cp 'target/classes;target/test-classes' imageproof.SingleImageDataset samples/single-image
java '-Djava.awt.headless=true' -cp 'target/classes;target/test-classes' imageproof.SingleImageEvaluation .
```

On Unix use `:` instead of `;`. Regeneration overwrites only the named synthetic
fixtures in the specified directory. Do not point it at personal images.
Tests resolve committed assets from Maven's `imageproof.basedir` property.
The legacy manifest independently verifies every committed asset. Lossless
regeneration is checked by decoded pixels, and JPEG by dimensions/decodability
and mean absolute RGB deviation <=12 from its baseline. Regenerated JPEG bytes
are not required to equal another JDK's encoder output. This tolerance is a
fixture portability check, not a forensic classification threshold.

## Provenance and ground truth

All inputs are group-created using Java standard APIs, with no private/external
photos. The original legacy 192×128 gradient/checkerboard and transformations
remain in `samples/generated` with exact settings in the archived report.
`SingleImageDataset` adds:

| File | Deterministic construction / known region |
|---|---|
| copied-texture.png | 96×96 grayscale RGB texture, Random(360); copy source [0,32)×[0,32) to [64,96)×[64,96) |
| inserted-object.png | Same seeded texture; solid RGB ffcc33 rectangle x=[40,72), y=[32,56) |
| repeated-pattern.png | 128×128; gray=(x mod 8)*20+(y mod 8)*10; no copy operation |
| transparent.png | 32×32; left half hidden white alpha=0, right black alpha=128 |
| grayscale.png | 32×32 BYTE_GRAY; raw sample=x*8 |
| blurred.png | Original seeded texture, available 3×3 rounded channel mean |
| resized.png | Original seeded texture resized 96→48 with bilinear interpolation |
| corrupt.png | Six-byte truncated PNG-like header; loader must reject |

The original, identical, brightness, contrast, crop, JPEG, shuffle, localized
change and unsupported-text cases are also measured by the evaluation command.

## Observations and failures

- Known copied object: 9 coherent candidate pairs with displacement (64,64).
- Repeated pattern: 256 returned pairs and truncation, despite no copy operation.
- JPEG recompression: 8 candidate pairs in this fixture; these are false-positive
  candidate responses, not proof of a copied object.
- Original/shuffle share mean, SD and entropy, but spatial diagnostics differ:
  mean residual variance rises from 9.790058 to 2178.681115 after shuffling.
- Localized change slightly shifts global statistics; no detection claim follows.
- Blur reduces residual variance in the seeded texture; normal processing affects
  these measurements. A single small tile has neighbor TVD 0 even when textured.

There is one small synthetic family, no representative natural-image corpus,
held-out benchmark or probability calibration. Exact grid-based matching can
miss near-exact, off-grid, rotated, scaled, blurred or compressed copies. Local
boundaries and texture can respond strongly. No accuracy percentage, population
false-positive rate, authenticity verdict or manipulation probability is claimed.
