# Contributing

This repository is for **CSC360 Group 2**. The current product is the offline single-image analyzer in `docs/SINGLE_IMAGE_BLUEPRINT.md`; preserve useful legacy comparison code/tests.

## Team workflow

1. Create a separate branch for each feature or documentation update.
2. Keep commits focused and use meaningful messages.
3. Explain what changed and how it was tested in the pull request.
4. Ask another group member to review before merging where practical.
5. Keep unfinished changes in a draft pull request until checks and relevant tests pass.

## Implementation principles

- Prefer clear, understandable code suited to an academic demonstration.
- Separate image loading, histogram calculation, comparison, and presentation.
- Add tests for correct calculations and invalid inputs.
- Report numerical measurements and limitations; never infer forgery, authenticity or an editing probability from unusual statistics.
- Use Java 17/Swing/ImageIO only for analysis: no ML, pretrained models, OpenCV, external analysis APIs or unnecessary dependencies.
- Avoid including confidential images, personal information, credentials, or unlicensed sample data.

## Documentation updates

When features are implemented, update `README.md` with the actual dependencies, setup commands, usage example, and tested behavior. Update `docs/PROJECT_PLAN.md` as decisions are made.

## Questions and proposed changes

Discuss design choices with the team before adding significant dependencies, major new features, or changing the project's scope.
