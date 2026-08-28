package com.picoxr.librarywallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

final class LogicalWorkspaceLayout extends ViewGroup {
    private WorkspaceMetrics metrics = WorkspaceMetrics.fit(
            WallpaperState.EDITOR_WIDTH, WallpaperState.EDITOR_HEIGHT);

    LogicalWorkspaceLayout(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        metrics = WorkspaceMetrics.fit(width, height);
        int logicalWidth = MeasureSpec.makeMeasureSpec(WallpaperState.EDITOR_WIDTH, MeasureSpec.EXACTLY);
        int logicalHeight = MeasureSpec.makeMeasureSpec(WallpaperState.EDITOR_HEIGHT, MeasureSpec.EXACTLY);
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).measure(logicalWidth, logicalHeight);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).layout(0, 0, WallpaperState.EDITOR_WIDTH, WallpaperState.EDITOR_HEIGHT);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(metrics.left, metrics.top);
        canvas.scale(metrics.scale, metrics.scale);
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        android.view.MotionEvent logicalEvent = android.view.MotionEvent.obtain(event);
        logicalEvent.offsetLocation(-metrics.left, -metrics.top);
        logicalEvent.transform(inverseMatrix());
        boolean handled = super.dispatchTouchEvent(logicalEvent);
        logicalEvent.recycle();
        return handled;
    }

    private android.graphics.Matrix inverseMatrix() {
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.setScale(1f / metrics.scale, 1f / metrics.scale);
        return matrix;
    }

    WorkspaceMetrics metrics() {
        return metrics;
    }
}
