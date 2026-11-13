package imageproof;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

// Tests that the image, heatmap and mouse clicks line up at any panel size.
public class ImagePanelTest {

    static void check(boolean ok, String message) {
        if (!ok) {
            throw new AssertionError(message);
        }
    }

    static BufferedImage render(ImagePanel panel, int width, int height) {
        panel.setSize(width, height);
        BufferedImage out = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics g = out.getGraphics();
        panel.paint(g);
        g.dispose();
        return out;
    }

    public static void main(String[] args) {
        // Wide panel: image 200 x 100 in 600 x 200 -> 400 x 200 with 100 px bars left and right.
        check(ImagePanel.fit(200, 100, 600, 200).equals(new Rectangle(100, 0, 400, 200)), "wide letterbox");
        // Tall panel: 200 x 100 in 200 x 300 -> 200 x 100 with 100 px bars top and bottom.
        check(ImagePanel.fit(200, 100, 200, 300).equals(new Rectangle(0, 100, 200, 100)), "tall letterbox");

        BufferedImage image = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        ImagePanel panel = new ImagePanel();
        panel.setImage(image);

        int[][] sizes = {{600, 200}, {200, 300}, {333, 177}, {1000, 900}, {50, 40}};
        for (int i = 0; i < sizes.length; i++) {
            panel.setSize(sizes[i][0], sizes[i][1]);
            Rectangle area = panel.imageArea();
            // Clicks outside the image (in the bars) select nothing.
            check(panel.toImagePoint(area.x - 1, area.y) == null, "left of image");
            check(panel.toImagePoint(area.x, area.y + area.height) == null, "below image");
            // Corners map to the image corners.
            check(panel.toImagePoint(area.x, area.y).equals(new Point(0, 0)), "top left");
            Point last = panel.toImagePoint(area.x + area.width - 1, area.y + area.height - 1);
            // When shrunk, one screen pixel covers several image pixels, so allow that much.
            check(last.x <= 199 && last.x >= 199 - 200 / area.width, "bottom right x");
            check(last.y <= 99 && last.y >= 99 - 100 / area.height, "bottom right y");
            // A tile drawn on screen and clicked in its middle gives back the same tile.
            Rectangle tile = new Rectangle(128, 64, 64, 36);
            Rectangle onScreen = panel.toPanel(tile);
            Point middle = panel.toImagePoint(onScreen.x + onScreen.width / 2, onScreen.y + onScreen.height / 2);
            check(middle.x / 64 == 2 && middle.y / 64 == 1, "round trip at size " + i);
        }

        // Heatmap: 2 x 2 tiles of 100 x 50, top-left is the max (red), others 0 (yellow).
        double[][] heat = {{1, 0}, {0, 0}};
        panel.setHeat(heat, 100, "test");
        panel.setOpacity(100);
        BufferedImage out = render(panel, 600, 200);
        check(out.getRGB(150, 50) == Color.RED.getRGB(), "max tile red");
        check(out.getRGB(450, 50) == Color.YELLOW.getRGB(), "zero tile yellow");
        panel.setShowHeat(false);
        out = render(panel, 600, 200);
        check(out.getRGB(150, 50) == Color.BLACK.getRGB(), "heatmap hidden shows image");

        System.out.println("ImagePanelTest OK");
    }
}
