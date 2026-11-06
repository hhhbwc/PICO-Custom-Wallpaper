package com.picoxr.librarywallpaper.hook;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.picoxr.librarywallpaper.core.ColorStateMapper;
import com.picoxr.librarywallpaper.core.TextContrast;
import com.picoxr.librarywallpaper.core.WallpaperConfig;
import com.picoxr.librarywallpaper.core.WallpaperTarget;
import com.picoxr.librarywallpaper.core.WallpaperTransform;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Re-tints overlaid text so it stays readable on any wallpaper.
 *
 * <p>Two properties matter:</p>
 * <ul>
 *   <li>The light/dark decision comes from the wallpaper area actually behind the window,
 *       and texts sitting on their own artwork (app icons' labels, dropdown chips) are
 *       skipped — darkening them would make them unreadable on their dark backdrops.</li>
 *   <li>PICO's text colors are mostly {@link ColorStateList}s (normal vs activated vs
 *       disabled). Restyling must darken every entry of the selector; collapsing it to
 *       one flat color would erase the selected/disabled distinctions.</li>
 * </ul>
 */
public final class TextStyler {
    private static final Map<TextView, Integer> STYLE_SIGNATURE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TextView, ColorStateList> ORIGINAL_COLORS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private TextStyler() {
    }

    /** Restyles every eligible text under {@code root} for the current wallpaper. */
    public static void styleTree(Activity activity, View root, ViewGroup contentRoot,
                                 WallpaperTarget target) {
        if (root.getVisibility() != View.VISIBLE) {
            return;
        }
        Bitmap bitmap = HookContext.wallpaper(activity, target);
        WallpaperConfig config = HookContext.config(activity, target);
        if (bitmap == null || !config.isEnabled()
                || contentRoot.getWidth() <= 0 || contentRoot.getHeight() <= 0) {
            return;
        }
        styleTree(root, target, pageTextLight(bitmap, config, contentRoot));
    }

    /** Clears memoized state; called when the wallpaper bitmap changes. */
    public static void invalidateCache() {
        STYLE_SIGNATURE.clear();
    }

    private static void styleTree(View view, WallpaperTarget target, boolean light) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (isArtBacked(view)) {
            return; // text on its own artwork/chip: never follow the wallpaper
        }
        if (view instanceof TextView && shouldStyle(view)) {
            styleTextView((TextView) view, target, light);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                styleTree(group.getChildAt(index), target, light);
            }
        }
    }

    /**
     * Views whose text sits on its own backdrop: library tiles (label over game art) and
     * dropdown chips. Darkening them with the page decision breaks their contrast.
     */
    private static boolean isArtBacked(View view) {
        String className = view.getClass().getName();
        return className.endsWith("AMCardView") || className.endsWith("DropdownOptionView");
    }

    /** Maps the visible window rect into bitmap space, then votes on 5×5 samples. */
    private static boolean pageTextLight(Bitmap bitmap, WallpaperConfig config, ViewGroup contentRoot) {
        int width = contentRoot.getWidth();
        int height = contentRoot.getHeight();
        WallpaperTransform.RenderValues values = config.getTransform()
                .render(bitmap.getWidth(), bitmap.getHeight(), width, height);
        float left = -values.translateX / values.scale;
        float top = -values.translateY / values.scale;
        float right = (width - values.translateX) / values.scale;
        float bottom = (height - values.translateY) / values.scale;
        return TextContrast.isLightArea(bitmap, left, top, right, bottom, 5, 221.42857f);
    }

    private static boolean shouldStyle(View view) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        String className = view.getClass().getName();
        return !className.endsWith("SwitchView") && !className.endsWith("SwitchCompat");
    }

    private static void styleTextView(TextView textView, WallpaperTarget target, boolean light) {
        int signature = (light ? 1 : 0) * 31 + target.ordinal();
        Integer previous = STYLE_SIGNATURE.get(textView);
        if (previous != null && previous == signature) {
            return;
        }
        STYLE_SIGNATURE.put(textView, signature);

        ColorStateList original = ORIGINAL_COLORS.get(textView);
        if (original == null) {
            original = textView.getTextColors();
            ORIGINAL_COLORS.put(textView, original);
        }
        textView.setTextColor(light ? darkened(original) : original);
        if (light) {
            textView.setShadowLayer(2.2f, 0.0f, 1.0f, Color.argb(155, 255, 255, 255));
        } else {
            textView.setShadowLayer(2.2f, 0.0f, 1.0f, Color.argb(185, 0, 0, 0));
        }
    }

    /**
     * Darkens a color list while keeping every state entry. {@code withLStar} (API 31+)
     * re-lights all entries while preserving each entry's alpha — which is how PICO's
     * selectors differ (alpha variants of white), so the activated/disabled distinctions
     * survive. On older builds the list is flattened to its darkened default color; the
     * shadow layer still keeps the text readable.
     */
    private static ColorStateList darkened(ColorStateList list) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return list.withLStar(ColorStateMapper.lStarAfterDarkening(list.getDefaultColor(), 0.24f));
        }
        return ColorStateList.valueOf(ColorStateMapper.darken(list.getDefaultColor(), 0.24f));
    }
}
