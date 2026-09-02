package com.picoxr.librarywallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

final class CenterCropDrawable extends Drawable {
    // 深色遮罩强度:保证浅色文字在任意壁纸区域可读,同时让全页文字颜色深浅一致
    static final float SCRIM_ALPHA = 0.30f;
    private final Bitmap bitmap;
    private final WallpaperTransform transform;
    private final int contentWidth;
    private final int contentHeight;
    private final int viewportX;
    private final int viewportY;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint();

    CenterCropDrawable(Bitmap bitmap, WallpaperTransform transform, int contentWidth, int contentHeight,
            int viewportX, int viewportY) {
        this.bitmap = bitmap;
        this.transform = transform;
        this.contentWidth = contentWidth;
        this.contentHeight = contentHeight;
        this.viewportX = viewportX;
        this.viewportY = viewportY;
        scrimPaint.setColor(0xFF000000);
        scrimPaint.setAlpha(Math.round(255 * SCRIM_ALPHA));
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        WallpaperTransform.RenderValues values = transform.render(bitmap.getWidth(), bitmap.getHeight(),
                contentWidth, contentHeight);
        canvas.save();
        canvas.clipRect(bounds);
        canvas.translate(bounds.left + values.translateX - viewportX,
                bounds.top + values.translateY - viewportY);
        canvas.scale(values.scale, values.scale);
        canvas.drawBitmap(bitmap, 0f, 0f, paint);
        canvas.restore();
        canvas.drawRect(bounds, scrimPaint);
    }

    boolean matches(Bitmap otherBitmap, WallpaperTransform other, int width, int height, int x, int y) {
        return bitmap == otherBitmap && transform.scale == other.scale && transform.offsetX == other.offsetX
                && transform.offsetY == other.offsetY && contentWidth == width && contentHeight == height
                && viewportX == x && viewportY == y;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
