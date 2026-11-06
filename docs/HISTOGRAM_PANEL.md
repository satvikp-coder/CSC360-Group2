# Histogram panel contract and verification

Related issue: #4. Standalone implementation; integration awaits #1 and #3.

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
javac --release 17 -Xlint:all -d out src/imageproof/HistogramPanel.java tests/imageproof/*.java
java '-Djava.awt.headless=true' -cp out imageproof.HistogramPanelTest
```

Actual local result: 281 checks passed on JDK 24.0.2 using Java 17 compilation
target. Covers empty/null/zero/invalid input, defensive copy, correct endpoint
and middle bin positions, shared scale, updating, clearing, resizing, tiny
components and every single-bin spike in a narrow plot. The repository had no
existing tests or GitHub Actions workflows. CI is not configured.

## Manual verification still required

```powershell
java -cp out imageproof.HistogramPanelDemo
```

At the default 840 x 260 content size, verify titles, axes, original spikes at
0/128/255 and comparison spikes at 32/192 with the same 0.5 scale. Resize to
400 x 200 and verify labels and peaks; very narrow layouts intentionally clip
text. No interactive desktop inspection was performed by the agent. Record
observer, runtime, sizes and outcomes here before considering this criterion
complete. The harness is not the missing application workflow.

## Acceptance status

- Synthetic positions, common scale, updates/resizing/clearing: automated checks pass.
- Titles, frequency explanation, safe invalid-input painting: implemented and tested.
- Manual visual check at normal/small sizes: outstanding.
- Real two-image workflow: blocked on Histogram/App (#1/#3).
- Issue remains open; merge requires teammate approval and completed verification.
