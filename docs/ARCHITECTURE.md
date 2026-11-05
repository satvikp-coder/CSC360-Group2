# Simple-Image-Proof — Architecture

**CSC360 · Group 2** · Minimal version: two images, one grayscale histogram each, one difference score.

> One-line idea: count how many pixels have each brightness (0–255) in both images, compare the counts, and report *similar* or *different*.

---

## 1. Big picture

```mermaid
flowchart LR
    A[Original image] --> C[Convert to grayscale]
    B[Suspect image] --> C
    C --> D[Count pixels per gray value<br/>256 bins]
    D --> E[Normalize<br/>count ÷ total pixels]
    E --> F[Difference score<br/>0.5 × Σ abs p−q]
    F --> G{score > threshold T?}
    G -- yes --> H[LIKELY EDITED]
    G -- no --> I[LIKELY UNEDITED]
```

The same steps run on **both** images. Only the last two boxes look at both together.

---

## 2. What happens to the pixels

![From one pixel to a normalized histogram](images/pixel-to-histogram.svg)

| Step | Input | Output |
|---|---|---|
| Grayscale | pixel (R, G, B) | one number 0–255: `0.299R + 0.587G + 0.114B` |
| Histogram | all gray values | `int[256]` — `hist[v]` = how many pixels have gray value `v` |
| Normalize | `int[256]` | `double[256]` — each count ÷ total pixels; all values add up to 1 |

**Why normalize?** A 4000×3000 photo has far more pixels than a 400×300 copy. Dividing by the total turns counts into *fractions*, so image size stops mattering.

**Why these weights (0.299, 0.587, 0.114)?** Human eyes are most sensitive to green and least to blue. This is the standard luminance formula (ITU-R BT.601).

---

## 3. How two histograms are compared

![Comparing two histograms](images/histogram-compare.svg)

```
difference = 0.5 × Σ |p[i] − q[i]|      for i = 0 … 255
```

- `p` = normalized histogram of original, `q` = of suspect.
- `|p[i] − q[i]|` = how far apart the two bars are at gray value `i` (red bars in panel B).
- Add all gaps, then halve → result always between **0** (identical histograms) and **1** (no overlap at all).
- Name of this measure: **total variation distance**.

**Verdict rule:** `difference > T` → "Likely edited". `T` is picked from our test results (section 7), not guessed.

---

## 4. Classes (only 4)

```mermaid
classDiagram
    class App {
        -BufferedImage original
        -BufferedImage suspect
        -HistogramPanel originalPanel
        -HistogramPanel suspectPanel
        -JLabel resultLabel
        +main(String[] args)
        -loadImage() BufferedImage
        -compare()
    }
    class Histogram {
        +toGray(int rgb)$ int
        +compute(BufferedImage img)$ int[]
        +normalize(int[] counts)$ double[]
    }
    class Comparison {
        +THRESHOLD$ double
        +difference(double[] p, double[] q)$ double
        +isEdited(double score)$ boolean
    }
    class HistogramPanel {
        -double[] values
        +setValues(double[] values)
        #paintComponent(Graphics g)
    }
    App --> Histogram : uses
    App --> Comparison : uses
    App *-- "2" HistogramPanel : shows
```

| Class | Job (one sentence) | Knows about Swing? |
|---|---|---|
| `Histogram` | Turns an image into a normalized 256-value array. | No |
| `Comparison` | Turns two arrays into a score and a yes/no verdict. | No |
| `HistogramPanel` | Draws one array as bars on screen. | Yes |
| `App` | Window, buttons, file loading; connects everything. | Yes |

**Design rule:** the math (`Histogram`, `Comparison`) never touches the UI. It can be tested alone, and explained alone.

---

## 5. What happens when you click "Compare"

```mermaid
sequenceDiagram
    actor User
    participant App
    participant Histogram
    participant Comparison
    participant Panels as HistogramPanel ×2

    User->>App: Load Original / Load Suspect
    App->>App: ImageIO.read(file) → BufferedImage
    User->>App: click Compare
    App->>Histogram: compute(original)
    Histogram-->>App: int[256] counts
    App->>Histogram: normalize(counts)
    Histogram-->>App: double[256] p
    App->>Histogram: compute + normalize (suspect)
    Histogram-->>App: double[256] q
    App->>Comparison: difference(p, q)
    Comparison-->>App: score
    App->>Comparison: isEdited(score)
    Comparison-->>App: true / false
    App->>Panels: setValues(p), setValues(q)
    Panels-->>User: bars redrawn
    App-->>User: "Difference: 0.27 → LIKELY EDITED"
```

---

## 6. Screen layout

![UI wireframe](images/ui-wireframe.svg)

One window (`JFrame`): button row on top, two image previews, two histogram panels, one result line.

---

## 7. Testing: how we pick threshold T

```mermaid
flowchart LR
    O[One original photo] --> E1[Same file]
    O --> E2[Brighter]
    O --> E3[More contrast]
    O --> E4[Cropped]
    O --> E5[Re-saved as JPEG]
    O --> E6[Pixels shuffled]
    E1 & E2 & E3 & E4 & E5 & E6 --> R[Record score of each pair]
    R --> T[Choose T between<br/>'same' scores and 'edited' scores]
```

| # | Pair | Expected |
|---|---|---|
| 1 | Image vs itself | score = 0 |
| 2 | vs brighter copy | high score |
| 3 | vs higher contrast | high score |
| 4 | vs cropped | some score, depends on what was cut |
| 5 | vs JPEG re-save | small score — **not** an edit; T must sit above this |
| 6 | vs shuffled pixels | score = 0 — **limitation demo** |
| 7 | Non-image file | clear error message, no score |

Edits are made by hand in any image editor (e.g., GIMP / Preview) and saved in `samples/`.

---

## 8. What this method cannot do (we say this openly)

```mermaid
flowchart LR
    X[Histogram keeps only<br/>HOW MANY pixels per brightness] --> Y[Loses WHERE pixels are]
    Y --> Z1[Shuffled image → same histogram → score 0]
    Y --> Z2[Small pasted object → tiny change → may be missed]
    X --> Z3[JPEG / resize change bars slightly → possible false alarm]
```

So the verdict means *"brightness distribution changed"*, not *"proof of forgery"*.

---

## 9. Files

```
CSC360-Group2/
├── src/imageproof/
│   ├── App.java             window + buttons + glue
│   ├── Histogram.java       gray, count, normalize
│   ├── Comparison.java      score + verdict
│   └── HistogramPanel.java  draws bars
├── samples/                 test images (original + edited copies)
└── docs/
    ├── ARCHITECTURE.md      this file
    ├── JAVA_CONCEPTS.md     Java topics needed
    └── images/              diagrams used here
```

Build & run (plain JDK 17, no libraries):

```bash
javac -d out src/imageproof/*.java
```

```bash
java -cp out imageproof.App
```
