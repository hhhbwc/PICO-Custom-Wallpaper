package com.picoxr.librarywallpaper;

final class WorkspaceMetrics {
    final float scale;
    final float left;
    final float top;

    private WorkspaceMetrics(float scale, float left, float top) {
        this.scale = scale;
        this.left = left;
        this.top = top;
    }

    static WorkspaceMetrics fit(int availableWidth, int availableHeight) {
        float scale = Math.min((float) availableWidth / WallpaperState.EDITOR_WIDTH,
                (float) availableHeight / WallpaperState.EDITOR_HEIGHT);
        float left = (availableWidth - WallpaperState.EDITOR_WIDTH * scale) / 2f;
        float top = (availableHeight - WallpaperState.EDITOR_HEIGHT * scale) / 2f;
        return new WorkspaceMetrics(scale, left, top);
    }

    float toLogicalDistance(float physicalDistance) {
        return physicalDistance / scale;
    }
}
