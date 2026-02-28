# MetaPurge ProGuard Rules

# Keep Compose
-dontwarn androidx.compose.**

# Keep ExifInterface
-keep class androidx.exifinterface.** { *; }

# Keep Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep data classes
-keep class com.metapurge.app.domain.model.** { *; }
