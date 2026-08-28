package com.picoxr.librarywallpaper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WallpaperTransformTest {
    private static final float DELTA = 0.001f;

    @Test
    public void centerCropFillsLibraryContentArea() {
        WallpaperTransform.RenderValues values = WallpaperTransform.centered()
                .render(1920, 1080, 825, 750);
        assertEquals(750f / 1080f, values.scale, DELTA);
        assertEquals(-254.167f, values.translateX, DELTA);
        assertEquals(0f, values.translateY, DELTA);
    }

    @Test
    public void centerCropFillsSettingsContentArea() {
        WallpaperTransform.RenderValues values = WallpaperTransform.centered()
                .render(1920, 1080, 825, 750);
        assertEquals(750f / 1080f, values.scale, DELTA);
        assertEquals(-254.167f, values.translateX, DELTA);
    }

    @Test
    public void scaleAndOffsetsAreClamped() {
        WallpaperTransform transform = new WallpaperTransform(20f, -3f, 3f);
        assertEquals(6f, transform.scale, DELTA);
        assertEquals(-1f, transform.offsetX, DELTA);
        assertEquals(1f, transform.offsetY, DELTA);
    }

    @Test
    public void movementIsClampedToVisibleImageBounds() {
        WallpaperTransform transform = WallpaperTransform.centered()
                .scaled(2f)
                .moved(100000f, -100000f, 1920, 1080, 825, 750);
        assertEquals(1f, transform.offsetX, DELTA);
        assertEquals(-1f, transform.offsetY, DELTA);
    }
}
