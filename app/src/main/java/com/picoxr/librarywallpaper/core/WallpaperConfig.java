package com.picoxr.librarywallpaper.core;

/** Enabled flag plus the placement transform, as stored and served to the hooks. */
public final class WallpaperConfig {
    public static final int VERSION = 1;

    private final boolean enabled;
    private final WallpaperTransform transform;

    public WallpaperConfig(boolean enabled, WallpaperTransform transform) {
        this.enabled = enabled;
        this.transform = transform == null ? WallpaperTransform.centered() : transform;
    }

    public static WallpaperConfig disabled() {
        return new WallpaperConfig(false, WallpaperTransform.centered());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public WallpaperTransform getTransform() {
        return transform;
    }
}
