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
import android.view.View;
import android.view.ViewGroup;

/** 按钮毛玻璃背景:每次绘制实时读取按钮在面板卡片内的位置,采样壁纸模糊缩略图对应区域,叠加高光与状态描边。 */
final class GlassDrawable extends Drawable {
    private static final int TINT = 0x22FFFFFF;
    private static final int TINT_PRESSED = 0x3CFFFFFF;
    private static final int BORDER = 0x30FFFFFF;
    private static final int BORDER_FOCUSED = 0xFF5AA9FF;

    private final View view;
    private final ViewGroup cardRoot;
    private final Bitmap blurred;
    private final WallpaperTransform transform;
    private final int imageWidth;
    private final int imageHeight;
    private final float radius;
    // 采样量化步长:模糊图里 1 像素对应的卡片位移,步长内的移动不影响观感但免去重录显示列表
    private final float quantum;
    // 被替换背景的固有尺寸,保持 wrap_content 布局不缩水;-1 表示无
    private final int intrinsicWidth;
    private final int intrinsicHeight;
    // 系统在玻璃之后设置的状态图案(如开启态高亮药丸),叠加绘制在模糊层上
    private Drawable stateOverlay;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    private final Rect src = new Rect();
    private final int[] viewLocation = new int[2];
    private final int[] cardLocation = new int[2];

    GlassDrawable(View view, ViewGroup cardRoot, Bitmap blurred, WallpaperTransform transform,
            int imageWidth, int imageHeight, float radius, int intrinsicWidth, int intrinsicHeight) {
        this.view = view;
        this.cardRoot = cardRoot;
        this.blurred = blurred;
        this.transform = transform;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.radius = radius;
        this.intrinsicWidth = intrinsicWidth;
        this.intrinsicHeight = intrinsicHeight;
        this.quantum = blurred == null || blurred.getWidth() == 0 ? 16f
                : Math.max(12f, Math.min(128f,
                transform.scale * imageWidth / blurred.getWidth()));
    }

    void setStateOverlay(Drawable drawable) {
        stateOverlay = drawable;
        invalidateSelf();
    }

    float samplingQuantum() {
        return quantum;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    @Override
    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    boolean matches(Bitmap otherBlurred, WallpaperTransform other) {
        return blurred == otherBlurred && transform.scale == other.scale
                && transform.offsetX == other.offsetX && transform.offsetY == other.offsetY;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (blurred == null || blurred.isRecycled() || bounds.isEmpty() || imageWidth <= 0
                || !view.isAttachedToWindow() || cardRoot.getWidth() == 0
                || cardRoot.getHeight() == 0) {
            return;
        }
        // 实时读取位置,并量化到步长网格:同格内的重绘采样同一区域,模糊下无感
        view.getLocationInWindow(viewLocation);
        cardRoot.getLocationInWindow(cardLocation);
        float viewportX = Math.round((viewLocation[0] - cardLocation[0]) / quantum) * quantum;
        float viewportY = Math.round((viewLocation[1] - cardLocation[1]) / quantum) * quantum;
        int cardWidth = cardRoot.getWidth();
        int cardHeight = cardRoot.getHeight();
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
        if (stateOverlay != null) {
            // 系统切换的状态图案(开启态等)叠加在模糊层上,保留原生开启反馈
            stateOverlay.setBounds(bounds);
            stateOverlay.setState(state);
            stateOverlay.draw(canvas);
        }
        overlay.setStyle(Paint.Style.STROKE);
        overlay.setStrokeWidth(Math.max(1f, radius * 0.05f));
        overlay.setColor(hasState(state, android.R.attr.state_focused)
                || hasState(state, android.R.attr.state_hovered) ? BORDER_FOCUSED : BORDER);
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
