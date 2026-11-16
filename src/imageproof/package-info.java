/**
 * Simple-Image-Proof: a small teaching program that measures one PNG or JPEG
 * image and points out areas that may be worth a closer look.
 *
 * <h2>Flow</h2>
 * <p>The steps run in this order. {@link imageproof.Analysis} runs them and
 * {@link imageproof.App} shows the result.</p>
 * <pre>
 * Histogram -&gt; Comparison -&gt; Regions -&gt; Noise / Edges -&gt; CopyMove / FlatPatch
 *           -&gt; LocalAnomaly -&gt; TamperingAssessment -&gt; App
 * </pre>
 *
 * <h2>Classes</h2>
 * <ul>
 * <li>{@link imageproof.LoadedImage}: reads a PNG or JPEG file and finds its real format.</li>
 * <li>{@link imageproof.Histogram}: gray values
 * ({@code round(0.299 R + 0.587 G + 0.114 B)}) and the normalized 256-bin histogram.</li>
 * <li>{@link imageproof.Comparison}: total variation distance
 * {@code TVD = 0.5 * sum|p - q|} between two histograms, from 0 to 1.</li>
 * <li>{@link imageproof.Regions}: the grid of 64-pixel tiles, with a histogram and
 * a neighbour variation value for each tile.</li>
 * <li>{@link imageproof.Noise}: noise residual (pixel minus 3 x 3 local mean) and its variance per tile.</li>
 * <li>{@link imageproof.Edges}: Sobel gradients and edge strength
 * {@code sqrt(gx^2 + gy^2)}, per tile and as a picture.</li>
 * <li>{@link imageproof.CopyMove}: finds identical 16 x 16 blocks that moved by the same shift.</li>
 * <li>{@link imageproof.FlatPatch}: finds solid-colour patches in an otherwise noisy image.</li>
 * <li>{@link imageproof.LocalAnomaly}: flags tiles whose histogram and noise both
 * stand out from their neighbours and whose edges do not explain it.</li>
 * <li>{@link imageproof.TamperingAssessment}: combines the rules into one cautious outcome
 * and the measured anomalous area.</li>
 * <li>{@link imageproof.Analysis}: holds all measurements of one image and runs the steps.</li>
 * <li>{@link imageproof.App}: the Swing window with background jobs, tile selection and Advanced details.</li>
 * <li>{@link imageproof.ImagePanel} and {@link imageproof.HistogramPanel}: the drawing panels.</li>
 * </ul>
 *
 * <h2>Important</h2>
 * <p>This package is for learning. It is not forensic proof. The anomalous-area
 * percentage is a measured area. It is never a probability of forgery or editing.
 * Normal photos can have unusual tiles, so a finding only means "look closer".
 * "No notable indicators" does not prove that an image is original or authentic.</p>
 */
package imageproof;
