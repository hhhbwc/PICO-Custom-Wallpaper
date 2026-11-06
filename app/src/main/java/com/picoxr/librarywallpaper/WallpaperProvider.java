package com.picoxr.librarywallpaper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;

import com.picoxr.librarywallpaper.core.WallpaperConfig;
import com.picoxr.librarywallpaper.core.WallpaperTarget;
import com.picoxr.librarywallpaper.hook.HookContext;
import com.picoxr.librarywallpaper.data.WallpaperStore;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Read-only bridge that hands the wallpaper PNG and its config to the hooked processes.
 *
 * <p>ACL: only the module itself and the two hooked packages may read. Everything else
 * (insert/update/delete included) is rejected, so the provider is effectively a
 * two-app image/config feed.</p>
 */
public final class WallpaperProvider extends ContentProvider {
    public static final String AUTHORITY = "com.picoxr.librarywallpaper.wallpaper";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        WallpaperTarget target = targetFor(uri);
        Context context = getContext();
        if (target == null || context == null || !"r".equals(mode) || !canRead()) {
            throw new FileNotFoundException(uri.toString());
        }
        WallpaperConfig config = WallpaperStore.loadConfig(context, target);
        File wallpaper = WallpaperStore.file(context, target);
        if (!config.isEnabled() || !wallpaper.isFile()) {
            throw new FileNotFoundException(wallpaper.getAbsolutePath());
        }
        return ParcelFileDescriptor.open(wallpaper, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public String getType(Uri uri) {
        return targetFor(uri) == null ? null : "image/png";
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        WallpaperTarget target = targetFor(uri);
        Context context = getContext();
        if (target == null || context == null || !canRead()) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{"_display_name", "_size"});
        File wallpaper = WallpaperStore.file(context, target);
        if (WallpaperStore.loadConfig(context, target).isEnabled() && wallpaper.isFile()) {
            cursor.addRow(new Object[]{wallpaper.getName(), wallpaper.length()});
        }
        return cursor;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        WallpaperTarget target = WallpaperTarget.fromKey(arg);
        Context context = getContext();
        if (!"config".equals(method) || target == null || context == null || !canRead()) {
            return null;
        }
        WallpaperConfig config = WallpaperStore.loadConfig(context, target);
        Bundle result = new Bundle();
        result.putBoolean("enabled", config.isEnabled());
        result.putFloat("scale", config.getTransform().getScale());
        result.putFloat("offset_x", config.getTransform().getOffsetX());
        result.putFloat("offset_y", config.getTransform().getOffsetY());
        return result;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    static Uri uriFor(WallpaperTarget target) {
        return Uri.parse("content://" + AUTHORITY + "/" + target.getKey());
    }

    private WallpaperTarget targetFor(Uri uri) {
        if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1) {
            return null;
        }
        return WallpaperTarget.fromKey(uri.getLastPathSegment());
    }

    private boolean canRead() {
        if (Binder.getCallingUid() == Process.myUid()) {
            return true;
        }
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null) {
            return false;
        }
        for (String packageName : packages) {
            if (HookContext.APP_MANAGER.equals(packageName) || HookContext.SETTINGS.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
