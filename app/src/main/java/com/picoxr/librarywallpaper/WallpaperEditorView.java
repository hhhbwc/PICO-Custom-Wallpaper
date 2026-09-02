package com.picoxr.librarywallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

final class WallpaperEditorView extends View {
    interface Listener {
        void onTransformChanged(WallpaperTransform transform);
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private Bitmap bitmap;
    private WallpaperTarget target = WallpaperTarget.LIBRARY;
    private WallpaperTransform transform = WallpaperTransform.centered();
    private Listener listener;
    private float lastX;
    private float lastY;
    private int lastPointerCount;

    WallpaperEditorView(Context context) {
        super(context);
        setBackgroundColor(Color.rgb(19, 23, 28));
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(2f);
        framePaint.setColor(Color.rgb(104, 181, 255));
        dimPaint.setColor(Color.argb(140, 0, 0, 0));
        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                transform = transform.scaled(detector.getScaleFactor());
                notifyChanged();
                invalidate();
                return true;
            }
        });
    }

    void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
        invalidate();
    }

    void setTarget(WallpaperTarget target, WallpaperTransform transform) {
        this.target = target;
        this.transform = transform;
        invalidate();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        RectF frame = frame();
        canvas.drawColor(Color.rgb(19, 23, 28));
        if (bitmap == null) {
            return;
        }
        WallpaperTransform.RenderValues values = transform.render(bitmap.getWidth(), bitmap.getHeight(),
                Math.round(frame.width()), Math.round(frame.height()));
        canvas.save();
        canvas.translate(frame.left + values.translateX, frame.top + values.translateY);
        canvas.scale(values.scale, values.scale);
        canvas.drawBitmap(bitmap, 0f, 0f, paint);
        canvas.restore();
        canvas.drawRect(0f, 0f, getWidth(), frame.top, dimPaint);
        canvas.drawRect(0f, frame.bottom, getWidth(), getHeight(), dimPaint);
        canvas.drawRect(0f, frame.top, frame.left, frame.bottom, dimPaint);
        canvas.drawRect(frame.right, frame.top, getWidth(), frame.bottom, dimPaint);
        canvas.drawRect(frame, framePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (bitmap == null) {
            return false;
        }
        scaleDetector.onTouchEvent(event);
        boolean pinchEnded = lastPointerCount > 1 && event.getPointerCount() == 1;
        lastPointerCount = event.getPointerCount();
        if (event.getPointerCount() > 1) {
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = event.getX();
                lastY = event.getY();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (pinchEnded) {
                    // 双指结束后的第一个 MOVE 只重新锚定,避免残留位移导致画面跳变
                    lastX = event.getX();
                    lastY = event.getY();
                    return true;
                }
                float dx = event.getX() - lastX;
                float dy = event.getY() - lastY;
                RectF frame = frame();
                transform = transform.moved(dx, dy, bitmap.getWidth(), bitmap.getHeight(),
                        Math.round(frame.width()), Math.round(frame.height()));
                lastX = event.getX();
                lastY = event.getY();
                notifyChanged();
                invalidate();
                return true;
            default:
                return true;
        }
    }

    private RectF frame() {
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        float ratio = (float) target.previewWidth / target.previewHeight;
        float frameWidth = Math.min(width, height * ratio);
        float frameHeight = frameWidth / ratio;
        float left = getPaddingLeft() + (width - frameWidth) / 2f;
        float top = getPaddingTop() + (height - frameHeight) / 2f;
        return new RectF(left, top, left + frameWidth, top + frameHeight);
    }

    private void notifyChanged() {
        if (listener != null) {
            listener.onTransformChanged(transform);
        }
    }
}
