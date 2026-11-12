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

## Issue disposition

| Issues | Disposition |
|---|---|
| #2, #4, #13, #15, #17, #24 | Already closed; left unchanged |
| #1 | Relevant histogram foundation; owner kept; companion implementation in #31 |
| #3 | Partial legacy app; Varun's history/body kept; migration follow-up #27 |
| #5 | Updated to single-image fixtures/actual evaluation, preserving legacy artifacts |
| #6 | Commented, then closed as not planned: threshold-verdict work superseded by the explicit scope decision |
| #7, #14 | Build/CI remain relevant; implementation and evidence proposed in #31 |
| #8, #16 | Delivery work retained with original owners; migration notes posted |
| #22, #23 | Valid gray/alpha bugs retained; coordinated companion fixes in #31 |
| #25, #26 | Valid launcher/portability bugs retained; reviewed and fixes proposed |
| #27–#30 | Four grouped new issues; implementation in #31; unassigned for team allocation |

No teammate assignment or active branch was changed. PR #20 remains draft/open
with an explanatory migration comment. PR #31 requests review from Varun,
Shambhavee and Garv. Main was not overwritten or force-pushed. Branch-protection
details were unavailable to the connector (403); repository rulesets list was
empty. The change remains reviewable, rather than bypassing any review gate.
