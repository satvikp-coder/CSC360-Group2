package imageproof;

import java.awt.Container;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

// Draws the real App window into PNG files for the README (needs a desktop, not headless).
// Run: java -cp target/classes:target/test-classes imageproof.Screenshots samples docs/images
public class Screenshots {

    static String samples;
    static String output;

    public static void main(String[] args) throws Exception {
        samples = args[0];
        output = args[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                try {
                    App app = new App();
                    shot(app, "copied.png", 0, -1, -1, "app-regions.png");
                    shot(app, "copied.png", 0, 2, 1, "app-tile.png");
                    shot(app, "copied.png", 3, -1, -1, "app-edges.png");
                    shot(app, "copied.png", 4, -1, -1, "app-copy-move.png");
                    shot(app, "repeated.png", 4, -1, -1, "app-repeated.png");
                    app.frame.dispose();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
        System.out.println("Screenshots written to " + output);
    }

    // Load a sample, analyse it, pick a view (and maybe a tile), save the window.
    static void shot(App app, String sample, int view, int row, int col, String name) throws Exception {
        app.current = LoadedImage.load(new File(samples, sample));
        app.imagePanel.setImage(app.current.image);
        app.clearResults();
        app.showResults(Analysis.run(app.current.image));
        app.status.setText(app.describe(app.current) + "  - analysed");
        app.view.setSelectedIndex(view);
        if (row >= 0) {
            app.selectTile(row, col);
        }
        Container content = app.frame.getContentPane();
        content.validate();
        BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics g = image.getGraphics();
        content.paint(g);
        g.dispose();
        ImageIO.write(image, "png", new File(output, name));
    }
}
