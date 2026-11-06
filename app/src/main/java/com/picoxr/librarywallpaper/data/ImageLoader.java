package com.picoxr.librarywallpaper.data;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.picoxr.librarywallpaper.core.WallpaperState;

import java.io.InputStream;

/** Single decoding path used by the picker, the editor preview and the hook read path. */
public final class ImageLoader {
    private ImageLoader() {
    }

    /** Decodes a picked image, subsampled so its long side fits {@link WallpaperState#MAX_DIMENSION}. */
    public static Bitmap load(Context context, Uri uri) {
        return loadCapped(context.getContentResolver(), uri, WallpaperState.MAX_DIMENSION);
    }

    /** Decodes with the same cap, from an already opened stream pair provider. */
    public static Bitmap loadCapped(ContentResolver resolver, Uri uri, int maxDimension) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream head = resolver.openInputStream(uri)) {
            BitmapFactory.decodeStream(head, null, bounds);
        } catch (Exception e) {
            return null;
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = WallpaperState.sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimension);
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream body = resolver.openInputStream(uri)) {
            return BitmapFactory.decodeStream(body, null, options);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * One-shot decode of the wallpaper served by the module provider. The provider hands
     * out a fresh pipe for every open, so the two-pass (bounds, then decode) strategy just
     * re-opens; on failure the caller backs off instead of retrying every frame.
     */
    public static Bitmap decodeProviderWallpaper(Context context, Uri uri) {
        return loadCapped(context.getContentResolver(), uri, WallpaperState.MAX_DIMENSION);
    }
}
