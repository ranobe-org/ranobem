package in.atulpatare.ranobem.download;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes an image only book as an EPUB 3 package that also carries an EPUB 2 NCX table of
 * contents, so older readers can open it too. Every page gets its own XHTML document so each
 * image starts on a fresh screen in reflowable readers.
 * <p>
 * Pages must already be JPEG, PNG or GIF, the image types every reader has to support.
 */
final class EpubBuilder {
    private static final String CSS = "@page { margin: 0; }\n"
            + "html, body { margin: 0; padding: 0; }\n"
            + "body { text-align: center; }\n"
            + "div.page { margin: 0; padding: 0; page-break-after: always; break-after: page; }\n"
            + "img { max-width: 100%; height: auto; }\n"
            // regular pages fit the screen, long webtoon strips keep their width and scroll
            + "img.fit { max-height: 100vh; width: auto; }\n";

    private EpubBuilder() {
    }

    static final class Book {
        String id;
        String title;
        String author;
        String description;
        String language = "en";
        String source;
        File cover;
        final List<Section> sections = new ArrayList<>();
    }

    static final class Section {
        final String title;
        final List<File> pages = new ArrayList<>();

        Section(String title) {
            this.title = title;
        }
    }

    interface Progress {
        // may throw to abort the build
        void onPage(int done, int total);
    }

    private static final class Page {
        final File file;
        final ImageInfo info;
        final String name; // file name without extension, unique in the book

        Page(File file, ImageInfo info, String name) {
            this.file = file;
            this.info = info;
            this.name = name;
        }

        String imageHref() {
            return "images/" + name + "." + info.format;
        }

        String pageHref() {
            return "text/" + name + ".xhtml";
        }
    }

    static void write(Book book, OutputStream out, Progress progress) throws IOException {
        // read every header up front, an unreadable page is left out rather than breaking the book
        List<List<Page>> sections = new ArrayList<>();
        int total = 0;
        for (int s = 0; s < book.sections.size(); s++) {
            List<Page> pages = new ArrayList<>();
            List<File> files = book.sections.get(s).pages;
            for (int p = 0; p < files.size(); p++) {
                ImageInfo info = readUsable(files.get(p));
                if (info == null) continue;
                pages.add(new Page(files.get(p), info, String.format(Locale.ROOT, "c%04d-p%04d", s + 1, p + 1)));
            }
            sections.add(pages);
            total += pages.size();
        }
        if (total == 0) throw new IOException("No pages to put in the book");

        Page cover = null;
        if (book.cover != null) {
            ImageInfo info = readUsable(book.cover);
            if (info != null) cover = new Page(book.cover, info, "cover");
        }
        if (cover == null) {
            // fall back to the first page
            for (List<Page> pages : sections) {
                if (!pages.isEmpty()) {
                    cover = new Page(pages.get(0).file, pages.get(0).info, "cover");
                    break;
                }
            }
        }

        ZipOutputStream zip = new ZipOutputStream(out);
        // the mimetype entry must come first, uncompressed and without extra fields
        putStored(zip, "mimetype", "application/epub+zip".getBytes(StandardCharsets.US_ASCII));
        putText(zip, "META-INF/container.xml", containerXml());
        putText(zip, "OEBPS/styles.css", CSS);
        putText(zip, "OEBPS/content.opf", opf(book, cover, sections));
        putText(zip, "OEBPS/nav.xhtml", nav(book, sections));
        putText(zip, "OEBPS/toc.ncx", ncx(book, sections));

        putText(zip, "OEBPS/" + cover.pageHref(), pageXhtml(book.language, book.title, cover, "Cover", false));
        putFile(zip, "OEBPS/" + cover.imageHref(), cover.file);

        int done = 0;
        for (int s = 0; s < sections.size(); s++) {
            String sectionTitle = book.sections.get(s).title;
            List<Page> pages = sections.get(s);
            for (int p = 0; p < pages.size(); p++) {
                Page page = pages.get(p);
                String title = sectionTitle + " - " + (p + 1);
                putText(zip, "OEBPS/" + page.pageHref(), pageXhtml(book.language, title, page, title, true));
                putFile(zip, "OEBPS/" + page.imageHref(), page.file);
                progress.onPage(++done, total);
            }
        }
        zip.finish();
        zip.flush();
    }

    // null for a file that is gone, unreadable or not a type every reader supports
    private static ImageInfo readUsable(File file) {
        try {
            ImageInfo info = ImageInfo.read(file);
            return info != null && ImageInfo.isEpubCore(info.format) ? info : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static String containerXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\">\n"
                + "  <rootfiles>\n"
                + "    <rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/>\n"
                + "  </rootfiles>\n"
                + "</container>\n";
    }

    private static String opf(Book book, Page cover, List<List<Page>> sections) {
        SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT);
        iso.setTimeZone(TimeZone.getTimeZone("UTC"));

        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"book-id\" xml:lang=\"")
                .append(esc(book.language)).append("\">\n")
                .append("  <metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\">\n")
                .append("    <dc:identifier id=\"book-id\">").append(esc(book.id)).append("</dc:identifier>\n")
                .append("    <dc:title>").append(esc(book.title)).append("</dc:title>\n")
                .append("    <dc:language>").append(esc(book.language)).append("</dc:language>\n");
        if (!isBlank(book.author)) b.append("    <dc:creator>").append(esc(book.author)).append("</dc:creator>\n");
        if (!isBlank(book.description)) {
            b.append("    <dc:description>").append(esc(book.description)).append("</dc:description>\n");
        }
        if (!isBlank(book.source)) b.append("    <dc:source>").append(esc(book.source)).append("</dc:source>\n");
        b.append("    <meta property=\"dcterms:modified\">").append(iso.format(new Date())).append("</meta>\n")
                // EPUB 2 readers find the cover through this
                .append("    <meta name=\"cover\" content=\"img-cover\"/>\n")
                .append("  </metadata>\n")
                .append("  <manifest>\n")
                .append("    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n")
                .append("    <item id=\"ncx\" href=\"toc.ncx\" media-type=\"application/x-dtbncx+xml\"/>\n")
                .append("    <item id=\"css\" href=\"styles.css\" media-type=\"text/css\"/>\n")
                .append("    <item id=\"page-cover\" href=\"").append(cover.pageHref()).append("\" media-type=\"application/xhtml+xml\"/>\n")
                .append("    <item id=\"img-cover\" href=\"").append(cover.imageHref()).append("\" media-type=\"")
                .append(ImageInfo.mediaType(cover.info.format)).append("\" properties=\"cover-image\"/>\n");
        for (List<Page> pages : sections) {
            for (Page page : pages) {
                b.append("    <item id=\"page-").append(page.name).append("\" href=\"").append(page.pageHref())
                        .append("\" media-type=\"application/xhtml+xml\"/>\n")
                        .append("    <item id=\"img-").append(page.name).append("\" href=\"").append(page.imageHref())
                        .append("\" media-type=\"").append(ImageInfo.mediaType(page.info.format)).append("\"/>\n");
            }
        }
        b.append("  </manifest>\n")
                .append("  <spine toc=\"ncx\">\n")
                .append("    <itemref idref=\"page-cover\"/>\n");
        for (List<Page> pages : sections) {
            for (Page page : pages) b.append("    <itemref idref=\"page-").append(page.name).append("\"/>\n");
        }
        b.append("  </spine>\n")
                .append("  <guide>\n")
                .append("    <reference type=\"cover\" title=\"Cover\" href=\"").append(cover.pageHref()).append("\"/>\n")
                .append("  </guide>\n")
                .append("</package>\n");
        return b.toString();
    }

    private static String nav(Book book, List<List<Page>> sections) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"")
                .append(esc(book.language)).append("\" lang=\"").append(esc(book.language)).append("\">\n")
                .append("<head>\n  <meta charset=\"UTF-8\"/>\n  <title>").append(esc(book.title)).append("</title>\n</head>\n")
                .append("<body>\n")
                .append("  <nav epub:type=\"toc\" id=\"toc\">\n    <h1>").append(esc(book.title)).append("</h1>\n    <ol>\n");
        for (int s = 0; s < sections.size(); s++) {
            List<Page> pages = sections.get(s);
            if (pages.isEmpty()) continue;
            b.append("      <li><a href=\"").append(pages.get(0).pageHref()).append("\">")
                    .append(esc(book.sections.get(s).title)).append("</a></li>\n");
        }
        b.append("    </ol>\n  </nav>\n")
                .append("  <nav epub:type=\"landmarks\" id=\"landmarks\" hidden=\"\">\n    <ol>\n")
                .append("      <li><a epub:type=\"cover\" href=\"text/cover.xhtml\">Cover</a></li>\n");
        for (List<Page> pages : sections) {
            if (pages.isEmpty()) continue;
            b.append("      <li><a epub:type=\"bodymatter\" href=\"").append(pages.get(0).pageHref()).append("\">Start</a></li>\n");
            break;
        }
        b.append("    </ol>\n  </nav>\n</body>\n</html>\n");
        return b.toString();
    }

    private static String ncx(Book book, List<List<Page>> sections) {
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<ncx xmlns=\"http://www.daisy.org/z3986/2005/ncx/\" version=\"2005-1\" xml:lang=\"")
                .append(esc(book.language)).append("\">\n")
                .append("  <head>\n")
                .append("    <meta name=\"dtb:uid\" content=\"").append(esc(book.id)).append("\"/>\n")
                .append("    <meta name=\"dtb:depth\" content=\"1\"/>\n")
                .append("    <meta name=\"dtb:totalPageCount\" content=\"0\"/>\n")
                .append("    <meta name=\"dtb:maxPageNumber\" content=\"0\"/>\n")
                .append("  </head>\n")
                .append("  <docTitle><text>").append(esc(book.title)).append("</text></docTitle>\n")
                .append("  <navMap>\n");
        int order = 0;
        for (int s = 0; s < sections.size(); s++) {
            List<Page> pages = sections.get(s);
            if (pages.isEmpty()) continue;
            order++;
            b.append("    <navPoint id=\"nav-").append(order).append("\" playOrder=\"").append(order).append("\">\n")
                    .append("      <navLabel><text>").append(esc(book.sections.get(s).title)).append("</text></navLabel>\n")
                    .append("      <content src=\"").append(pages.get(0).pageHref()).append("\"/>\n")
                    .append("    </navPoint>\n");
        }
        b.append("  </navMap>\n</ncx>\n");
        return b.toString();
    }

    private static String pageXhtml(String language, String title, Page page, String alt, boolean body) {
        // regular pages are fitted to the screen, strips much taller than wide are not
        boolean fit = page.info.width > 0 && page.info.height <= page.info.width * 2;
        StringBuilder b = new StringBuilder();
        b.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html>\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:epub=\"http://www.idpf.org/2007/ops\" xml:lang=\"")
                .append(esc(language)).append("\" lang=\"").append(esc(language)).append("\">\n")
                .append("<head>\n  <meta charset=\"UTF-8\"/>\n")
                .append("  <title>").append(esc(title)).append("</title>\n")
                .append("  <link rel=\"stylesheet\" type=\"text/css\" href=\"../styles.css\"/>\n")
                .append("</head>\n")
                .append("<body").append(body ? "" : " epub:type=\"cover\"").append(">\n")
                .append("  <div class=\"page\"><img src=\"../").append(page.imageHref()).append("\" alt=\"").append(esc(alt)).append("\"");
        if (fit) b.append(" class=\"fit\"");
        if (page.info.width > 0 && page.info.height > 0) {
            b.append(" width=\"").append(page.info.width).append("\" height=\"").append(page.info.height).append("\"");
        }
        b.append("/></div>\n</body>\n</html>\n");
        return b.toString();
    }

    private static void putStored(ZipOutputStream zip, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(data.length);
        entry.setCompressedSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
    }

    private static void putText(ZipOutputStream zip, String name, String text) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.DEFLATED);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // images are already compressed, storing them saves a lot of time on big books
    private static void putFile(ZipOutputStream zip, String name, File file) throws IOException {
        byte[] buffer = new byte[64 * 1024];
        CRC32 crc = new CRC32();
        try (InputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) crc.update(buffer, 0, read);
        }
        ZipEntry entry = new ZipEntry(name);
        entry.setMethod(ZipEntry.STORED);
        entry.setSize(file.length());
        entry.setCompressedSize(file.length());
        entry.setCrc(crc.getValue());
        zip.putNextEntry(entry);
        try (InputStream in = new FileInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) zip.write(buffer, 0, read);
        }
        zip.closeEntry();
    }

    // escapes text for XML, dropping characters XML 1.0 can't carry at all
    static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&':
                    b.append("&amp;");
                    break;
                case '<':
                    b.append("&lt;");
                    break;
                case '>':
                    b.append("&gt;");
                    break;
                case '"':
                    b.append("&quot;");
                    break;
                case '\'':
                    b.append("&apos;");
                    break;
                default:
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') {
                        if (c != 0xFFFE && c != 0xFFFF) b.append(c);
                    }
            }
        }
        return b.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
