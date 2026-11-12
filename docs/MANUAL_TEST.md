# Manual end-to-end test: Swing workflow (issue #3)

Run `./launcher.sh` (or `launcher.bat`) from the repository root. Sample inputs are in `samples/generated/`
(see [EVALUATION.md](EVALUATION.md)). **Outcome** columns are filled in by the tester; leave as `TODO` until run.

## Preliminary expected scores

Computed by `EvaluateDataset` using `Comparison` and the *placeholder* `Histogram` (issue #1 not merged yet), with
`original.png` as the reference. They are **not** final measurements and are **not** evidence for a threshold (#6).

| Comparison file | Distance | Comment |
| --- | --- | --- |
| identical.png | 0.0000 | same pixels |
| brightness.png | 0.2056 | +30 per channel |
| contrast.png | 0.1799 | contrast x1.4 |
| crop.png | 0.3317 | different size, same top-left content |
| recompressed.jpg | 0.0370 | JPEG q=0.65, not a deliberate edit |
| shuffled.png | 0.0000 | same pixels, moved: **limitation** |
| localized.png | 0.0026 | 64 pixels pasted white: easy to miss |

## Steps

| # | Steps | Expected | Outcome |
| --- | --- | --- | --- |
| 1 | Start the app | Window opens; Compare disabled; message "Choose both images" | TODO |
| 2 | Load reference = original.png; load comparison = brightness.png; press Compare | Two previews, two histograms, score about 0.2056, caveat text visible | TODO |
| 3 | Load comparison = crop.png (different dimensions) and Compare | Works; score about 0.3317 | TODO |
| 4 | After a result, load another comparison image | Plots cleared, message "Inputs changed - press Compare." until Compare is pressed | TODO |
| 5 | Press Load comparison, then Cancel | Nothing changes; previous result and previews stay | TODO |
| 6 | Load unsupported.txt | Error dialog "not a supported image"; previous inputs unchanged | TODO |
| 7 | Load a corrupt file (e.g. truncate original.png with `head -c 100`) | Error dialog or "not a supported image"; no crash, no score | TODO |
| 8 | Load identical.png as comparison | Score 0.0000 | TODO |
| 9 | Load shuffled.png as comparison | Score 0.0000; images look different (limitation demo) | TODO |
| 10 | Resize the window to the minimum and maximize | Controls usable, score legible, plots resize | TODO |

Record: date, tester, OS, `java -version`, and any deviation.
