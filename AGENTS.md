# Multi-Agent Engineering Bootstrap (`AGENTS.md`)

Read [`MASTER_PLAN.md`](./MASTER_PLAN.md) **first** before planning or writing any code. It is the single source of truth for architecture, coordinate conventions, completed modules, and phase checklists.

## Current Project State
- **Phase 1 (Complete & Verified):** `:core-domain` pure Kotlin JVM Spatial Geometry (`domain.geometry`) and Material/Consumables Estimation (`domain.estimation`) engines + 23 passing JUnit tests + Gradle version catalog (`gradle/libs.versions.toml`).
- **Phase 2 (Complete & Verified):** ARCore + SceneView camera session control, center-reticle hit testing, EMA/coplanarity filtering, hole-punched Filament surface meshes, and the Compose AR measurement state machine.
- **Phase 3 (Complete & Verified):** Room persistence, seeded material catalog, repository/quote aggregation, AR-to-quote handoff, and the responsive quote workspace.
- **Phase 4 (Complete & Verified):** Native A4/Letter vector blueprint and multi-page PDF quote generation, contractor branding, private-file export, and Android share-sheet integration.
- **Phases 5–10 (Complete & Verified):** Quote-correctness, manual entry, multi-project/currency, polish, critical bug fixes, AR permission/session lifecycle, and R8 keep rules.
- **Phase 11 (Complete & Verified):** Responsive phone layouts, in-dialog validation banners, currency/unit consistency, exact decimal rate entry, PDF typography fit, launcher-icon safe zone.
- **Phase 12 (Complete & Verified):** Signed + dynamically versioned release APKs, resource shrinking, Apache-2.0 license, and an auto-updating GitHub Release workflow.
- **Phase 13 (Complete & Verified):** In-app GitHub release update checker with checksum-verified downloads.
- **Next Active Phase:** None. The project is feature complete and shipping signed APKs via `releases/latest`.

## Strict Engineering Rules
1. **No Stubs:** Never write `// TODO` or placeholder math/UI logic. Deliver complete, production-compilable code.
2. **Preserve Pure JVM Boundary:** Never add Android dependencies to `:core-domain`. Delegate all EMA smoothing (`EmaPoseFilter`), coplanarity checks (`CoplanarityValidator`), 3D-to-2D projection (`PlanarProjector`), and polygon/cutout triangulation (`PolygonGeometryEngine`) to `:core-domain`.
3. **Work Strictly One Phase at a Time:** Complete only the requested phase, update the checkboxes and status banner in `MASTER_PLAN.md`, verify compilation and unit tests, and stop for user review.

## Local Build & Test Environment (macOS)
Always export Android Studio's bundled JDK 21 (`JBR`) before invoking `./gradlew` (system default Java 24 is incompatible with Android `jlink`):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or write sdk.dir into local.properties
./gradlew :core-domain:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Release builds sign automatically only when `RELEASE_STORE_FILE`, `RELEASE_STORE_PASSWORD`,
`RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD` are set; otherwise the APK stays unsigned, which is
fine for local verification. CI supplies the signed path through repository secrets.
