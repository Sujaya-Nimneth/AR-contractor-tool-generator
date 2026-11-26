# ARCore & Filament / SceneView JNI keep rules
-keep class com.google.ar.core.** { *; }
-keep class com.google.android.filament.** { *; }

# Room, Compose and kotlinx.serialization ship their own consumer rules. The rules below cover
# names that are persisted *by name* (Room columns, SavedStateHandle JSON) and therefore must
# never be obfuscated, otherwise saved quotes stop deserializing after an app update.

# Enums persisted through Enum.name / Enum.valueOf(...)
-keepclassmembers enum com.arcontractor.estimator.domain.geometry.CutoutKind { *; }
-keepclassmembers enum com.arcontractor.estimator.domain.estimation.TradeType { *; }
-keepclassmembers enum com.arcontractor.estimator.domain.estimation.PackageUnitType { *; }
-keepclassmembers enum com.arcontractor.estimator.domain.estimation.PricingAreaUnit { *; }
-keepclassmembers enum com.arcontractor.estimator.domain.estimation.WastePreset { *; }
-keepclassmembers enum com.arcontractor.estimator.domain.estimation.TaxPolicy { *; }
-keepclassmembers enum com.arcontractor.estimator.ui.ar.ArMeasurePhase { *; }
-keepclassmembers enum com.arcontractor.estimator.spatial.tracking.ArRecoveryAction { *; }

# kotlinx.serialization DTOs restored through SavedStateHandle
-keep class com.arcontractor.estimator.ui.ar.PersistedMeasurementDraft { *; }
-keep class com.arcontractor.estimator.ui.ar.PersistedVec3 { *; }
-keep class com.arcontractor.estimator.ui.ar.PersistedCutout { *; }

# kotlinx.serialization DTOs persisted in Room (trade parameter + vertex JSON)
-keep class com.arcontractor.estimator.data.local.VertexDto { *; }
-keep class com.arcontractor.estimator.data.local.TradeParametersCodec { *; }
-keep class com.arcontractor.estimator.data.local.TradeParametersCodec$* { *; }
-keepclassmembers class com.arcontractor.estimator.data.local.TradeParametersCodec$* {
    <fields>;
    <init>(...);
}
