# MetaPurge ProGuard Rules

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep Compose
-dontwarn androidx.compose.**

# Keep ExifInterface
-keep class androidx.exifinterface.** { *; }

# Keep Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep data classes
-keep class com.metapurge.app.domain.model.** { *; }

# Remove unused Kotlin metadata
-keepattributes *Annotation*
-keepattributes Signature

# Optimize
-optimizationpasses 5
-dontusemixedcaseclassnames
-verbose
