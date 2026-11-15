# grid.
## Releasing

1. Add a `## X.Y.Z` section to `CHANGELOG.md` (its bullets become the release notes and the in-app update sheet).
2. Tag and push: `git tag v0.1.0 && git push origin v0.1.0`

GitHub Actions builds the APK, signs it with the key stored in repo secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`), and publishes it as `grid-<version>.apk` on a GitHub Release.
Version codes are `major*10000 + minor*100 + patch`, so tags must be `vMAJOR.MINOR.PATCH`.

## In-app updater

On launch (and from "Check for updates") the app calls the GitHub API for the latest release, compares its tag to the installed version code, and if newer shows a sheet with the changelog. "Download & install" downloads the `.apk` asset and hands it to the system `PackageInstaller`. The first time, Android asks you to allow installs from Grid. Updates only work because every release is signed with the same key.

Local builds: `./gradlew assembleDebug testDebugUnitTest` (release builds fall back to debug signing without the `GRID_KEYSTORE_*` env vars).
