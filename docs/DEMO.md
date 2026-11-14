# Demo and presentation guide

A short live demo using only the committed `samples/`, plus the questions we expect and the answers the code supports. Numbers come from [EVALUATION.md](EVALUATION.md); rerun `Evaluate` if the code changes.

## Before the demo

1. Fresh checkout, then `mvn test`: must end with `All tests passed`.
2. `./launcher.sh` (or `launcher.bat`) once, so the jar is built and the window opens quickly.
3. Walk through [MANUAL_TEST.md](MANUAL_TEST.md) once by hand and fill in its "Recorded outcomes".

## Demo script (about 6 minutes)

| # | Do | Show / say |
| --- | --- | --- |
| 1 | Open `original.png`, **Analyze** | Global histogram, mean 126.35, std 50.22, entropy 7.650 bits. The note: measurements, not a verdict. |
| 2 | Click a tile | That tile's own histogram and numbers. Regional variation = average TVD to the 4 neighbour tiles. |
| 3 | Open `copied.png`, **Analyze**, view **Copy-move candidates** | 25 pairs, all with shift (152, 96): the known copied 48 x 48 square. |
| 4 | Open `repeated.png`, **Analyze**, **Copy-move candidates** | 200 candidates and LIMIT REACHED, but nothing was edited: repeated patterns are a false positive. |
| 5 | Open `inserted.png`, **Analyze**, views **Noise** and **Edge strength** | Highest noise and edge values are in tile r1 c2, which covers part of the inserted square, but object outlines do the same in normal photos. No copy-move candidate: a missed change. |
| 6 | Open `shuffled.png`, **Analyze** | Same mean, std and entropy as the original although it is pure noise: a global histogram ignores pixel positions. |
| 7 | Open `corrupt.png`, then `unsupported.txt` | Error dialog; the previous image and results stay. |

Close with the limitations slide: no threshold, no probability, no "edited / not edited" answer.

## Diagrams for slides

- [pixel-to-histogram.svg](images/pixel-to-histogram.svg): one pixel to one gray value, then counting into bins.
- [histogram-compare.svg](images/histogram-compare.svg): how TVD adds up the gaps between two histograms (16 bins shown for readability; the app uses 256).
- Screenshots of the real window: `images/app-*.png`.

## Questions to expect

| Question | Answer |
| --- | --- |
| Why grayscale and not RGB? | One histogram per image or tile, easy to show and explain. Luma weights `0.299, 0.587, 0.114` follow how sensitive eyes are to each colour. |
| Why normalize? | Tiles and images have different pixel counts; dividing by the total makes histograms comparable. |
| Why total variation distance? | Simple formula, always between 0 and 1, symmetric. It is not the percentage of edited pixels. |
| Why 64 px tiles? | 4,096 pixels give a usable histogram per tile, and tiles are still small enough to point at a region. Edge tiles can be smaller. |
| Why no threshold? | One synthetic original and a few variants cannot support one. Normal edges, texture and compression move the same numbers. |
| How are transparent and gray images handled? | Transparent pixels are mixed onto white (what a viewer shows); gray images read the stored sample, because `getRGB` changes gray values (issues #22, #23). |
| How does copy-move work? | 16 x 16 blocks every 8 px, flat blocks skipped, exact equal blocks that do not touch, grouped by shift; a shift needs at least 3 matches. Limits keep it fast and are reported on screen. |
| What can copy-move not find? | Copies that are rotated, scaled, recompressed differently, or moved by a shift that is not a multiple of 8. |
| Why does the JPEG copy still match? | The shift (152, 96) is a multiple of 8, the JPEG block size, so 15 of 25 copied blocks were encoded identically. Other shifts usually break exact matching. |
| Why is the window responsive? | Loading and analysis run in `SwingWorker`s; a job number makes sure a slow old result is never shown for a newer image. |
| How do you know the maths is right? | `mvn test` runs headless checks for every class (uniform images give zero noise and edges, hand-calculated TVD values, exact copy found, limits respected), on Java 17 in CI on Ubuntu and Windows. |
| Can this prove an image is fake or real? | No. It describes the image and points at regions worth a closer look. |
