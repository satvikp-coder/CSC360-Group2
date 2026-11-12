# Histogram panel contract and verification

Related issue: #4. The standalone panel was merged in PR #9; application
integration and end-to-end verification continue in #3 / PR #12.

Construct `new HistogramPanel("Original image")` and a second panel titled
`"Comparison image"` on the Swing event dispatch thread. Call `setValues(p)` and
`setValues(q)` with normalized 256-bin arrays produced by Histogram. Arrays are
copied. Null and all-zero arrays clear the plot. Invalid lengths, nonfinite,
negative, above-one or nonnormalized values clear stale data then throw
IllegalArgumentException; normalization tolerance is 1e-9.

Both panels default to the same fixed maximum frequency of 1.0. For more detail,
App may calculate the largest bin across BOTH arrays and pass that same positive
value to both `setMaximumFrequency` calls before setting values. Do not calculate
an independent scale for each plot. Zero maximum: retain the default scale.
Values above an explicitly supplied scale are clipped at the plot top.
On input replacement, clear BOTH panels with `clearData()` and invalidate the
score until comparison runs again. All mutations belong on the event dispatch
thread. No histogram calculation belongs in this component.

All bins are drawn; bins sharing a screen column retain the tallest bar. Any
positive frequency has at least one pixel height. Very small components clip
labels and omit the plot when no positive drawing area remains. Insets are
respected. Use adequate layout space for fully readable labels.

## Reproduce automated verification

From repository root (Java 17 or later; no libraries):

```powershell
javac --release 17 -Xlint:all -d out src/imageproof/HistogramPanel.java tests/imageproof/HistogramPanelTest.java tests/imageproof/HistogramPanelDemo.java tests/imageproof/HistogramPanelDesktopCheck.java
java '-Djava.awt.headless=true' -cp out imageproof.HistogramPanelTest
```

Recorded standalone result: 1,356 checks passed on both JDK 24.0.2 and the actual
Temurin 17.0.21+9 runtime, compiled with `javac --release 17 -Xlint:all` on JDK 24.
Covers empty/null/zero/invalid input, defensive copy, every bin's full-width
position and narrow visibility, shared scales with different distributions,
updating, clearing, resizing, tiny components, tiny positive scales and insets.
No compilation warnings. CI is not configured.

The portable Java 17 JRE was downloaded from Eclipse Adoptium's release API,
outside the repository, without changing PATH or the installed Java:

- Archive: `OpenJDK17U-jre_x64_windows_hotspot_17.0.21_9.zip`
- SHA-256 verified against the package checksum published by the release API:
  `fe4656bd78fb09812dc38c49da777f79cc33e7ee435a254be82f9e7f40d9d117`
- Runtime: `openjdk version "17.0.21"`, `Temurin-17.0.21+9`.

To reproduce with a portable runtime, replace `java` above with its full path:

```powershell
& "$env:TEMP\imageproof-jre17\jdk-17.0.21+9-jre\bin\java.exe" '-Djava.awt.headless=true' -cp out imageproof.HistogramPanelTest
```

The explicit source list builds only the panel and its three verification
programs; it does not pull in dataset or application tests with other dependencies.
`HistogramPanelTest` is the automated headless check. `HistogramPanelDemo` and
`HistogramPanelDesktopCheck` are manual desktop tools: compiling them through
Maven's test source directory does not run them, and `mvn test` is not evidence
of a visual pass. Run the commands below on a graphical desktop and inspect
the output separately.

## Desktop visual verification

```powershell
java -cp out imageproof.HistogramPanelDemo
```

On 2026-11-06, Codex inspected screenshots captured from the actual visible
Swing window on the available Windows desktop (1536 x 864), running the actual
Temurin 17.0.21+9 runtime. This is agent visual inspection, not teammate approval
or a human-operated end-to-end application session.

| Content size / state | Observed outcome | Evidence |
| --- | --- | --- |
| 840 x 260 | Titles/axes/scale readable; original 0/128/255 spikes at half/full/half height; comparison 32/192 spikes full height | [Normal](images/histogram-panel-normal.png) |
| 640 x 220 | Same positions and comparable heights retained; labels readable | [Small](images/histogram-panel-small.png) |
| 400 x 200 | All five spikes retained despite narrow graphs; titles, intensity endpoints and frequency scales readable | [Narrow](images/histogram-panel-narrow.png) |
| 840 x 260 after clear/null | Both plots show no bars and display "No histogram loaded" | [Cleared](images/histogram-panel-cleared.png) |

The screenshots contain only the demo's content area, not other desktop content.
Reproduce the capture (requires an unlocked graphical desktop and briefly opens
the test window, moves the pointer outside it, then disposes the window):

```powershell
java -cp out imageproof.HistogramPanelDesktopCheck out/desktop-check
```

Capture success alone does not establish a visual pass; inspect the images.
The harness verifies actual content dimensions rather than relying on a resize
request. These deliberately retained screenshots are test evidence; transient
captures and compiled classes remain in ignored `out/`. Smaller components can
clip text as documented above. The harness is not the missing application.

## Acceptance status

- Synthetic positions, common scale, updates/resizing/clearing: automated checks pass.
- Titles, frequency explanation, safe invalid-input painting: implemented and tested.
- Normal/small/narrow desktop captures and cleared state: visually inspected by Codex; evidence above.
- Real two-image workflow: blocked on Histogram/App (#1/#3).
- Varun approved and merged the standalone panel in PR #9 on 2026-11-12.
- Issue #4 is closed; application integration and end-to-end verification continue in #3 / PR #12.
