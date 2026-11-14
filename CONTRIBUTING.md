# Contributing

This repository is for **CSC360 Group 2**. Contributions should support the assigned histogram-based single-image analysis project.

## Team workflow

1. Create a separate branch for each feature or documentation update.
2. Keep commits focused and use meaningful messages.
3. Explain what changed and how it was tested in the pull request.
4. Ask another group member to review before merging where practical.
5. Keep unfinished changes in a draft pull request until checks and relevant tests pass.

## Implementation principles

- Prefer clear, understandable code suited to an academic demonstration.
- Separate image loading, measurements, and presentation: maths classes contain no Swing code.
- Add tests for correct calculations and invalid inputs; `mvn test` must pass.
- Do not label an image as edited, forged or authentic; show measurements only.
- Avoid including confidential images, personal information, credentials, or unlicensed sample data.

## Documentation updates

When behaviour changes, update `README.md` (setup, usage, tested behaviour) and `docs/PROJECT_PLAN.md` (decisions). If measurements change, rerun `Evaluate` and update `docs/EVALUATION.md`, `docs/RESULTS.csv` and the numbers in `docs/DEMO.md`.

## Questions and proposed changes

Discuss design choices with the team before adding significant dependencies, major new features, or changing the project's scope.
