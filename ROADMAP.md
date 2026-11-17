# Grid. — Roadmap

Turn phone photos into printable instant-film style cards, laid out on an A4 sheet, exported as a PDF for any print shop.

## Working agreement
- **Build only when the owner explicitly asks.** Between builds we only discuss and grow the backlog below.
- When asked to build, the whole "Next build" backlog ships as one batch → one release.
- Opus plans/reviews; Sonnet/Haiku build. Merging to `main` auto-releases the top CHANGELOG version.

## Decisions

| Area | Decision |
|---|---|
| Platform | Android only, **minSdk 30 (Android 11)** |
| Package | `com.nbansal.grid` · display name **Grid.** |
| Stack | Kotlin + Jetpack Compose, `android.graphics.pdf.PdfDocument`, Photo Picker (no storage permissions) |
| Builds | GitHub Actions → signed APK → GitHub Releases (public repo). Merge to `main` = release. |
| Updates | In-app updater reads the latest GitHub release, downloads the APK, hands it to the system installer. One stable signing key (GitHub secrets, owner keeps backup). |
| Captions | Per photo, optional, with "apply to all" |
| Aesthetic | **Stationery (light) + Darkroom (dark)**, following system theme |

## Design system

| Token | Light · Stationery | Dark · Darkroom |
|---|---|---|
| background | `#F4F1EA` (faint 8dp grid) | `#161514` |
| surface | `#FFFFFF` | `#22201E` |
| onBackground | `#141414` | `#EDE8E0` |
| muted | `#6D685D` | `#8D867B` |
| line | `#141414` @ 18% | `#3A3632` |
| accent | `#FF5A1F` date-stamp orange | `#E5483B` safelight red |

- Type: Space Grotesk (UI), JetBrains Mono (labels, sizes, versions), Caveat (captions on cards only).
- Accent used sparingly: the "." in the wordmark, primary button, progress, selection.

## Print spec
- Pages true-to-size, images ≥300 DPI, 5 mm safe margin, crop marks, 10 mm calibration ruler.
- Mini card 54 × 86 mm, photo window 46 × 62 mm, footer strip 46 × 18 mm → 9 per A4 (3×3).

## Shipped
- **v0.1.0** — scaffold, themes, CI releases, in-app updater
- **v0.2.0** — photo picker, Mini cards, pan/zoom, captions, A4 preview, PDF export (share/save), app icon
- **v0.3.0** — caption studio (8 fonts, colours, frames, date/location, footer layouts, film stamp, sheet-wide Style + per-card override); 15 card shapes (instant, prints, film strip, stamp, ticket, postcard, bookmark, trading card, slide mount, contact sheet); mixed sheets with straight-cut packing; 11 film filters with adjustments, sheet-wide or per card, baked into the PDF; drafts that auto-save with their own photo copies; drag to reorder; "Auto" frame/font defaults; R8 shrinking (APK ~21 MB → see CHANGELOG); fixes (caption glyph overlap, dark-mode top bar, back saves edits, pinch anchor)

## Next build

- **Play Store build** (see `docs/PLAY-STORE.md`): `site` / `play` product flavors; `play` has no updater, no `REQUEST_INSTALL_PACKAGES`, no `INTERNET`; targetSdk 36; CI also builds `bundlePlayRelease` AAB as an artifact; manual pepk workflow for exporting the existing key to Play App Signing. Not started — waiting for owner's "build".

## Later (ideas, unprioritised)
- Paper sizes: Letter, A3, 4×6 (A4 only for now)

## Team
- **Opus** — architecture, task specs, reviews, decisions
- **Sonnet** — complex features (renderer, PDF, updater, screens)
- **Haiku** — small/mechanical tasks (tokens, strings, docs, simple fixes)
