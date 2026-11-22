# Protobuf
-shrinkunusedprotofields

# Commons-compress
-dontwarn com.github.luben.zstd.**

# WebView reflects into @JavascriptInterface methods.
-keepclassmembers class com.sukisu.nova.ui.component.background.DynamicWallpaperBridge {
    @android.webkit.JavascriptInterface <methods>;
}
