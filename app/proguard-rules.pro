# ─────────────────────────────────────────────────
# ProGuard / R8 Rules for XploitMusic Release Build
# ─────────────────────────────────────────────────

# ── Gson (used by Retrofit for JSON parsing) ──
# Keep all data classes used in API responses / DTOs
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.xploitmusic.app.data.remote.** { *; }
-keep class com.xploitmusic.app.data.dto.** { *; }

# ── Retrofit ──
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*

# ── OkHttp ──
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ── Room ──
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *
-keepclassmembers class com.xploitmusic.app.data.local.entity.** { *; }

# ── Hilt / Dagger ──
-dontwarn dagger.hilt.internal.aggregatedroot.codegen.**
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Media3 / ExoPlayer ──
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── Coil ──
-dontwarn coil.**

# ── Compose ──
-dontwarn androidx.compose.**

# ── Domain models (keep for reflection, serialization) ──
-keep class com.xploitmusic.app.domain.model.** { *; }

# ── App classes that use reflection ──
-keep class com.xploitmusic.app.XploitApp { *; }
-keep class com.xploitmusic.app.MainActivity { *; }

# ── Kotlin Coroutines ──
-dontwarn kotlinx.coroutines.**

# ── NewPipeExtractor JavaScript deciphering ──
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**

# ── General ──
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
