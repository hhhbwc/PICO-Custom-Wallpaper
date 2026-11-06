package com.picoxr.librarywallpaper.draw;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.picoxr.librarywallpaper.core.CornerRadius;
import com.picoxr.librarywallpaper.core.WallpaperTransform;

/**
 * Draws the wallpaper bitmap cover-fit into a pane, clipped to the pane's real corner
 * shape, then dims it slightly so PICO's light text keeps its contrast.
 *
 * <p>PICO's window panels are rounded on their outer corners only (left rail vs right
 * content), so the drawable takes explicit per-corner radii captured from the drawable
 * it replaces; drawing a plain square rect visibly breaks the panel silhouette.</p>
 */
public final class RoundedWallpaperDrawable extends Drawable {
    /** Same scrim alpha PICO-style panels read well with (30% black). */
    static final float SCRIM_ALPHA = 0.3f;

    private final Bitmap bitmap;
    private final WallpaperTransform transform;
    private final CornerRadius radius;
    private final int contentWidth;
    private final int contentHeight;
    private final int viewportX;
    private final int viewportY;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final Paint scrimPaint = new Paint();
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();

    public RoundedWallpaperDrawable(Bitmap bitmap, WallpaperTransform transform, CornerRadius radius,
                                    int contentWidth, int contentHeight, int viewportX, int viewportY) {
        this.bitmap = bitmap;
        this.transform = transform;
        this.radius = radius == null ? CornerRadius.SQUARE : radius;
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
        if (bounds.isEmpty() || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        WallpaperTransform.RenderValues values = transform.render(
                bitmap.getWidth(), bitmap.getHeight(), contentWidth, contentHeight);
        canvas.save();
        if (!radius.isSquare()) {
            rect.set(bounds);
            clipPath.reset();
            clipPath.addRoundRect(rect, radius.toRadiiArray(), Path.Direction.CW);
            canvas.clipPath(clipPath);
        } else {
            canvas.clipRect(bounds);
        }
        canvas.translate((bounds.left + values.translateX) - viewportX,
                (bounds.top + values.translateY) - viewportY);
        canvas.scale(values.scale, values.scale);
        canvas.drawBitmap(bitmap, 0.0f, 0.0f, paint);
        canvas.restore();
        canvas.drawRect(bounds, scrimPaint);
    }

    /** Identity check so layout passes can skip re-installing an identical drawable. */
    public boolean matches(Bitmap other, WallpaperTransform otherTransform, CornerRadius otherRadius,
                           int width, int height, int x, int y) {
        return bitmap == other
                && transform.getScale() == otherTransform.getScale()
                && transform.getOffsetX() == otherTransform.getOffsetX()
                && transform.getOffsetY() == otherTransform.getOffsetY()
                && radius.equals(otherRadius)
                && contentWidth == width
                && contentHeight == height
                && viewportX == x
                && viewportY == y;
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
    @Deprecated
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
