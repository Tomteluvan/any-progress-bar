# Any Progress Bar

An IntelliJ IDEA plugin that replaces the standard progress bar with animated,
themeable ones. It ships with 8 presets (animals and Tung Tung
Tung Sahur themes) and has an in-IDE editor for making your own from PNG, JPG or SVG
images.

> Inspired by the **Ultimate Progress Bar** plugin. This is an independent
> implementation: it contains no code or assets from that project.

## Presets

![Preview of the built-in themes](docs/theme-previews.png)

**Animals**

| Animal | Trail | Motion |
|---|---|---|
| Cat | Rainbow | Bounce |
| Dog | Confetti | Wiggle |
| Fox | Stars | Glide |
| Rabbit | Bubbles | Bounce |
| Penguin | Flowers | Wiggle |

**Character themes**

| Theme | Object | Trail |
|---|---|---|
| Tung Tung Sahur - Night | log character, bat raised | moons and stars |
| Tung Tung Sahur - Drumbeat | log character, bat forward | drum sound rings |
| Tung Tung Sahur - Bat squad | log character, bat on shoulder | mini bats |

The Tung Tung Tung Sahur character is an original drawing of an internet meme character.

## Install

Requires **IntelliJ IDEA 2026.1 (build 261) or newer**.

1. Download `any-progress-bar-<version>.zip` from the repository's **Releases** page
   (or [build it yourself](#build)).
2. In the IDE open **Settings > Plugins > gear icon > Install Plugin from Disk...**
   and pick the zip. Do not unzip it.
3. Restart the IDE.

When upgrading from an older version, new presets are added to your library once and
your custom designs are kept.

## Use

Open **Settings > Appearance & Behavior > Any Progress Bar**.

![The Any Progress Bar editor in IntelliJ](docs/in-editor-example.png)

- Pick a design from the list and click **Apply** to make it the active one.
- **New** / **Duplicate** create a design; **Delete** removes one.
- Import an object image, a repeating trail image and/or a background image.
- Set a background colour as `#RRGGBB`, or leave it empty to use the IDE colour.
- Adjust object size, speed, trail length and motion (glide, bounce or wiggle).
- The preview shows both indeterminate loading and determinate progress.
- **Restore presets** resets the library to the built-in presets after confirmation.
- Untick **Enable custom progress bars** to go back to the IDE's default rendering.

Every image layer is optional. Backgrounds stretch across the bar, objects keep their
aspect ratio, and trails tile behind the object. Transparent PNG or SVG works best for
objects and trails.

## Build

Requires **JDK 21 or newer**. The Gradle wrapper downloads everything else, including
the IntelliJ IDEA 2026.1 SDK, so the first build takes a while.

```sh
./gradlew test                       # run the tests
./gradlew buildPlugin                # build/distributions/any-progress-bar-<version>.zip
./gradlew verifyCompatibilityMetadata  # build and check the plugin descriptor
./gradlew runIde                     # start a sandbox IDE with the plugin loaded
```

The plugin declares `since-build="261"` and no upper build limit, so newer IDEs are not
blocked at install time. Future IDE API changes may still require plugin updates.
`verifyCompatibilityMetadata` checks the built zip for exactly this.

### Adding a preset

1. Put the SVG files in `src/main/resources/art/`.
2. Add an entry to `Design.themePresets()` in
   `src/main/java/dev/anyprogressbar/Design.java`.
3. Run `./gradlew test`.

Private presets can also be listed in an optional `src/main/resources/art/brand/presets.tsv`
(see `Design.optionalPresets()`); that folder is git-ignored and left out of the published
build with `./gradlew buildPlugin -PpublicBuild`.

## License

[MIT](LICENSE) (c) Mikael Broström

## Image support and limits

- PNG, JPG/JPEG and **static SVG**. SVG scripts, interactivity and SVG animation are
  not supported (motion comes from the plugin).
- SVGs must be self-contained: linked images, stylesheet imports, scripts and document
  types are rejected. Fragment references such as `url(#gradient)` work.
- Files up to 10 MB; raster images up to 8192 px per side and 16 megapixels.
- Imported images are copied into `<IDE config directory>/any-progress-bar/assets`, so
  the originals can be moved afterwards. Settings are stored in
  `any-progress-bar.xml` and are local to that IDE configuration.

## Scope and troubleshooting

Horizontal Swing progress bars use the design; vertical bars and views that do not use
`JProgressBar` keep standard rendering. Determinate bars follow real completion,
indeterminate bars loop at the chosen speed. Very thin bars scale the artwork down to
fit, so the preview looks larger than the real bar. Do not enable another
progress-bar replacement plugin at the same time.

If artwork is missing or unreadable, the plugin reports it and restores the IDE bar.
Fix or clear the image, pick another design, or disable custom bars.

## Project layout

```
src/main/java/dev/anyprogressbar/   plugin code (painter, settings, editor UI)
src/main/resources/art/             bundled SVG artwork
src/main/resources/META-INF/        plugin.xml
src/test/java/                      unit and IDE integration tests
```
