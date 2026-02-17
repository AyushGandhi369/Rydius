# Keep Socket.IO and OkHttp
-keep class io.socket.** { *; }
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep Retrofit models
-keepclassmembers class com.rydius.mobile.data.model.** { *; }

# Keep JavaScript interface methods
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# MapLibre
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
