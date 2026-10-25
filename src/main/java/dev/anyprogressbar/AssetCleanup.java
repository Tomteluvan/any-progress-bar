package dev.anyprogressbar;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Removes imported images that nothing refers to any more: from deleted designs, replaced images, restored presets and
 * cancelled edits. Deliberately conservative: it only touches files this plugin stored (named by content hash), only
 * those no saved design refers to, and never a file written or re-used recently, which may belong to an import or edit
 * that is still in progress.
 */
final class AssetCleanup {
    /** How long an unreferenced image is left alone; covers imports and edits that have not been applied yet. */
    static final Duration GRACE = Duration.ofHours(1);
    private static final Pattern STORED_IMAGE = Pattern.compile("[0-9a-f]{64}\\.(png|jpg|jpeg|svg)");
    private static final Logger LOG = Logger.getInstance(AssetCleanup.class);

    private AssetCleanup() {}

    /** Cleans the default asset folder in the background, keeping every image any of {@code libraries} refers to. */
    @SafeVarargs
    static void cleanInBackground(Collection<Design>... libraries) {
        var references = new ArrayList<Design>();
        for (Collection<Design> library : libraries) for (Design design : library) references.add(design.copy());
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                List<Path> removed = removeUnused(Artwork.assetsDirectory(), references, Instant.now(), GRACE);
                if (!removed.isEmpty()) LOG.info("Removed " + removed.size() + " unused imported image(s)");
            } catch (IOException | RuntimeException e) {
                LOG.warn("Cannot clean up unused imported images", e);
            }
        });
    }

    /**
     * Deletes the stored images in {@code assets} that no design in {@code referencedBy} uses and that were last
     * written before {@code now - grace}. Returns the files that were deleted.
     */
    static List<Path> removeUnused(Path assets, Collection<Design> referencedBy, Instant now, Duration grace) throws IOException {
        var removed = new ArrayList<Path>();
        if (!Files.isDirectory(assets, LinkOption.NOFOLLOW_LINKS)) return removed;
        Set<String> used = new HashSet<>();
        for (Design design : referencedBy) {
            for (String reference : new String[]{design.objectImage, design.trailImage, design.backgroundImage}) {
                if (reference == null || reference.isBlank() || reference.startsWith("bundled:")) continue;
                try {
                    Path name = Path.of(reference).getFileName();
                    if (name != null) used.add(name.toString());
                } catch (java.nio.file.InvalidPathException e) {
                    // not a path we could have stored, so it protects nothing
                }
            }
        }
        Instant cutoff = now.minus(grace);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(assets)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!STORED_IMAGE.matcher(name).matches() || used.contains(name)) continue;
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) continue;
                try {
                    if (Files.getLastModifiedTime(file).toInstant().isAfter(cutoff)) continue;
                    Files.delete(file);
                    removed.add(file);
                } catch (IOException e) {
                    LOG.warn("Cannot remove unused image " + file, e);
                }
            }
        }
        return removed;
    }
}
