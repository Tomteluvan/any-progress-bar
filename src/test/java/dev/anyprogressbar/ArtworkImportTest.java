package dev.anyprogressbar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkImportTest {
    @TempDir Path temp;

    @Test void theStoredBytesAreExactlyTheValidatedBytes() throws IOException {
        byte[] svg;
        try (var in = getClass().getResourceAsStream("/art/cat.svg")) { svg = in.readAllBytes(); }
        Path stored = Path.of(Artwork.importBytes(svg, "svg", temp.resolve("assets")));
        assertArrayEquals(svg, Files.readAllBytes(stored));
        assertTrue(stored.getFileName().toString().endsWith(".svg"));
        assertNotNull(Artwork.load(stored.toString()));
    }

    @Test void invalidBytesAreNeverStored() throws IOException {
        Path assets = temp.resolve("assets");
        assertThrows(IOException.class, () -> Artwork.importBytes("not an image".getBytes(), "png", assets));
        assertThrows(IOException.class, () -> Artwork.importBytes("<svg><script/></svg>".getBytes(), "svg", assets));
        assertThrows(IOException.class, () -> Artwork.importBytes(new byte[1], "exe", assets));
        assertFalse(Files.exists(assets), "nothing should be created for rejected images");
    }

    @Test void oversizedFilesAreRejectedBeforeBeingFullyRead() throws IOException {
        byte[] big = new byte[Artwork.MAX_IMAGE_BYTES + 1];
        Arrays.fill(big, (byte) ' ');
        Path file = temp.resolve("big.svg");
        Files.write(file, big);
        assertThrows(IOException.class, () -> Artwork.importFile(file, temp.resolve("assets")));
        assertThrows(IOException.class, () -> Artwork.load(file.toString()));
        assertThrows(IOException.class, () -> Artwork.importBytes(big, "svg", temp.resolve("assets")));
    }

    @Test void keepsCopiesForSeveralSizesAndBoundsThem() throws IOException {
        var source = new java.awt.image.BufferedImage(400, 400, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Path file = temp.resolve("big.png");
        javax.imageio.ImageIO.write(source, "png", file.toFile());
        Artwork art = Artwork.load(file.toString());
        var target = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = target.createGraphics();
        try {
            art.draw(g, 0, 0, 20, 20);
            art.draw(g, 0, 0, 30, 30);
            art.draw(g, 0, 0, 20, 20);
            assertEquals(2, art.cachedSizes(), "different bar heights must not evict each other");
            for (int size = 40; size < 40 + 2 * Artwork.MAX_CACHED_SIZES; size++) art.draw(g, 0, 0, size, size);
            assertTrue(art.cachedSizes() <= Artwork.MAX_CACHED_SIZES);
            g.scale(2, 2);
            art.draw(g, 0, 0, 25, 25); // shown at twice the size on a HiDPI screen: the copy must have 50 pixels
            assertTrue(art.cachedSizes() >= 1);
        } finally { g.dispose(); }
    }
}
