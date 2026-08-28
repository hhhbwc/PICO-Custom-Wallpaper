package com.picoxr.librarywallpaper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WallpaperStateTest {
    @Test
    public void keepsImagesWithinConfiguredDimension() {
        assertEquals(1, WallpaperState.sampleSizeFor(2048, 1024, 2048));
        assertEquals(2, WallpaperState.sampleSizeFor(4096, 1024, 2048));
        assertEquals(4, WallpaperState.sampleSizeFor(8000, 6000, 2048));
        assertEquals("wallpaper_library.png", WallpaperState.fileName(WallpaperTarget.LIBRARY));
        assertEquals("wallpaper_settings.png", WallpaperState.fileName(WallpaperTarget.SETTINGS));
    }
}
