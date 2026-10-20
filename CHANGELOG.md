# Changelog

## 1.3.0
- Six new everyday-object presets: chair, hamburger, table fan, coffee mug, umbrella and alarm clock, each with its own trail.
- Existing users receive the new presets once on upgrade; custom designs are preserved.
- Releases are published to the JetBrains Marketplace automatically by CI when the version is bumped on main.

## 1.2.0
- New motion types: hop, spin, pulse, bob and ping-pong (indeterminate bars travel back and forth).
- One shared animation timer for all progress bars; it stops when every bar is idle (0%/100%), hidden or minimized.
- Bars shorter than 12 px are drawn as a plain fill instead of unreadable artwork.
- Raster images are scaled once per size instead of every frame.
- SVGs are limited to 5000 elements and 32 nesting levels.
- Startup uses `AppLifecycleListener` instead of the deprecated `StartupActivity`.
- Settings: units, ranges and tooltips on fields; the preview also shows 16 px and 8 px bars.
- Errors in the optional `presets.tsv` are logged per line instead of silently discarding every preset.
- Plugin icon, vendor URL, Gradle version catalog and tests for hostile SVGs.

## 1.1.0
- Export and import designs as shareable `.apbdesign` files (images included).
- A broken or missing image now skips only that layer instead of disabling the whole design, with a notification.
- Background colour has an IDE colour-picker swatch and a "Use IDE colour" button.
- Added `docs/IMPROVEMENTS.md`, the backlog of planned improvements.

## 1.0.3
- Earlier releases; see git history.
