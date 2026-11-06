package com.picoxr.librarywallpaper.core;

/** Shared constants and pure helpers for the wallpaper pipeline. */
public final class WallpaperState {
    /** Editor canvas the user composes on. */
    public static final int EDITOR_WIDTH = 1280;
    public static final int EDITOR_HEIGHT = 720;

    /** Hard cap for the long side of any decoded wallpaper, on the save path and the hook read path. */
    public static final int MAX_DIMENSION = 2048;

    private WallpaperState() {
    }

    public static String fileName(WallpaperTarget target) {
        return "wallpaper_" + target.getKey() + ".png";
    }

    /**
     * Smallest power-of-two {@code inSampleSize} so the sampled long side fits into
     * {@code maxDimension}. Overflow-safe: terminates for any non-negative input.
     */
    public static int sampleSizeFor(int width, int height, int maxDimension) {
        int sampleSize = 1;
        while (Math.max(width / sampleSize, height / sampleSize) > maxDimension) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
