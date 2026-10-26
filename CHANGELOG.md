# Changelog

## 1.7.0
- Selecting a design in the list makes it the active one again once you click Apply; the separate "Use this design" button and Active badge are gone.
- Internal: replaced the deprecated combo box renderer and the plugin now installs correctly when loaded dynamically.

## 1.6.0
- Export and import your whole design library as one `.apblibrary` file, with Merge (skips designs you already have) or Replace on import.
- The editor only reports changes when something really differs from the saved settings.
- Validation errors now appear next to the field that caused them, the field is highlighted, the preview keeps the last valid version, and a broken image names its layer.
- The editor scrolls in small settings windows, respects large UI scales, and has mnemonics, accessible names and announced validation messages.
- Objects using hop, bob, wiggle or spin are sized so they are no longer clipped by the bar.
- Invalid saved settings are repaired instead of discarded: valid designs are kept, the selection is fixed, a copy of the original is saved and a notification explains what changed.
- Importing a design pack now has total size, entry count, descriptor size and name length limits.
- Images are read once and the stored bytes are exactly the validated bytes. Unused imported images are cleaned up after Apply and at startup.
- Image loading, import and export no longer run on the UI thread, and can be cancelled.
- Progress bars use only the shared animation clock, including vertical bars and bars that become indeterminate later.
- The README documents the current release process, recovery steps and plugin signing.

## 1.5.1
- Internal: updated build dependencies (JUnit 6, Gradle 9.8.2, changelog plugin) and the GitHub Actions used by CI. Patch versions are not published to the Marketplace.

## 1.5.0
- Internal: replaced platform APIs scheduled for removal (the combo box renderer and image creation) and applied IDE inspection fixes, so the plugin keeps working on upcoming IDE versions.

## 1.4.2
- Documentation only: the README preset tables are now collapsed by default. Patch versions are not published to the Marketplace.

## 1.4.1
- Documentation only: refreshed the preset preview image in the README. Patch versions are not published to the Marketplace.

## 1.4.0
- Five new original presets: Snail mail, Cowsmic encounter, Ramen racer, The frog of compile and Disco duck, each with matching artwork, trail and motion. Existing libraries receive them once without changing custom designs, the active selection or previously deleted presets.
- Exporting a design no longer overwrites an existing file if the export fails.
- Importing a design pack rejects pack formats this version cannot read, with a clear message.
- Right-to-left bars shorter than 12 px are now mirrored like taller ones.
- Upgrading no longer brings back themed presets you deleted earlier; only presets added since your saved version are added.
- Motions show readable names (for example "Ping-pong"), and the editor's labels, mnemonics and Browse/Clear buttons are exposed properly to screen readers.
- Releases now publish the exact archive that CI built and verified, GitHub Actions are pinned to commit SHAs, and Dependabot keeps them up to date.

## 1.3.0
- Seven new presets: chair, hamburger, table fan, coffee mug, umbrella, alarm clock and a smiley toggle, each with its own trail.
- The trail now ends at the back of the object instead of its centre, so it no longer runs under the whole object.
- Importing a design pack rejects bundled artwork names that point outside the artwork folder.
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
