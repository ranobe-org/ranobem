package in.atulpatare.ranobem.download;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.SequenceInputStream;

/**
 * Type and size of an image, read from its header only. Plain Java so it is cheap to run over
 * thousands of pages while building a book.
 */
final class ImageInfo {
    static final String JPEG = "jpg";
    static final String PNG = "png";
    static final String GIF = "gif";
    static final String WEBP = "webp";

    final String format;
    final int width;
    final int height;

    private ImageInfo(String format, int width, int height) {
        this.format = format;
        this.width = width;
        this.height = height;
    }

    /**
     * The formats every EPUB reader must support, anything else is converted first.
     */
    static boolean isEpubCore(String format) {
        return JPEG.equals(format) || PNG.equals(format) || GIF.equals(format);
    }

    static String mediaType(String format) {
        switch (format) {
            case PNG:
                return "image/png";
            case GIF:
                return "image/gif";
            case WEBP:
                return "image/webp";
            default:
                return "image/jpeg";
        }
    }

    /**
     * Returns null when the file isn't an image we recognise.
     */
    static ImageInfo read(File file) throws IOException {
        try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
            byte[] head = new byte[12];
            in.readFully(head);
            String format = sniff(head);
            if (format == null) return null;
            switch (format) {
                case PNG:
                    // signature(8) length(4) "IHDR"(4) then width, height
                    skip(in, 4);
                    return new ImageInfo(format, in.readInt(), in.readInt());
                case GIF:
                    return new ImageInfo(format, le16(head[6], head[7]), le16(head[8], head[9]));
                case JPEG:
                    return readJpeg(in, head);
                default:
                    return new ImageInfo(format, 0, 0);
            }
        } catch (EOFException e) {
            return null;
        }
    }

    static String sniff(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xff) == 0xFF && (h[1] & 0xff) == 0xD8 && (h[2] & 0xff) == 0xFF) return JPEG;
        if (h.length >= 8 && (h[0] & 0xff) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return PNG;
        if (h.length >= 6 && h[0] == 'G' && h[1] == 'I' && h[2] == 'F' && h[3] == '8') return GIF;
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return WEBP;
        return null;
    }

    // walks the markers up to the frame header that holds the size
    private static ImageInfo readJpeg(DataInputStream in, byte[] head) throws IOException {
        // the 12 header bytes are already read, replay everything after the SOI marker
        DataInputStream data = new DataInputStream(
                new SequenceInputStream(new ByteArrayInputStream(head, 2, head.length - 2), in));
        while (true) {
            int b = data.readUnsignedByte();
            if (b != 0xFF) continue;
            int marker = data.readUnsignedByte();
            while (marker == 0xFF) marker = data.readUnsignedByte();
            if (marker == 0xD8 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) continue;
            if (marker == 0xD9 || marker == 0xDA) return null;
            int length = data.readUnsignedShort();
            boolean frame = marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
            if (frame) {
                data.readUnsignedByte(); // precision
                int height = data.readUnsignedShort();
                int width = data.readUnsignedShort();
                return new ImageInfo(JPEG, width, height);
            }
            skip(data, length - 2);
        }
    }

    private static void skip(DataInputStream in, int n) throws IOException {
        if (n > 0) in.readFully(new byte[n]);
    }

    private static int le16(byte lo, byte hi) {
        return (lo & 0xff) | ((hi & 0xff) << 8);
    }
}
