# Archived two-image project plan

Historical plan only; current scope and acceptance criteria are in
SINGLE_IMAGE_BLUEPRINT.md, ROADMAP.md and PROJECT_PLAN.md.

**Course:** CSC360  
**Group:** 2  
**Status:** Proposed plan, subject to group decisions

## Problem statement

Investigate whether simple pixel-intensity histograms can reveal differences between a reference image and a modified version. Produce an understandable comparison and communicate what the method can and cannot establish.

## Scope

### In scope
- Two-image comparison: reference and image under examination.
- Grayscale histogram generation (256 intensity bins).
- Normalized histogram comparison and basic visualization.
- A reproducible set of example images with known modifications.
- A short explanation of the result and the method's limitations.

### Optional if time permits
- RGB channel histograms.
- A simple command-line or graphical interface.
- Comparison of several basic distance measures.

### Out of scope for the initial version
- Claiming that an image is authentic or forged.
- Identifying the exact edited region using only a global histogram.
- AI-based deepfake detection, watermark analysis, or full forensic investigation.
- Treating a histogram score as courtroom-grade proof.

## Proposed implementation approach

1. Agree on the language, libraries, and interface.
2. Implement image loading and friendly errors for invalid paths/formats.
3. Generate a 256-bin grayscale histogram per image.
4. Normalize bin values by total pixels.
5. Calculate **total variation distance** between normalized histograms:

   `distance = 0.5 × Σ |p[i] − q[i]|` for `i = 0...255`

6. Display the value and both histograms.
7. Record test observations and document false positives/false negatives.

The score is a difference between intensity distributions, **not** a measured editing percentage. Any decision threshold must be justified experimentally rather than assumed.

## Suggested test plan

| Test | Comparison | What to investigate |
| --- | --- | --- |
| T1 | Identical image vs. itself | Distance should be zero |
| T2 | Original vs. brightness-adjusted copy | Whether brightness changes the distribution |
| T3 | Original vs. contrast-adjusted copy | Sensitivity to contrast |
| T4 | Original vs. cropped copy | Sensitivity to changes in image content |
| T5 | Original vs. recolored copy | Difference between grayscale and RGB comparisons |
| T6 | Original vs. recompressed JPEG | False alarms from normal encoding changes |
| T7 | Original vs. spatially rearranged pixels | Important limitation: histogram can remain identical |
| T8 | Unreadable / non-image input | Clear failure rather than a misleading result |

Use images that the group created or is permitted to use, and do not commit private photos or sensitive metadata without permission.

## Acceptance criteria

- Two valid input images produce histograms and a reproducible comparison score.
- Identical image inputs produce a zero difference (within numerical precision).
- Invalid inputs fail with understandable errors.
- Output explains that histogram comparison is not definitive proof of editing.
- At least the core test cases are documented with expected vs. observed results.
- README includes actual setup and run steps once implementation exists.

## Proposed deliverables

- Source code and instructions.
- Sample inputs for demonstration, with appropriate rights.
- Histogram plots and comparison examples.
- Tests/results and a discussion of method limitations.
- A short presentation or live demo if required by the course.

## Team coordination

Agree on responsibilities and a working timeline together. Prefer small pull requests, clear commit messages, and review before merging changes into `main`.

## Open decisions

- Implementation language and dependencies.
- CLI vs. GUI vs. notebook interface.
- Grayscale only vs. grayscale plus RGB.
- Evaluation dataset and any comparison threshold.
- Final submission format and course deadline.
