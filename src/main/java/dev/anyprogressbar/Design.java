package dev.anyprogressbar;

import java.awt.Color;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class Design {
    private static final com.intellij.openapi.diagnostic.Logger LOG =
        com.intellij.openapi.diagnostic.Logger.getInstance(Design.class);

    public enum Motion {
        GLIDE("Glide"), BOUNCE("Bounce"), WIGGLE("Wiggle"), HOP("Hop"), SPIN("Spin"), PULSE("Pulse"), BOB("Bob"),
        PING_PONG("Ping-pong");

        private final String label;
        Motion(String label) { this.label = label; }
        /** Name shown in the editor; the constant's own name is what gets saved. */
        public String label() { return label; }
    }
    public String id = UUID.randomUUID().toString();
    public String name = "My design";
    public String objectImage = "";
    public String trailImage = "";
    public String backgroundImage = "";
    public String backgroundColor = "";
    public int size = 24;
    public int speed = 100;
    public int trailLength = 160;
    public Motion motion = Motion.GLIDE;

    public Design copy() {
        Design d = new Design();
        d.id = id;
        d.name = name;
        d.objectImage = objectImage;
        d.trailImage = trailImage;
        d.backgroundImage = backgroundImage;
        d.backgroundColor = backgroundColor;
        d.size = size;
        d.speed = speed;
        d.trailLength = trailLength;
        d.motion = motion;
        return d;
    }

    /** The editable parts of a design, so a problem can be shown next to the control that caused it. */
    public enum Field {
        NAME("Name"), OBJECT_IMAGE("Object image"), TRAIL_IMAGE("Trail image"), BACKGROUND_IMAGE("Background image"),
        BACKGROUND_COLOR("Background colour"), SIZE("Object size"), SPEED("Speed"), TRAIL_LENGTH("Trail length"),
        MOTION("Motion"), OTHER("Design");

        private final String label;
        Field(String label) { this.label = label; }
        public String label() { return label; }
    }

    public record Problem(Field field, String message) {}

    /** Every problem with the design's values, in form order; empty when the design is valid. */
    public List<Problem> problems() {
        var problems = new java.util.ArrayList<Problem>();
        if (name == null || name.isBlank()) problems.add(new Problem(Field.NAME, "Give the design a name."));
        if (id == null || id.isBlank()) problems.add(new Problem(Field.OTHER, "The design has no ID."));
        if (objectImage == null) problems.add(new Problem(Field.OBJECT_IMAGE, "Image values must not be null."));
        if (trailImage == null) problems.add(new Problem(Field.TRAIL_IMAGE, "Image values must not be null."));
        if (backgroundImage == null) problems.add(new Problem(Field.BACKGROUND_IMAGE, "Image values must not be null."));
        if (backgroundColor == null) {
            problems.add(new Problem(Field.BACKGROUND_COLOR, "Colour values must not be null."));
        } else if (!backgroundColor.isBlank() && !backgroundColor.matches("#[0-9a-fA-F]{6}")) {
            problems.add(new Problem(Field.BACKGROUND_COLOR, "Background colour must be #RRGGBB, or empty for the IDE colour."));
        }
        if (size < 8 || size > 64) problems.add(new Problem(Field.SIZE, "Object size must be 8-64 px."));
        if (speed < 10 || speed > 400) problems.add(new Problem(Field.SPEED, "Speed must be 10-400%."));
        if (trailLength < 0 || trailLength > 600) problems.add(new Problem(Field.TRAIL_LENGTH, "Trail length must be 0-600 px."));
        if (motion == null) problems.add(new Problem(Field.MOTION, "Choose a motion."));
        return problems;
    }

    public void validate() {
        List<Problem> problems = problems();
        if (!problems.isEmpty()) throw new IllegalArgumentException(problems.getFirst().message());
    }

    /**
     * True when both designs would look and be saved the same. Imported images are stored under their content hash, so
     * they are compared by file name rather than by the folder they happen to live in.
     */
    public boolean sameContent(Design other) {
        return Objects.equals(id, other.id) && sameLook(other);
    }

    /** Like {@link #sameContent} but ignoring the ID: whether two designs are duplicates of each other. */
    public boolean sameLook(Design other) {
        return Objects.equals(name, other.name)
            && imageKey(objectImage).equals(imageKey(other.objectImage))
            && imageKey(trailImage).equals(imageKey(other.trailImage))
            && imageKey(backgroundImage).equals(imageKey(other.backgroundImage))
            && Objects.equals(backgroundColor, other.backgroundColor)
            && size == other.size && speed == other.speed && trailLength == other.trailLength && motion == other.motion;
    }

    private static String imageKey(String reference) {
        if (reference == null || reference.isBlank() || reference.startsWith("bundled:")) return reference == null ? "" : reference;
        try {
            var name = java.nio.file.Path.of(reference).getFileName();
            return name == null ? reference : name.toString();
        } catch (java.nio.file.InvalidPathException e) {
            return reference;
        }
    }

    public Color color(Color fallback) {
        return backgroundColor.isBlank() ? fallback : Color.decode(backgroundColor);
    }

    public static List<Design> presets() {
        var all = new java.util.ArrayList<>(animalPresets());
        all.addAll(themePresets());
        return all;
    }

    public static List<Design> animalPresets() {
        return List.of(
            preset("cat", "Cat & rainbow", "rainbow", "#242437", Motion.BOUNCE),
            preset("dog", "Dog & confetti", "confetti", "#24302B", Motion.WIGGLE),
            preset("fox", "Fox & stars", "stars", "#302539", Motion.GLIDE),
            preset("rabbit", "Rabbit & bubbles", "bubbles", "#23323C", Motion.BOUNCE),
            preset("penguin", "Penguin & flowers", "flowers", "#29313D", Motion.WIGGLE)
        );
    }

    /** Preset version in which a themed preset first shipped; see {@link ProgressSettings.Data#PRESET_VERSION}. */
    static int introducedIn(Design preset) {
        return switch (preset.id) {
            case "preset-snail-mail", "preset-cowsmic", "preset-ramen-racer", "preset-frog-wizard", "preset-disco-duck" -> 4;
            default -> preset.id.startsWith("preset-tung-") ? 2 : 3;
        };
    }

    /** Themed presets that shipped after {@code version}, so upgrades never restore ones the user already had and deleted. */
    public static List<Design> themePresetsAddedAfter(int version) {
        return themePresets().stream().filter(p -> introducedIn(p) > version).toList();
    }

    /**
     * Themed presets added after the original five; existing users receive them once via {@link ProgressSettings}.
     * Extra presets can be supplied by an optional {@code /art/brand/presets.tsv} resource (see {@link #optionalPresets()}).
     */
    public static List<Design> themePresets() {
        var all = new java.util.ArrayList<>(List.of(
            themed("tung-night", "Tung Tung Sahur - Night", "tung-bat-up.svg", "sahur-moons.svg", "", "#1B1740", 40, 120, 220, Motion.WIGGLE),
            themed("tung-drum", "Tung Tung Sahur - Drumbeat", "tung-bat-forward.svg", "drum-rings.svg", "", "#4A2318", 40, 160, 240, Motion.BOUNCE),
            themed("tung-bats", "Tung Tung Sahur - Bat squad", "tung-bat-shoulder.svg", "mini-bats.svg", "", "#3B2A5C", 40, 140, 260, Motion.WIGGLE),
            themed("chair", "Chair & sawdust", "chair.svg", "sawdust.svg", "", "#2E2A26", 32, 100, 180, Motion.HOP),
            themed("hamburger", "Hamburger & crumbs", "hamburger.svg", "crumbs.svg", "", "#33261F", 32, 110, 200, Motion.BOUNCE),
            themed("fan", "Table fan & breeze", "fan.svg", "wind.svg", "", "#1F2F38", 32, 120, 220, Motion.SPIN),
            themed("mug", "Coffee mug & steam", "mug.svg", "steam.svg", "", "#2B2724", 32, 90, 200, Motion.BOB),
            themed("umbrella", "Umbrella & raindrops", "umbrella.svg", "raindrops.svg", "", "#222E3D", 32, 100, 200, Motion.WIGGLE),
            themed("alarm-clock", "Alarm clock & notes", "alarm-clock.svg", "notes.svg", "", "#2A2A3A", 32, 100, 200, Motion.PULSE),
            themed("smiley-toggle", "Smiley toggle", "smiley-toggle.svg", "toggle-fill.svg", "", "#383D5E", 32, 100, 600, Motion.GLIDE),
            themed("snail-mail", "Snail mail", "snail-mail.svg", "postage.svg", "", "#243D36", 60, 90, 540, Motion.GLIDE),
            themed("cowsmic", "Cowsmic encounter", "cowsmic.svg", "milky-way.svg", "", "#2C2947", 60, 90, 540, Motion.BOB),
            themed("ramen-racer", "Ramen racer", "ramen-racer.svg", "noodle-trail.svg", "", "#442E2C", 60, 90, 540, Motion.WIGGLE),
            themed("frog-wizard", "The frog of compile", "frog-wizard.svg", "pond-magic.svg", "", "#263D35", 60, 90, 540, Motion.BOB),
            themed("disco-duck", "Disco duck", "disco-duck.svg", "disco-trail.svg", "", "#3B2D48", 60, 90, 540, Motion.BOUNCE)
        ));
        all.addAll(optionalPresets());
        return all;
    }

    /**
     * Reads presets from the optional {@code /art/brand/presets.tsv} resource; empty when the file is absent.
     * Tab-separated columns: id, name, object, trail, background, colour, size, speed, trail length, motion.
     * Image names are relative to {@code /art/}. Lines starting with {@code #} are ignored.
     */
    static List<Design> optionalPresets() {
        var result = new java.util.ArrayList<Design>();
        try (var in = Design.class.getResourceAsStream("/art/brand/presets.tsv")) {
            if (in == null) return result;
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            int lineNumber = 0;
            for (String line; (line = reader.readLine()) != null; ) {
                lineNumber++;
                if (line.isBlank() || line.startsWith("#")) continue;
                try {
                    String[] c = line.split("\\t", -1);
                    result.add(themed(c[0], c[1], c[2], c[3], c[4], c[5], Integer.parseInt(c[6]), Integer.parseInt(c[7]),
                        Integer.parseInt(c[8]), Motion.valueOf(c[9])));
                } catch (RuntimeException e) { // a malformed line must never break the plugin; skip it and say why
                    LOG.warn("Ignoring presets.tsv line " + lineNumber + ": " + e, e);
                }
            }
        } catch (java.io.IOException e) {
            LOG.warn("Cannot read presets.tsv", e);
        }
        return result;
    }

    private static Design themed(String id, String name, String object, String trail, String background, String color,
                                 int size, int speed, int trailLength, Motion motion) {
        Design d = new Design();
        d.id = "preset-" + id;
        d.name = name;
        d.objectImage = "bundled:" + object;
        d.trailImage = "bundled:" + trail;
        d.backgroundImage = background.isEmpty() ? "" : "bundled:" + background;
        d.backgroundColor = color;
        d.size = size;
        d.speed = speed;
        d.trailLength = trailLength;
        d.motion = motion;
        return d;
    }

    private static Design preset(String animal, String name, String trail, String color, Motion motion) {
        Design d = new Design();
        d.id = "preset-" + animal;
        d.name = name;
        d.objectImage = "bundled:" + animal + ".svg";
        d.trailImage = "bundled:" + trail + ".svg";
        d.backgroundColor = color;
        d.motion = motion;
        return d;
    }

    @Override public String toString() { return name; }
}
