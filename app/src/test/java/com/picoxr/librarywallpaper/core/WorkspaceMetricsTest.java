package com.picoxr.librarywallpaper.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WorkspaceMetricsTest {
    private static final float DELTA = 0.001f;

    @Test
    public void usesIdentityScaleForLogicalCanvasSize() {
        WorkspaceMetrics metrics = WorkspaceMetrics.fit(1280, 720);
        assertEquals(1f, metrics.getScale(), DELTA);
        assertEquals(0f, metrics.getLeft(), DELTA);
        assertEquals(0f, metrics.getTop(), DELTA);
    }

    @Test
    public void doublesLogicalCanvasForPicoEditorDisplay() {
        WorkspaceMetrics metrics = WorkspaceMetrics.fit(2560, 1440);
        assertEquals(2f, metrics.getScale(), DELTA);
        assertEquals(0f, metrics.getLeft(), DELTA);
        assertEquals(0f, metrics.getTop(), DELTA);
    }

    @Test
    public void centersOnNonMatchingAspectRatios() {
        WorkspaceMetrics metrics = WorkspaceMetrics.fit(1280, 900);
        assertEquals(1f, metrics.getScale(), DELTA);
        assertEquals(0f, metrics.getLeft(), DELTA);
        assertEquals((900f - 720f) / 2f, metrics.getTop(), DELTA);
    }
}
