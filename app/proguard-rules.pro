# Add project specific ProGuard rules here.

# Keep Moshi adapters
-keepclassmembers class * {
    @com.squareup.moshi.FromJson *;
    @com.squareup.moshi.ToJson *;
}

# Keep Room entities
-keep class com.samcod3.meditrack.data.local.entity.** { *; }

# Keep Retrofit interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
