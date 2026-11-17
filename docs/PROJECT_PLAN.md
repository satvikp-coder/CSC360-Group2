# Project Plan — Simple-Image-Proof

**Course:** CSC360 | **Group:** 2 | **Status:** implemented (single-image analyzer)

## Problem statement

Use simple, explainable Java image processing (histograms first) to describe one image and point at regions worth a closer look, while being honest that none of it proves editing.

## Scope

In scope (professor-directed revision, 2026-11-12): **one** uploaded PNG/JPEG; global histogram and statistics; regional histograms and heatmap; noise residual; Sobel edges; bounded copy-move candidates; synthetic samples with known edits; measured results.

Out of scope: definitive authenticity/forgery verdicts, calibrated probabilities,
machine learning, outside libraries or services, rotation/scale-invariant copy
detection and a two-image comparison screen. A limited explainable exact-duplication
assessment is requested in #40; its geometry rule and known failures are documented
in EVALUATION.md. It does not turn generic histogram/noise/edge extrema into votes.

## Decisions taken

| Decision | Choice | Why |
| --- | --- | --- |
| Language / UI | Java 17 + Swing | Course language; no extra libraries |
| Build | Maven | One command for build and tests; works without an IDE |
| Tests | Plain `main` programs run by `AllTests` (no JUnit) | Nothing extra to learn; nonzero exit on failure |
| CI | GitHub Actions, Temurin 17, Ubuntu + Windows | Checks every PR |
| Gray rule | `round(0.299R + 0.587G + 0.114B)` | Standard luma weights |
| Transparency | Mixed onto white | Matches what a viewer shows; hidden RGB ignored |
| Gray images | Read stored sample (16-bit scaled) | `getRGB` changes gray values |
| Distance | Total variation distance | Easy to explain, 0 to 1 |
| Tile size | 64 px | Enough pixels per histogram, small enough to localise |
| Copy-move | Exact 16 px blocks, step 8, limits | Simple and bounded; limits shown on screen |
| Threshold | None | Not supported by evaluation; see `EVALUATION.md` |
| Assessment | Local anomaly rule (histogram + noise + neighbour agreement, edges explain) plus exact-copy evidence | Uses the measurements that exist instead of relying on copied blocks; uncertain evidence is inconclusive, no match or flag ever certifies authenticity |
| Percentage | Anomalous area = 100 x flagged pixels / analyzed pixels | A measured share of the image, not a probability; no calibration data exists, so no editing probability is shown |
| Flat patch | >= 6 equal 4x4 blocks with a 3x3 core, noisy image, not JPEG | Catches Paint fills that the noise/histogram rule cannot; excluded cases listed in `EVALUATION.md` |
| Thresholds | Modified z 3.5, TVD floor 0.10, noise ratio 2, noise floor 4, 3 neighbours | Round values chosen before measuring; validated only on small synthetic sets (see `EVALUATION.md`) |

## Test plan and results

Automated: `mvn test` (see `javatest/imageproof`). Measured sample results: [EVALUATION.md](EVALUATION.md). GUI steps: [MANUAL_TEST.md](MANUAL_TEST.md).

## Remaining

- Manual GUI walkthrough by hand: record outcomes in `MANUAL_TEST.md`.
- Course submission format and deadline: to confirm with the instructor.
