# ARCore & Filament / SceneView JNI keep rules
-keep class com.google.ar.core.** { *; }
-keep class com.google.android.filament.** { *; }

# kotlinx.serialization DTOs restored through SavedStateHandle
-keep class com.arcontractor.estimator.ui.ar.PersistedMeasurementDraft { *; }
-keep class com.arcontractor.estimator.ui.ar.PersistedVec3 { *; }
-keep class com.arcontractor.estimator.ui.ar.PersistedCutout { *; }
-keep class com.arcontractor.estimator.domain.geometry.CutoutKind { *; }
