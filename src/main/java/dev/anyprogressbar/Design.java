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
    /** Group the design is listed under in the editor; empty for designs that are always shown. */
    public String category = "";

    public static final String AUTUMN = "Autumn";
    public static final String WINTER = "Winter & Christmas";
    public static final String SPRING = "Spring";
    public static final String SUMMER = "Summer";
    /** The categories in the order the editor lists them. */
    public static final List<String> CATEGORIES = List.of(AUTUMN, WINTER, SPRING, SUMMER);

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
        d.category = category;
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
        return Objects.equals(id, other.id) && Objects.equals(category, other.category) && sameLook(other);
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
        var all = new java.util.ArrayList<>(originalPresets());
        all.addAll(themePresets());
        return all;
    }

    public static List<Design> originalPresets() {
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
        if (!preset.category.isBlank()) return 6;
        return switch (preset.id) {
            case "preset-snail-mail", "preset-cowsmic", "preset-ramen-racer", "preset-frog-wizard", "preset-disco-duck" -> 4;
            case "preset-sloth-skater", "preset-pizza-scooter", "preset-octopus-barista", "preset-ghost-commuter",
                 "preset-dino-bike", "preset-toaster-jetpack", "preset-narwhal-sub", "preset-snowman-surfer",
                 "preset-sushi-train", "preset-gingerbread-dash" -> 5;
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
            themed("disco-duck", "Disco duck", "disco-duck.svg", "disco-trail.svg", "", "#3B2D48", 60, 90, 540, Motion.BOUNCE),
            themed("sloth-skater", "Sloth skater", "sloth-skater.svg", "leaf-drift.svg", "", "#26352B", 60, 90, 540, Motion.GLIDE),
            themed("pizza-scooter", "Pizza scooter", "pizza-scooter.svg", "cheese-pull.svg", "", "#3F2D27", 60, 90, 540, Motion.BOUNCE),
            themed("octopus-barista", "Octopus barista", "octopus-barista.svg", "coffee-beans.svg", "", "#352B2D", 60, 90, 540, Motion.WIGGLE),
            themed("ghost-commuter", "Ghost commuter", "ghost-commuter.svg", "wisps.svg", "", "#2B2845", 60, 90, 540, Motion.BOB),
            themed("dino-bike", "Dino on a bike", "dino-bike.svg", "footprints.svg", "", "#233A3A", 60, 90, 540, Motion.HOP),
            themed("toaster-jetpack", "Toaster jetpack", "toaster-jetpack.svg", "toast-trail.svg", "", "#2E2B3A", 60, 90, 540, Motion.BOB),
            themed("narwhal-sub", "Narwhal submarine", "narwhal-sub.svg", "sea-bubbles.svg", "", "#1F3248", 60, 90, 540, Motion.BOB),
            themed("snowman-surfer", "Snowman surfer", "snowman-surfer.svg", "snow-spray.svg", "", "#26344A", 60, 90, 540, Motion.GLIDE),
            themed("sushi-train", "Sushi train", "sushi-train.svg", "soy-drops.svg", "", "#2A3A3C", 60, 90, 540, Motion.GLIDE),
            themed("gingerbread-dash", "Gingerbread dash", "gingerbread-dash.svg", "sprinkles.svg", "", "#3A2A38", 60, 90, 540, Motion.BOUNCE),
            seasonal(Design.AUTUMN, "hedgehog-apples", "Hedgehog & apples", "hedgehog-apples.svg", "maple-leaves.svg", "#3A2A22", Motion.WIGGLE),
            seasonal(Design.AUTUMN, "pumpkin-cart", "Pumpkin cart", "pumpkin-cart.svg", "pumpkin-vine.svg", "#3B2A22", Motion.BOUNCE),
            seasonal(Design.AUTUMN, "scarecrow-crow", "Scarecrow & crow", "scarecrow-crow.svg", "straw-wisps.svg", "#3A3122", Motion.HOP),
            seasonal(Design.AUTUMN, "acorn-squirrel", "Acorn squirrel", "acorn-squirrel.svg", "acorn-trail.svg", "#38291F", Motion.BOUNCE),
            seasonal(Design.AUTUMN, "mushroom-gnome", "Mushroom gnome", "mushroom-gnome.svg", "spore-puffs.svg", "#2F2A2A", Motion.BOB),
            seasonal(Design.AUTUMN, "cauldron-cat", "Cauldron cat", "cauldron-cat.svg", "potion-fizz.svg", "#2A2438", Motion.BOB),
            seasonal(Design.AUTUMN, "apple-pie", "Apple pie", "apple-pie.svg", "cinnamon-steam.svg", "#3D2B24", Motion.BOUNCE),
            seasonal(Design.AUTUMN, "wise-owl", "Wise owl", "wise-owl.svg", "feather-drift.svg", "#2D2A3A", Motion.GLIDE),
            seasonal(Design.AUTUMN, "tractor-hay", "Harvest tractor", "tractor-hay.svg", "wheat-dust.svg", "#3A3320", Motion.WIGGLE),
            seasonal(Design.AUTUMN, "turkey-sneakers", "Turkey in sneakers", "turkey-sneakers.svg", "corn-feathers.svg", "#3B2A26", Motion.HOP),
            seasonal(Design.WINTER, "santa-sleigh", "Santa's sleigh", "santa-sleigh.svg", "sleigh-stars.svg", "#2A3348", Motion.GLIDE),
            seasonal(Design.WINTER, "rudolph", "Rudolph", "rudolph.svg", "red-glow.svg", "#2B3550", Motion.BOUNCE),
            seasonal(Design.WINTER, "elf-sled", "Elf on a sled", "elf-sled.svg", "candy-canes.svg", "#2E3C4E", Motion.GLIDE),
            seasonal(Design.WINTER, "walking-tree", "Walking Christmas tree", "walking-tree.svg", "ornaments.svg", "#22343A", Motion.WIGGLE),
            seasonal(Design.WINTER, "penguin-skater", "Penguin skater", "penguin-skater.svg", "ice-crystals.svg", "#233A52", Motion.GLIDE),
            seasonal(Design.WINTER, "polar-bear-cocoa", "Polar bear cocoa", "polar-bear-cocoa.svg", "marshmallows.svg", "#33384F", Motion.BOB),
            seasonal(Design.WINTER, "snow-globe", "Snow globe", "snow-globe.svg", "glitter-snow.svg", "#2D3250", Motion.PULSE),
            seasonal(Design.WINTER, "gift-box", "Gift box", "gift-box.svg", "ribbons-confetti.svg", "#2A2F45", Motion.BOUNCE),
            seasonal(Design.WINTER, "yeti-hug", "Friendly yeti", "yeti-hug.svg", "snowballs.svg", "#263650", Motion.WIGGLE),
            seasonal(Design.WINTER, "cardinal-wreath", "Cardinal & wreath", "cardinal-wreath.svg", "holly-berries.svg", "#2B3A3A", Motion.HOP),
            seasonal(Design.SPRING, "easter-bunny", "Easter bunny", "easter-bunny.svg", "painted-eggs.svg", "#2F3F34", Motion.BOUNCE),
            seasonal(Design.SPRING, "ladybug-leaf", "Ladybug", "ladybug-leaf.svg", "petals-drift.svg", "#2D3A32", Motion.WIGGLE),
            seasonal(Design.SPRING, "rain-boot-duckling", "Duckling in rain boots", "rain-boot-duckling.svg", "puddle-splash.svg", "#2A3C4A", Motion.HOP),
            seasonal(Design.SPRING, "bumblebee", "Bumblebee", "bumblebee.svg", "pollen-flowers.svg", "#2C3A30", Motion.BOB),
            seasonal(Design.SPRING, "cloud-lamb", "Cloud lamb", "cloud-lamb.svg", "clover-meadow.svg", "#2D3B4E", Motion.BOUNCE),
            seasonal(Design.SPRING, "watering-can", "Watering can", "watering-can.svg", "sprouts.svg", "#34302D", Motion.WIGGLE),
            seasonal(Design.SPRING, "butterfly", "Butterfly", "butterfly.svg", "flutter-path.svg", "#2E2D48", Motion.PULSE),
            seasonal(Design.SPRING, "gnome-wheelbarrow", "Garden gnome", "gnome-wheelbarrow.svg", "tulips.svg", "#2D3D34", Motion.WIGGLE),
            seasonal(Design.SPRING, "robin-blossom", "Robin & blossoms", "robin-blossom.svg", "blossom-twigs.svg", "#2F3A3E", Motion.GLIDE),
            seasonal(Design.SPRING, "sunny-cloud", "Sunny cloud", "sunny-cloud.svg", "rain-rainbow.svg", "#2B3A55", Motion.BOB),
            seasonal(Design.SUMMER, "ice-cream-cone", "Ice cream cone", "ice-cream-cone.svg", "melt-drops.svg", "#2E3B52", Motion.BOUNCE),
            seasonal(Design.SUMMER, "beach-crab", "Beach crab", "beach-crab.svg", "shell-prints.svg", "#33403F", Motion.HOP),
            seasonal(Design.SUMMER, "flamingo-float", "Flamingo float", "flamingo-float.svg", "ripple-waves.svg", "#1F3A4C", Motion.BOB),
            seasonal(Design.SUMMER, "watermelon-shades", "Cool watermelon", "watermelon-shades.svg", "melon-seeds.svg", "#2B3F36", Motion.BOUNCE),
            seasonal(Design.SUMMER, "beach-ball", "Beach ball", "beach-ball.svg", "sand-stars.svg", "#3A3A44", Motion.SPIN),
            seasonal(Design.SUMMER, "sunshine-shades", "Sunshine", "sunshine-shades.svg", "sun-rays.svg", "#2B4056", Motion.SPIN),
            seasonal(Design.SUMMER, "campfire-smore", "Campfire s'more", "campfire-smore.svg", "sparks-smores.svg", "#272A3C", Motion.PULSE),
            seasonal(Design.SUMMER, "lemonade-pitcher", "Lemonade", "lemonade-pitcher.svg", "lemon-slices.svg", "#2E4A48", Motion.BOB),
            seasonal(Design.SUMMER, "sea-turtle", "Sea turtle", "sea-turtle.svg", "tide-foam.svg", "#1E3F4F", Motion.GLIDE),
            seasonal(Design.SUMMER, "sailboat", "Sailboat", "sailboat.svg", "gulls-waves.svg", "#24405C", Motion.GLIDE)
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

    /** A themed preset listed under a seasonal category. */
    private static Design seasonal(String category, String id, String name, String object, String trail, String color, Motion motion) {
        Design d = themed(id, name, object, trail, "", color, 60, 90, 540, motion);
        d.category = category;
        return d;
    }

    private static Design preset(String slug, String name, String trail, String color, Motion motion) {
        Design d = new Design();
        d.id = "preset-" + slug;
        d.name = name;
        d.objectImage = "bundled:" + slug + ".svg";
        d.trailImage = "bundled:" + trail + ".svg";
        d.backgroundColor = color;
        d.motion = motion;
        return d;
    }

    @Override public String toString() { return name; }
}
