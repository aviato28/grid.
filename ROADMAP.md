# Grid. — Roadmap

Turn phone photos into printable instant-film style cards, laid out on an A4 sheet, exported as a PDF for any print shop.

## Decisions

| Area | Decision |
|---|---|
| Platform | Android only, **minSdk 30 (Android 11)** |
| Package | `com.nbansal.grid` · display name **Grid.** |
| Stack | Kotlin + Jetpack Compose, `android.graphics.pdf.PdfDocument`, Photo Picker (no storage permissions) |
| Builds | GitHub Actions → signed APK → GitHub Releases (repo is public). No local builds required. |
| Updates | In-app updater reads the latest GitHub release, downloads the APK, hands it to the system installer. One stable signing key, stored as a GitHub secret, backed up offline. |
| Captions | Per photo, optional, with an "apply to all" shortcut |
| Aesthetic | **Stationery (light) + Darkroom (dark)**, following system theme. See below. |

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
- Corners 6dp, sheets 14dp top corners, thin line dividers, no gradients.

## Print spec

- Pages true-to-size, images embedded at ≥300 DPI.
- 5 mm safe margin; optional thin cut marks.
- 10 mm calibration ruler + "print at 100% / actual size" reminder.
- MVP template: **Mini** card 54 × 86 mm → 9 per A4 (3×3).

## Phase 0 — Foundation
- [ ] Compose project scaffold, theme tokens
- [ ] GitHub Actions: build, sign, release on tag
- [ ] In-app updater (check → download → install)
- [ ] Versioning + changelog convention

## Phase 1 — MVP
- [ ] Pick photos (Photo Picker)
- [ ] Mini template
- [ ] Auto-crop with pan/zoom adjust per photo
- [ ] Per-photo captions
- [ ] Live A4 preview, auto-paginate past 9 photos
- [ ] Export PDF → share / save

## Phase 2 — Variety
- [ ] Templates: Classic (88×107, 4/sheet), Square, Photo strip
- [ ] Date stamp (from photo date), caption fonts
- [ ] Paper sizes: A4, Letter, A3, 4×6
- [ ] Toggles: cut marks, border width/colour
- [ ] Light filters: B&W, warm, faded

## Phase 3 — Polish
- [ ] Saved projects / drafts
- [ ] Drag to reorder
- [ ] Mixed-size sheets (auto-pack)
- [ ] Extra frames: film strip, postage stamp, ticket stub, wallet/passport

## Team
- **Opus** — architecture, task specs, reviews, decisions
- **Sonnet** — Gradle/CI/signing, updater, layout + PDF engine, screens
- **Haiku** — tokens, strings, icons, docs, layout-math tests, small fixes
