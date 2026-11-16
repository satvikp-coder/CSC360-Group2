# Java API documentation (Javadoc)

## What is Javadoc?

Javadoc is the Java tool that turns the `/** ... */` comments in the source code
into a set of web pages. Each class gets a page that explains what it is for, and
each method lists what it does, what its inputs (`@param`) mean, what it returns
(`@return`) and when it can fail (`@throws`).

In this project the comments are written in simple English and explain the real
maths: grayscale conversion, the 256-bin histogram, total variation distance,
tiles, the noise residual, Sobel edges, copy-move matching, the local anomaly
rule and how the anomalous-area percentage is calculated.

## Generate the pages

Run this in the project folder. `javadoc` is part of the JDK (it lives in the
JDK's `bin` folder next to `java` and `javac`; if your terminal does not find it,
use the full path, for example `"C:\Program Files\Java\jdk-17\bin\javadoc"`).

```bash
javadoc -encoding UTF-8 -charset UTF-8 -Xdoclint:all,-missing -d target/site/apidocs -sourcepath src -subpackages imageproof
```

* `-sourcepath src -subpackages imageproof` reads the application code in `src/`.
* `-d target/site/apidocs` is where the pages are written.
* `-Xdoclint:all,-missing` checks all comment tags and links. It only leaves out
  the note "default constructor has no comment" for the small utility classes.

The command ends with no errors and no warnings. The generated pages are build
output: they live under `target/`, which Git ignores, so they are **not** committed.

## Open the pages

Open this file in a web browser:

```text
target/site/apidocs/index.html
```

For example, on Windows run `start target\site\apidocs\index.html`.

## How to read a class page

1. **Package overview** (`index.html`): the flow of the program and one line per class.
2. **Class page**, for example `Histogram.html`: the first paragraph says what the class is for.
3. **Field Summary** lists constants such as the tile size or thresholds.
4. **Method Summary** lists every method with a one-line description. Click a name for the full description with its `Parameters`, `Returns` and `Throws` sections.
5. **Constructor Detail** and **Method Detail** show the exact signature.

A good place to start is `TamperingAssessment`: it explains the three outcomes and
that the anomalous-area percentage is a **measured area**, not a probability that an
image was forged.

## Notes

* Javadoc pages are not published to GitHub Pages. Generate them locally.
* Only the application code in `src/` is documented. The tests (`javatest/`) and tools (`tools/`) are ordinary programs.
