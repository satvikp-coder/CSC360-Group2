# Manual GUI test

## Final assessment feature (#40)

After `mvn clean test`, run `java -cp 'target/classes;target/test-classes'
imageproof.AssessmentGuiTest` on a graphical desktop (Unix: `:`). This is a
separate real-App integration test, not part of headless Maven/CI. Its simulated
error capture does not prove that a modal dialog looks correct.

Check the current version manually:

| Step | Expected |
|---|---|
| Original, Analyze | "No notable indicators", anomalous area 0.0%, three short reasons, one stats block; no numeric grids anywhere |
| Copied PNG/JPEG, Analyze | "Possible tampering indicators", area from the matching blocks, copy reasons; matching blocks outlined |
| Repeated pattern | "Inconclusive", area N/A, never a warning from a match count alone |
| Inserted object | "No notable indicators" (a known missed manipulation, see EVALUATION.md) |
| Tile click | Blue outline, histogram of that tile, about 7-line SELECTED REGION summary; the assessment above does not change; **Whole-image histogram** returns |
| Views / overlay / opacity | Flagged-regions view first; measurement maps outline flagged tiles; opacity 0-100 fades the overlay |
| Advanced details... | Separate read-only window with tables, rule, thresholds and copy-search counts |
| Replace image | Old assessment, area, histogram, advanced view cleared until the new analysis |
| Cancel / invalid file | Current valid image/result retained; error message on invalid input |
| Resize to minimum (920 x 700) and maximize | Assessment, area, selected-region summary and buttons stay visible and readable |

### Recorded verification: local anomaly rule and concise UI, 2026-11-16

Windows 11, real desktop, driven by a scratch harness using `java.awt.Robot`
(screen capture, one real mouse click) on the exact code of this commit.
Compiled with `--release 17` on JDK 24.0.2 (no JDK 17 was installed locally; CI
runs Temurin 17 on Ubuntu and Windows). Screenshots were inspected by eye:
[selected region](../assets/images/desktop-selected-region.png),
[minimum size 920 x 700](../assets/images/desktop-min-size.png),
[advanced details](../assets/images/desktop-advanced.png).

| Check | Result |
|---|---|
| Concise result after Analyze (constructed noise-patch image) | Pass: status, "ANOMALOUS AREA: 4.2% of analyzed image area", 3 reasons, note, stats block |
| Real mouse click on a flagged tile | Pass: summary shows Position (192, 128), the same tile that is outlined |
| Selected-region summary and whole-image assessment | Pass: summary is 7 lines; assessment text unchanged; Whole-image histogram button restores the global histogram |
| Opacity 0 / 25 / 50 / 75 / 100 % | Pass: pixel in a flagged tile reads 121,121,121 / 145,91,91 / 171,61,61 / 195,30,30 / 220,0,0 (linear blend, image visible at 0) |
| All 6 views | Pass: each draws; measurement maps outline the flagged tiles in dark red |
| Exact minimum size 920 x 700 | Pass after one fix: the interpretation text was clipped, now shorter; Open/Analyze, assessment, histogram (103 px high), summary and buttons visible, no overlap |
| Minimize, restore, maximize | Pass: state ICONIFIED then NORMAL; repainted correctly; tile click still works |
| `original.png`, `copied.jpg`, `grayscale.png`, `transparent.png`, `inserted.png` through the GUI worker path | Pass: results 0.0 % / 6.3 % / 0.0 % / 0.0 % / 0.0 % |
| `unsupported.txt`, `corrupt.png` | Pass: error text "unsupported.txt is not a PNG or JPEG image." / "Error reading PNG metadata"; previous valid image and assessment kept |
| Replacement clears results | Pass: analysis null, "Not analyzed", area blank |
| 4000 x 3000 (12 MP) image | Pass: loads in about 1.4 s, analysis about 4.9 s, worst UI-thread delay 42 ms, heap about 118 MB of 4 GB; result Inconclusive because the copy search covers only part of a large image (stated in the reasons) |
| Keyboard | Focus order Open, Analyze, overlay checkbox, opacity slider, reasons, summary (the view list and buttons are skipped while disabled); every control has an accessible name |

`AssessmentGuiTest` passes 23 checks including the short result, a short tile
summary, assessment unchanged by tile selection, and the return to the
whole-image histogram.

**Not verified:** a real screen reader (only accessible names were checked
programmatically), a real file-chooser dialog (the harness calls the same load
method), Linux/macOS desktops for this version, and drag-resizing of the window
border by hand (sizes were set programmatically).

### Earlier verification (previous UI, same day)

Before this change: 10 test programs, 133 assessment checks, 17 desktop checks,
Java 17 Maven build, and Windows screenshots of the earlier exact-copy-only
assessment ([original](../assets/images/assessment-original-windows.png),
[copy](../assets/images/assessment-copy-windows.png),
[repeated](../assets/images/assessment-repeated-windows.png),
[inserted](../assets/images/assessment-inserted-windows.png)). Those images show the
superseded layout with the long numeric text.

## Step-by-step (old and still valid)

Run `./launcher.sh` (or `launcher.bat`) and follow the steps with files from `samples/`.

| # | Step | Expected |
| --- | --- | --- |
| 1 | Start the app | Window opens; Analyze disabled; note about measurements visible |
| 2 | Open image..., then Cancel | Nothing changes |
| 3 | Open `copied.png` | Preview shown, status shows `256 x 192 PNG`, Analyze enabled |
| 4 | Analyze | Short assessment, anomalous area, histogram and flagged-region overlay appear |
| 5 | Click a tile | Blue outline on that tile; histogram and the short region summary change to that tile |
| 6 | Move Opacity, untick Show overlay | Colours fade; overlay hidden |
| 7 | Views: Regional variation, Noise, Edge strength, Edge map, Copy-move | Each view changes the image area; Copy-move shows green/magenta boxes at (16,16) and (168,112) |
| 8 | Resize the window | Image, overlay, boxes and clicks stay aligned |
| 9 | Open `corrupt.png`, then `unsupported.txt` | Error dialog each time; `copied.png` and its results stay |
| 10 | Open `repeated.png`, Analyze, Copy-move view | Many boxes (false positive, nothing edited); assessment Inconclusive |
| 11 | Open `original.png` while results are shown | Old results cleared until Analyze |

## Recorded outcomes

- `ImagePanelTest` covers click/resize alignment at 5 panel sizes. `AnalysisTest` and `SamplesTest` cover load errors.
- Steps 1-11 on the real window were exercised on 2026-11-16 (table above) except cancelling the real file chooser, which is unchanged code and not re-run. Fill in further runs (date, OS, Java version, pass/fail) here.
