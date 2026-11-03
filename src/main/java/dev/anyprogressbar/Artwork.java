package dev.anyprogressbar;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.renderer.awt.NullPlatformSupport;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import com.intellij.util.ui.ImageUtil;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class Artwork {
    static final int MAX_SVG_ELEMENTS = 5000;
    static final int MAX_SVG_DEPTH = 32;

    private final BufferedImage raster;
    private final SVGDocument vector;
    /** Pre-scaled copies of {@link #raster} by pixel size, least recently used first; see {@link #scaledRaster}. */
    private final java.util.LinkedHashMap<Long, BufferedImage> scaledCache = new java.util.LinkedHashMap<>(16, 0.75f, true);
    private long scaledBytes;
    static final int MAX_CACHED_SIZES = 8;
    static final long MAX_CACHE_BYTES = 8L * 1024 * 1024;

    private Artwork(BufferedImage raster, SVGDocument vector) {
        this.raster = raster;
        this.vector = vector;
    }

    /** Largest image file that is read, imported or exported. */
    static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;

    public static Artwork load(String reference) throws IOException {
        if (reference.isBlank()) return null;
        byte[] bytes;
        if (reference.startsWith("bundled:")) {
            URL url = Artwork.class.getResource("/art/" + reference.substring(8));
            if (url == null) throw new IOException("Missing bundled artwork: " + reference);
            try (var stream = url.openStream()) { bytes = readBounded(stream); }
        } else {
            try (var stream = Files.newInputStream(Path.of(reference))) { bytes = readBounded(stream); }
        }
        return decode(bytes, reference.toLowerCase(Locale.ROOT).endsWith(".svg"), reference);
    }

    /** Reads at most {@link #MAX_IMAGE_BYTES}; anything larger is rejected without being read in full. */
    static byte[] readBounded(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_IMAGE_BYTES + 1);
        if (bytes.length > MAX_IMAGE_BYTES) throw new IOException("Images must be at most 10 MB.");
        return bytes;
    }

    /** Validates and decodes one snapshot of an image, so what is validated is exactly what is rendered or stored. */
    private static Artwork decode(byte[] bytes, boolean svgFile, String label) throws IOException {
        if (svgFile) {
            validateSvg(bytes);
            SVGDocument svg = new SVGLoader().load(new ByteArrayInputStream(bytes), URI.create("about:blank"),
                LoaderContext.builder().resourceLoader(uri -> {
                    throw new IOException("External SVG resources are not supported: " + uri);
                }).build());
            if (svg == null || svg.size().width <= 0 || svg.size().height <= 0) {
                throw new IOException("Cannot read SVG: " + label);
            }
            return new Artwork(null, svg);
        }
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Cannot read image: " + label);
            var reader = readers.next();
            try {
                reader.setInput(input);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > 8192 || height > 8192 || (long) width * height > 16_000_000) {
                    throw new IOException("Raster images must be at most 8192 px per side and 16 megapixels.");
                }
                return new Artwork(reader.read(0), null);
            } finally { reader.dispose(); }
        }
    }

    private static void validateSvg(byte[] bytes) throws IOException {
        try (var stream = new ByteArrayInputStream(bytes)) {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element root = factory.newDocumentBuilder().parse(stream).getDocumentElement();
            if (!root.getTagName().equals("svg") && !root.getTagName().endsWith(":svg")) {
                throw new IOException("The file is not an SVG document.");
            }
            var nodes = root.getElementsByTagName("*");
            if (nodes.getLength() > MAX_SVG_ELEMENTS) {
                throw new IOException("SVG is too complex: at most " + MAX_SVG_ELEMENTS + " elements.");
            }
            validateSvgDepth(root, 1);
            validateSvgElement(root);
            for (int i = 0; i < nodes.getLength(); i++) validateSvgElement((Element) nodes.item(i));
        } catch (javax.xml.parsers.ParserConfigurationException | SAXException e) {
            throw new IOException("Invalid SVG: " + e.getMessage(), e);
        }
    }

    private static void validateSvgDepth(Element element, int depth) throws IOException {
        if (depth > MAX_SVG_DEPTH) throw new IOException("SVG is nested too deeply: at most " + MAX_SVG_DEPTH + " levels.");
        for (var child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child instanceof Element e) validateSvgDepth(e, depth + 1);
        }
    }

    private static void validateSvgElement(Element element) throws IOException {
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        if (tag.equals("script") || tag.endsWith(":script") || tag.equals("foreignobject")) {
            throw new IOException("SVG scripts and foreign objects are not supported.");
        }
        var attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            var attribute = attributes.item(i);
            String key = attribute.getNodeName().toLowerCase(Locale.ROOT);
            String value = attribute.getNodeValue().trim();
            if ((key.equals("href") || key.endsWith(":href")) && !value.startsWith("#")) {
                throw new IOException("SVG image references must be local fragments (#id).");
            }
            validateSvgCss(value);
        }
        if (tag.equals("style") || tag.endsWith(":style")) validateSvgCss(element.getTextContent());
    }

    private static void validateSvgCss(String value) throws IOException {
        var urls = java.util.regex.Pattern.compile("url\\s*\\(([^)]*)\\)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
        while (urls.find()) {
            String target = urls.group(1).trim().replace("\"", "").replace("'", "");
            if (!target.startsWith("#")) throw new IOException("SVG resources must be self-contained.");
        }
        if (value.toLowerCase(Locale.ROOT).contains("@import")) {
            throw new IOException("SVG stylesheet imports are not supported.");
        }
    }

    public static String importFile(Path source, Path directory) throws IOException {
        String extension = extensionOf(source.getFileName().toString());
        checkExtension(extension);
        byte[] bytes;
        try (var stream = Files.newInputStream(source)) { bytes = readBounded(stream); }
        return importBytes(bytes, extension, directory);
    }

    /** Checks that {@code bytes} are an acceptable image of the given type, without storing anything. */
    static void validate(byte[] bytes, String extension) throws IOException {
        checkExtension(extension);
        if (bytes.length > MAX_IMAGE_BYTES) throw new IOException("Images must be at most 10 MB.");
        decode(bytes, extension.equals("svg"), "the selected file");
    }

    /** Validates {@code bytes} and stores exactly those bytes under their content hash; returns the stored path. */
    static String importBytes(byte[] bytes, String extension, Path directory) throws IOException {
        return importBytes(bytes, extension, directory, null);
    }

    /**
     * Like {@link #importBytes(byte[], String, Path)}; every file this call newly creates is added to {@code created}
     * (when given), so a caller that fails later can remove exactly those and nothing that was stored before.
     */
    static String importBytes(byte[] bytes, String extension, Path directory, java.util.List<Path> created) throws IOException {
        validate(bytes, extension);
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(hash + "." + extension);
        boolean exists = Files.exists(target);
        if (!exists) {
            try {
                Files.write(target, bytes, java.nio.file.StandardOpenOption.CREATE_NEW);
                if (created != null) created.add(target);
            } catch (java.nio.file.FileAlreadyExistsException e) {
                exists = true; // stored by someone else in the meantime: not ours to remove
            } catch (IOException e) {
                Files.deleteIfExists(target); // CREATE_NEW succeeded or failed before; either way this half-written file is ours
                throw e;
            }
        }
        if (exists) {
            // Re-using a stored image counts as using it now, so cleanup does not remove it while an edit holds it.
            try { Files.setLastModifiedTime(target, java.nio.file.attribute.FileTime.from(java.time.Instant.now())); }
            catch (IOException e) { /* only affects how soon an unused copy may be cleaned up */ }
        }
        return target.toString();
    }

    /** Where imported images are stored; their names are the SHA-256 of their content. */
    static Path assetsDirectory() {
        return Path.of(com.intellij.openapi.application.PathManager.getConfigPath(), "any-progress-bar", "assets");
    }

    static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static void checkExtension(String extension) throws IOException {
        if (!java.util.Set.of("png", "jpg", "jpeg", "svg").contains(extension)) {
            throw new IOException("Choose a PNG, JPG or SVG file.");
        }
    }

    public float aspect() {
        return vector == null ? (float) raster.getWidth() / raster.getHeight() : vector.size().width / vector.size().height;
    }

    public void draw(Graphics2D graphics, int x, int y, int width, int height) {
        if (vector != null) {
            vector.renderWithPlatform(NullPlatformSupport.INSTANCE, graphics, new ViewBox(x, y, width, height));
            return;
        }
        // Scale to the pixels actually shown, so a HiDPI screen gets a sharp copy and different bar heights each keep theirs.
        var transform = graphics.getTransform();
        double scale = Math.hypot(transform.getScaleX(), transform.getShearY());
        int pixelWidth = Math.max(1, (int) Math.round(width * scale));
        int pixelHeight = Math.max(1, (int) Math.round(height * scale));
        graphics.drawImage(scaledRaster(pixelWidth, pixelHeight), x, y, width, height, null);
    }

    /** Number of pre-scaled copies currently kept; for tests. */
    synchronized int cachedSizes() { return scaledCache.size(); }

    /**
     * A copy of the image at {@code width} x {@code height} pixels. A few recent sizes are kept (at most
     * {@link #MAX_CACHED_SIZES} and {@link #MAX_CACHE_BYTES} in total), so bars of different heights and animations that
     * change the size every frame do not throw each other's copies away. Sizes beyond the source are not scaled up.
     */
    private synchronized BufferedImage scaledRaster(int width, int height) {
        if (width >= raster.getWidth() && height >= raster.getHeight()) return raster;
        long key = ((long) width << 32) | height;
        BufferedImage cached = scaledCache.get(key);
        if (cached != null) return cached;
        BufferedImage result = ImageUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(raster, 0, 0, width, height, null);
        } finally { g.dispose(); }
        scaledCache.put(key, result);
        scaledBytes += 4L * width * height;
        var oldest = scaledCache.entrySet().iterator();
        while (scaledCache.size() > 1 && (scaledCache.size() > MAX_CACHED_SIZES || scaledBytes > MAX_CACHE_BYTES) && oldest.hasNext()) {
            var entry = oldest.next();
            if (entry.getValue() == result) continue; // never drop the copy just made
            scaledBytes -= 4L * entry.getValue().getWidth() * entry.getValue().getHeight();
            oldest.remove();
        }
        return result;
    }
}
