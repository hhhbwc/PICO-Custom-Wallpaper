package com.picoxr.librarywallpaper.core;

/** One of the PICO windows that can carry a custom wallpaper. */
public enum WallpaperTarget {
    LIBRARY("library", 1125, 750),
    SETTINGS("settings", 1125, 750);

    private final String key;
    private final int previewWidth;
    private final int previewHeight;

    WallpaperTarget(String key, int previewWidth, int previewHeight) {
        this.key = key;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    public String getKey() {
        return key;
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public static WallpaperTarget fromKey(String key) {
        for (WallpaperTarget target : values()) {
            if (target.key.equals(key)) {
                return target;
            }
        }
        return null;
    }
}
