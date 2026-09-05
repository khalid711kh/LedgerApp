# Keep JS interface (none currently used) and WebView classes
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
