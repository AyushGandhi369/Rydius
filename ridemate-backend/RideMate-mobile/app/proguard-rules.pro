# Keep JavaScript interface methods and WebView related classes from obfuscation.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
