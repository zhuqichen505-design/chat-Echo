# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Retrofit 在 release/R8 后仍需要读取方法返回值的泛型和注解。
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

-keep interface com.aiassistant.data.remote.** { *; }

# Keep Gson serialized classes
-keep class com.aiassistant.domain.model.** { *; }
-keepclassmembers class com.aiassistant.domain.model.** { *; }
-keep class com.aiassistant.utils.BackupManager$* { *; }
-keepclassmembers class com.aiassistant.utils.BackupManager$* { *; }
-keep class com.aiassistant.utils.TavilySearchManager$* { *; }
-keepclassmembers class com.aiassistant.utils.TavilySearchManager$* { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep @androidx.annotation.Keep class * { *; }
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep Room entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
