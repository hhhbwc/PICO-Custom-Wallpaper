package com.picoxr.librarywallpaper;

final class WallpaperState {
    static final int MAX_DIMENSION = 2048;
    static final int EDITOR_WIDTH = 1280;
    static final int EDITOR_HEIGHT = 720;

    private WallpaperState() {
    }

    static String fileName(WallpaperTarget target) {
        return "wallpaper_" + target.key + ".png";
    }

    static int sampleSizeFor(int width, int height, int maxDimension) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > maxDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
