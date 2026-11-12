# Single-image roadmap

Scope: [SINGLE_IMAGE_BLUEPRINT.md](SINGLE_IMAGE_BLUEPRINT.md).
New issues stay unassigned for team allocation; existing owners are preserved.

| Order | Issue | Priority | Depends on |
|---|---|---|---|
| 1 | #1 histogram, #22 grayscale, #23 alpha; #25 launcher, #26 portability | High | Existing code |
| 2 | #27 one-image workflow and global statistics | High | #1, #22, #23 |
| 3 | #28 regional histograms, heatmap and inspection | High | #27 |
| 4 | #29 noise residual and Sobel diagnostics | Medium | #27, #28 |
| 5 | #30 bounded copy-move candidates | Medium | #27, #28 |
| 6 | #5 fixtures and measured evaluation | High | #27–#30, #26 |
| 7 | #7 build, #14 Java 17 CI; #8/#16 documentation/demo | High | Verified modules |

#6 threshold selection is superseded by the no-verdict/no-probability decision.
Closed #2/#4/#13/#15/#17/#24 retain their history. #3 retains Varun's original
workflow; #27 is the migration follow-up. Draft PR #20 remains for its author
to reconcile. Optional work does not delay required modules.
