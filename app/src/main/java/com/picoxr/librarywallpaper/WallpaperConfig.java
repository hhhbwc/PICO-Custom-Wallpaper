package com.picoxr.librarywallpaper;

final class WallpaperConfig {
    static final int VERSION = 1;

    final boolean enabled;
    final WallpaperTransform transform;

    WallpaperConfig(boolean enabled, WallpaperTransform transform) {
        this.enabled = enabled;
        this.transform = transform;
    }

    static WallpaperConfig disabled() {
        return new WallpaperConfig(false, WallpaperTransform.centered());
    }
}
