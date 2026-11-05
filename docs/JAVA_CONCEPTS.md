# Java Concepts Needed — Simple-Image-Proof

Only what the 4 classes actually use. Grouped by where you'll meet it.

## 1. Core Java (used everywhere)
| Concept | Where in project |
|---|---|
| Classes, `static` methods & constants | `Histogram`, `Comparison` are all-static helpers; `THRESHOLD` is `static final` |
| Packages (`package imageproof;`) & `import` | Top of every file |
| Arrays: `int[]`, `double[]`, indexing | `hist[gray]++`, 256-bin arrays |
| Nested `for` loops | Walk every pixel: `for y … for x …` |
| `int` vs `double`, casting | `(double) count / total` — **without the cast, int division gives 0** |
| `Math.round`, `Math.abs` | Gray value rounding; `|p − q|` |
| `if` / boolean return | `isEdited(score)` |

## 2. Images (`java.awt.image`, `javax.imageio`)
| Concept | Where |
|---|---|
| `BufferedImage`, `getWidth()`, `getHeight()`, `getRGB(x, y)` | Reading pixels in `Histogram.compute` |
| `java.awt.Color` → `getRed()`, `getGreen()`, `getBlue()` | Splitting a pixel into R, G, B (avoids bit-shifting) |
| `ImageIO.read(File)` | Loading PNG/JPG/BMP; returns `null` if format unsupported |

## 3. Errors
| Concept | Where |
|---|---|
| `try` / `catch (IOException e)` | File can't be read |
| `null` check | `ImageIO.read` returned `null` → not an image |
| `JOptionPane.showMessageDialog` | Show the error to the user |

## 4. Swing GUI (`javax.swing`)
| Concept | Where |
|---|---|
| `JFrame`, `JPanel`, `JButton`, `JLabel` | The window and its parts |
| Layout managers: `BorderLayout`, `GridLayout` | Arranging buttons / images / panels |
| `JFileChooser` | "Load Original" / "Load Suspect" |
| Event listeners with lambdas: `button.addActionListener(e -> compare())` | Button clicks |
| `ImageIcon` in a `JLabel` | Showing image previews |
| `SwingUtilities.invokeLater(...)` | Starting the GUI safely in `main` |

## 5. Custom drawing (`java.awt.Graphics`)
| Concept | Where |
|---|---|
| Inheritance: `class HistogramPanel extends JPanel` | Custom panel |
| Overriding `paintComponent(Graphics g)` + `super.paintComponent(g)` | Drawing the bars |
| `g.fillRect(x, y, w, h)`, `g.setColor(...)` | One rectangle per bin |
| Screen y-axis points **down** | Bar top = `panelHeight − barHeight` |
| `repaint()` | Redraw after new data via `setValues` |

## 6. Optional
| Concept | Where |
|---|---|
| JUnit 5 `@Test`, `assertEquals(expected, actual, delta)` | Check: image vs itself → score 0 |
| `javac -d out` / `java -cp out` | Compile & run without an IDE |

**Not needed:** generics, collections, streams, threads, interfaces, external libraries.
