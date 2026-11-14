# Project Plan — Simple-Image-Proof

**Course:** CSC360 | **Group:** 2 | **Status:** implemented (single-image analyzer)

## Problem statement

Use simple, explainable Java image processing (histograms first) to describe one image and point at regions worth a closer look, while being honest that none of it proves editing.

## Scope

In scope (professor-directed revision, 2026-11-12): **one** uploaded PNG/JPEG; global histogram and statistics; regional histograms and heatmap; noise residual; Sobel edges; bounded copy-move candidates; synthetic samples with known edits; measured results.

Out of scope: authenticity/forgery verdicts, thresholds or probabilities, machine learning, outside libraries or services, rotation/scale-invariant copy detection, two-image comparison screen.

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

## Test plan and results

Automated: `mvn test` (see `tests/imageproof`). Measured sample results: [EVALUATION.md](EVALUATION.md). GUI steps: [MANUAL_TEST.md](MANUAL_TEST.md). Demo script: [DEMO.md](DEMO.md).

## Remaining

- Manual GUI walkthrough by hand: record outcomes in `MANUAL_TEST.md`.
- Course submission format, presentation and deadline: to confirm with the instructor.
