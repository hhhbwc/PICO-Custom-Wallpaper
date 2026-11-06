package com.picoxr.librarywallpaper.core;

/**
 * Pure color math shared by the text restyler. Operating on raw state/color arrays keeps
 * this JVM-testable; the android {@code ColorStateList} wrapper lives in {@code TextStyler}.
 */
public final class ColorStateMapper {
    private ColorStateMapper() {
    }

    /**
     * Multiplies the RGB channels of every color by {@code factor} while keeping alpha,
     * so a state selector (normal / activated / disabled / ...) stays a state selector
     * after darkening. Flattening to a single color would erase those distinctions.
     *
     * @param colors packed colors, one per state set
     * @return darkened copy, or {@code colors} when empty
     */
    public static int[] darkenAll(int[] colors, float factor) {
        if (colors == null) {
            return null;
        }
        int[] result = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            result[i] = darken(colors[i], factor);
        }
        return result;
    }

    /** Darkens one packed color: alpha preserved, RGB scaled by {@code factor}. */
    public static int darken(int color, float factor) {
        int alpha = (color >>> 24) & 0xFF;
        int red = (int) (((color >>> 16) & 0xFF) * factor);
        int green = (int) (((color >>> 8) & 0xFF) * factor);
        int blue = (int) ((color & 0xFF) * factor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    /**
     * Target CIELAB lightness of {@link #darken} applied to {@code color}.
     *
     * <p>Used together with {@code ColorStateList#withLStar}: that API cannot enumerate
     * the selector's state entries, but it can re-light every entry while keeping each
     * entry's alpha — which is exactly how PICO's selectors differ (normal vs activated
     * vs disabled are alpha variants of white). Darkening via L* therefore preserves the
     * whole selector structure.</p>
     */
    public static float lStarAfterDarkening(int color, float factor) {
        return lStarOf(darken(color, factor));
    }

    /** CIELAB L* of a packed sRGB color (gamma-expanded, D65). */
    public static float lStarOf(int color) {
        double red = linearize(((color >>> 16) & 0xFF) / 255.0);
        double green = linearize(((color >>> 8) & 0xFF) / 255.0);
        double blue = linearize((color & 0xFF) / 255.0);
        double luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue;
        if (luminance <= 216.0 / 24389.0) {
            return (float) (luminance * 24389.0 / 27.0);
        }
        return (float) (116.0 * Math.cbrt(luminance) - 16.0);
    }

    private static double linearize(double channel) {
        return channel <= 0.04045 ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
