package dev.anyprogressbar;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.attributes.ViewBox;
import com.github.weisj.jsvg.parser.LoaderContext;
import com.github.weisj.jsvg.renderer.awt.NullPlatformSupport;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
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
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

public final class Artwork {
    static final int MAX_SVG_ELEMENTS = 5000;
    static final int MAX_SVG_DEPTH = 32;

    private final BufferedImage raster;
    private final SVGDocument vector;
    /** Last pre-scaled copy of {@link #raster}; avoids rescaling the source on every animation frame. */
    private BufferedImage scaled;

    private Artwork(BufferedImage raster, SVGDocument vector) {
        this.raster = raster;
        this.vector = vector;
    }

    public static Artwork load(String reference) throws IOException {
        if (reference.isBlank()) return null;
        URL url;
        if (reference.startsWith("bundled:")) {
            url = Artwork.class.getResource("/art/" + reference.substring(8));
            if (url == null) throw new IOException("Missing bundled artwork: " + reference);
        } else {
            url = Path.of(reference).toUri().toURL();
        }
        if (reference.toLowerCase(Locale.ROOT).endsWith(".svg")) {
            validateSvg(url);
            SVGDocument svg = new SVGLoader().load(url, LoaderContext.builder().resourceLoader(uri -> {
                throw new IOException("External SVG resources are not supported: " + uri);
            }).build());
            if (svg == null || svg.size().width <= 0 || svg.size().height <= 0) {
                throw new IOException("Cannot read SVG: " + reference);
            }
            return new Artwork(null, svg);
        }
        try (var stream = url.openStream(); var input = ImageIO.createImageInputStream(stream)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Cannot read image: " + reference);
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

    private static void validateSvg(URL url) throws IOException {
        try (var stream = url.openStream()) {
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
        String filename = source.getFileName().toString();
        int dot = filename.lastIndexOf('.');
        String extension = dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!java.util.Set.of("png", "jpg", "jpeg", "svg").contains(extension)) {
            throw new IOException("Choose a PNG, JPG or SVG file.");
        }
        if (Files.size(source) > 10 * 1024 * 1024) throw new IOException("Images must be at most 10 MB.");
        load(source.toString());
        byte[] bytes = Files.readAllBytes(source);
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(hash + "." + extension);
        if (!Files.exists(target)) Files.write(target, bytes);
        return target.toString();
    }

    public float aspect() {
        return vector == null ? (float) raster.getWidth() / raster.getHeight() : vector.size().width / vector.size().height;
    }

    public void draw(Graphics2D graphics, int x, int y, int width, int height) {
        if (vector == null) graphics.drawImage(scaledRaster(width, height), x, y, null);
        else vector.renderWithPlatform(NullPlatformSupport.INSTANCE, graphics, new ViewBox(x, y, width, height));
    }

    private BufferedImage scaledRaster(int width, int height) {
        if (width <= 0 || height <= 0 || (width == raster.getWidth() && height == raster.getHeight())) return raster;
        BufferedImage cached = scaled;
        if (cached != null && cached.getWidth() == width && cached.getHeight() == height) return cached;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(raster, 0, 0, width, height, null);
        } finally { g.dispose(); }
        scaled = result;
        return result;
    }
}
