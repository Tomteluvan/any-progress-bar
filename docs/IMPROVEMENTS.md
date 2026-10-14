# Improvement backlog

Ideas found in a code review. Items marked **Done** have been implemented.

## Robustness and correctness
1. **Done (1.1.0)** Per-layer fallback for missing/broken images (`ProgressPainter.lenient`).
2. Share one application-level animation clock/timer across all bars (currently one `Timer` and start time per bar).
3. Minimum-height guard or "main progress only" option; tiny bars (status bar, inline) look bad at 24 px.
4. Replace deprecated `StartupActivity` (`ProgressStartupActivity`); app-level init via a listener or the service itself.
5. Cache pre-scaled raster images per size instead of scaling every frame; respect reduce-motion / high-contrast.
6. Stop the 30 fps timer for idle bars (determinate at 0%/100%, hidden or minimized windows).
7. Cap SVG complexity (element count, nesting depth) in `Artwork.validateSvg`.

## Features
1. **Done (1.1.0)** Design sharing: export/import `.apbdesign` files (`DesignPack`).
2. Animated sprites: GIF, APNG or sprite-sheet objects.
3. More motion types: hop, spin, pulse, bob, ping-pong for indeterminate bars.
4. Milestone effects at 25/50/75% and a finish animation at 100%.
5. Random or rotating designs per launch/project/task.
6. Light/dark theme variants per design (presets are dark-only colours).
7. Per-task designs (indexing vs builds); needs a way to identify the owning task.
8. More bundled themes, e.g. seasonal.

## Settings UI
1. **Done (1.1.0)** Colour swatch/picker for the background colour.
2. Preview at several bar heights, including the tiny status-bar size.
3. Drag-and-drop images onto the object/trail/background slots.
4. Undo for Delete and Restore presets.
5. Field hints (speed is %, trail length is px).

## Build and release hygiene
1. **Done (1.1.0)** `CHANGELOG.md` and `<change-notes>` in `plugin.xml`.
2. Plugin icon (`pluginIcon.svg`), `<vendor url>`, Marketplace screenshots.
3. **Done** GitHub Actions: `ci.yml` tests PRs/main, `release.yml` publishes a release on `v*` tags.
4. Run `verifyPlugin` against several IDE versions (no upper build limit is declared).
5. Gradle version catalog (`libs.versions.toml`).
6. Log errors from the optional `presets.tsv` instead of swallowing them silently.
7. Tests for hostile SVGs in `Artwork.validateSvg` (external hrefs, `@import`, entity expansion).
