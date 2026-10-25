package dev.anyprogressbar;

import com.intellij.openapi.progress.ProgressManager;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Shareable form of a whole design library: a zip holding {@code library.properties} and one complete
 * {@link DesignPack} per design ({@code design-0.apbdesign}, ...). Every design therefore goes through the same
 * validation and size limits as a single imported design, and moving between machines never depends on the absolute
 * asset paths stored in the settings file.
 */
public final class LibraryPack {
    public static final String EXTENSION = "apblibrary";
    private static final String DESCRIPTOR = "library.properties";
    private static final Pattern DESIGN_ENTRY = Pattern.compile("design-(\\d{1,4})\\." + DesignPack.EXTENSION);
    private static final int FORMAT = 1;
    static final int MAX_DESIGNS = 200;
    private static final long MAX_DESCRIPTOR_BYTES = 64L * 1024;
    private static final long MAX_DESIGN_PACK_BYTES = 45L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;

    private LibraryPack() {}

    /** The designs read from a library file (with fresh IDs) and the index of the one that was active, or -1. */
    public record Imported(List<Design> designs, int activeIndex) {
        public Design active() { return activeIndex < 0 ? null : designs.get(activeIndex); }
    }

    /** What importing into an existing library would do: designs to add and how many already exist. */
    public record MergePlan(List<Design> additions, int duplicates) {}

    /** Writes every design to {@code target} via a temporary sibling, so a failed export never damages an existing file. */
    public static void export(List<Design> designs, String activeId, Path target) throws IOException {
        if (designs.isEmpty()) throw new IllegalArgumentException("There are no designs to export.");
        if (designs.size() > MAX_DESIGNS) {
            throw new IllegalArgumentException("A library file can hold at most " + MAX_DESIGNS + " designs.");
        }
        DesignPack.writeAtomically(target, out -> {
            var properties = new Properties();
            properties.setProperty("format", Integer.toString(FORMAT));
            properties.setProperty("count", Integer.toString(designs.size()));
            try (var zip = new ZipOutputStream(out)) {
                for (int i = 0; i < designs.size(); i++) {
                    ProgressManager.checkCanceled();
                    Design design = designs.get(i);
                    if (Objects.equals(design.id, activeId)) properties.setProperty("active", Integer.toString(i));
                    var pack = new ByteArrayOutputStream();
                    try {
                        design.validate();
                        if (design.name.length() > DesignPack.MAX_NAME_LENGTH) {
                            throw new IllegalArgumentException("Shorten the name to at most " + DesignPack.MAX_NAME_LENGTH + " characters.");
                        }
                        DesignPack.write(design, pack);
                    } catch (IllegalArgumentException | IOException e) {
                        throw new IOException("Cannot export \"" + design.name + "\": " + e.getMessage(), e);
                    }
                    zip.putNextEntry(new ZipEntry("design-" + i + "." + DesignPack.EXTENSION));
                    pack.writeTo(zip);
                    zip.closeEntry();
                }
                var text = new StringWriter();
                properties.store(text, "Any Progress Bar library");
                zip.putNextEntry(new ZipEntry(DESCRIPTOR));
                zip.write(text.toString().getBytes(StandardCharsets.ISO_8859_1));
                zip.closeEntry();
            }
        });
    }

    /**
     * Reads a library file, importing every design's images into {@code assets}. Nothing is returned unless the whole
     * file is valid; images already copied for a design that is then rejected are left for the asset cleanup.
     */
    public static Imported importFrom(Path source, Path assets) throws IOException {
        Properties props = null;
        var designs = new TreeMap<Integer, Design>();
        var seen = new HashSet<String>();
        long total = 0;
        try (var in = java.nio.file.Files.newInputStream(source)) {
            var zip = new ZipInputStream(in);
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                ProgressManager.checkCanceled();
                String name = entry.getName();
                if (seen.size() > MAX_DESIGNS) throw new IOException("Library file has too many entries.");
                if (!seen.add(name)) throw new IOException("Library file has a duplicate entry: " + name);
                if (entry.isDirectory()) continue;
                var design = DESIGN_ENTRY.matcher(name);
                boolean descriptor = name.equals(DESCRIPTOR);
                long limit = Math.min(descriptor ? MAX_DESCRIPTOR_BYTES : MAX_DESIGN_PACK_BYTES, MAX_TOTAL_BYTES - total);
                byte[] bytes = zip.readNBytes((int) limit + 1);
                if (bytes.length > limit) {
                    throw new IOException(descriptor ? "The library descriptor must be at most " + MAX_DESCRIPTOR_BYTES / 1024 + " KB."
                        : limit < MAX_DESIGN_PACK_BYTES ? "Library files may hold at most " + MAX_TOTAL_BYTES / (1024 * 1024) + " MB in total."
                        : "A design in a library file must be at most " + MAX_DESIGN_PACK_BYTES / (1024 * 1024) + " MB.");
                }
                total += bytes.length;
                if (descriptor) {
                    props = new Properties();
                    props.load(new StringReader(new String(bytes, StandardCharsets.ISO_8859_1)));
                } else if (design.matches()) {
                    if (designs.size() >= MAX_DESIGNS) throw new IOException("Library file has more than " + MAX_DESIGNS + " designs.");
                    try {
                        designs.put(Integer.parseInt(design.group(1)), DesignPack.importFrom(new ByteArrayInputStream(bytes), assets));
                    } catch (IOException e) {
                        throw new IOException("Cannot import " + name + ": " + e.getMessage(), e);
                    }
                }
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("Not a valid library file.", e);
        }
        if (props == null) throw new IOException("Not a library file: " + DESCRIPTOR + " is missing.");
        checkFormat(props.getProperty("format", ""));
        if (designs.isEmpty()) throw new IOException("The library file contains no designs.");
        String count = props.getProperty("count", "");
        if (!count.equals(Integer.toString(designs.size()))) {
            throw new IOException("Invalid library file: it lists " + count + " designs but contains " + designs.size() + ".");
        }
        int active = -1;
        try {
            active = Integer.parseInt(props.getProperty("active", "-1").trim());
        } catch (NumberFormatException e) {
            // an unreadable active marker only means that no design is activated by the import
        }
        var list = new ArrayList<>(designs.values());
        return new Imported(list, active >= 0 && active < list.size() ? active : -1);
    }

    private static void checkFormat(String value) throws IOException {
        int format;
        try { format = Integer.parseInt(value.trim()); }
        catch (NumberFormatException e) { throw new IOException("Invalid library file: unreadable format \"" + value + "\".", e); }
        if (format > FORMAT) {
            throw new IOException("This library file uses format " + format + ", which this version of Any Progress Bar "
                + "does not support. Update the plugin to import it.");
        }
        if (format < 1) throw new IOException("Invalid library file: unsupported format " + format + ".");
    }

    /** Which imported designs are new: one that looks the same as an existing design (ignoring its ID) is a duplicate. */
    public static MergePlan planMerge(List<Design> existing, List<Design> imported) {
        var additions = new ArrayList<Design>();
        int duplicates = 0;
        for (Design design : imported) {
            boolean known = existing.stream().anyMatch(design::sameLook) || additions.stream().anyMatch(design::sameLook);
            if (known) duplicates++;
            else additions.add(design);
        }
        return new MergePlan(additions, duplicates);
    }
}
