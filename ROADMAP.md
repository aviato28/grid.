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

## Next build — backlog (not started)

### Bugs (from device testing)
- [ ] Caption glyphs overlap/clip on the card ("COCKpit"). Cause: text drawn at tiny mm size on a scaled canvas. Fix: draw text in magnified space (×100), subpixel + linear text. Applies to preview and PDF.
- [ ] Dark mode: Edit Photo top-bar title and back arrow are near-black on dark background. Audit all hardcoded colours.
- [ ] System back on Edit Photo discards edits → should save.
- [ ] Pinch-zoom anchors on card centre → should anchor on the pinch point.

### Caption studio (customisable footer strip)
- [ ] Fonts (files already in repo): Handwritten (Caveat), Script (Homemade Apple), Pencil (Reenie Beanie), Marker (Permanent Marker), Typewriter (Special Elite), Serif (DM Serif Italic), Mono (Space Mono), Clean (Space Grotesk). Font chips rendered in their own font.
- [ ] Text size S/M/L, alignment, text colour presets (Ink, Pen blue, Red, Pencil, White); auto-contrast on dark frames.
- [ ] Frame colour presets: White, Cream, Black, Blush, Sage, Sky.
- [ ] Date per photo: auto from EXIF, editable, clearable; formats `15 Nov 2026`, `15.11.26`, `NOV 2026`, `Sunday, 15 Nov`.
- [ ] Location per photo: free text; best-effort prefill from EXIF GPS (Photo Picker often strips it).
- [ ] Footer layouts: Caption only · Caption + date · Caption left / date right · Caption + date · location · Date only · Location only · Blank.
- [ ] Film stamp: orange digital date (`'26 11 15`) inside the photo, bottom-right.
- [ ] Sheet-wide **Style** sheet + per-photo "Only this photo" override.

### Film filters (approved)
- [ ] Gold, Portrait, Vivid, Superia, Instant, Tungsten, Disposable, Expired, Tri-X, Soft B&W, Sepia (film-inspired names, no brands)
- [ ] Intensity slider; grain, vignette, light-leak toggles; brightness/contrast/warmth
- [ ] Sheet-wide default + per-photo override; baked into PDF

### Card shapes (all approved, mm)
- [ ] Instant: Mini 54×86 (have), Square 72×86, Wide 108×86, Classic 88×107
- [ ] Prints: Wallet 64×89, Photo strip 51×152 (3–4 photos), Passport 35×45
- [ ] Novelty: Film strip (35 mm negative, sprockets, frame numbers), Postage stamp (perforated), Ticket stub (admit-one, uses date/location)
- [ ] Extras: Postcard 148×105, Bookmark 50×150, Trading card 63×88, Slide mount 50×50, Contact sheet (full page)

### Sheets & projects
- [ ] Mixed-size sheets: different card shapes auto-packed on one A4 in straight rows (guillotine-friendly cuts)
- [ ] Saved drafts (projects survive app restarts)
- [ ] Drag to reorder

### Housekeeping
- [ ] Enable R8 shrinking (APK ~21 MB → a few MB); verify updater/org.json survive.

### Before building
- [ ] One quick static mock-up of the new screens → owner approval

## Later (ideas, unprioritised)
- Paper sizes: Letter, A3, 4×6 (A4 only for now)

## Team
- **Opus** — architecture, task specs, reviews, decisions
- **Sonnet** — complex features (renderer, PDF, updater, screens)
- **Haiku** — small/mechanical tasks (tokens, strings, docs, simple fixes)
