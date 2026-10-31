# Multi-Agent Engineering Bootstrap (`AGENTS.md`)

Read [`MASTER_PLAN.md`](./MASTER_PLAN.md) **first** before planning or writing any code. It is the single source of truth for architecture, coordinate conventions, completed modules, and phase checklists.

## Current Project State
- **Phase 1 (Complete & Verified):** `:core-domain` pure Kotlin JVM Spatial Geometry (`domain.geometry`) and Material/Consumables Estimation (`domain.estimation`) engines + 23 passing JUnit tests + Gradle version catalog (`gradle/libs.versions.toml`).
- **Phase 2 (Complete & Verified):** ARCore + SceneView camera session control, center-reticle hit testing, EMA/coplanarity filtering, hole-punched Filament surface meshes, and the Compose AR measurement state machine.
- **Phase 3 (Complete & Verified):** Room persistence, seeded material catalog, repository/quote aggregation, AR-to-quote handoff, and the responsive quote workspace.
- **Phase 4 (Complete & Verified):** Native A4/Letter vector blueprint and multi-page PDF quote generation, contractor branding, private-file export, and Android share-sheet integration.
- **Next Active Phase:** None. The project is ready for final review.

## Strict Engineering Rules
1. **No Stubs:** Never write `// TODO` or placeholder math/UI logic. Deliver complete, production-compilable code.
2. **Preserve Pure JVM Boundary:** Never add Android dependencies to `:core-domain`. Delegate all EMA smoothing (`EmaPoseFilter`), coplanarity checks (`CoplanarityValidator`), 3D-to-2D projection (`PlanarProjector`), and polygon/cutout triangulation (`PolygonGeometryEngine`) to `:core-domain`.
3. **Work Strictly One Phase at a Time:** Complete only the requested phase, update the checkboxes and status banner in `MASTER_PLAN.md`, verify compilation and unit tests, and stop for user review.

## Local Build & Test Environment (macOS)
Always export Android Studio's bundled JDK 21 (`JBR`) before invoking `./gradlew` (system default Java 24 is incompatible with Android `jlink`):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :core-domain:test :app:compileDebugKotlin
```
