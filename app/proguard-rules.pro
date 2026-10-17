-keepattributes *Annotation*,InnerClasses,Signature,RuntimeVisibleAnnotations
-dontwarn kotlinx.serialization.**

# Kotlinx Serialization DTOs (TMDB, Stremio Manifests, AIOStreams, TorBox API)
-keepclassmembers class **$serializer { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep @kotlinx.serialization.Serializable class com.argon.stream.** { *; }

# Retrofit & OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Room Database Entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Retrofit service interfaces: keep method signatures (generic envelopes + suspend functions)
-keep,allowobfuscation interface com.argon.stream.data.remote.**.*ApiService { *; }
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Exceptions,EnclosingMethod
