package com.picoxr.librarywallpaper.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;

import com.picoxr.librarywallpaper.core.WallpaperConfig;
import com.picoxr.librarywallpaper.core.WallpaperState;
import com.picoxr.librarywallpaper.core.WallpaperTarget;
import com.picoxr.librarywallpaper.core.WallpaperTransform;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Module-side persistence: one PNG per target plus a shared preferences file for the
 * enabled flag and placement transform. The hook processes read the same state through
 * {@code WallpaperProvider}.
 */
public final class WallpaperStore {
    private static final String PREFERENCES = "wallpaper_state";

    private WallpaperStore() {
    }

    public static File file(Context context, WallpaperTarget target) {
        return new File(context.getFilesDir(), WallpaperState.fileName(target));
    }

    public static WallpaperConfig loadConfig(Context context, WallpaperTarget target) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        if (!preferences.getBoolean(key(target, "enabled"), false)) {
            return WallpaperConfig.disabled();
        }
        WallpaperTransform transform = new WallpaperTransform(
                preferences.getFloat(key(target, "scale"), 1.0f),
                preferences.getFloat(key(target, "offset_x"), 0.0f),
                preferences.getFloat(key(target, "offset_y"), 0.0f));
        return new WallpaperConfig(true, transform);
    }

    /** Atomically replaces the stored image: write temp, delete old, rename. */
    public static boolean saveImage(Context context, Uri uri, WallpaperTarget target) {
        Bitmap bitmap = ImageLoader.load(context, uri);
        if (bitmap == null) {
            return false;
        }
        File output = file(context, target);
        File temporary = new File(context.getCacheDir(), output.getName() + ".tmp");
        try {
            FileOutputStream stream = new FileOutputStream(temporary);
            try {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                    return false;
                }
                stream.close();
                if (output.exists() && !output.delete()) {
                    return false;
                }
                return temporary.renameTo(output);
            } finally {
                stream.close();
                bitmap.recycle();
            }
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }
    }

    public static void saveConfig(Context context, WallpaperTarget target, WallpaperConfig config) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putInt("version", WallpaperConfig.VERSION)
                .putBoolean(key(target, "enabled"), config.isEnabled())
                .putFloat(key(target, "scale"), config.getTransform().getScale())
                .putFloat(key(target, "offset_x"), config.getTransform().getOffsetX())
                .putFloat(key(target, "offset_y"), config.getTransform().getOffsetY())
                .apply();
    }

    public static boolean restore(Context context, WallpaperTarget target) {
        File output = file(context, target);
        boolean removed = !output.exists() || output.delete();
        if (removed) {
            saveConfig(context, target, WallpaperConfig.disabled());
        }
        return removed;
    }

    private static String key(WallpaperTarget target, String name) {
        return target.getKey() + "_" + name;
    }
}
