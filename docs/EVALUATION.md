# Reproducible histogram behavior evaluation

Related issue: #5. Dataset preparation is complete; measured evaluation is BLOCKED
on the real Histogram (#1) and Comparison (#2) implementations. Neither exists
on main or any fetched teammate branch at preparation time. No score below is a
measurement. No alternative scoring implementation has been substituted.

## Objective and provenance

Explore sensitivity of normalized grayscale histograms to transformations and
the loss of spatial information. All source pixels are generated for CSC360
Group 2 by the code in `tools/imageproof/DatasetGenerator.java`; no external
images, personal photographs, or third-party permission are involved. The group
may publish these synthetic samples with this project. This statement does not
change the licensing of unrelated repository content.

The single original is a 192 x 128 opaque RGB image (24,576 pixels). For each
coordinate, integer division gives R = x*255/191, G = y*255/127 and
B = 48 when (x/12 + y/16) is even, otherwise 208. This combines horizontal and
vertical brightness/color gradients with a checkerboard. ImageIO receives no
source metadata; only codec-generated structural metadata is written.

## Dataset and transformation settings

All files are included in `samples/generated/`. All images are 192 x 128 except
the 96 x 96 crop. All images use lossless PNG except the JPEG case.

| Comparison file | Exact generation method | Format |
| --- | --- | --- |
| identical.png | Same RGB pixels as original.png | PNG |
| brightness.png | Add 30 to each RGB channel; clamp to [0,255] | PNG |
| contrast.png | Each channel: Math.round(128 + 1.4*(channel-128)); clamp | PNG |
| crop.png | Rectangle x=0..95, y=0..95, copied without resizing | PNG |
| recompressed.jpg | Original RGB encoded once using default JDK JPEG writer, explicit quality 0.65f, default subsampling | JPEG |
| shuffled.png | Fisher-Yates permutation, java.util.Random seed 3602026, descending i, nextInt(i+1) | PNG |
| localized.png | Set x=88..95, y=60..67 to RGB white; exactly 64 pixels changed | PNG |
| unsupported.txt | UTF-8 explanatory text, deliberately not an image | Text |

`original.png` is the reference for every pair. JPEG starts from the original
synthetic pixels, not another JPEG. Default codec details may vary by JDK vendor
and version. Byte reproducibility is checked within the recorded environment;
the committed SHA256SUMS provides the exact artifact baseline. Different codecs
may require recording a new manifest and repeating measurements. Do not silently
replace a baseline or claim cross-vendor byte identity.

## Reproduction (repository root, PowerShell)

Java 17 or later, standard library only:

```powershell
javac --release 17 -Xlint:all -d out tools/imageproof/*.java tests/imageproof/DatasetTest.java
java -cp out imageproof.DatasetGenerator samples/generated
java '-Djava.awt.headless=true' -cp out imageproof.DatasetTest
java '-Djava.awt.headless=true' -cp out imageproof.EvaluateDataset samples/generated
```

Actual preparation results: generation produced nine inputs and SHA256SUMS;
DatasetTest now passes 9,292 checks on both JDK 24.0.2 and the actual Temurin
17.0.21+9 runtime, with Java 17 compilation target and no compilation warnings.
The Java 17 regeneration matches the committed baseline byte-for-byte, including
JPEG, PNGs and text files; no baseline changes were necessary.
The test generates twice into fresh temporary directories, compares both
generations and committed files byte-for-byte (including manifest), checks
dimensions/decoding, crop content, identical pixels, shuffle multiset and changed
positions, exactly 64 localized changes, and ImageIO returning null for the text
file. Additional checks independently hash each committed file against its
manifest entry, verify actual file formats, check fixed brightness/contrast RGB
fixtures (including saturation) and check localized patch boundaries. The
temporary test directories are removed by the test.

The portable Java 17 JRE was obtained from Eclipse Adoptium, kept outside the
repository, and verified against the release API's SHA-256 checksum:

```text
OpenJDK17U-jre_x64_windows_hotspot_17.0.21_9.zip
fe4656bd78fb09812dc38c49da777f79cc33e7ee435a254be82f9e7f40d9d117
openjdk version "17.0.21" / Temurin-17.0.21+9
```

The installed Java and PATH were unchanged. Actual Java 17 invocation:

```powershell
& "$env:TEMP\imageproof-jre17\jdk-17.0.21+9-jre\bin\java.exe" '-Djava.awt.headless=true' -cp out imageproof.DatasetTest
& "$env:TEMP\imageproof-jre17\jdk-17.0.21+9-jre\bin\java.exe" '-Djava.awt.headless=true' -cp out imageproof.EvaluateDataset samples/generated
```

The final command actually executed and reported:

```text
BLOCKED: real Histogram and Comparison implementations required; no scores produced.
```

Its actual native exit status was 2, with zero bytes on stdout. No results CSV
is committed because no scores have been measured. Dependencies were rechecked
on 2026-11-06: main and all remote branches still lack Histogram/Comparison/App.
The adapter now also rejects a nonfinite reverse score before testing symmetry;
that dependent execution path remains unverified until real math is available.

## Measured score table and expectations

| Pair against original | Actual score | Expected behavior to investigate |
| --- | --- | --- |
| Identical | BLOCKED | Exactly zero distribution distance |
| Brightness +30 | BLOCKED | Clipping and shifted channel values may change distribution |
| Contrast x1.4 | BLOCKED | Distribution may broaden and accumulate at extremes |
| Top-left crop | BLOCKED | Content-dependent: selected area excludes brighter gradient regions |
| JPEG quality 0.65 | BLOCKED | Benign encoding can change decoded pixels and histogram |
| Lossless shuffle | BLOCKED | Zero: positions change while exact RGB multiset is preserved |
| Local white 8 x 8 patch | BLOCKED | Small distribution effect; only 64/24576 pixels changed |
| Unsupported text | No score; ImageIO returned null (tested) | Application must show a useful error; App verification pending |

These expectations are hypotheses or mathematical invariants, not observed
distance results. Successful shuffle multiset verification is observed evidence
about pixels, not a completed execution of Histogram/Comparison.

## Complete the measured evaluation when dependencies arrive

1. Verify Histogram and Comparison tests and document their actual grayscale
   rule. Required formula is 0.299R + 0.587G + 0.114B; the rounding rule remains
   undecided by its owner, so do not assume floor or round here.
2. Compile actual source with the adapter:

   ```powershell
   javac --release 17 -Xlint:all -d out src/imageproof/*.java tools/imageproof/*.java tests/imageproof/DatasetTest.java
   java '-Djava.awt.headless=true' -cp out imageproof.EvaluateDataset samples/generated
   ```

3. The adapter currently uses the planned public static methods
   `Histogram.compute(BufferedImage)`, `Histogram.normalize(int[])` and
   `Comparison.difference(double[], double[])`. Adapt only these calls if the
   owners finalize a different API. Never add a fallback histogram/distance.
4. Record the exact application commit, runtime, rounding rule and actual CSV
   output in this report. The adapter checks 256 finite nonnegative bins summing
   to one, score range/symmetry, zero identical/shuffle distances and exact
   shuffled histograms. It prints scores only after all checks pass. It invokes
   the application mathematics on images decoded by ImageIO.
5. Compare expectations against each observed value, record benign/missed-change
   examples and rerun on Java 17. Then complete the real GUI invalid-file demo.

The required normalized bins are counts divided by image pixel count; distance
is 0.5 * sum(abs(p[i]-q[i])). This is a contract, not an implemented calculation
in this branch.

## Interpretation and limits

This evaluation does not support a reliable classification threshold. There are
no measured distances yet, and even future results from one synthetic original
and seven variants are too small and dependent a sample to establish population
accuracy, false-alarm rates or reliable thresholds. There is no held-out set,
camera pipeline variation, natural-image coverage, or survey of edit types.

The shuffle is a deliberate spatial change that a global histogram necessarily
misses: it counts how often a value occurs and discards where it occurs. A
benign JPEG conversion can alter frequencies and cause a false alarm under an
over-sensitive threshold. These are planned missed-change and false-alarm
demonstrations; measured magnitudes remain pending. Cropping is content-dependent
and another crop might preserve the distribution. The local patch changes only
about 0.2604% of pixels, which bounds its possible total variation effect but is
not a measured score. A larger spatial rearrangement can have zero distance.

No histogram score proves editing, absence of editing, forgery or authenticity.
Distance is not the percentage of edited pixels. A threshold cannot generally
separate arbitrary edits from benign changes because distinct images can have
the same distribution.

## Acceptance status

- Reproducible pairs, provenance and transformation parameters: complete and tested.
- Actual scores and observed behavior for all core cases: blocked on #1/#2.
- Benign differences, spatial limitations and threshold caveat: documented;
  measured examples outstanding.
- App error-handling demonstration: blocked on #3.
- Issue #5 remains open; PR remains draft pending measured evaluation and review.
- CI is not configured; local checks are not a CI run.
