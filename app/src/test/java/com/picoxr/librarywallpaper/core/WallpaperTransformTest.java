package com.picoxr.librarywallpaper.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WallpaperTransformTest {
    private static final float DELTA = 0.001f;

    @Test
    public void centerCropFillsFullWindowArea() {
        WallpaperTransform.RenderValues values = WallpaperTransform.centered()
                .render(1920, 1080, 1125, 750);
        assertEquals(750f / 1080f, values.scale, DELTA);
        assertEquals(-104.167f, values.translateX, DELTA);
        assertEquals(0f, values.translateY, DELTA);
    }

    @Test
    public void sharedTransformUsesOneContinuousPaneSeam() {
        WallpaperTransform.RenderValues values = WallpaperTransform.centered()
                .render(1920, 1080, 1125, 750);
        float sourceAtLeftPaneRightEdge = (300f - values.translateX) / values.scale;
        float sourceAtRightPaneLeftEdge = (0f + 300f - values.translateX) / values.scale;
        assertEquals(sourceAtLeftPaneRightEdge, sourceAtRightPaneLeftEdge, DELTA);
    }

    @Test
    public void scaleAndOffsetsAreClamped() {
        WallpaperTransform transform = new WallpaperTransform(20f, -3f, 3f);
        assertEquals(WallpaperTransform.MAX_USER_SCALE, transform.getScale(), DELTA);
        assertEquals(-1f, transform.getOffsetX(), DELTA);
        assertEquals(1f, transform.getOffsetY(), DELTA);
    }

    @Test
    public void degenerateSizesFallBackToIdentity() {
        WallpaperTransform.RenderValues values = WallpaperTransform.centered()
                .render(0, 100, 100, 100);
        assertEquals(1f, values.scale, DELTA);
        assertEquals(0f, values.translateX, DELTA);
        assertEquals(0f, values.translateY, DELTA);
    }

    /** Regression: a NaN from a corrupted preference must never reach the canvas. */
    @Test
    public void nanScaleDegradesToMinimumInsteadOfPoisoning() {
        WallpaperTransform transform = new WallpaperTransform(Float.NaN, Float.NaN, Float.NaN);
        assertEquals(WallpaperTransform.MIN_USER_SCALE, transform.getScale(), DELTA);
        assertEquals(-1f, transform.getOffsetX(), DELTA);
        assertEquals(-1f, transform.getOffsetY(), DELTA);
        WallpaperTransform.RenderValues values = transform.render(2048, 2048, 1125, 750);
        assertTrue(Float.isFinite(values.scale));
        assertTrue(Float.isFinite(values.translateX));
        assertTrue(Float.isFinite(values.translateY));
    }

    @Test
    public void infiniteScaleClampsToMaximum() {
        WallpaperTransform transform = new WallpaperTransform(Float.POSITIVE_INFINITY, 0f, 0f);
        assertEquals(WallpaperTransform.MAX_USER_SCALE, transform.getScale(), DELTA);
    }
}
