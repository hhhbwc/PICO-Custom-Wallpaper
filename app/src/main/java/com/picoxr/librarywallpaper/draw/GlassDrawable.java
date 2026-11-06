package com.picoxr.librarywallpaper.draw;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import com.picoxr.librarywallpaper.core.WallpaperTransform;

/**
 * "Frosted glass" background for quick-settings buttons: samples the blurred wallpaper
 * under the button's current position inside the card, so the button appears to refract
 * the wallpaper behind it.
 *
 * <p>The render is cached per (position-cell, state, size) triple and only re-rendered
 * when the view moves at least one {@link #samplingQuantum} pixel — see the frame loop in
 * {@code ViewApplier} that drives revalidation. Hover/press add a white wash and a
 * border; focused/hovered switch the border to PICO's focus blue.</p>
 */
public final class GlassDrawable extends Drawable {
    private static final int BORDER = 0x30FFFFFF;
    private static final int BORDER_FOCUSED = 0xFF99AAFF;
    private static final int TINT = 0x23FFFFFF;
    private static final int TINT_PRESSED = 0x3DFFFFFF;

    private static final int STATE_PRESSED = 1;
    private static final int STATE_FOCUSED = 2;
    private static final int STATE_HOVERED = 4;

    private final View view;
    private final ViewGroup cardRoot;
    private final Bitmap blurred;
    private final WallpaperTransform transform;
    private final int imageWidth;
    private final int imageHeight;
    private final float radius;
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    private final float quantum;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Rect src = new Rect();
    private final int[] viewLocation = new int[2];
    private final int[] cardLocation = new int[2];

    private Bitmap cache;
    private float cacheCellX = Float.NaN;
    private float cacheCellY = Float.NaN;
    private int cacheState = -1;
    private Drawable stateOverlay;

    public GlassDrawable(View view, ViewGroup cardRoot, Bitmap blurred, WallpaperTransform transform,
                         int imageWidth, int imageHeight, float radius,
                         int intrinsicWidth, int intrinsicHeight) {
        this.view = view;
        this.cardRoot = cardRoot;
        this.blurred = blurred;
        this.transform = transform;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.radius = radius;
        this.intrinsicWidth = intrinsicWidth;
        this.intrinsicHeight = intrinsicHeight;
        this.quantum = computeQuantum(blurred, transform, imageWidth);
    }

    private static float computeQuantum(Bitmap blurred, WallpaperTransform transform, int imageWidth) {
        if (blurred == null || blurred.getWidth() == 0) {
            return 16.0f;
        }
        float ratio = transform.getScale() * imageWidth / blurred.getWidth();
        return Math.max(12.0f, Math.min(128.0f, ratio));
    }

    /** Position delta (px) that must accumulate before the cache is re-rendered. */
    public float samplingQuantum() {
        return quantum;
    }

    /** Replaces the drawable that shows pressed/hover states on top of the glass. */
    public void setStateOverlay(Drawable overlay) {
        this.stateOverlay = overlay;
        this.cacheState = -1;
        invalidateSelf();
    }

    public boolean matches(Bitmap otherBlurred, WallpaperTransform otherTransform) {
        return blurred == otherBlurred
                && transform.getScale() == otherTransform.getScale()
                && transform.getOffsetX() == otherTransform.getOffsetX()
                && transform.getOffsetY() == otherTransform.getOffsetY();
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (blurred == null || blurred.isRecycled() || bounds.isEmpty()
                || imageWidth <= 0 || !view.isAttachedToWindow()
                || cardRoot.getWidth() == 0 || cardRoot.getHeight() == 0) {
            return;
        }
        view.getLocationInWindow(viewLocation);
        cardRoot.getLocationInWindow(cardLocation);
        float cellX = Math.round((viewLocation[0] - cardLocation[0]) / quantum) * quantum;
        float cellY = Math.round((viewLocation[1] - cardLocation[1]) / quantum) * quantum;
        int cardWidth = cardRoot.getWidth();
        int cardHeight = cardRoot.getHeight();
        int state = stateSignature();
        if (cache == null || cache.getWidth() != bounds.width() || cache.getHeight() != bounds.height()
                || cellX != cacheCellX || cellY != cacheCellY || state != cacheState) {
            renderCache(bounds, cellX, cellY, cardWidth, cardHeight, state);
            cacheCellX = cellX;
            cacheCellY = cellY;
            cacheState = state;
        }
        canvas.drawBitmap(cache, 0.0f, 0.0f, paint);
    }

    private void renderCache(Rect bounds, float cellX, float cellY, int cardWidth, int cardHeight, int state) {
        if (cache == null || cache.getWidth() != bounds.width() || cache.getHeight() != bounds.height()) {
            cache = Bitmap.createBitmap(Math.max(1, bounds.width()), Math.max(1, bounds.height()),
                    Bitmap.Config.ARGB_8888);
        }
        Canvas cacheCanvas = new Canvas(cache);
        cacheCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

        WallpaperTransform.RenderValues values = transform.render(imageWidth, imageHeight, cardWidth, cardHeight);
        float ratio = (float) blurred.getWidth() / imageWidth;

        rect.set(bounds);
        path.reset();
        path.addRoundRect(rect, radius, radius, Path.Direction.CW);
        cacheCanvas.save();
        cacheCanvas.clipPath(path);

        int left = clamp(((cellX - values.translateX) / values.scale) * ratio, 0, blurred.getWidth());
        int top = clamp(((cellY - values.translateY) / values.scale) * ratio, 0, blurred.getHeight());
        int right = clamp(((cellX + bounds.width() - values.translateX) / values.scale) * ratio, 0, blurred.getWidth());
        int bottom = clamp(((cellY + bounds.height() - values.translateY) / values.scale) * ratio, 0, blurred.getHeight());
        src.set(left, top, Math.max(left + 1, right), Math.max(top + 1, bottom));
        cacheCanvas.drawBitmap(blurred, src, rect, paint);

        overlay.setStyle(Paint.Style.FILL);
        overlay.setColor((state & STATE_PRESSED) != 0 ? TINT_PRESSED : TINT);
        cacheCanvas.drawRoundRect(rect, radius, radius, overlay);

        if (stateOverlay != null) {
            stateOverlay.setBounds(bounds);
            stateOverlay.setState(getState());
            stateOverlay.draw(cacheCanvas);
        }

        overlay.setStyle(Paint.Style.STROKE);
        overlay.setStrokeWidth(Math.max(1.0f, radius * 0.05f));
        overlay.setColor((state & (STATE_FOCUSED | STATE_HOVERED)) != 0 ? BORDER_FOCUSED : BORDER);
        float inset = overlay.getStrokeWidth() / 2.0f;
        rect.inset(inset, inset);
        cacheCanvas.drawRoundRect(rect, Math.max(0.0f, radius - inset), Math.max(0.0f, radius - inset), overlay);
        cacheCanvas.restore();
    }

    private int stateSignature() {
        int[] state = getState();
        int signature = 0;
        if (hasState(state, android.R.attr.state_pressed)) {
            signature |= STATE_PRESSED;
        }
        if (hasState(state, android.R.attr.state_focused)) {
            signature |= STATE_FOCUSED;
        }
        if (hasState(state, android.R.attr.state_hovered)) {
            signature |= STATE_HOVERED;
        }
        return signature;
    }

    private static int clamp(float value, int min, int max) {
        return (int) Math.max(min, Math.min(max - 1, value));
    }

    private static boolean hasState(int[] state, int attr) {
        if (state == null) {
            return false;
        }
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
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
    }

    @Override
    @Deprecated
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
