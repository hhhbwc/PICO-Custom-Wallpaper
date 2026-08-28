-keep class com.picoxr.librarywallpaper.LibraryWallpaperHook implements de.robv.android.xposed.IXposedHookLoadPackage {
    <init>();
    void handleLoadPackage(de.robv.android.xposed.callbacks.XC_LoadPackage$LoadPackageParam);
}
-dontwarn de.robv.android.xposed.**
