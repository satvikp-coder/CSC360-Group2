# API Reference

Package `imageproof` (Java 17). The complete reference is the Javadoc in
[`docs/api/`](api/index.html); open `docs/api/index.html` in a web browser.

Simple-Image-Proof: a small teaching program that measures one PNG or JPEG image and points out areas that may be worth a closer look.

## Classes

| Class | Summary |
| --- | --- |
| [`Analysis`](api/imageproof/Analysis.html) | All measurements for one image, collected in one place. |
| [`App`](api/imageproof/App.html) | Main window of the program. |
| [`Comparison`](api/imageproof/Comparison.html) | Total variation distance (TVD) between two normalized histograms. |
| [`CopyMove`](api/imageproof/CopyMove.html) | Looks for copy-move candidates: two 16 x 16 blocks with exactly the same gray values. |
| [`Edges`](api/imageproof/Edges.html) | Sobel edge diagnostic. |
| [`FlatPatch`](api/imageproof/FlatPatch.html) | Finds solid-colour patches (like a Paint fill) in an otherwise noisy image. |
| [`Histogram`](api/imageproof/Histogram.html) | Turns an image into gray values (0 to 255) and a 256-bin histogram. |
| [`HistogramPanel`](api/imageproof/HistogramPanel.html) | Swing panel that draws a normalized 256-bin histogram as bars. |
| [`ImagePanel`](api/imageproof/ImagePanel.html) | Swing panel that shows an image scaled to fit, keeping its shape (empty bars at the sides). |
| [`LoadedImage`](api/imageproof/LoadedImage.html) | One image file read from disk: the pixels plus its real format (PNG or JPEG). |
| [`LocalAnomaly`](api/imageproof/LocalAnomaly.html) | Local anomaly rule for ONE image. |
| [`Noise`](api/imageproof/Noise.html) | Noise diagnostic. |
| [`Regions`](api/imageproof/Regions.html) | Splits the image into square tiles (64 x 64 pixels by default) and measures each tile. |
| [`TamperingAssessment`](api/imageproof/TamperingAssessment.html) | Final assessment of ONE image. |

## Generating the documentation

```bash
javadoc -encoding UTF-8 -charset UTF-8 --release 17 -Xdoclint:all,-missing -notimestamp -windowtitle "Simple-Image-Proof API" -doctitle "Simple-Image-Proof API" -d docs/api -sourcepath src -subpackages imageproof
```

`javadoc` is in the JDK's `bin` folder.
