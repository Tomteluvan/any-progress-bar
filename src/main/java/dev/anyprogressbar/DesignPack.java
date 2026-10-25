package dev.anyprogressbar;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    /** Aggregate limits: a pack holds one descriptor and at most one image per layer, so these are generous. */
    private static final int MAX_ENTRIES = 32;
    private static final long MAX_DESCRIPTOR_BYTES = 64L * 1024;
    private static final long MAX_TOTAL_BYTES = 40L * 1024 * 1024;
    private static final long MAX_IMAGE_BYTES = 30L * 1024 * 1024;
    static final int MAX_NAME_LENGTH = 200;

    private DesignPack() {}

    /** Writes the pack to a temporary sibling first so a failed export never damages an existing file at {@code target}. */
    public static void export(Design design, Path target) throws IOException {
        design.validate();
        if (design.name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Shorten the design name to at most " + MAX_NAME_LENGTH + " characters to export it.");
        }
        writeAtomically(target, out -> write(design, out));
    }

    /** Something that writes a file's contents; see {@link #writeAtomically}. */
    interface Writer {
        void write(OutputStream out) throws IOException;
    }

    /** Writes to a temporary sibling and only replaces {@code target} once the whole file was written successfully. */
    static void writeAtomically(Path target, Writer writer) throws IOException {
        Path temp = Files.createTempFile(target.toAbsolutePath().getParent(), ".apb-export", ".tmp");
        try {
            try (var out = Files.newOutputStream(temp)) { writer.write(out); }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    /** Writes one design as a pack to {@code out}, which is closed. */
    static void write(Design design, OutputStream out) throws IOException {
        Properties props = new Properties();
        props.setProperty("format", Integer.toString(FORMAT));
        props.setProperty("name", design.name);
        props.setProperty("backgroundColor", design.backgroundColor);
        props.setProperty("size", Integer.toString(design.size));
        props.setProperty("speed", Integer.toString(design.speed));
        props.setProperty("trailLength", Integer.toString(design.trailLength));
        props.setProperty("motion", design.motion.name());
        String[] references = {design.objectImage, design.trailImage, design.backgroundImage};
        try (var zip = new ZipOutputStream(out)) {
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
        try (var in = Files.newInputStream(source)) { return importFrom(in, assets); }
    }

    /** Like {@link #importFrom(Path, Path)} for a pack that is already open; the stream is not closed. */
    static Design importFrom(InputStream source, Path assets) throws IOException {
        Properties props = null;
        var images = new java.util.HashMap<String, byte[]>();
        var seen = new java.util.HashSet<String>();
        long total = 0;
        long retained = 0;
        try {
            var zip = new ZipInputStream(source);
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                String entryName = entry.getName();
                if (seen.size() >= MAX_ENTRIES) {
                    throw new IOException("Design file has too many entries: at most " + MAX_ENTRIES + ".");
                }
                if (!seen.add(entryName)) throw new IOException("Design file has a duplicate entry: " + entryName);
                if (entry.isDirectory()) continue;
                boolean descriptor = entryName.equals(DESCRIPTOR);
                boolean layer = isLayerEntry(entryName);
                // Every entry is read through the same budget, recognised or not, so skipped data counts too.
                long limit = Math.min(descriptor ? MAX_DESCRIPTOR_BYTES : MAX_ENTRY_BYTES, MAX_TOTAL_BYTES - total);
                byte[] bytes = readLimited(zip, limit, descriptor
                    ? "The design descriptor must be at most " + MAX_DESCRIPTOR_BYTES / 1024 + " KB."
                    : limit < MAX_ENTRY_BYTES ? "Design files may hold at most " + MAX_TOTAL_BYTES / (1024 * 1024) + " MB in total."
                    : "Design file entries must be at most 10 MB.");
                total += bytes.length;
                if (descriptor) {
                    props = new Properties();
                    props.load(new StringReader(new String(bytes, StandardCharsets.ISO_8859_1)));
                } else if (layer) {
                    retained += bytes.length;
                    if (retained > MAX_IMAGE_BYTES) {
                        throw new IOException("Design file images may total at most " + MAX_IMAGE_BYTES / (1024 * 1024) + " MB.");
                    }
                    images.put(entryName, bytes);
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
            if (design.name.length() > MAX_NAME_LENGTH) {
                throw new IOException("Invalid design file: the name is longer than " + MAX_NAME_LENGTH + " characters.");
            }
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

    private static byte[] readLimited(InputStream in, long limit, String message) throws IOException {
        byte[] bytes = in.readNBytes((int) limit + 1);
        if (bytes.length > limit) throw new IOException(message);
        return bytes;
    }
}
