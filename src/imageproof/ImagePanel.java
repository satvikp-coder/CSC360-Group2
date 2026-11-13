package imageproof;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JPanel;

// Shows an image scaled to fit the panel, keeping its shape (empty bars at the sides).
public class ImagePanel extends JPanel {

    private BufferedImage image;

    public ImagePanel() {
        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(new Dimension(500, 400));
    }

    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    // Where the image is drawn inside the panel.
    public Rectangle imageArea() {
        return fit(image.getWidth(), image.getHeight(), getWidth(), getHeight());
    }

    // Biggest rectangle with the image's shape that fits in the panel, centred.
    public static Rectangle fit(int imageWidth, int imageHeight, int panelWidth, int panelHeight) {
        double scale = Math.min((double) panelWidth / imageWidth, (double) panelHeight / imageHeight);
        int w = Math.max(1, (int) Math.round(imageWidth * scale));
        int h = Math.max(1, (int) Math.round(imageHeight * scale));
        int x = (panelWidth - w) / 2;
        int y = (panelHeight - h) / 2;
        return new Rectangle(x, y, w, h);
    }

    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (image == null) {
            g.setColor(Color.DARK_GRAY);
            g.drawString("Open a PNG or JPEG image", 20, 30);
            return;
        }
        Rectangle area = imageArea();
        // White behind the image, same background the analysis uses for transparency.
        g.setColor(Color.WHITE);
        g.fillRect(area.x, area.y, area.width, area.height);
        g.drawImage(image, area.x, area.y, area.width, area.height, null);
    }
}
