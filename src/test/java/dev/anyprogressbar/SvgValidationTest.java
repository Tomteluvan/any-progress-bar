package dev.anyprogressbar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SvgValidationTest {
    @TempDir Path temp;

    private static final String OPEN = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"10\" height=\"10\">";

    private void assertRejected(String body) throws IOException {
        Path svg = temp.resolve("hostile.svg");
        Files.writeString(svg, body);
        assertThrows(IOException.class, () -> Artwork.load(svg.toString()), body);
    }

    @Test void rejectsScriptsAndForeignObjects() throws IOException {
        assertRejected(OPEN + "<script>alert(1)</script></svg>");
        assertRejected(OPEN + "<foreignObject width=\"5\" height=\"5\"/></svg>");
    }

    @Test void rejectsExternalHrefs() throws IOException {
        assertRejected(OPEN + "<image href=\"http://example.com/a.png\" width=\"5\" height=\"5\"/></svg>");
        assertRejected(OPEN + "<image xlink:href=\"file:///etc/passwd\" width=\"5\" height=\"5\"/></svg>");
        assertRejected(OPEN + "<use href=\"other.svg#a\"/></svg>");
    }

    @Test void rejectsCssImportsAndExternalUrls() throws IOException {
        assertRejected(OPEN + "<style>@import url(http://example.com/a.css);</style></svg>");
        assertRejected(OPEN + "<style>@IMPORT 'a.css';</style></svg>");
        assertRejected(OPEN + "<rect width=\"5\" height=\"5\" style=\"fill:url(http://example.com/x#a)\"/></svg>");
        assertRejected(OPEN + "<style>rect { fill: url('file:///tmp/x') }</style></svg>");
    }

    @Test void rejectsEntityExpansionAndDoctypes() throws IOException {
        assertRejected("<!DOCTYPE svg [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;\">]>" + OPEN + "&b;</svg>");
        assertRejected("<!DOCTYPE svg SYSTEM \"http://example.com/evil.dtd\">" + OPEN + "</svg>");
    }

    @Test void rejectsNonSvgRoot() throws IOException {
        assertRejected("<html><body/></html>");
    }

    @Test void rejectsTooManyElements() throws IOException {
        var body = new StringBuilder(OPEN);
        for (int i = 0; i <= Artwork.MAX_SVG_ELEMENTS; i++) body.append("<rect width=\"1\" height=\"1\"/>");
        assertRejected(body.append("</svg>").toString());
    }

    @Test void rejectsDeepNesting() throws IOException {
        var body = new StringBuilder(OPEN);
        for (int i = 0; i < Artwork.MAX_SVG_DEPTH + 1; i++) body.append("<g>");
        body.append("<rect width=\"1\" height=\"1\"/>");
        for (int i = 0; i < Artwork.MAX_SVG_DEPTH + 1; i++) body.append("</g>");
        assertRejected(body.append("</svg>").toString());
    }

    @Test void acceptsReasonableSvgWithLocalReferences() throws IOException {
        Path svg = temp.resolve("ok.svg");
        Files.writeString(svg, OPEN + "<defs><linearGradient id=\"g\"><stop offset=\"0\" stop-color=\"red\"/></linearGradient></defs>"
            + "<rect width=\"10\" height=\"10\" fill=\"url(#g)\"/></svg>");
        assertNotNull(Artwork.load(svg.toString()));
    }
}
