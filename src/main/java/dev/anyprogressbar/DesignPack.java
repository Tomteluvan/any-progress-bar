package dev.anyprogressbar;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Shareable single-file form of a {@link Design}: a zip holding {@code design.properties} plus the design's custom
 * images ({@code object.*}, {@code trail.*}, {@code background.*}). Bundled artwork is referenced, not copied.
 * Entries are only ever read by their fixed names, never extracted by path.
 */
public final class DesignPack {
    public static final String EXTENSION = "apbdesign";
    private static final String DESCRIPTOR = "design.properties";
    private static final String[] LAYERS = {"object", "trail", "background"};
    /** Current pack format; packs without a {@code format} entry predate versioning and are read as format 1. */
    private static final int FORMAT = 1;
    private static final long MAX_ENTRY_BYTES = 10L * 1024 * 1024;

    private DesignPack() {}

    /** Writes the pack to a temporary sibling first so a failed export never damages an existing file at {@code target}. */
    public static void export(Design design, Path target) throws IOException {
        design.validate();
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), ".apb-export", ".tmp");
        try {
            write(design, temp);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    private static void write(Design design, Path target) throws IOException {
        Properties props = new Properties();
        props.setProperty("format", Integer.toString(FORMAT));
        props.setProperty("name", design.name);
        props.setProperty("backgroundColor", design.backgroundColor);
        props.setProperty("size", Integer.toString(design.size));
        props.setProperty("speed", Integer.toString(design.speed));
        props.setProperty("trailLength", Integer.toString(design.trailLength));
        props.setProperty("motion", design.motion.name());
        String[] references = {design.objectImage, design.trailImage, design.backgroundImage};
        try (var zip = new ZipOutputStream(Files.newOutputStream(target))) {
            for (int i = 0; i < LAYERS.length; i++) {
                String reference = references[i];
                if (reference.isBlank()) continue;
                if (reference.startsWith("bundled:")) {
                    props.setProperty(LAYERS[i], reference);
                    continue;
                }
                Path source = Path.of(reference);
                String entry = LAYERS[i] + extensionOf(reference);
                zip.putNextEntry(new ZipEntry(entry));
                Files.copy(source, zip);
                zip.closeEntry();
                props.setProperty(LAYERS[i], "file:" + entry);
            }
            var text = new StringWriter();
            props.store(text, "Any Progress Bar design");
            zip.putNextEntry(new ZipEntry(DESCRIPTOR));
            zip.write(text.toString().getBytes(StandardCharsets.ISO_8859_1));
            zip.closeEntry();
        }
    }

    /** Reads a design pack, imports its images into {@code assets} and returns a validated design with a fresh id. */
    public static Design importFrom(Path source, Path assets) throws IOException {
        Properties props = null;
        var images = new java.util.HashMap<String, byte[]>();
        try (var zip = new ZipInputStream(Files.newInputStream(source))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String entryName = entry.getName();
                if (entry.isDirectory()) continue;
                if (entryName.equals(DESCRIPTOR)) {
                    props = new Properties();
                    props.load(new StringReader(new String(readLimited(zip), StandardCharsets.ISO_8859_1)));
                } else if (isLayerEntry(entryName)) {
                    images.put(entryName, readLimited(zip));
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("Not a valid design file.", e);
        }
        if (props == null) throw new IOException("Not a design file: " + DESCRIPTOR + " is missing.");
        checkFormat(props.getProperty("format", "1"));
        try {
            Design design = new Design();
            design.id = UUID.randomUUID().toString();
            design.name = props.getProperty("name", "Imported design");
            design.backgroundColor = props.getProperty("backgroundColor", "");
            design.size = Integer.parseInt(props.getProperty("size", "24"));
            design.speed = Integer.parseInt(props.getProperty("speed", "100"));
            design.trailLength = Integer.parseInt(props.getProperty("trailLength", "160"));
            design.motion = Design.Motion.valueOf(props.getProperty("motion", "GLIDE"));
            design.objectImage = resolve(props.getProperty("object", ""), images, assets);
            design.trailImage = resolve(props.getProperty("trail", ""), images, assets);
            design.backgroundImage = resolve(props.getProperty("background", ""), images, assets);
            design.validate();
            new ProgressPainter(design);
            return design;
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid design file: " + e.getMessage(), e);
        }
    }

    private static void checkFormat(String value) throws IOException {
        int format;
        try { format = Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { throw new IOException("Invalid design file: unreadable format \"" + value + "\".", e); }
        if (format > FORMAT) {
            throw new IOException("This design file uses format " + format + ", which this version of Any Progress Bar "
                + "does not support. Update the plugin to import it.");
        }
        if (format < 1) throw new IOException("Invalid design file: unsupported format " + format + ".");
    }

    private static String resolve(String value, java.util.Map<String, byte[]> images, Path assets) throws IOException {
        if (value.isBlank()) return "";
        if (value.startsWith("bundled:")) {
            String name = value.substring(8);
            if (!name.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*\\.(svg|png|jpe?g)") || name.contains("..")) {
                throw new IOException("Invalid bundled artwork name: " + value);
            }
            if (Artwork.class.getResource("/art/" + name) == null) {
                throw new IOException("Unknown bundled artwork: " + value);
            }
            return value;
        }
        if (!value.startsWith("file:") || !images.containsKey(value.substring(5))) {
            throw new IOException("Design file references a missing image: " + value);
        }
        String entry = value.substring(5);
        return Artwork.importBytes(images.get(entry), Artwork.extensionOf(entry), assets);
    }

    private static boolean isLayerEntry(String name) {
        for (String layer : LAYERS) {
            if (name.startsWith(layer + ".") && name.indexOf('/') < 0 && name.indexOf('\\') < 0) return true;
        }
        return false;
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static byte[] readLimited(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes((int) MAX_ENTRY_BYTES + 1);
        if (bytes.length > MAX_ENTRY_BYTES) throw new IOException("Design file entries must be at most 10 MB.");
        return bytes;
    }
}
