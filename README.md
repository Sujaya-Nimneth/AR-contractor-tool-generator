# AR Contractor Quote & Material Estimator

An offline-first Android app for measuring contractor surfaces in AR, estimating materials and labor, and exporting branded client-ready quote PDFs.

## Features

- ARCore + SceneView surface capture with plane/depth hit testing, EMA pose smoothing, coplanarity checks, and undo/redo.
- Pure Kotlin spatial geometry for best-fit planes, gravity-aligned 2D projection, cutout clipping, and polygon triangulation.
- Trade-specific estimates for tiling, flooring, painting, and drywall, including package rounding, waste, consumables, labor, and tax.
- Room-backed projects, surfaces, cutouts, editable contractor branding, and a seeded material catalog.
- Multiple projects with a switcher: each keeps its own surfaces, client, tax policy, and ISO 4217 currency; every UI total and PDF figure renders in the project currency.
- Responsive Jetpack Compose quote workspace with material, waste, labor, and tax controls.
- Metric/imperial display toggle for quote measurements; storage and pricing stay SI.
- Native A4 and US Letter vector PDF exports with contractor branding, hatched blueprints, dimension callouts, itemized pricing, terms, and a signature block.
- Private, revisioned PDF storage exposed to the Android share sheet through a read-only `FileProvider`.
- Deterministic project-level tax allocation, paginated surface summaries, and local data excluded from Android backups.
- Optional ARCore declaration with runtime camera permission, installation prompts, depth fallback, and a manual-quote path on unsupported devices.
- Typed-dimension manual surface entry (metric or feet) with rectangular door/window/vent openings — full quotes without an AR camera.
- In-place surface repair: re-enter dimensions or re-measure in AR while keeping the material assignment, pricing overrides, and surface order.
- In-progress AR drafts restored from `SavedStateHandle`, with persistence limited to structural edits and lifecycle boundaries.
- Collision-safe, millisecond-revisioned private PDF exports with bounded cleanup of older quote files.

All geometry, persistence, estimation, and document generation run on-device. The app has no cloud data dependency.

## Architecture

| Module | Responsibility |
| --- | --- |
| `:core-domain` | Pure Kotlin JVM geometry, unit conversion, material estimation, and multi-surface aggregation |
| `:app` | Android AR session, Filament rendering, Compose UI, Room persistence, repositories, and PDF generation |

`MASTER_PLAN.md` is the source of truth for coordinate conventions, module boundaries, phase history, and verification criteria.

## Requirements

- macOS, Linux, or Windows with Android Studio
- Android Studio bundled JDK 21
- Android SDK 36 and an Android device/emulator running API 26 or newer
- An ARCore-capable physical Android device for camera measurement

The system JDK may be newer than the Android Gradle Plugin toolchain supports. Use Android Studio's bundled JBR explicitly:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

## Download & Install

Every push to `main` runs the verification gate, builds a signed release APK with a strictly higher `versionCode`, and refreshes the rolling **[latest release](https://github.com/Sujaya-Nimneth/AR-contractor-tool-generator/releases/latest)**.

| Channel | Release | APKs |
| --- | --- | --- |
| Rolling build of `main` | [`latest`](https://github.com/Sujaya-Nimneth/AR-contractor-tool-generator/releases/tag/latest) | `ar-contractor-estimator-arm64-v8a-latest.apk` (~11 MB, most phones) · `ar-contractor-estimator-universal-latest.apk` (~33 MB) |
| Pinned SemVer tag | [`v1.0.0`](https://github.com/Sujaya-Nimneth/AR-contractor-tool-generator/releases/tag/v1.0.0) | `ar-contractor-estimator-arm64-v8a-v1.0.0.apk` · `ar-contractor-estimator-universal-v1.0.0.apk` |

Installed builds can also check for updates themselves: the quote workspace footer has a
**Check for updates** action, and a dismissible banner appears when a newer release is published.
The updater reads only the public `version.json`, verifies the APK's SHA-256 before installing,
and never sends project data anywhere.

Each release also ships `SHA256SUMS.txt` and `version.json` (version code, git SHA, build time, APK SHA-256, download URL).

Verify a downloaded APK before installing:

```bash
shasum -a 256 ar-contractor-estimator-universal-latest.apk
# compare against SHA256SUMS.txt from the same release
```

Because every release is signed with the same keystore and uses a monotonically increasing `versionCode`, a newer APK installs over an older one **without uninstalling** — your saved projects, surfaces, and material catalog are preserved.

## Build And Test

GitHub Actions runs the domain tests, app unit tests, lint, and debug assembly on every push and pull request (JDK 21).

```bash
# Domain tests, Android JVM tests, and app compilation
./gradlew :core-domain:test :app:testDebugUnitTest :app:compileDebugKotlin

# Compile device-side PDF/FileProvider tests
./gradlew :app:compileDebugAndroidTestKotlin

# Assemble and lint the debug app
./gradlew :app:assembleDebug :app:lintDebug

# Verify the minified, resource-shrunk release build and R8 keep rules
./gradlew :app:assembleRelease

# Build a release with explicit version metadata (as CI does)
./gradlew :app:assembleRelease \
  -PappVersionCode=142 -PappVersionName=1.0.42 -PappGitSha=$(git rev-parse --short HEAD)
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

With a connected Android device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The instrumentation suite generates real A4 and Letter PDFs with Android's `PdfDocument`, reopens them with `PdfRenderer`, and verifies the shareable `FileProvider` URI.

## PDF Verification

The native Canvas unit test renders every A4 and Letter page into `app/build/pdf-preview/` for visual inspection. These PNG previews are build artifacts and are not committed.

## Release Signing (Maintainers)

The release build signs automatically when the four signing values are present as Gradle
properties or environment variables; otherwise `assembleRelease` produces an unsigned APK so
local verification still works.

```bash
keytool -genkeypair -v -keystore release.jks -alias ar-contractor \
  -keyalg RSA -keysize 4096 -validity 9125 -storetype PKCS12

base64 < release.jks | tr -d '\n' > release.jks.base64

gh secret set ANDROID_KEYSTORE_BASE64 --body "$(cat release.jks.base64)"
gh secret set ANDROID_KEYSTORE_PASSWORD
gh secret set ANDROID_KEY_ALIAS
gh secret set ANDROID_KEY_PASSWORD
```

`*.jks`, `*.keystore`, and `keystore.properties` are git-ignored. Back the keystore up somewhere
safe: losing it means future APKs can no longer upgrade installed copies in place.

Cut a permanent release by pushing a SemVer tag:

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE) for
third-party component attribution.
