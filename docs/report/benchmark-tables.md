### Development set (126 images, 7 scenes)

| Truth | Edit | Images | Possible | No notable | Inconclusive | Mean area when flagged |
|---|---|---|---|---|---|---|
| genuine | original scene | 7 | 0 | 7 | 0 | - |
| genuine | re-save / format / size | 28 | 0 | 26 | 2 | - |
| local-edit | insert-object | 7 | 0 | 7 | 0 | - |
| local-edit | brightness-patch+45 | 7 | 0 | 7 | 0 | - |
| local-edit | brightness-patch-45 | 7 | 0 | 7 | 0 | - |
| local-edit | paint-fill-rect | 7 | 7 | 0 | 0 | 3.9% |
| local-edit | paint-fill-ellipse | 7 | 7 | 0 | 0 | 3.1% |
| local-edit | paint-scribble | 7 | 0 | 7 | 0 | - |
| copy-move | copy-move-grid | 7 | 5 | 1 | 1 | 6.6% |
| copy-move | copy-move-offgrid | 7 | 0 | 7 | 0 | - |
| global-edit | global-brightness+40 | 7 | 0 | 7 | 0 | - |
| global-edit | global-blur | 7 | 0 | 7 | 0 | - |
| genuine | small 160x120 original | 7 | 0 | 0 | 7 | - |
| local-edit | small 160x120 edits | 14 | 0 | 0 | 14 | - |

Slowest analysis 128 ms, median 41 ms; copy-search coverage 100% for all images.

### Held-out set (162 images, 9 scenes)

| Truth | Edit | Images | Possible | No notable | Inconclusive | Mean area when flagged |
|---|---|---|---|---|---|---|
| genuine | original scene | 9 | 0 | 9 | 0 | - |
| genuine | re-save / format / size | 36 | 0 | 34 | 2 | - |
| local-edit | insert-object | 9 | 0 | 9 | 0 | - |
| local-edit | brightness-patch+45 | 9 | 0 | 9 | 0 | - |
| local-edit | brightness-patch-45 | 9 | 0 | 9 | 0 | - |
| local-edit | paint-fill-rect | 9 | 9 | 0 | 0 | 4.3% |
| local-edit | paint-fill-ellipse | 9 | 9 | 0 | 0 | 3.4% |
| local-edit | paint-scribble | 9 | 0 | 9 | 0 | - |
| copy-move | copy-move-grid | 9 | 7 | 1 | 1 | 7.0% |
| copy-move | copy-move-offgrid | 9 | 0 | 9 | 0 | - |
| global-edit | global-brightness+40 | 9 | 0 | 9 | 0 | - |
| global-edit | global-blur | 9 | 0 | 9 | 0 | - |
| genuine | small 160x120 original | 9 | 0 | 0 | 9 | - |
| local-edit | small 160x120 edits | 18 | 0 | 0 | 18 | - |

Slowest analysis 651 ms, median 40 ms; copy-search coverage 100% for all images.

