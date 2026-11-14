# Manual GUI test

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
