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
# Gson 的匿名 TypeToken 子类依赖 getGenericSuperclass() 读取泛型签名。
# Gson 自带规则允许 shrinking，R8 会把匿名子类并入 TypeToken，运行时抛
# "Missing type parameter"（v2.8.38 release 点击进对话闪退根因）。
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
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
