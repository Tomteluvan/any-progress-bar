# Improvement backlog

Ideas found in a code review. Items marked **Done** have been implemented.

## Robustness and correctness
1. **Done (1.1.0)** Per-layer fallback for missing/broken images (`ProgressPainter.lenient`).
2. **Done (1.2.0)** Share one application-level animation clock/timer across all bars (currently one `Timer` and start time per bar).
3. **Done (1.2.0)** Minimum-height guard or "main progress only" option; tiny bars (status bar, inline) look bad at 24 px.
4. **Done (1.2.0)** Replace deprecated `StartupActivity` (`ProgressStartupActivity`); app-level init via a listener or the service itself.
5. **Done (1.2.0, partly)** Raster images are cached per size. Still open: respect reduce-motion / high-contrast.
6. **Done (1.2.0)** Stop the 30 fps timer for idle bars (determinate at 0%/100%, hidden or minimized windows).
7. **Done (1.2.0)** Cap SVG complexity (element count, nesting depth) in `Artwork.validateSvg`.

## Features
1. **Done (1.1.0)** Design sharing: export/import `.apbdesign` files (`DesignPack`).
2. Animated sprites: GIF, APNG or sprite-sheet objects.
3. **Done (1.2.0)** More motion types: hop, spin, pulse, bob, ping-pong (ping-pong affects indeterminate bars only).
4. Milestone effects at 25/50/75% and a finish animation at 100%.
5. Random or rotating designs per launch/project/task.
6. Light/dark theme variants per design (presets are dark-only colours).
7. Per-task designs (indexing vs builds); needs a way to identify the owning task.
8. **Done (1.3.0, partly)** More bundled themes: six everyday-object presets (chair, hamburger, table fan, mug, umbrella, alarm clock) and a smiley toggle. Still open: seasonal themes.

## Settings UI
1. **Done (1.1.0)** Colour swatch/picker for the background colour.
2. **Done (1.2.0)** Preview at several bar heights, including the tiny status-bar size.
3. Drag-and-drop images onto the object/trail/background slots.
4. Undo for Delete and Restore presets.
5. **Done (1.2.0)** Field hints (speed is %, trail length is px).

## Build and release hygiene
1. **Done (1.1.0, reworked 1.3.0)** `CHANGELOG.md`; the Marketplace change notes are now generated from the section matching the project version (`org.jetbrains.changelog`).
2. **Done (1.2.0, partly)** Plugin icon and `<vendor url>`. Still open: Marketplace screenshots.
3. **Done (1.3.0)** GitHub Actions: `ci.yml` tests PRs and main; after a green push to main with a new `version` it publishes to the JetBrains Marketplace (`PUBLISH_TOKEN` secret) and then creates the GitHub release. `release.yml` was removed because the tag-triggered flow never ran after `ci.yml` had already created the release.
4. **Done (1.3.0)** `verifyPlugin` runs in CI against the recommended IDE versions (no upper build limit is declared).
5. **Done (1.2.0)** Gradle version catalog (`libs.versions.toml`).
6. **Done (1.2.0)** Log errors from the optional `presets.tsv` instead of swallowing them silently.
7. **Done (1.2.0)** Tests for hostile SVGs in `Artwork.validateSvg` (external hrefs, `@import`, entity expansion).

## Security and hardening
Found in a review; the SVG validator and the `.apbdesign` importer are already hardened (no DOCTYPE, no external
references, element/depth caps, entries read by fixed name only, 10 MB per entry).
1. **Done (1.3.0)** Tests for `DesignPack` abuse: missing/oversized entries, bad references, path-traversal entry names, hostile SVGs, invalid values, content-hash storage (`DesignPackTest`).
2. Pin GitHub Actions to commit SHAs, add `dependabot.yml`, and put `PUBLISH_TOKEN` in a GitHub Environment with required approval.
3. Sign the plugin (`signPlugin`; certificate chain, private key and password as secrets).
4. Gradle wrapper validation and dependency verification (`verification-metadata.xml`) for `jsvg`.
5. **Done (1.3.0)** Reject `..`, path separators tricks and non-image names in `bundled:` references when importing a pack (`DesignPack.resolve`). Previously `bundled:../META-INF/plugin.xml` passed the existence check and only failed later because it is not an image.
6. Cap the total size and number of entries in a pack, and the length of a design name.
7. `Artwork.importFile` validates a file and then reads it again, and SVGs are parsed twice (validator, then `jsvg`). Read the bytes once and validate those.
8. `ProgressSettings.loadState` throws on invalid saved state (duplicate ids, no designs); fall back to the defaults instead.
