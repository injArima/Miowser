# proguard-android-optimize.txt already preserves @JavascriptInterface methods.
# Keep this file narrow: bridge and local-player journeys exercise the R8 APK.
# Add a rule only for a demonstrated reflection/native integration requirement.

# JNI exports this exact class/method name from libmiowser_adblock.so.
-keep class com.miowser.app.adblock.NativeDomainLookup { native <methods>; }
