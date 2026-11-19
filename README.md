# AR Contractor Quote & Material Estimator

An offline-first Android app for measuring contractor surfaces in AR, estimating materials and labor, and exporting branded client-ready quote PDFs.

## Features

- ARCore + SceneView surface capture with plane/depth hit testing, EMA pose smoothing, coplanarity checks, and undo/redo.
- Pure Kotlin spatial geometry for best-fit planes, gravity-aligned 2D projection, cutout clipping, and polygon triangulation.
- Trade-specific estimates for tiling, flooring, painting, and drywall, including package rounding, waste, consumables, labor, and tax.
- Room-backed projects, surfaces, cutouts, editable contractor branding, and a seeded material catalog.
- Multiple projects with a switcher: each keeps its own surfaces, client, tax policy, and ISO 4217 currency; every UI total and PDF figure renders in the project currency.
- Responsive Jetpack Compose quote workspace with material, waste, labor, and tax controls.
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

## Build And Test

```bash
# Domain tests, Android JVM tests, and app compilation
./gradlew :core-domain:test :app:testDebugUnitTest :app:compileDebugKotlin

# Compile device-side PDF/FileProvider tests
./gradlew :app:compileDebugAndroidTestKotlin

# Assemble and lint the debug app
./gradlew :app:assembleDebug :app:lintDebug

# Verify the minified release build and R8 keep rules
./gradlew :app:assembleRelease
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

With a connected Android device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The instrumentation suite generates real A4 and Letter PDFs with Android's `PdfDocument`, reopens them with `PdfRenderer`, and verifies the shareable `FileProvider` URI.

## PDF Verification

The native Canvas unit test renders every A4 and Letter page into `app/build/pdf-preview/` for visual inspection. These PNG previews are build artifacts and are not committed.

## License

This repository does not yet include a license file. Add one before distributing the application or source publicly.
