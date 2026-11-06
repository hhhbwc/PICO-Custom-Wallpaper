package com.picoxr.librarywallpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.picoxr.librarywallpaper.core.WorkspaceMetrics;

/**
 * Hosts the fixed 1280×720 logical editor canvas inside whatever window size the headset
 * provides, scaling and centering it; touch events are mapped back through the inverse
 * matrix so the workspace feels native at any resolution.
 */
final class LogicalWorkspaceLayout extends ViewGroup {
    private static final int LOGICAL_WIDTH = 1280;
    private static final int LOGICAL_HEIGHT = 720;

    private WorkspaceMetrics metrics = WorkspaceMetrics.fit(LOGICAL_WIDTH, LOGICAL_HEIGHT);

    LogicalWorkspaceLayout(Context context) {
        super(context);
        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        metrics = WorkspaceMetrics.fit(width, height);
        int logicalWidth = MeasureSpec.makeMeasureSpec(LOGICAL_WIDTH, MeasureSpec.EXACTLY);
        int logicalHeight = MeasureSpec.makeMeasureSpec(LOGICAL_HEIGHT, MeasureSpec.EXACTLY);
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).measure(logicalWidth, logicalHeight);
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int index = 0; index < getChildCount(); index++) {
            getChildAt(index).layout(0, 0, LOGICAL_WIDTH, LOGICAL_HEIGHT);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        int save = canvas.save();
        canvas.translate(metrics.getLeft(), metrics.getTop());
        canvas.scale(metrics.getScale(), metrics.getScale());
        super.dispatchDraw(canvas);
        canvas.restoreToCount(save);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        MotionEvent logicalEvent = MotionEvent.obtain(event);
        logicalEvent.offsetLocation(-metrics.getLeft(), -metrics.getTop());
        logicalEvent.transform(inverseMatrix());
        boolean handled = super.dispatchTouchEvent(logicalEvent);
        logicalEvent.recycle();
        return handled;
    }

    private Matrix inverseMatrix() {
        Matrix matrix = new Matrix();
        matrix.setScale(1.0f / metrics.getScale(), 1.0f / metrics.getScale());
        return matrix;
    }
}
