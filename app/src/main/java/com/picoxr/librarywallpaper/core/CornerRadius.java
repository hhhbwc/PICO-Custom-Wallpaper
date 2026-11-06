package com.picoxr.librarywallpaper.core;

/**
 * Per-corner radius in pixels, ordered topLeft / topRight / bottomRight / bottomLeft
 * (the same order {@code GradientDrawable#getCornerRadii()} uses).
 *
 * <p>PICO's window panels are rounded on the outer corners only: the navigation rail is
 * rounded on the left ({@link #leftRounded(float)}), the content pane on the right
 * ({@link #rightRounded(float)}). Wallpaper drawn with square corners visibly breaks the
 * silhouette, so the painter clips to these radii.</p>
 */
public final class CornerRadius {
    public static final CornerRadius SQUARE = new CornerRadius(0f, 0f, 0f, 0f);

    public final float topLeft;
    public final float topRight;
    public final float bottomRight;
    public final float bottomLeft;

    public CornerRadius(float topLeft, float topRight, float bottomRight, float bottomLeft) {
        this.topLeft = Math.max(0f, topLeft);
        this.topRight = Math.max(0f, topRight);
        this.bottomRight = Math.max(0f, bottomRight);
        this.bottomLeft = Math.max(0f, bottomLeft);
    }

    public static CornerRadius uniform(float radius) {
        return new CornerRadius(radius, radius, radius, radius);
    }

    /** Only the two left corners are rounded (navigation rail). */
    public static CornerRadius leftRounded(float radius) {
        return new CornerRadius(radius, 0f, 0f, radius);
    }

    /** Only the two right corners are rounded (content pane). */
    public static CornerRadius rightRounded(float radius) {
        return new CornerRadius(0f, radius, radius, 0f);
    }

    /** Reads {@code GradientDrawable#getCornerRadii()} order; {@code null} maps to square. */
    public static CornerRadius fromRadii(float[] radii) {
        if (radii == null || radii.length != 8) {
            return SQUARE;
        }
        return new CornerRadius(radii[0], radii[2], radii[4], radii[6]);
    }

    /** {@code true} when every corner is (near) zero. */
    public boolean isSquare() {
        return topLeft < 0.5f && topRight < 0.5f && bottomRight < 0.5f && bottomLeft < 0.5f;
    }

    public float[] toRadiiArray() {
        return new float[]{topLeft, topLeft, topRight, topRight, bottomRight, bottomRight, bottomLeft, bottomLeft};
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof CornerRadius)) {
            return false;
        }
        CornerRadius o = (CornerRadius) other;
        return topLeft == o.topLeft && topRight == o.topRight
                && bottomRight == o.bottomRight && bottomLeft == o.bottomLeft;
    }

    @Override
    public int hashCode() {
        return java.util.Arrays.hashCode(toRadiiArray());
    }
}
