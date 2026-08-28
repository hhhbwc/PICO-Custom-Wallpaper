package com.picoxr.librarywallpaper;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

final class CenterCropDrawable extends Drawable {
    private final Bitmap bitmap;
    private final WallpaperTransform transform;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();

    CenterCropDrawable(Bitmap bitmap, WallpaperTransform transform) {
        this.bitmap = bitmap;
        this.transform = transform;
        paint.setShader(new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        WallpaperTransform.RenderValues values = transform.render(bitmap.getWidth(), bitmap.getHeight(),
                bounds.width(), bounds.height());
        matrix.setScale(values.scale, values.scale);
        matrix.postTranslate(bounds.left + values.translateX, bounds.top + values.translateY);
        paint.getShader().setLocalMatrix(matrix);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
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
