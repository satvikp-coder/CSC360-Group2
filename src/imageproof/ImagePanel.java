package imageproof;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JPanel;

// Shows an image scaled to fit the panel, keeping its shape (empty bars at the sides).
// Can draw a heatmap on top: one coloured square per tile, yellow = low, red = high.
public class ImagePanel extends JPanel {

    private BufferedImage image;
    private double[][] heat;      // [row][col] values, null = no heatmap
    private int tileSize;
    private String heatName = "";
    private int opacity = 50;     // percent, 0 to 100
    private boolean showHeat = true;
    private Rectangle selected;   // selected tile in image pixels, null = none
    private ArrayList<CopyMove.Match> pairs; // copy-move candidates, null = none

    public ImagePanel() {
        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(new Dimension(500, 400));
    }

    // Change the picture only; the heatmap and selection stay.
    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    // values = null removes the heatmap.
    public void setHeat(double[][] values, int tileSize, String name) {
        this.heat = values;
        this.tileSize = tileSize;
        this.heatName = name;
        repaint();
    }

    public void setOpacity(int percent) {
        opacity = percent;
        repaint();
    }

    public void setShowHeat(boolean show) {
        showHeat = show;
        repaint();
    }

    // Copy-move candidates to outline. null removes them.
    public void setPairs(ArrayList<CopyMove.Match> pairs) {
        this.pairs = pairs;
        repaint();
    }

    public void setSelected(Rectangle tile) {
        selected = tile;
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

    // Panel point (e.g. a mouse click) -> image pixel. null if outside the image.
    public Point toImagePoint(int panelX, int panelY) {
        if (image == null) {
            return null;
        }
        Rectangle area = imageArea();
        if (!area.contains(panelX, panelY)) {
            return null;
        }
        int x = (int) ((long) (panelX - area.x) * image.getWidth() / area.width);
        int y = (int) ((long) (panelY - area.y) * image.getHeight() / area.height);
        return new Point(x, y);
    }

    // Image rectangle -> panel rectangle (where it appears on screen).
    public Rectangle toPanel(Rectangle r) {
        Rectangle area = imageArea();
        int x0 = area.x + (int) ((long) r.x * area.width / image.getWidth());
        int y0 = area.y + (int) ((long) r.y * area.height / image.getHeight());
        int x1 = area.x + (int) ((long) (r.x + r.width) * area.width / image.getWidth());
        int y1 = area.y + (int) ((long) (r.y + r.height) * area.height / image.getHeight());
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }

    // Value 0..1 -> colour from yellow (low) to red (high), see-through by the opacity.
    public Color heatColor(double value) {
        int alpha = opacity * 255 / 100;
        int green = (int) Math.round(255 * (1 - value));
        return new Color(255, green, 0, alpha);
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

        if (heat != null && showHeat) {
            drawHeat(g);
            drawLegend(g);
        }
        if (pairs != null) {
            drawPairs(g);
        }
        if (selected != null) {
            Rectangle s = toPanel(selected);
            g.setColor(Color.BLUE);
            g.drawRect(s.x, s.y, s.width - 1, s.height - 1);
            g.drawRect(s.x + 1, s.y + 1, s.width - 3, s.height - 3);
        }
    }

    private void drawHeat(Graphics g) {
        // Colours are scaled so the biggest value in this image is full red.
        double max = 0;
        for (int row = 0; row < heat.length; row++) {
            for (int col = 0; col < heat[row].length; col++) {
                max = Math.max(max, heat[row][col]);
            }
        }
        for (int row = 0; row < heat.length; row++) {
            for (int col = 0; col < heat[row].length; col++) {
                double value = 0;
                if (max > 0) {
                    value = heat[row][col] / max;
                }
                int w = Math.min(tileSize, image.getWidth() - col * tileSize);
                int h = Math.min(tileSize, image.getHeight() - row * tileSize);
                Rectangle r = toPanel(new Rectangle(col * tileSize, row * tileSize, w, h));
                g.setColor(heatColor(value));
                g.fillRect(r.x, r.y, r.width, r.height);
            }
        }
    }

    // First block green, its copy magenta, joined by a line.
    private void drawPairs(Graphics g) {
        int b = CopyMove.BLOCK;
        for (int i = 0; i < pairs.size(); i++) {
            CopyMove.Match m = pairs.get(i);
            Rectangle first = toPanel(new Rectangle(m.x1, m.y1, b, b));
            Rectangle second = toPanel(new Rectangle(m.x2, m.y2, b, b));
            g.setColor(Color.GREEN);
            g.drawRect(first.x, first.y, first.width, first.height);
            g.setColor(Color.MAGENTA);
            g.drawRect(second.x, second.y, second.width, second.height);
            g.drawLine(first.x + first.width / 2, first.y + first.height / 2,
                    second.x + second.width / 2, second.y + second.height / 2);
        }
    }

    private void drawLegend(Graphics g) {
        int x = 10;
        int y = getHeight() - 45;
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRect(x - 5, y - 15, 330, 50);
        g.setColor(Color.BLACK);
        g.drawString(heatName, x, y);
        for (int i = 0; i < 100; i++) {
            g.setColor(new Color(255, (int) Math.round(255 * (1 - i / 99.0)), 0));
            g.fillRect(x + i, y + 5, 1, 10);
        }
        g.setColor(Color.BLACK);
        g.drawString("low", x, y + 30);
        g.drawString("high (larger measured value,", x + 105, y + 14);
        g.drawString("not proof of editing)", x + 105, y + 30);
    }
}
