# Manual GUI test

## Final assessment feature (#40)

After `mvn clean test`, run `java -cp 'target/classes;target/test-classes'
imageproof.AssessmentGuiTest` on a graphical desktop (Unix: `:`). This is a
separate real-App integration test, not part of headless Maven/CI. Its simulated
error capture does not prove that a modal dialog looks correct.

Check the new version manually:

| Step | Expected |
|---|---|
| Original, Analyze | No clear indicators, explicit limited-search caveat; reasons visible without scrolling numeric grids |
| Copied PNG/JPEG, Analyze | Possible warning, exact reasons/coordinates; candidate view and existing outlines shown |
| Repeated pattern | Inconclusive with cap explanation, never warning from count alone |
| Inserted object | No clear indicators explicitly explained as a known possible missed manipulation |
| Tile click / noise / edge / heatmap / opacity | Existing controls work; whole-image conclusion remains visible |
| Replace image | Old assessment and explanation cleared until new analysis |
| Cancel / invalid file | Current valid image/result retained; error message on invalid input |
| Replace while old worker finishes / repeated Analyze | No stale or mismatched conclusion; no double-start while busy |
| Resize to minimum and maximize | Conclusion/reasons remain readable; status label stays below controls |

### Recorded feature verification — 2026-11-16

Windows 11, Temurin 17.0.21+9, Maven 3.9.16. `mvn -B clean test` passed
all ten plain-Java test programs, including 133 assessment checks. The separate
`AssessmentGuiTest` passed 17 checks using real App workers and the EDT; it
covered stale load/analysis completion, replacement, double Analyze, invalid
load, failure recovery and an EDT heartbeat while a worker was held.

The packaged JAR was launched with Java 17 and inspected through Windows desktop
automation. Observed passes:

- Original: preview, histogram, regional heatmap and the limited no-indicator
  explanation ([screenshot](images/assessment-original-windows.png)).
- Copied PNG: amber possible warning, reasons, 25 pairs, patch coordinates,
  automatic candidate view and matching outlines
  ([screenshot](images/assessment-copy-windows.png)). Clicking a tile changed
  its histogram and measurements while preserving the whole-image assessment.
- Repeated pattern: inconclusive, 200 pairs, incomplete-search explanation
  ([screenshot](images/assessment-repeated-windows.png)).
- Inserted object: no clear indicators with an explicit missed-insertion caveat;
  no authenticity claim ([screenshot](images/assessment-inserted-windows.png)).
- Image replacement cleared old results; noise overlay and Sobel map displayed;
  heatmap off restored the plain image. Cancel retained the current result.
- Corrupt PNG displayed `Error reading PNG metadata`; dismissing it restored
  the assessment for the unchanged current image and permitted further use.
- Maximize and restore kept controls, assessment and histogram visible. The
  application was left running for manual use.

Not visually verified in this session: exact minimum-size layout, full slider
range, minimized-window restoration, large-image interaction, screen-reader
behavior, and JPEG/grayscale/transparent/unsupported-file chooser interactions.
Their mathematical/input behavior is covered by automated tests; that does not
prove those desktop interactions. Attempted border resizing did not visibly
change the window, so no minimum-size pass is claimed. No visual defect requiring
a code change was observed in the layouts inspected.

Run `./launcher.sh` (or `launcher.bat`) and follow the steps with files from `samples/`.

| # | Step | Expected |
| --- | --- | --- |
| 1 | Start the app | Window opens; Analyze disabled; note about measurements visible |
| 2 | Open image..., then Cancel | Nothing changes |
| 3 | Open `copied.png` | Preview shown, status shows `256 x 192 PNG`, Analyze enabled |
| 4 | Analyze | Histogram, statistics and regional heatmap with legend appear |
| 5 | Click a tile | Blue outline on that tile; histogram and numbers change to that tile |
| 6 | Move Opacity, untick Show heatmap | Colours fade; heatmap hidden |
| 7 | Views: Noise, Edge strength, Edge map, Copy-move | Each view changes the image area; Copy-move shows green/magenta boxes at (16,16) and (168,112) |
| 8 | Resize the window | Image, heatmap, boxes and clicks stay aligned |
| 9 | Open `corrupt.png`, then `unsupported.txt` | Error dialog each time; `copied.png` and its results stay |
| 10 | Open `repeated.png`, Analyze, Copy-move view | Many boxes and "LIMIT REACHED" (false positive, nothing edited) |
| 11 | Open `original.png` while results are shown | Old results cleared until Analyze |

## Recorded outcomes

- Steps 4, 5 and 7 (results display, tile selection, all views) were checked by drawing the real window with `tools/imageproof/Screenshots.java` on macOS, OpenJDK 21: see `docs/images/app-*.png`. Outlines, heatmap and copy-move boxes line up with the known rectangles.
- Click/resize alignment (step 8) is covered by `ImagePanelTest` at 5 panel sizes.
- Load errors (step 9) are covered by `AnalysisTest` and `SamplesTest`.
- Steps 1–3, 6, 8, 9–11 by hand with the mouse: **not yet recorded**. Whoever runs them should fill in date, OS, Java version and pass/fail here.
