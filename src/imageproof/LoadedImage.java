package imageproof;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

// One image file read from disk: the pixels plus its real format (PNG or JPEG).
public class LoadedImage {

    public File file;
    public BufferedImage image;
    public String format;

    // Reads a PNG or JPEG. Throws IOException with a message the user can understand.
    public static LoadedImage load(File file) throws IOException {
        ImageInputStream input = ImageIO.createImageInputStream(file);
        if (input == null) {
            throw new IOException("Cannot open " + file.getName());
        }
        try {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException(file.getName() + " is not a PNG or JPEG image.");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toUpperCase();
                if (format.equals("JPG")) {
                    format = "JPEG";
                }
                if (!format.equals("PNG") && !format.equals("JPEG")) {
                    throw new IOException(file.getName() + " is " + format + ". Only PNG and JPEG are supported.");
                }
                reader.setInput(input);
                // Check the size from the header before decoding all the pixels.
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > Histogram.MAX_PIXELS) {
                    throw new IOException(file.getName() + " is bigger than 16 megapixels.");
                }
                LoadedImage loaded = new LoadedImage();
                loaded.file = file;
                loaded.format = format;
                loaded.image = reader.read(0);
                return loaded;
            } catch (IOException e) {
                throw e;
            } catch (RuntimeException e) {
                // Broken files can make the decoder throw other errors.
                throw new IOException(file.getName() + " is damaged: " + e.getMessage());
            } finally {
                reader.dispose();
            }
        } finally {
            input.close();
        }
    }
}
