# Post-Phase-5 Audit & Improvement Plan

> **Audit date:** 2026-11-19 · **Baseline:** `d985c9b` (Phase 5 complete)
> **Verified at audit time:** `:core-domain:test`, `:app:testDebugUnitTest`, `:app:lintDebug` — BUILD SUCCESSFUL, zero lint issues, no `TODO` markers anywhere in `*.kt`/`*.kts`.
> This plan is advisory. Per `AGENTS.md`, each work item below is executed **one phase at a time**, with `MASTER_PLAN.md` updated and builds/tests re-verified before stopping for review.

---

## 1. Audit Summary

The codebase is in genuinely good shape: the pure-JVM boundary holds, geometry math is well-factored, money math is cent-deterministic, tax allocation reconciles, and error/recovery states are thorough. The findings below are ranked by user impact, not by code cleanliness.

### A. Correctness / trust issues (affect quoted numbers or user data)

| # | Finding | Evidence | Impact |
|---|---------|----------|--------|
| A1 | **Overlapping cutouts inflate trim/consumables quantities.** `cutoutPerimeterMeters` sums each cutout's *full raw* perimeter, including overlapping regions and parts protruding outside the parent. Area subtraction is overlap-safe, but trim strips, J-bead, and painter's tape are billed on the inflated perimeter. | `PolygonGeometryEngine.kt` (`totalCutoutPerim2D = analyzedCutouts.sumOf { it.perimeter2DMeters }`) | Overbilling on client quotes when openings overlap or overshoot the boundary. |
| A2 | **`CutoutKind` is dead in estimation.** The plan promises trade-specific adjustments (baseboard deduction at doors, bullnose vs. casing). Doors currently *add* full-perimeter baseboard for flooring and full-perimeter tape for painting, and every cutout gets identical trim treatment regardless of kind. | `rg CutoutKind domain/estimation/` → no matches; `MaterialEstimationEngine.estimateFlooringConsumables` uses raw `outerPerimeterMeters` | Baseboard ordered across doorways; kind selector in the AR HUD has no financial effect. |
| A3 | **Tax UI silently clamps to 30%.** The domain accepts `taxRate ∈ [0,1]`, the repository accepts `[0,1]`, but the ViewModel coerces to `0.0..0.30` with no user feedback. Users in >30% VAT jurisdictions get a wrong quote without any warning. | `QuoteViewModel.setProjectTaxRate` (`rate.coerceIn(0.0, 0.30)`) | Silent wrong totals for high-tax regions; inconsistent contract across layers. |
| A4 | **No way to repair a corrupted saved surface.** `reconstructSurface` failures produce `loadError` lines; export is blocked until they are "fixed", but the only available action is delete — losing the measurement. There is no re-measure/replace path targeting an existing surface ID. | `RoomProjectRepository.reconstructSurface`, `QuoteViewModel.exportPdf` guard | A single bad row can hold a whole quote hostage with data loss as the only exit. |

### B. Product gaps (claimed or implied, not delivered)

| # | Finding | Evidence | Impact |
|---|---------|----------|--------|
| B1 | **"Manual quote entry on non-AR devices" does not exist.** README and Phase 5 exit criterion 6 claim a manual path; on unsupported devices the user can only manage catalog/tax and go back. There is no typed-dimensions surface entry (e.g., wall 3.2 m × 2.4 m + door 0.9 m × 2.1 m). `addSurface` fully supports synthetic vertices — only the UI is missing. | `QuoteScreen.kt` (no manual-entry action), README feature list | App is nearly useless on non-ARCore devices despite the claim; also blocks quick desk estimates on AR devices. |
| B2 | **Single hardcoded project.** `ensureDefaultProject` always returns the first project; DAOs already have `observeAll()`/`delete()`. Contractors cannot keep separate clients/jobs. | `RoomProjectRepository.ensureDefaultProject`, `ProjectDao` | Every job mixes into one quote; a core workflow limitation for the target user. |
| B3 | **Currency hardcoded to USD.** `PdfQuoteFormat.money` uses `"$%,.2f"` and UI totals follow. No currency setting on project or PDF. | `PdfQuoteFormat.kt:38` | Non-US contractors (incl. this machine's locale) get wrongly branded quotes. |
| B4 | **No project/surface editing after save.** Vertices can't be adjusted post-save (only delete + re-measure); no cutout add/remove in the quote workspace; no imperial display toggle in the quote UI even though `UnitConverter` and dual-unit PDF labels exist. | `QuoteAction` surface actions limited to material/waste/labor/rename/delete | Small AR aiming errors force full re-measurement. |

### C. Robustness / engineering hygiene

| # | Finding | Evidence | Impact |
|---|---------|----------|--------|
| C1 | **No CI.** Verification is entirely local/manual; the JDK-21 requirement makes drive-by contributions fail confusingly. | no `.github/workflows` | Regressions can land silently; the green-build claim is only as fresh as the last manual run. |
| C2 | **`AppContainer.initialize()` races the ViewModel bootstrap.** Both seed the catalog and default project concurrently on startup. `ensureDefaultProject` is check-then-insert (benign with fixed ID + REPLACE, but timestamps can flip); double seeding is redundant work. | `EstimatorApplication.kt`, `QuoteViewModel.bootstrap` | Minor; possible flicker of `updatedAt` ordering; wasted startup IO. |
| C3 | **`applyOverrides` can throw outside the guarded block.** `WastePreset.valueOf(wastePresetOverride)` is called before `runCatching` in `reconstructSurface`; an unknown stored preset name kills the whole workspace Flow instead of degrading to one `loadError` line. | `RoomProjectRepository.kt` (`applyOverrides` call site) | One corrupt row breaks the entire quote screen rather than one surface. |
| C4 | **Coplanarity distance gate is asymmetric across layers.** Live reticle gate = 6 cm, but saved-cutout reconstruction gate = 10 cm default; a surface saved via AR can still fail reconstruction later if tolerance configs drift. Also the locked-normal substitution means the normal gate can never reject a depth hit once locked (intentional, but undocumented in the validator contract). | `ReticleHitTester` (0.06) vs `PolygonGeometryEngine` default (0.10) | Latent inconsistency; worth one shared config constant. |
| C5 | **Per-frame allocation churn in AR hot path.** `onSessionUpdated` builds new `ReticleSample` + full `ArMeasureUiState` copies at 30–60 fps, `renderSnapshot` `remember` keys include `reticleSample`, and Compose recomposes the entire HUD per frame. Fine on flagships; risky on the mid-range devices contractors actually carry. | `ArMeasureScreen.kt`, `ArMeasureViewModel.updateReticle` | Battery/thermal cost during long scans; possible dropped frames. |
| C6 | **Ear-clipping is O(n³) with per-ear quality scoring.** Fine for typical ≤20-vertex rooms; a pathological 100+ vertex trace would stall placement. Also `hasSelfIntersection` is O(n²) and runs on every preview vertex. | `PolygonClipping2D.triangulateSimplePolygon` | Low practical risk today; guard with a vertex-count cap + message instead of optimizing prematurely. |
| C7 | **Test coverage gaps on the riskiest seams.** No tests for: overlapping-cutout perimeter behavior (A1), tax clamp boundary (A3), `loadError` workspace degradation (C3), `MeasurementStateCodec` round-trip through process death, `allocateTax` remainder distribution edge cases (0 surfaces, 1-cent remainder ties). 60 JVM tests exist but miss these. | test inventory | The bugs above are exactly the kind a regression suite would pin. |
| C8 | **Missing LICENSE + minor repo hygiene.** README flags no license; `.DS_Store` tracked-adjacent; `local.properties` present (gitignored — verify). | `README.md`, repo root | Blocks any public distribution. |

### D. Deliberate non-issues (checked, no action)

- Pure JVM boundary: no `android.*` imports in `:core-domain` — holds.
- Money math: `BigDecimal` + `HALF_UP` everywhere; tax allocation floors + largest-remainder distributes exactly; surface totals reconcile.
- PDF export: revisioned collision-safe filenames, bounded pruning (12), read-only FileProvider, backup exclusions — all present.
- Room: schema v2 exported, `MIGRATION_1_2` matches, FK `RESTRICT` on in-use materials with UI-level guard.
- AR lifecycle: session generation restart, permission/install/readiness states, depth fallback, `SavedStateHandle` drafts — implemented and tested.
- Version pins (`arsceneview 2.3.0`, `arcore 1.48.0`, `room 2.7.2`, AGP 8.13.0): per `MASTER_PLAN.md` these are locked; **do not bump** as part of this plan.

---

## 2. Proposed Phases (execute one at a time, stop for review)

### Phase 6 — Quote-Correctness Fixes (A1–A3, C3) · ✅ COMPLETE (2026-11-19)
1. [x] **A1:** `effectiveCutoutPerimeterMeters` computed by a global boundary-clipping pass (parent + sibling crossings, strictly-inside drop, shared-edge dedup). Raw perimeter kept for diagnostics. Tests: overlapping windows, protruding window under CLIP_TO_PARENT, flush door, disjoint openings.
2. [x] **A2:** `doorOpeningWidthMeters` (floor-flush `CutoutKind.DOOR` thresholds only) deducts doorways from flooring baseboard; trim/J-bead/tape consume the effective perimeter. Rules documented in `MASTER_PLAN.md` §3.7. Per-kind trim opt-out deferred to owner decision #3.
3. [x] **A3:** Silent 30% clamp removed; explicit 0–100% validation with error message; slider widened. Tests for 40% acceptance and 150% rejection.
4. [x] **C3:** Overrides parsed defensively; unknown preset/out-of-range factor/negative labor degrade to material defaults with per-surface `loadErrors`. Corruption test added.
- **Exit met:** `:core-domain:test` (31 tests), `:app:testDebugUnitTest` (61 tests), `:app:lintDebug` green.

### Phase 7 — Manual Entry & Surface Repair (B1, A4) · ✅ COMPLETE (2026-11-19)
1. [x] `ManualSurfaceBuilder` (pure JVM) + manual dialog: width × height/depth in m or ft, wall/floor orientation, rectangular openings with kind and offsets; flows through the same `addSurface` pipeline.
2. [x] `replaceSurfaceMeasurement` keeps ID/pricing/order; "Fix dimensions"/"Re-enter dimensions" (manual) and "Re-measure in AR" (nav arg `measure?surfaceId=`) both replace in place.
3. [x] README and MASTER_PLAN updated to describe the delivered manual path.
- **Exit met:** manual entry → estimate verified in ViewModel tests (metric + imperial + replacement + validation); `:core-domain:test` (35), `:app:testDebugUnitTest` (67), `:app:lintDebug` green.

### Phase 8 — Multi-Project & Currency (B2, B3) · ✅ COMPLETE (2026-11-19)
1. [x] Project switcher dialog (create/switch/delete with confirmation, last-project guard); rename via existing project details editor; `default-project` bootstrap kept.
2. [x] Per-project `currencyCode` (Room v3 + `MIGRATION_2_3` + Robolectric migration test); all UI and PDF money renders the project currency; ISO 4217 validation.
- **Exit met:** migration test green, quote numbers/filenames already project-scoped, `:core-domain:test` (35), `:app:testDebugUnitTest` (73), `:app:lintDebug` green.

### Phase 9 — Polish & Hardening (B4, C1, C2, C4–C8)
1. CI workflow (GitHub Actions): JDK 21, `:core-domain:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`.
2. Single startup bootstrap owner (drop `AppContainer.initialize` duplication or make ViewModel await it).
3. Shared coplanarity config constant; document locked-normal behavior.
4. AR hot-path allocation trim (reuse snapshot when reticle unchanged beyond epsilon) + vertex-count guard with a friendly message.
5. Post-save vertex nudging / cutout editing in quote workspace; imperial display toggle.
6. Add LICENSE (owner decision needed), remove `.DS_Store`, confirm `local.properties` ignored.
7. Close remaining C7 test gaps (`MeasurementStateCodec` round-trip, `allocateTax` edges).
- **Exit:** full verification suite from `MASTER_PLAN.md` §7A green.

### Sequencing rationale
Phase 6 first because it changes *money on client invoices* with the smallest diff. Phase 7 next because it unlocks the largest user population (non-AR devices) and removes the data-loss trap. Phases 8–9 are additive and safe to defer.

---

## 3. Open Decisions (need owner input before the relevant phase)

1. **License choice** (C8) — MIT/Apache-2.0/proprietary?
2. **Currency scope** (B3) — single per-project currency vs. per-line? (Recommend per-project.)
3. **Trim deduction rules** (A2) — confirm trade practice: deduct door width from baseboard? Tape around windows only, or windows + doors?
4. **Multi-project UX** (B2) — is a project switcher sufficient, or is client-level grouping wanted?
