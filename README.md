# Simple-Image-Proof

**CSC360 End-Semester Project | Group 2 | Ahmedabad University**

> **Assigned topic:** "Simple-Image-Proof: Simple check of Image Edits using a basic Histogram technique."

## Overview

Simple-Image-Proof is a student project exploring a basic way to **compare images using their intensity/color histograms**. The goal is to show how a histogram-based comparison can highlight changes between a reference image and a second image that may have been edited.

This is an **educational image-comparison tool**, not a forensic authentication system. A different histogram can suggest that an image has changed, but it cannot establish *why* it changed or prove deliberate manipulation. An edit can also leave the histogram almost unchanged.

**Current status:** A first working version exists: open one PNG or JPEG image, press **Analyze**, and the app shows its grayscale histogram with mean, standard deviation and entropy.

Issue #5 adds reproducible synthetic sample images, a Java 17 dataset generator
and dependency-free tests. See [evaluation and reproduction commands](docs/EVALUATION.md).
Measured scores remain blocked until the histogram and comparison components
are available; no classification threshold has been validated.

The histogram panel from issue #4 is now part of the app window (`HistogramPanel`).

## Project objectives

- Read a reference image and a comparison image.
- Calculate and visualize basic grayscale histograms (optionally RGB histograms).
- Normalize histogram counts so images with different pixel counts can be compared.
- Compute a simple, explainable histogram-difference score.
- Display the histograms and comparison result in a clear, beginner-friendly way.
- Evaluate the method using original images and intentionally modified test images.

## Proposed method

1. **Select images:** Provide a reference image and an image to check.
2. **Load and validate:** Confirm both files can be read and identify their dimensions and formats.
3. **Calculate histograms:** Convert to grayscale and count pixels for each intensity value (0–255).
4. **Normalize:** Divide each histogram bin by the total pixel count.
5. **Compare:** Use normalized histogram distance to measure the difference.
6. **Report:** Show histogram plots and a numeric score, with a cautious interpretation.

A straightforward proposed metric is **total variation distance**:

```text
difference = 0.5 * sum(abs(reference_bin[i] - comparison_bin[i]))
             for i = 0..255
```

Here, `reference_bin` and `comparison_bin` are **normalized** histogram frequencies. The difference lies between **0** (same distribution) and **1** (no overlapping distribution). It does **not** measure the percentage of pixels edited.

No detection threshold has been selected yet; any threshold will need to be tested against representative examples.

## Expected output

When implemented, the project should show:

- A visual comparison of original and comparison histograms.
- A histogram-difference value.
- An explanation of whether the distributions are similar or different.
- An important note that the result alone **does not verify image authenticity**.

## Limitations

- Histograms discard pixel positions: two visibly different images can have the same histogram.
- Brightness, contrast, resizing, cropping, and compression can affect results without malicious editing.
- Small localized edits may be difficult to detect using a global histogram.
- Without a trusted reference image, a single histogram cannot reliably determine whether an image has been edited.

## Planned milestones

| Stage | Goal | Status |
| --- | --- | --- |
| 1 | Define the method, scope, and test plan | In planning |
| 2 | Implement image loading and histogram generation | Not started |
| 3 | Add histogram comparison and visual results | Not started |
| 4 | Test original vs. edited examples | Not started |
| 5 | Document findings and prepare demonstration | Not started |

See [Project Plan](docs/PROJECT_PLAN.md) for the proposed scope, acceptance criteria, and test cases.

## Technology and setup

Java 17+ with Swing and Maven. **No third-party libraries.** Sources live in `src/imageproof/`, tests in `tests/imageproof/`.

Needs **JDK 17+** and **Maven**. No IDE needed.

```bash
mvn test                                   # runs all tests (headless, fails the build on any error)
mvn package -Dexec.skip=true               # builds target/simple-image-proof-0.1.0.jar without tests
java -jar target/simple-image-proof-0.1.0.jar
```

Or run `./launcher.sh` (macOS/Linux) or `launcher.bat` (Windows). The launcher checks Java 17+ and Maven, rebuilds what changed, skips tests and starts the app. `mvn test` also works from another folder (`mvn -f path/to/pom.xml test`). GitHub Actions runs `mvn test` on Java 17 (Ubuntu and Windows) for every push and pull request.

Usage: press **Open image…** and choose a PNG or JPEG file, then press **Analyze**. Opening another image clears the previous result. Cancelling the file dialog changes nothing. Unreadable, unsupported or too large (over 16 megapixels) files show an error and keep the current image and its results.

Tested so far (`mvn test`): histogram counting, gray and alpha rules, normalization, statistics, `Comparison`, loading valid and invalid files, and the histogram panel. A recorded mouse walkthrough of the window (open, cancel, analyze, replace, bad file) is still to do.

## Team

**CSC360 — Group 2**

- Satvik Patel
- Varun
- Garv
- Shambhavee

**Topic assigned by:** Dhruvee Sheth

## Academic note

This repository is for the CSC360 end-semester group project. The histogram method will be presented together with its limitations; the team will not present a statistical difference as conclusive proof of image tampering.
