package com.picoxr.librarywallpaper.core;

/** Uniform scale + centering for the fixed logical editor canvas inside a real window. */
public final class WorkspaceMetrics {
    private final float scale;
    private final float left;
    private final float top;

    private WorkspaceMetrics(float scale, float left, float top) {
        this.scale = scale;
        this.left = left;
        this.top = top;
    }

    public static WorkspaceMetrics fit(int availableWidth, int availableHeight) {
        float scale = Math.min(availableWidth / 1280.0f, availableHeight / 720.0f);
        float left = (availableWidth - 1280.0f * scale) / 2.0f;
        float top = (availableHeight - 720.0f * scale) / 2.0f;
        return new WorkspaceMetrics(scale, left, top);
    }

    public float getScale() {
        return scale;
    }

    public float getLeft() {
        return left;
    }

    public float getTop() {
        return top;
    }

    public float toLogicalDistance(float physicalDistance) {
        return physicalDistance / scale;
    }
}
