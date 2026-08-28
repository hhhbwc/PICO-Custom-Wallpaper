package com.picoxr.librarywallpaper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class WorkspaceMetricsTest {
    private static final float DELTA = 0.001f;

    @Test
    public void usesIdentityScaleForLogicalCanvasSize() {
        WorkspaceMetrics metrics = WorkspaceMetrics.fit(1280, 720);
        assertEquals(1f, metrics.scale, DELTA);
        assertEquals(0f, metrics.left, DELTA);
        assertEquals(0f, metrics.top, DELTA);
    }

    @Test
    public void doublesLogicalCanvasForPicoEditorDisplay() {
        WorkspaceMetrics metrics = WorkspaceMetrics.fit(2560, 1440);
        assertEquals(2f, metrics.scale, DELTA);
        assertEquals(0f, metrics.left, DELTA);
        assertEquals(0f, metrics.top, DELTA);
        assertEquals(30f, metrics.toLogicalDistance(60f), DELTA);
    }

    @Test
    public void letterboxesWithoutDistortingWorkspace() {
        WorkspaceMetrics metrics = WorkspaceMetrics.fit(1600, 720);
        assertEquals(1f, metrics.scale, DELTA);
        assertEquals(160f, metrics.left, DELTA);
        assertEquals(0f, metrics.top, DELTA);
    }
}
