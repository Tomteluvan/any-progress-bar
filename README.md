# Any Progress Bar

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/35163?label=JetBrains%20Marketplace&logo=jetbrains)](https://plugins.jetbrains.com/plugin/35163-any-progress-bar)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/35163)](https://plugins.jetbrains.com/plugin/35163-any-progress-bar)
[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/Tomteluvan)

An IntelliJ IDEA plugin that replaces the standard progress bar with animated,
themeable ones. It ships with twenty presets (animals, everyday objects, Tung Tung Sahur and playful character themes)
and includes an in-IDE editor for creating your own designs from PNG, JPG or SVG images.

## Presets

![Preview of the built-in themes](docs/theme-previews.png)

<details>
<summary>Show preset details (objects, trails and motions)</summary>

**Animals**

| Animal | Trail | Motion |
|---|---|---|
| Cat | Rainbow | Bounce |
| Dog | Confetti | Wiggle |
| Fox | Stars | Glide |
| Rabbit | Bubbles | Bounce |
| Penguin | Flowers | Wiggle |

**Everyday objects**

| Object | Trail | Motion |
|---|---|---|
| Chair | Sawdust | Hop |
| Hamburger | Crumbs | Bounce |
| Table fan | Breeze | Spin |
| Coffee mug | Steam | Bob |
| Umbrella | Raindrops | Wiggle |
| Alarm clock | Notes | Pulse |
| Smiley toggle | Orange fill | Glide |

**Character themes**

| Theme | Object | Trail |
|---|---|---|
| Tung Tung Sahur - Night | log character, bat raised | moons and stars |
| Tung Tung Sahur - Drumbeat | log character, bat forward | drum sound rings |
| Tung Tung Sahur - Bat squad | log character, bat on shoulder | mini bats |

The Tung Tung Tung Sahur character is an original drawing of an internet meme character.

**Worth the wait**

| Theme | Object | Trail | Motion |
|---|---|---|---|
| Snail mail | Envelope-carrying snail | Letters and postage stamps | Glide |
| Cowsmic encounter | UFO abducting a cow | Stars and milk cartons | Bob |
| Ramen racer | Noodle bowl on wheels | Noodles and scallions | Wiggle |
| The frog of compile | Frog wizard riding a broom | Lily pads and magic sparkles | Bob |
| Disco duck | Duck on roller skates | Music notes and dance-floor sparkles | Bounce |

</details>

## Installation

Requires **IntelliJ IDEA 2026.1 (build 261) or newer**.

### From the JetBrains Marketplace (recommended)

In the IDE, open **Settings > Plugins > Marketplace**, search for **Any Progress Bar**,
click **Install** and restart the IDE. The plugin is also available on its
[Marketplace page](https://plugins.jetbrains.com/plugin/35163-any-progress-bar).

### From a release file

1. Download `any-progress-bar-<version>.zip` from the repository's **Releases** page,
   or [build it from source](#building).
2. In the IDE, open **Settings > Plugins > gear icon > Install Plugin from Disk...**
   and select the zip file. Do not unzip it.
3. Restart the IDE.

When upgrading from an earlier version, new presets are added to the library once and
custom designs are preserved.

## Usage

Open **Settings > Appearance & Behavior > Any Progress Bar**.

![The Any Progress Bar editor in IntelliJ](docs/in-editor-example.png)

- Select a design from the list and click **Apply** to make it the active one.
- **New** and **Duplicate** create a design; **Delete** removes one.
- **Export...** saves a design, including its images, as a shareable `.apbdesign` file;
  **Import...** adds one to the library.
- **Export library...** saves every design and all of their images as one `.apblibrary` file, which is
  the reliable way to move your designs to another machine (the settings file stores absolute image paths).
  **Import library...** offers to **Merge** (adds only designs you do not already have) or to **Replace Library**.
- Add an object image, a repeating trail image and/or a background image.
- Set a background colour with the colour picker or as `#RRGGBB`, or use the IDE colour.
- Adjust object size, speed, trail length and motion.
- The preview shows indeterminate and determinate progress at several bar heights.
- **Restore presets** resets the library to the built-in presets after confirmation.
- Clear **Enable custom progress bars** to return to the IDE's default rendering.

Every image layer is optional. Backgrounds stretch across the bar, objects keep their
aspect ratio, and trails tile behind the object. Transparent PNG or SVG files work best
for objects and trails.

### Motion types

| Motion | Effect |
|---|---|
| Glide | Moves steadily along the bar. |
| Bounce | Squashes and stretches vertically. |
| Wiggle | Rocks from side to side. |
| Hop | Jumps up and down while travelling. |
| Spin | Rotates continuously. |
| Pulse | Grows and shrinks. |
| Bob | Floats gently up and down. |
| Ping-pong | Indeterminate bars travel back and forth instead of looping. |

## Image support and limits

- PNG, JPG/JPEG and **static SVG**. SVG scripts, interactivity and SVG animation are
  not supported; motion is provided by the plugin.
- SVGs must be self-contained. Linked images, stylesheet imports, scripts and document
  type declarations are rejected, while fragment references such as `url(#gradient)` work.
  SVGs are limited to 5000 elements and 32 levels of nesting.
- Files may be up to 10 MB. Raster images may be up to 8192 px per side and 16 megapixels.
- Imported images are copied to `<IDE config directory>/any-progress-bar/assets`, so the
  originals can be moved or deleted afterwards. Settings are stored in
  `any-progress-bar.xml` and are local to that IDE configuration.

## Scope and troubleshooting

Horizontal Swing progress bars use the selected design. Vertical bars and views that do
not use `JProgressBar` keep the standard rendering. Determinate bars follow real
completion, and indeterminate bars loop at the configured speed. Bars shorter than 12 px
are drawn as a plain fill, because artwork is not legible at that size. Animation pauses
automatically for bars that are idle, hidden or in a minimized window.

If an image is missing or unreadable, the plugin shows a notification and skips only that
layer; the rest of the design is still used. Replace or clear the image in the settings.
If the design itself is invalid, the IDE's standard progress bar is used instead.

Do not enable another progress bar replacement plugin at the same time.

## Building

Requires **JDK 21 or newer**. The Gradle wrapper downloads all other dependencies,
including the IntelliJ IDEA 2026.1 SDK, so the first build can take a while.

```sh
./gradlew test                         # run the tests
./gradlew buildPlugin                  # build/distributions/any-progress-bar-<version>.zip
./gradlew verifyCompatibilityMetadata  # build the plugin and validate its descriptor
./gradlew runIde                       # start a sandbox IDE with the plugin loaded
```

The plugin declares `since-build="261"` and no upper build limit, so newer IDE versions
are not blocked at install time. Changes to the IDE API in future releases may still
require plugin updates. `verifyCompatibilityMetadata` validates this in the packaged zip.

### Adding a preset

1. Add the SVG files to `src/main/resources/art/`.
2. Add an entry to `Design.themePresets()` in
   `src/main/java/dev/anyprogressbar/Design.java`.
3. Run `./gradlew test`.

Private presets can also be listed in an optional `src/main/resources/art/brand/presets.tsv`
(see `Design.optionalPresets()`). That folder is git-ignored and excluded from builds by
default; pass `-PlocalBuild` (`./gradlew buildPlugin -PlocalBuild`) to include it in a
local build.

## Releasing

Releases are published by the `release` job in `.github/workflows/ci.yml`. There is no separate
tag-triggered workflow: pushing a `v<version>` tag does nothing, because the job creates the tag itself.

1. Update `version` in `build.gradle.kts` and add a matching section to `CHANGELOG.md`
   (it becomes the Marketplace change notes).
2. Push to `main`.
3. When the `build` job passes (tests, packaged-metadata check and `verifyPlugin`) and no GitHub
   release exists for that version yet, the `release` job takes the plugin archive that the `build`
   job tested and verified (it does not rebuild it) and, in this order:
   1. creates the `v<version>` tag and a GitHub release with the zip and a `SHA256SUMS` file attached
      (`gh release create`), then
   2. uploads the same zip to the JetBrains Marketplace (`./gradlew publishPlugin -PpluginArchive=<zip> -x buildPlugin`),
      authenticating with the `PUBLISH_TOKEN` repository secret, a token created in your JetBrains
      Marketplace profile.

The GitHub release comes first so that a failed release never leaves a Marketplace version without
one. The Marketplace reviews uploads before a new version becomes visible to users, so a successful
job does not mean the version is live yet.

Pushes that do not change the version do not create a release, and neither do patch versions
(`x.y.z` with `z` above 0, for example documentation-only updates): they run the normal build
and verification but are never published. Only minor and major versions (`x.y.0`) are released.

### Recovering from a partly failed release

The job decides whether to release by looking for a GitHub release named `v<version>`, so a
failure leaves one of these states:

- **Failed before or while creating the GitHub release** (a red `build` job, a missing archive,
  `gh release create` failing): nothing was published and no release exists, so fix the cause and
  push again or re-run the failed jobs from the Actions page.
- **The GitHub release exists but the Marketplace upload failed** (for example an expired
  `PUBLISH_TOKEN` or a Marketplace outage): re-running the job does nothing, because it sees the
  release and skips. First check the plugin's page on the Marketplace to make sure the version did not
  arrive after all, since the Marketplace rejects a version it already has. If it is missing, either
  - upload the already-verified archive by hand: download the zip from the release, check it against
    `SHA256SUMS`, then run
    `PUBLISH_TOKEN=<token> ./gradlew publishPlugin -PpluginArchive=<zip> -x buildPlugin`, or
  - delete the release and its tag (`gh release delete v<version> --cleanup-tag`) and re-run the
    `release` job, which then starts again from step 3.1. Only do this if the Marketplace does not
    have the version.
- **The release is wrong and must be replaced**: use a new version number. Marketplace versions
  cannot be overwritten.

### Plugin signing

The plugin is not signed yet, and the release job does not need signing. JetBrains recommends signing
and the Marketplace can verify a signature. Signing needs:

- a certificate chain and a private key (and its password), generated as described in the
  [JetBrains plugin signing documentation](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html),
  stored as repository secrets;
- a `signing { ... }` block in the `intellijPlatform` configuration of `build.gradle.kts` that reads
  those secrets (the certificate chain, private key and password) from environment variables;
- the `build` job running `signPlugin` and uploading the signed zip as the artifact, so the `release`
  job publishes the exact archive that was verified and signed. `-PpluginArchive` must then point to
  the signed zip rather than the unsigned one.

Keep the private key out of the repository and out of logs: only pass it to the job through secrets.

## Project layout

```
src/main/java/dev/anyprogressbar/   plugin code (painter, animation clock, settings, editor UI)
src/main/resources/art/             bundled SVG artwork
src/main/resources/META-INF/        plugin descriptor and icons
src/test/java/                      unit and IDE integration tests
docs/                               screenshots and the improvement backlog
```

## Support

If you enjoy the plugin, you can support its development:

[![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20a%20Coffee-FFDD00?logo=buymeacoffee&logoColor=black)](https://buymeacoffee.com/Tomteluvan)

Bug reports, ideas and pull requests are also welcome through the repository's issue tracker.

## License

[MIT](LICENSE) © Mikael Broström
