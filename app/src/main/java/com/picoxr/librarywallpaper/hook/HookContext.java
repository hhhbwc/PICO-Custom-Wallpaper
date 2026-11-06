package com.picoxr.librarywallpaper.hook;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.picoxr.librarywallpaper.core.WallpaperConfig;
import com.picoxr.librarywallpaper.core.WallpaperTarget;
import com.picoxr.librarywallpaper.data.ImageLoader;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import de.robv.android.xposed.XposedBridge;

/**
 * Per-process state shared by the hooks: config and bitmap caches, the active gate and
 * the refresh fan-out.
 *
 * <p>The gate is the heart of the "invisible when disabled" contract: the module must not
 * touch a single view while no wallpaper is configured for that target. Both hot paths
 * (the global setBackground hook fires for every view in the settings process) read a
 * plain volatile boolean that is recomputed on the background loader whenever config or
 * bitmap state changes.</p>
 */
public final class HookContext {
    public static final String TAG = "PicoLibraryWallpaper";
    public static final String APP_MANAGER = "com.pvr.appmanager";
    public static final String SETTINGS = "com.picovr.settings";
    private static final long CONFIG_TTL_MS = 2000;
    private static final long BITMAP_RETRY_MS = 2000;
    private static final boolean DEBUG_LOGGING = false;

    /** A hook that knows how to re-run its install passes for all its live activities. */
    public interface Refreshable {
        void refreshAll();
    }

    private static final Map<WallpaperTarget, Bitmap> WALLPAPER_CACHE = new ConcurrentHashMap<>();
    private static final Map<WallpaperTarget, Bitmap> BLURRED_CACHE = new ConcurrentHashMap<>();
    private static final Map<WallpaperTarget, WallpaperConfig> CONFIG_CACHE = new ConcurrentHashMap<>();
    private static final Map<WallpaperTarget, Long> CONFIG_FETCHED_AT = new ConcurrentHashMap<>();
    private static final Map<WallpaperTarget, Long> BITMAP_FAILED_AT = new ConcurrentHashMap<>();
    private static final Set<WallpaperTarget> PENDING_CONFIG = ConcurrentHashMap.newKeySet();
    private static final Set<WallpaperTarget> PENDING_BITMAP = ConcurrentHashMap.newKeySet();

    /** Cheap gate read for hot paths; true iff wallpaper enabled AND a bitmap is loaded. */
    private static volatile boolean libraryActive;
    private static volatile boolean settingsActive;

    private static final Set<Refreshable> HOOKS = ConcurrentHashMap.newKeySet();
    private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "PicoLibraryWallpaper-Loader");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile Handler mainHandler;

    private HookContext() {
    }

    public static void register(Refreshable hook) {
        HOOKS.add(hook);
    }

    public static boolean isLibraryActive() {
        return libraryActive;
    }

    public static boolean isSettingsActive() {
        return settingsActive;
    }

    public static boolean isActive(WallpaperTarget target) {
        return target == WallpaperTarget.LIBRARY ? libraryActive : settingsActive;
    }

    /** Cached config; stale entries trigger an async refresh and are returned as-is. */
    public static WallpaperConfig config(Context context, WallpaperTarget target) {
        WallpaperConfig config = CONFIG_CACHE.get(target);
        long now = SystemClock.elapsedRealtime();
        Long fetchedAt = CONFIG_FETCHED_AT.get(target);
        if (fetchedAt == null || now - fetchedAt > CONFIG_TTL_MS) {
            requestConfigRefresh(context, target);
        }
        return config == null ? WallpaperConfig.disabled() : config;
    }

    /** Cached wallpaper bitmap; {@code null} starts an async load (with retry backoff). */
    public static Bitmap wallpaper(Context context, WallpaperTarget target) {
        Bitmap bitmap = WALLPAPER_CACHE.get(target);
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        requestBitmapLoad(context, target);
        return null;
    }

    /**
     * Warms config + bitmap caches for a target without consuming the result. Install
     * passes call this before consulting {@link #isActive(WallpaperTarget)}: the gate
     * flips asynchronously, and the finished load re-triggers a refresh, so the first
     * pass after process start is allowed to be a no-op.
     */
    public static void warm(Context context, WallpaperTarget target) {
        config(context, target);
        wallpaper(context, target);
    }

    public static Bitmap blurred(WallpaperTarget target) {
        return BLURRED_CACHE.get(target);
    }

    public static void requestBitmapLoad(Context context, WallpaperTarget target) {
        Long failedAt = BITMAP_FAILED_AT.get(target);
        if (failedAt != null && SystemClock.elapsedRealtime() - failedAt < BITMAP_RETRY_MS) {
            return; // back off after a failed decode instead of spinning per layout pass
        }
        if (!PENDING_BITMAP.add(target)) {
            return;
        }
        Context app = context.getApplicationContext();
        LOADER.execute(() -> {
            try {
                Bitmap loaded = ImageLoader.decodeProviderWallpaper(app, providerUri(target));
                PENDING_BITMAP.remove(target);
                if (loaded == null) {
                    BITMAP_FAILED_AT.put(target, SystemClock.elapsedRealtime());
                    return;
                }
                BITMAP_FAILED_AT.remove(target);
                WALLPAPER_CACHE.put(target, loaded);
                BLURRED_CACHE.put(target, blurredThumbnail(loaded));
                TextStyler.invalidateCache();
                postRefresh();
            } catch (Throwable throwable) {
                PENDING_BITMAP.remove(target);
                BITMAP_FAILED_AT.put(target, SystemClock.elapsedRealtime());
                debugLog("wallpaper load failed: " + throwable);
            } finally {
                recomputeActive(target);
            }
        });
    }

    private static void requestConfigRefresh(Context context, WallpaperTarget target) {
        if (!PENDING_CONFIG.add(target)) {
            return;
        }
        Context app = context.getApplicationContext();
        LOADER.execute(() -> {
            try {
                WallpaperConfig fresh = readConfig(app, target);
                CONFIG_FETCHED_AT.put(target, SystemClock.elapsedRealtime());
                PENDING_CONFIG.remove(target);
                CONFIG_CACHE.put(target, fresh);
                postRefresh();
            } catch (Throwable throwable) {
                PENDING_CONFIG.remove(target);
                debugLog("config refresh failed: " + throwable);
            } finally {
                recomputeActive(target);
            }
        });
    }

    private static WallpaperConfig readConfig(Context context, WallpaperTarget target) {
        try {
            android.net.Uri uri = providerUri(target);
            android.os.Bundle bundle = context.getContentResolver().call(uri, "config", target.getKey(), null);
            if (bundle != null && bundle.getBoolean("enabled", false)) {
                return new WallpaperConfig(true, new com.picoxr.librarywallpaper.core.WallpaperTransform(
                        bundle.getFloat("scale", 1.0f),
                        bundle.getFloat("offset_x", 0.0f),
                        bundle.getFloat("offset_y", 0.0f)));
            }
            return WallpaperConfig.disabled();
        } catch (Throwable t) {
            return WallpaperConfig.disabled();
        }
    }

    private static android.net.Uri providerUri(WallpaperTarget target) {
        return new android.net.Uri.Builder()
                .scheme("content")
                .authority("com.picoxr.librarywallpaper.wallpaper")
                .appendPath(target.getKey())
                .build();
    }

    private static void recomputeActive(WallpaperTarget target) {
        WallpaperConfig config = CONFIG_CACHE.get(target);
        Bitmap bitmap = WALLPAPER_CACHE.get(target);
        boolean active = config != null && config.isEnabled()
                && bitmap != null && !bitmap.isRecycled();
        if (target == WallpaperTarget.LIBRARY) {
            libraryActive = active;
        } else {
            settingsActive = active;
        }
    }

    /** Re-runs install passes of every registered hook on the main thread. */
    public static void postRefresh() {
        Handler handler = mainHandler();
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            for (Refreshable hook : HOOKS.toArray(new Refreshable[0])) {
                try {
                    hook.refreshAll();
                } catch (Throwable throwable) {
                    debugLog("refresh failed: " + throwable);
                }
            }
        });
    }

    private static Handler mainHandler() {
        if (mainHandler == null) {
            synchronized (HookContext.class) {
                if (mainHandler == null && Looper.getMainLooper() != null) {
                    mainHandler = new Handler(Looper.getMainLooper());
                }
            }
        }
        return mainHandler;
    }

    private static Bitmap blurredThumbnail(Bitmap bitmap) {
        try {
            int quarterWidth = Math.max(1, bitmap.getWidth() / 4);
            int quarterHeight = Math.max(1, bitmap.getHeight() / 4);
            Bitmap quarter = Bitmap.createScaledBitmap(bitmap, quarterWidth, quarterHeight, true);
            int blurWidth = Math.max(1, bitmap.getWidth() / 16);
            Bitmap blurred = Bitmap.createScaledBitmap(quarter, blurWidth,
                    Math.max(1, quarter.getHeight() * blurWidth / quarter.getWidth()), true);
            if (blurred != quarter) {
                quarter.recycle();
            }
            new Canvas(blurred).drawColor(Math.round(255 * 0.3f) << 24);
            return blurred;
        } catch (Throwable throwable) {
            debugLog("blur thumbnail failed: " + throwable);
            return null;
        }
    }

    public static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    public static void debugLog(String message) {
        if (DEBUG_LOGGING) {
            XposedBridge.log(TAG + ": " + message);
        }
    }
}
