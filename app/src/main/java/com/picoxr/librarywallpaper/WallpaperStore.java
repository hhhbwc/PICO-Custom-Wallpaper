package com.picoxr.librarywallpaper;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

final class WallpaperStore {
    private static final String PREFERENCES = "wallpaper_state";

    private WallpaperStore() {
    }

    static File file(Context context, WallpaperTarget target) {
        return new File(context.getFilesDir(), WallpaperState.fileName(target));
    }

    static Bitmap load(Context context, WallpaperTarget target) {
        return BitmapFactory.decodeFile(file(context, target).getAbsolutePath());
    }

    static WallpaperConfig loadConfig(Context context, WallpaperTarget target) {
        SharedPreferences preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        if (!preferences.getBoolean(key(target, "enabled"), false)) {
            return WallpaperConfig.disabled();
        }
        WallpaperTransform transform = new WallpaperTransform(
                preferences.getFloat(key(target, "scale"), 1f),
                preferences.getFloat(key(target, "offset_x"), 0f),
                preferences.getFloat(key(target, "offset_y"), 0f));
        return new WallpaperConfig(true, transform);
    }

    static boolean saveImage(Context context, Uri uri, WallpaperTarget target) {
        Bitmap bitmap = decodeScaled(context.getContentResolver(), uri);
        if (bitmap == null) {
            return false;
        }
        File output = file(context, target);
        File temporary = new File(context.getCacheDir(), output.getName() + ".tmp");
        try (FileOutputStream stream = new FileOutputStream(temporary)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                return false;
            }
            if (output.exists() && !output.delete()) {
                return false;
            }
            return temporary.renameTo(output);
        } catch (Exception ignored) {
            temporary.delete();
            return false;
        } finally {
            bitmap.recycle();
        }
    }

    static void saveConfig(Context context, WallpaperTarget target, WallpaperConfig config) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putInt("version", WallpaperConfig.VERSION)
                .putBoolean(key(target, "enabled"), config.enabled)
                .putFloat(key(target, "scale"), config.transform.scale)
                .putFloat(key(target, "offset_x"), config.transform.offsetX)
                .putFloat(key(target, "offset_y"), config.transform.offsetY)
                .apply();
    }

    static boolean restore(Context context, WallpaperTarget target) {
        File output = file(context, target);
        boolean removed = !output.exists() || output.delete();
        if (removed) {
            saveConfig(context, target, WallpaperConfig.disabled());
        }
        return removed;
    }

    private static String key(WallpaperTarget target, String name) {
        return target.key + "_" + name;
    }

    private static Bitmap decodeScaled(ContentResolver resolver, Uri uri) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(stream, null, bounds);
        } catch (Exception ignored) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = WallpaperState.sampleSizeFor(bounds.outWidth, bounds.outHeight,
                WallpaperState.MAX_DIMENSION);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = resolver.openInputStream(uri)) {
            return BitmapFactory.decodeStream(stream, null, options);
        } catch (Exception ignored) {
            return null;
        }
    }
}
