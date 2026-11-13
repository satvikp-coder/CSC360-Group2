# Java Concepts Used — Simple-Image-Proof

Only what the code actually uses. The code avoids lambdas, streams, records and the `? :` operator so it reads like textbook Java.

## 1. Core Java
| Concept | Where |
|---|---|
| Classes, `static` methods, `static final` constants | `Histogram`, `Comparison`, `Noise`, `Edges` |
| Objects with public fields | `Analysis`, `Regions`, `LoadedImage`, `CopyMove.Match` |
| 1D / 2D / 3D arrays | `int[256]` counts, `gray[y][x]`, `histograms[row][col][bin]` |
| Nested `for` loops | walking pixels and tiles |
| Casting `(double) count / total` | without it, int division gives 0 |
| `long` to avoid overflow | `(long) width * height` |
| `Math.round`, `Math.abs`, `Math.sqrt`, `Math.log` | gray value, TVD, Sobel, entropy |
| Exceptions: `throw new IllegalArgumentException`, `try`/`catch`/`finally`, `IOException` | invalid input, bad files |
| Static nested class | `CopyMove.Match` |
| `ArrayList`, `HashMap` | copy-move blocks and groups by shift |
| Bit operations `(argb >> 16) & 255` | splitting a pixel into A, R, G, B |

## 2. Images
| Concept | Where |
|---|---|
| `BufferedImage`, `getRGB(x, y, w, 1, row, 0, w)` | reading a row of colour pixels |
| `Raster.getSample` | reading gray images exactly |
| `ImageIO`, `ImageReader`, `ImageInputStream` | real format check and loading |
| Writing PNG/JPEG | `SampleGenerator` |

## 3. Swing
| Concept | Where |
|---|---|
| `JFrame`, `JPanel`, `JButton`, `JLabel`, `JComboBox`, `JCheckBox`, `JSlider`, `JTextArea` | `App` |
| `BorderLayout`, `GridLayout` | window layout |
| `ActionListener`, `ChangeListener`, `MouseAdapter` | buttons, slider, clicks |
| `extends JPanel` + `paintComponent(Graphics g)` | `ImagePanel`, `HistogramPanel` |
| `SwingWorker` (`doInBackground`, `done`) | loading and analysing off the Swing thread |
| `SwingUtilities.invokeLater` | starting the GUI |
| Inner classes | `LoadWorker`, `AnalyzeWorker`, `ClickHandler` |

## 4. Build
| Concept | Where |
|---|---|
| Maven `pom.xml`, `mvn test`, `mvn package` | build and tests |
| Tests as `main` methods that throw `AssertionError` | `tests/imageproof` |
