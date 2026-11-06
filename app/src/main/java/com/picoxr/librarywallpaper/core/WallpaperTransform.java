package com.picoxr.librarywallpaper.core;

/**
 * User-authored placement of the wallpaper inside the target window: a zoom factor plus
 * pan offsets normalised to the remaining overhang on each axis.
 *
 * <p>All values are clamped in the constructor. The clamp is NaN-hostile: NaN never
 * survives (it degrades to {@link #MIN_USER_SCALE}), so a corrupted preference file can
 * at worst show a centered wallpaper instead of silently blanking the canvas.</p>
 */
public final class WallpaperTransform {
    public static final float MIN_USER_SCALE = 1.0f;
    public static final float MAX_USER_SCALE = 6.0f;

    private final float scale;
    private final float offsetX;
    private final float offsetY;

    public WallpaperTransform(float scale, float offsetX, float offsetY) {
        this.scale = clamp(scale, MIN_USER_SCALE, MAX_USER_SCALE);
        this.offsetX = clamp(offsetX, -1.0f, 1.0f);
        this.offsetY = clamp(offsetY, -1.0f, 1.0f);
    }

    public static WallpaperTransform centered() {
        return new WallpaperTransform(MIN_USER_SCALE, 0.0f, 0.0f);
    }

    public float getScale() {
        return scale;
    }

    public float getOffsetX() {
        return offsetX;
    }

    public float getOffsetY() {
        return offsetY;
    }

    /** Cover-fit math for drawing the image into a target area. */
    public RenderValues render(int imageWidth, int imageHeight, int targetWidth, int targetHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || targetWidth <= 0 || targetHeight <= 0) {
            return new RenderValues(MIN_USER_SCALE, 0.0f, 0.0f);
        }
        float baseScale = Math.max((float) targetWidth / imageWidth, (float) targetHeight / imageHeight);
        float finalScale = scale * baseScale;
        float drawnWidth = imageWidth * finalScale;
        float drawnHeight = imageHeight * finalScale;
        float remainingX = Math.max(0.0f, drawnWidth - targetWidth) / 2.0f;
        float remainingY = Math.max(0.0f, drawnHeight - targetHeight) / 2.0f;
        return new RenderValues(finalScale,
                -remainingX + offsetX * remainingX,
                -remainingY + offsetY * remainingY);
    }

    /** Panning step used by the editor; keeps offsets inside the current overhang. */
    public WallpaperTransform moved(float deltaX, float deltaY, int imageWidth, int imageHeight,
                                    int targetWidth, int targetHeight) {
        RenderValues values = render(imageWidth, imageHeight, targetWidth, targetHeight);
        float remainingX = Math.max(0.0f, imageWidth * values.scale - targetWidth) / 2.0f;
        float remainingY = Math.max(0.0f, imageHeight * values.scale - targetHeight) / 2.0f;
        float nextX = remainingX == 0.0f ? 0.0f : offsetX + deltaX / remainingX;
        float nextY = remainingY != 0.0f ? offsetY + deltaY / remainingY : 0.0f;
        return new WallpaperTransform(scale, nextX, nextY);
    }

    public WallpaperTransform scaled(float factor) {
        return new WallpaperTransform(scale * factor, offsetX, offsetY);
    }

    private static float clamp(float value, float min, float max) {
        if (!(value >= min)) {
            return min; // also catches NaN
        }
        return Math.min(value, max);
    }

    /** Immutable result of {@link #render}. */
    public static final class RenderValues {
        public final float scale;
        public final float translateX;
        public final float translateY;

        RenderValues(float scale, float translateX, float translateY) {
            this.scale = scale;
            this.translateX = translateX;
            this.translateY = translateY;
        }
    }
}
