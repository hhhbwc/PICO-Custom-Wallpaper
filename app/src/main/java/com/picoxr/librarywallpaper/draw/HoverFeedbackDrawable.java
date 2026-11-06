package com.picoxr.librarywallpaper.draw;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import com.picoxr.librarywallpaper.core.CornerRadius;

/**
 * Row/control background that keeps PICO's pointer feedback while staying visually
 * transparent over the wallpaper.
 *
 * <p>PICO settings rows signal hover/press through a background tint selector
 * (normal 4% white, hover 8%, press 12%). A wallpaper module that simply clears the
 * background and nulls the tint leaves the user with no idea which row the controller
 * pointer is on. This drawable keeps the exact same three states but renders the normal
 * state fully transparent, so the wallpaper shows through untouched.</p>
 */
public final class HoverFeedbackDrawable extends Drawable {
    /** White with alpha 0x14 (8%): PICO's row hover tint. */
    public static final int HOVER_TINT = 0x14FFFFFF;
    /** White with alpha 0x1E (12%): PICO's row pressed tint. */
    public static final int PRESSED_TINT = 0x1EFFFFFF;

    private final CornerRadius radius;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public HoverFeedbackDrawable(CornerRadius radius) {
        this.radius = radius == null ? CornerRadius.SQUARE : radius;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) {
            return;
        }
        int[] state = getState();
        int color;
        if (hasState(state, android.R.attr.state_pressed)) {
            color = PRESSED_TINT;
        } else if (hasState(state, android.R.attr.state_hovered)) {
            color = HOVER_TINT;
        } else {
            return; // normal state: fully transparent, wallpaper shows through
        }
        paint.setColor(color);
        rect.set(bounds);
        path.reset();
        if (radius.isSquare()) {
            canvas.drawRect(rect, paint);
        } else {
            path.addRoundRect(rect, radius.toRadiiArray(), Path.Direction.CW);
            canvas.drawPath(path, paint);
        }
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
    @Deprecated
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
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
}
