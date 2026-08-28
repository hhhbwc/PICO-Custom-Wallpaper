package com.picoxr.librarywallpaper;

final class WallpaperTransform {
    static final float MIN_USER_SCALE = 1f;
    static final float MAX_USER_SCALE = 6f;

    final float scale;
    final float offsetX;
    final float offsetY;

    WallpaperTransform(float scale, float offsetX, float offsetY) {
        this.scale = clamp(scale, MIN_USER_SCALE, MAX_USER_SCALE);
        this.offsetX = clamp(offsetX, -1f, 1f);
        this.offsetY = clamp(offsetY, -1f, 1f);
    }

    static WallpaperTransform centered() {
        return new WallpaperTransform(1f, 0f, 0f);
    }

    RenderValues render(int imageWidth, int imageHeight, int targetWidth, int targetHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return new RenderValues(1f, 0f, 0f);
        }
        float baseScale = Math.max((float) targetWidth / imageWidth, (float) targetHeight / imageHeight);
        float finalScale = baseScale * scale;
        float drawnWidth = imageWidth * finalScale;
        float drawnHeight = imageHeight * finalScale;
        float remainingX = Math.max(0f, drawnWidth - targetWidth) / 2f;
        float remainingY = Math.max(0f, drawnHeight - targetHeight) / 2f;
        return new RenderValues(finalScale, -remainingX + offsetX * remainingX,
                -remainingY + offsetY * remainingY);
    }

    WallpaperTransform moved(float deltaX, float deltaY, int imageWidth, int imageHeight,
            int targetWidth, int targetHeight) {
        RenderValues values = render(imageWidth, imageHeight, targetWidth, targetHeight);
        float remainingX = Math.max(0f, imageWidth * values.scale - targetWidth) / 2f;
        float remainingY = Math.max(0f, imageHeight * values.scale - targetHeight) / 2f;
        float nextX = remainingX == 0f ? 0f : offsetX + deltaX / remainingX;
        float nextY = remainingY == 0f ? 0f : offsetY + deltaY / remainingY;
        return new WallpaperTransform(scale, nextX, nextY);
    }

    WallpaperTransform scaled(float factor) {
        return new WallpaperTransform(scale * factor, offsetX, offsetY);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static final class RenderValues {
        final float scale;
        final float translateX;
        final float translateY;

        RenderValues(float scale, float translateX, float translateY) {
            this.scale = scale;
            this.translateX = translateX;
            this.translateY = translateY;
        }
    }
}
