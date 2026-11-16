package imageproof;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JPanel;

/**
 * Swing panel that shows an image scaled to fit, keeping its shape
 * (empty bars at the sides).
 *
 * <p>It can draw overlays on top of the image:</p>
 * <ul>
 * <li>a heatmap: one coloured square per tile, yellow = low, red = high,</li>
 * <li>tiles flagged by {@link LocalAnomaly}, in red,</li>
 * <li>solid-colour patches from {@link FlatPatch}, in red,</li>
 * <li>copy-move pairs from {@link CopyMove}: first block green, its copy magenta,
 * joined by a line,</li>
 * <li>the selected tile, in blue.</li>
 * </ul>
 * <p>The overlays show measured values. They are not proof of editing.
 * {@link App} sets the overlays and reads mouse clicks through
 * {@link #toImagePoint(int, int)}.</p>
 */
public class ImagePanel extends JPanel {

    /** The picture to show. */
    private BufferedImage image;
    /** Heatmap values as [row][col]; null = no heatmap. */
    private double[][] heat;
    /** Tile size of the heatmap in image pixels. */
    private int tileSize;
    /** Legend title of the heatmap. */
    private String heatName = "";
    /** Overlay opacity in percent, 0 to 100. */
    private int opacity = 50;
    /** True if the heatmap and the other overlays are drawn. */
    private boolean showHeat = true;
    /** Selected tile in image pixels; null = none. */
    private Rectangle selected;
    /** Copy-move candidates; null = none. */
    private ArrayList<CopyMove.Match> pairs;
    /** Tiles flagged by the local anomaly rule; null = none. */
    private boolean[][] flags;
    /** Tile size of the flagged tiles in image pixels. */
    private int flagTileSize;
    /** Solid-colour patches in image pixels; null = none. */
    private java.util.List<Rectangle> regions;

    /** Creates an empty panel with a light gray background and a 500 x 400 preferred size. */
    public ImagePanel() {
        setBackground(Color.LIGHT_GRAY);
        setPreferredSize(new Dimension(500, 400));
    }

    /**
     * Changes the picture only. The heatmap and the selection stay.
     *
     * @param image the picture to show
     */
    public void setImage(BufferedImage image) {
        this.image = image;
        repaint();
    }

    /**
     * Sets the heatmap.
     *
     * @param values   one value per tile as {@code [row][col]}, or {@code null} to remove the heatmap;
     *                 each value is scaled by the biggest value in the grid
     * @param tileSize tile side length in image pixels
     * @param name     the legend title
     */
    public void setHeat(double[][] values, int tileSize, String name) {
        this.heat = values;
        this.tileSize = tileSize;
        this.heatName = name;
        repaint();
    }

    /**
     * Sets how solid the overlays are.
     *
     * @param percent opacity from 0 (see-through) to 100 (solid)
     */
    public void setOpacity(int percent) {
        opacity = percent;
        repaint();
    }

    /**
     * Shows or hides the heatmap, the flagged tiles and the solid patches.
     * Copy-move pairs and the selection are always drawn.
     *
     * @param show true to show them
     */
    public void setShowHeat(boolean show) {
        showHeat = show;
        repaint();
    }

    /**
     * Sets the copy-move candidates to outline.
     *
     * @param pairs the candidate pairs, or {@code null} to remove them
     */
    public void setPairs(ArrayList<CopyMove.Match> pairs) {
        this.pairs = pairs;
        repaint();
    }

    /**
     * Sets the tiles flagged by the local anomaly rule.
     * They are filled red (using the opacity) and outlined.
     *
     * @param flags    flagged tiles as {@code [row][col]}, or {@code null} to remove them
     * @param tileSize tile side length in image pixels
     */
    public void setFlags(boolean[][] flags, int tileSize) {
        this.flags = flags;
        this.flagTileSize = tileSize;
        repaint();
    }

    /**
     * Sets the solid-colour patches found by {@link FlatPatch}.
     *
     * @param regions rectangles in image pixels, or {@code null} to remove them
     */
    public void setRegions(java.util.List<Rectangle> regions) {
        this.regions = regions;
        repaint();
    }

    /**
     * Sets the selected tile, drawn as a blue frame.
     *
     * @param tile the tile rectangle in image pixels, or {@code null} for no selection
     */
    public void setSelected(Rectangle tile) {
        selected = tile;
        repaint();
    }

    /**
     * Gives the area of the panel where the image is drawn.
     * An image must be set first.
     *
     * @return the rectangle in panel pixels
     */
    public Rectangle imageArea() {
        return fit(image.getWidth(), image.getHeight(), getWidth(), getHeight());
    }

    /**
     * Finds the biggest rectangle with the image's shape that fits in the panel, centred.
     *
     * @param imageWidth  image width in pixels
     * @param imageHeight image height in pixels
     * @param panelWidth  panel width in pixels
     * @param panelHeight panel height in pixels
     * @return the rectangle in panel pixels (at least 1 x 1)
     */
    public static Rectangle fit(int imageWidth, int imageHeight, int panelWidth, int panelHeight) {
        double scale = Math.min((double) panelWidth / imageWidth, (double) panelHeight / imageHeight);
        int w = Math.max(1, (int) Math.round(imageWidth * scale));
        int h = Math.max(1, (int) Math.round(imageHeight * scale));
        int x = (panelWidth - w) / 2;
        int y = (panelHeight - h) / 2;
        return new Rectangle(x, y, w, h);
    }

    /**
     * Converts a panel point (for example a mouse click) to an image pixel.
     *
     * @param panelX x in panel pixels
     * @param panelY y in panel pixels
     * @return the image pixel, or {@code null} if no image is set or the point is outside the image
     */
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

    /**
     * Converts an image rectangle to a panel rectangle (where it appears on screen).
     * An image must be set first.
     *
     * @param r a rectangle in image pixels
     * @return the rectangle in panel pixels
     */
    public Rectangle toPanel(Rectangle r) {
        Rectangle area = imageArea();
        int x0 = area.x + (int) ((long) r.x * area.width / image.getWidth());
        int y0 = area.y + (int) ((long) r.y * area.height / image.getHeight());
        int x1 = area.x + (int) ((long) (r.x + r.width) * area.width / image.getWidth());
        int y1 = area.y + (int) ((long) (r.y + r.height) * area.height / image.getHeight());
        return new Rectangle(x0, y0, x1 - x0, y1 - y0);
    }

    /**
     * Gives the heatmap colour for a value, using the current opacity.
     *
     * @param value a value from 0 to 1
     * @return a colour from yellow (0, low) to red (1, high)
     */
    public Color heatColor(double value) {
        int alpha = opacity * 255 / 100;
        int green = (int) Math.round(255 * (1 - value));
        return new Color(255, green, 0, alpha);
    }

    /**
     * Draws the image on a white background, then the overlays.
     *
     * @param g the graphics context given by Swing
     */
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
        if (flags != null && showHeat) {
            drawFlags(g);
        }
        if (regions != null && showHeat) {
            for (Rectangle region : regions) {
                Rectangle r = toPanel(region);
                g.setColor(new Color(220, 0, 0, opacity * 255 / 100));
                g.fillRect(r.x, r.y, r.width, r.height);
                g.setColor(new Color(160, 0, 0));
                g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
            }
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
        // Scale the colours so the biggest value in this image is full red.
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

    private void drawFlags(Graphics g) {
        for (int row = 0; row < flags.length; row++) {
            for (int col = 0; col < flags[row].length; col++) {
                if (flags[row][col]) {
                    int w = Math.min(flagTileSize, image.getWidth() - col * flagTileSize);
                    int h = Math.min(flagTileSize, image.getHeight() - row * flagTileSize);
                    Rectangle r = toPanel(new Rectangle(col * flagTileSize, row * flagTileSize, w, h));
                    g.setColor(new Color(220, 0, 0, opacity * 255 / 100));
                    g.fillRect(r.x, r.y, r.width, r.height);
                    g.setColor(new Color(160, 0, 0));
                    g.drawRect(r.x, r.y, r.width - 1, r.height - 1);
                }
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
