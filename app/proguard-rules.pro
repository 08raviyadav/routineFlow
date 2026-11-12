# RoutineFlow ProGuard rules (release builds keep minification off in v1,
# these rules are here so enabling it later does not break Room / serialization).
-keep class com.thor.routineflow.model.** { *; }
-keepclassmembers class com.thor.routineflow.model.** { *; }
-keepattributes *Annotation*, Signature
-dontwarn kotlinx.serialization.**
