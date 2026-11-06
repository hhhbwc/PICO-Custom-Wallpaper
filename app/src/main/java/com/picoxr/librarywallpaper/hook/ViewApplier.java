package com.picoxr.librarywallpaper.hook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Choreographer;
import android.view.View;
import android.view.ViewGroup;

import com.picoxr.librarywallpaper.core.CornerRadius;
import com.picoxr.librarywallpaper.core.WallpaperConfig;
import com.picoxr.librarywallpaper.core.WallpaperTarget;
import com.picoxr.librarywallpaper.core.WallpaperTransform;
import com.picoxr.librarywallpaper.draw.GlassDrawable;
import com.picoxr.librarywallpaper.draw.HoverFeedbackDrawable;
import com.picoxr.librarywallpaper.draw.RoundedWallpaperDrawable;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

/**
 * All view mutations performed inside the hooked apps: wallpaper installation, row
 * clearing with hover preservation and the frosted-glass effect.
 *
 * <p>Every mutation is wrapped in a re-entrancy guard ({@link #isMutating(View)}): the
 * settings hook watches {@code View.setBackground*} globally, so our own writes would
 * otherwise trigger the hook again and recurse.</p>
 */
public final class ViewApplier {
    /** Views currently being written by us; the global background hook must skip them. */
    private static final Set<View> MUTATING =
            Collections.newSetFromMap(new WeakHashMap<>());
    /** Views this module forced transparent; re-cleared when PICO redraws them. */
    private static final Set<View> TRANSPARENT_SURFACES =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static final Map<View, GlassSurface> GLASS_SURFACES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean glassFrameLoopRunning;

    private ViewApplier() {
    }

    public static boolean isMutating(View view) {
        return MUTATING.contains(view);
    }

    // ------------------------------------------------------------------ wallpaper

    /**
     * Installs the wallpaper background on one pane. No-op unless the target is active
     * (wallpaper enabled and loaded), so the app keeps its pristine PICO look otherwise.
     */
    public static void install(Activity activity, View view, WallpaperTarget target,
                               ViewGroup contentRoot, CornerRadius fallbackRadius) {
        if (view == null || contentRoot == null
                || contentRoot.getWidth() == 0 || contentRoot.getHeight() == 0) {
            return;
        }
        if (!HookContext.isActive(target)) {
            return;
        }
        Bitmap bitmap = HookContext.wallpaper(activity, target);
        if (bitmap == null) {
            return;
        }
        WallpaperConfig config = HookContext.config(activity, target);
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        contentRoot.getLocationInWindow(rootLocation);
        view.getLocationInWindow(viewLocation);
        int viewportX = viewLocation[0] - rootLocation[0];
        int viewportY = viewLocation[1] - rootLocation[1];
        synchronized (MUTATING) {
            MUTATING.add(view);
            try {
                applyBackground(view, bitmap, config, contentRoot, viewportX, viewportY, target, fallbackRadius);
                MUTATING.remove(view);
            } catch (Throwable throwable) {
                MUTATING.remove(view);
                throw throwable;
            }
        }
    }

    private static void applyBackground(View view, Bitmap bitmap, WallpaperConfig config,
                                        ViewGroup contentRoot, int viewportX, int viewportY,
                                        WallpaperTarget target, CornerRadius fallbackRadius) {
        Drawable existing = view.getBackground();
        CornerRadius radius = radiusOf(existing, fallbackRadius);
        if (existing instanceof RoundedWallpaperDrawable) {
            RoundedWallpaperDrawable current = (RoundedWallpaperDrawable) existing;
            if (current.matches(bitmap, config.getTransform(), radius,
                    contentRoot.getWidth(), contentRoot.getHeight(), viewportX, viewportY)) {
                return;
            }
        }
        view.setBackground(new RoundedWallpaperDrawable(bitmap, config.getTransform(), radius,
                contentRoot.getWidth(), contentRoot.getHeight(), viewportX, viewportY));
        HookContext.log(target.getKey() + " wallpaper applied to " + describe(view)
                + " at " + viewportX + "," + viewportY
                + " in " + contentRoot.getWidth() + "x" + contentRoot.getHeight());
    }

    /**
     * Reads the corner shape of the drawable being replaced, so wallpapered panes keep
     * PICO's rounded-panel silhouette. Shape drawables store per-corner radii, which is
     * exactly what menu_bg / layout_bg / bg_white_c12_left|right use.
     */
    public static CornerRadius radiusOf(Drawable drawable, CornerRadius fallback) {
        if (drawable instanceof RoundedWallpaperDrawable) {
            return CornerRadius.SQUARE; // never reuse our own clip; caller supplies fallback
        }
        if (drawable instanceof GradientDrawable) {
            GradientDrawable shape = (GradientDrawable) drawable;
            float[] radii = shape.getCornerRadii();
            if (radii != null) {
                return CornerRadius.fromRadii(radii);
            }
            float uniform = shape.getCornerRadius();
            if (uniform > 0f) {
                return CornerRadius.uniform(uniform);
            }
        }
        return fallback == null ? CornerRadius.SQUARE : fallback;
    }

    // ------------------------------------------------------------------ clearing

    /**
     * Makes a row/control transparent while keeping pointer feedback: the wallpaper shows
     * through, hover/press still paint PICO's white tints (see HoverFeedbackDrawable).
     */
    public static void clearWithHoverFeedback(View view) {
        synchronized (MUTATING) {
            if (MUTATING.contains(view)) {
                return;
            }
            MUTATING.add(view);
            try {
                Drawable existing = view.getBackground();
                if (existing instanceof HoverFeedbackDrawable) {
                    return;
                }
                CornerRadius radius = radiusOf(existing, CornerRadius.SQUARE);
                view.setBackgroundTintList(null);
                view.setBackground(new HoverFeedbackDrawable(radius));
            } finally {
                MUTATING.remove(view);
            }
        }
    }

    /** Fully transparent view (no hover pill) — used for plain containers and children. */
    public static void clearToTransparent(View view) {
        synchronized (MUTATING) {
            if (MUTATING.contains(view)) {
                return;
            }
            MUTATING.add(view);
            try {
                if (view.getBackground() instanceof HoverFeedbackDrawable) {
                    return;
                }
                view.setBackgroundTintList(null);
                view.setBackgroundColor(0);
                TRANSPARENT_SURFACES.add(view);
            } finally {
                MUTATING.remove(view);
            }
        }
    }

    /** Re-applies our state when PICO redraws a view we manage (non-control surfaces). */
    public static void restoreSettingsSurface(View view) {
        if (TRANSPARENT_SURFACES.contains(view)) {
            clearToTransparent(view);
        }
    }

    /** Clears a whole subtree; the root keeps hover feedback, descendants go transparent. */
    public static void clearSubtree(View root, boolean rootHover) {
        if (rootHover) {
            clearWithHoverFeedback(root);
        } else {
            clearToTransparent(root);
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int index = 0; index < group.getChildCount(); index++) {
                clearSubtree(group.getChildAt(index), false);
            }
        }
    }

    // ------------------------------------------------------------------ glass

    /** Restores/maintains the glass background of a quick-panel button. */
    public static void applyGlassSurface(View view, ViewGroup card, Bitmap blurred, Bitmap full,
                                         WallpaperTransform transform, float radius) {
        Drawable replaced = view.getBackground();
        int intrinsicWidth = replaced == null ? -1 : replaced.getIntrinsicWidth();
        int intrinsicHeight = replaced == null ? -1 : replaced.getIntrinsicHeight();

        GlassSurface existing = GLASS_SURFACES.get(view);
        if (existing != null && existing.cardRoot == card
                && view.getBackground() instanceof GlassDrawable) {
            GlassDrawable current = (GlassDrawable) view.getBackground();
            if (current.matches(blurred, transform)) {
                return;
            }
        }

        // If the view's current background is not PICO's default pill anymore, it is a
        // hover/press state drawable — keep it layered on top of the glass.
        Drawable stateOverlay = null;
        if (existing != null && existing.defaultState != null) {
            Drawable incoming = view.getBackground();
            if (incoming != null && !sameConstantState(incoming, existing.defaultState)) {
                stateOverlay = incoming;
            }
        }

        GLASS_SURFACES.put(view, new GlassSurface(card, blurred, transform,
                full.getWidth(), full.getHeight(), radius, intrinsicWidth, intrinsicHeight,
                replaced == null ? null : replaced.getConstantState()));
        startGlassFrameLoop();
        synchronized (MUTATING) {
            if (MUTATING.contains(view)) {
                return;
            }
            MUTATING.add(view);
            try {
                GlassDrawable drawable = new GlassDrawable(view, card, blurred, transform,
                        full.getWidth(), full.getHeight(), radius, intrinsicWidth, intrinsicHeight);
                view.setBackground(drawable);
                if (stateOverlay != null) {
                    drawable.setStateOverlay(stateOverlay);
                }
            } finally {
                MUTATING.remove(view);
            }
        }
    }

    private static boolean sameConstantState(Drawable first, Drawable.ConstantState second) {
        if (first == null || second == null) {
            return false;
        }
        Drawable.ConstantState state = first.getConstantState();
        return state != null && state.equals(second);
    }

    /** Per-view glass bookkeeping kept between re-applications. */
    static final class GlassSurface {
        final ViewGroup cardRoot;
        final Bitmap blurred;
        final WallpaperTransform transform;
        final int imageWidth;
        final int imageHeight;
        final float radius;
        final int intrinsicWidth;
        final int intrinsicHeight;
        final Drawable.ConstantState defaultState;
        final int[] viewViewport = new int[2];
        final int[] cardViewport = new int[2];
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        int cardWidth = -1;
        int cardHeight = -1;

        GlassSurface(ViewGroup cardRoot, Bitmap blurred, WallpaperTransform transform,
                     int imageWidth, int imageHeight, float radius,
                     int intrinsicWidth, int intrinsicHeight, Drawable.ConstantState defaultState) {
            this.cardRoot = cardRoot;
            this.blurred = blurred;
            this.transform = transform;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.radius = radius;
            this.intrinsicWidth = intrinsicWidth;
            this.intrinsicHeight = intrinsicHeight;
            this.defaultState = defaultState;
        }
    }

    private static void startGlassFrameLoop() {
        if (glassFrameLoopRunning) {
            return;
        }
        glassFrameLoopRunning = true;
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                try {
                    tickGlassViews();
                } catch (Throwable throwable) {
                    HookContext.debugLog("glass frame tick failed: " + throwable);
                }
                if (!GLASS_SURFACES.isEmpty()) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    glassFrameLoopRunning = false;
                }
            }
        });
    }

    /** Drops detached glass views and invalidates ones that moved or resized. */
    private static void tickGlassViews() {
        View[] views = GLASS_SURFACES.keySet().toArray(new View[0]);
        ViewGroup lastCard = null;
        int cardX = 0;
        int cardY = 0;
        for (View view : views) {
            GlassSurface glass = GLASS_SURFACES.get(view);
            if (glass == null || !view.isAttachedToWindow()
                    || !(view.getBackground() instanceof GlassDrawable)) {
                GLASS_SURFACES.remove(view);
                continue;
            }
            GlassDrawable drawable = (GlassDrawable) view.getBackground();
            view.getLocationInWindow(glass.viewViewport);
            if (glass.cardRoot != lastCard) {
                lastCard = glass.cardRoot;
                lastCard.getLocationInWindow(glass.cardViewport);
                cardX = glass.cardViewport[0];
                cardY = glass.cardViewport[1];
            }
            float relativeX = glass.viewViewport[0] - cardX;
            float relativeY = glass.viewViewport[1] - cardY;
            float quantum = drawable.samplingQuantum();
            boolean moved = Float.isNaN(glass.lastX)
                    || Math.abs(relativeX - glass.lastX) >= quantum
                    || Math.abs(relativeY - glass.lastY) >= quantum;
            boolean resized = glass.cardWidth != glass.cardRoot.getWidth()
                    || glass.cardHeight != glass.cardRoot.getHeight();
            if (moved || resized) {
                glass.lastX = relativeX;
                glass.lastY = relativeY;
                glass.cardWidth = glass.cardRoot.getWidth();
                glass.cardHeight = glass.cardRoot.getHeight();
                view.invalidate();
            }
        }
    }

    /** Per-view glass bookkeeping (position + card size as of last tick). */

    // ------------------------------------------------------------------ helpers

    public static ViewGroup parentGroup(View view) {
        android.view.ViewParent parent = view.getParent();
        return parent instanceof ViewGroup ? (ViewGroup) parent : null;
    }

    public static String describe(View view) {
        String name = "no-id";
        if (view.getId() != -1) {
            try {
                name = view.getResources().getResourceEntryName(view.getId());
            } catch (Throwable throwable) {
                name = "id=" + view.getId();
            }
        }
        return name + " " + view.getClass().getSimpleName()
                + " " + view.getWidth() + "x" + view.getHeight();
    }

    public static String resourceName(View view) {
        if (view == null || view.getId() == -1) {
            return "no-id";
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable throwable) {
            return "no-id";
        }
    }
}
