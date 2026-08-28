package com.picoxr.librarywallpaper;

import android.graphics.Bitmap;

final class TextContrast {
    private TextContrast() {
    }

    static boolean isLightRegion(Bitmap bitmap, float centerX, float centerY, int radius) {
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
        long luminance = 0;
        int count = 0;
        for (int y = top; y <= bottom; y += 2) {
            for (int x = left; x <= right; x += 2) {
                int color = bitmap.getPixel(x, y);
                luminance += (299 * ((color >> 16) & 0xff)
                        + 587 * ((color >> 8) & 0xff)
                        + 114 * (color & 0xff)) / 1000;
                count++;
            }
        }
        return count > 0 && isLightLuminance(luminance / count);
    }

    static boolean isLightLuminance(long luminance) {
        return luminance >= 155;
    }
}
