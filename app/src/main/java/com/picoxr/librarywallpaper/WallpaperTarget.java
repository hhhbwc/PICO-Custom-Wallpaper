package com.picoxr.librarywallpaper;

enum WallpaperTarget {
    LIBRARY("library", 825, 750),
    SETTINGS("settings", 825, 750);

    final String key;
    final int previewWidth;
    final int previewHeight;

    WallpaperTarget(String key, int previewWidth, int previewHeight) {
        this.key = key;
        this.previewWidth = previewWidth;
        this.previewHeight = previewHeight;
    }

    static WallpaperTarget fromKey(String key) {
        for (WallpaperTarget target : values()) {
            if (target.key.equals(key)) {
                return target;
            }
        }
        return null;
    }
}
