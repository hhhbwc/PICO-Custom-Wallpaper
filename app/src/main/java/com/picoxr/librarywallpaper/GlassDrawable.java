package com.picoxr.librarywallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** 按钮毛玻璃背景:采样壁纸模糊缩略图中按钮正后方的区域,叠加高光与状态描边。 */
final class GlassDrawable extends Drawable {
    private static final int TINT = 0x22FFFFFF;
    private static final int TINT_PRESSED = 0x3CFFFFFF;
    private static final int BORDER = 0x66FFFFFF;
    private static final int BORDER_FOCUSED = 0xFF5AA9FF;

    private final Bitmap blurred;
    private final WallpaperTransform transform;
    private final int imageWidth;
    private final int imageHeight;
    private final int cardWidth;
    private final int cardHeight;
    private final float viewportX;
    private final float viewportY;
    private final float radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Rect src = new Rect();

    GlassDrawable(Bitmap blurred, WallpaperTransform transform, int imageWidth, int imageHeight,
            int cardWidth, int cardHeight, float viewportX, float viewportY, float radius) {
        this.blurred = blurred;
        this.transform = transform;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.cardWidth = cardWidth;
        this.cardHeight = cardHeight;
        this.viewportX = viewportX;
        this.viewportY = viewportY;
        this.radius = radius;
    }

    boolean matches(Bitmap otherBlurred, WallpaperTransform other, int width, int height,
            float x, float y) {
        return blurred == otherBlurred && transform.scale == other.scale
                && transform.offsetX == other.offsetX && transform.offsetY == other.offsetY
                && cardWidth == width && cardHeight == height
                && viewportX == x && viewportY == y;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (blurred == null || blurred.isRecycled() || bounds.isEmpty() || imageWidth <= 0) {
            return;
        }
        WallpaperTransform.RenderValues values = transform.render(imageWidth, imageHeight,
                cardWidth, cardHeight);
        float ratio = (float) blurred.getWidth() / imageWidth;
        rect.set(bounds);
        path.reset();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(path);
        int[] state = getState();
        int left = clamp((viewportX - values.translateX) / values.scale * ratio,
                0, blurred.getWidth());
        int top = clamp((viewportY - values.translateY) / values.scale * ratio,
                0, blurred.getHeight());
        int right = clamp((viewportX + bounds.width() - values.translateX) / values.scale * ratio,
                0, blurred.getWidth());
        int bottom = clamp((viewportY + bounds.height() - values.translateY) / values.scale * ratio,
                0, blurred.getHeight());
        src.set(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
        canvas.drawBitmap(blurred, src, rect, paint);
        overlay.setStyle(Paint.Style.FILL);
        overlay.setColor(hasState(state, android.R.attr.state_pressed) ? TINT_PRESSED : TINT);
        canvas.drawRoundRect(rect, radius, radius, overlay);
        overlay.setStyle(Paint.Style.STROKE);
        overlay.setStrokeWidth(Math.max(1.5f, radius * 0.08f));
        overlay.setColor(hasState(state, android.R.attr.state_focused) ? BORDER_FOCUSED : BORDER);
        float inset = overlay.getStrokeWidth() / 2f;
        rect.inset(inset, inset);
        canvas.drawRoundRect(rect, Math.max(0f, radius - inset), Math.max(0f, radius - inset),
                overlay);
        canvas.restore();
    }

    private static int clamp(float value, int min, int max) {
        return (int) Math.max(min, Math.min(max - 1, value));
    }

    private static boolean hasState(int[] state, int attr) {
        for (int item : state) {
            if (item == attr) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        invalidateSelf();
        return true;
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
