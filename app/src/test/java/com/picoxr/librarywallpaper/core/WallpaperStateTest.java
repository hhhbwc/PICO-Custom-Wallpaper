package com.picoxr.librarywallpaper.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WallpaperStateTest {
    @Test
    public void keepsSmallImagesUnsampled() {
        assertEquals(1, WallpaperState.sampleSizeFor(1125, 750, 2048));
        assertEquals(1, WallpaperState.sampleSizeFor(2048, 2048, 2048));
    }

    @Test
    public void halvesJustOversizedImages() {
        assertEquals(2, WallpaperState.sampleSizeFor(2049, 10, 2048));
        assertEquals(2, WallpaperState.sampleSizeFor(4096, 4096, 2048));
    }

    @Test
    public void handlesHugeHeadersWithoutOverflow() {
        assertEquals(64, WallpaperState.sampleSizeFor(100000, 100000, 2048));
    }

    @Test
    public void fileNameUsesTargetKey() {
        assertEquals("wallpaper_library.png",
                WallpaperState.fileName(WallpaperTarget.LIBRARY));
        assertEquals("wallpaper_settings.png",
                WallpaperState.fileName(WallpaperTarget.SETTINGS));
    }
}
