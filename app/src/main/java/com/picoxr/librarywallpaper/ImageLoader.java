package com.picoxr.librarywallpaper;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.InputStream;

final class ImageLoader {
    private ImageLoader() {
    }

    static Bitmap load(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();
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
