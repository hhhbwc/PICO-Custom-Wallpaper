package com.picoxr.librarywallpaper.core;

import android.graphics.Bitmap;

/** Samples a wallpaper bitmap to decide whether overlaid text should be light or dark. */
public final class TextContrast {
    public static final float LIGHT_THRESHOLD = 155.0f;

    private TextContrast() {
    }

    public static boolean isLightLuminance(long luminance) {
        return (float) luminance >= LIGHT_THRESHOLD;
    }

    /**
     * Votes over a {@code grid}×{@code grid} lattice covering the wallpaper area visible
     * behind the window; text goes dark only when the majority of samples is light.
     * All coordinates are in bitmap space; out-of-range samples are ignored, and NaN
     * coordinates simply never count, degrading to "keep PICO's light text".
     */
    public static boolean isLightArea(Bitmap bitmap, float left, float top, float right, float bottom,
                                      int grid, float threshold) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0
                || right <= left || bottom <= top || grid <= 0) {
            return false;
        }
        int votes = 0;
        int count = 0;
        for (int row = 0; row < grid; row++) {
            for (int col = 0; col < grid; col++) {
                float x = ((right - left) * (col + 0.5f) / grid) + left;
                float y = ((bottom - top) * (row + 0.5f) / grid) + top;
                if (x >= 0.0f && y >= 0.0f && x < bitmap.getWidth() && y < bitmap.getHeight()) {
                    count++;
                    if (isLightRegion(bitmap, x, y, 16, threshold)) {
                        votes++;
                    }
                }
            }
        }
        return count > 0 && votes * 2 >= count;
    }

    private static boolean isLightRegion(Bitmap bitmap, float centerX, float centerY, int radius, float threshold) {
        if (bitmap == null || bitmap.isRecycled() || bitmap.getWidth() == 0 || bitmap.getHeight() == 0) {
            return false;
        }
        int left = Math.max(0, Math.round(centerX) - radius);
        int top = Math.max(0, Math.round(centerY) - radius);
        int right = Math.min(bitmap.getWidth() - 1, Math.round(centerX) + radius);
        int bottom = Math.min(bitmap.getHeight() - 1, Math.round(centerY) + radius);
        if (left > right || top > bottom) {
            return false;
        }
        int width = (right - left) + 1;
        int height = (bottom - top) + 1;
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, left, top, width, height);
        long luminance = 0;
        int count = 0;
        for (int y = 0; y < height; y += 2) {
            int row = y * width;
            for (int x = 0; x < width; x += 2) {
                int color = pixels[row + x];
                luminance += (long) ((((color >> 16) & 255) * 299
                        + (((color >> 8) & 255) * 587)
                        + ((color & 255) * 114)) / 1000);
                count++;
            }
        }
        return count > 0 && (float) (luminance / (long) count) >= threshold;
    }
}
