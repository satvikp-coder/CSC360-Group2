# Migration audit — 2026-11-12

Repository/account confirmed: satvikp-coder/CSC360-Group2 / Satvik Patel.
Baseline main: 7504737 (full SHA in Git history). Initial working tree clean;
all remote branches and tags fetched; work starts on feature/single-image-analyzer.

All issues and PR descriptions/states were read. Closed #2, #4, #13, #15,
#17 and #24 remain historical completions. PRs #9–#12, #18, #19 and #21
are merged. Varun's draft #20 remains active and its documentation/manual work
must not be overwritten on his branch. Reviews there contain no major findings,
but the required real application demonstration is outstanding.

Baseline `mvn -B test` passed on JDK 24.0.2 with release 17: ComparisonTest,
HistogramTest, 1,356 panel checks and 9,292 dataset checks. Surefire's zero-test
summary is not the evidence: the exec plugin actually runs the plain-main tests.
No CI workflow is tracked at baseline. A Temurin 17.0.21+9 runtime is available
for actual Java 17 execution after compilation.

Reuse: histogram panel, TVD, background-worker approach, synthetic dataset and
plain-main tests. Histogram itself is still labelled PLACEHOLDER and lacks
validation. #22/#23 are real grayscale/alpha bugs; #25/#26 are valid launcher
and dataset reliability bugs. #1/#3/#5/#6/#7/#8/#14/#16 retain useful work but
need explicit migration notes. Existing assignments will be preserved.

README, architecture, project plan, evaluation status and two-image manual
instructions are outdated for the professor's new one-image requirement.
The blueprint is recorded before issue administration. No closed issue is
reopened simply because the scope changed.
