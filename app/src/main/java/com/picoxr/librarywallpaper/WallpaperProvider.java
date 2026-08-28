package com.picoxr.librarywallpaper;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;

public final class WallpaperProvider extends ContentProvider {
    static final String AUTHORITY = "com.picoxr.librarywallpaper.wallpaper";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        WallpaperTarget target = targetFor(uri);
        if (target == null || !"r".equals(mode) || !canRead()) {
            throw new FileNotFoundException(uri.toString());
        }
        WallpaperConfig config = WallpaperStore.loadConfig(getContext(), target);
        File wallpaper = WallpaperStore.file(getContext(), target);
        if (!config.enabled || !wallpaper.isFile()) {
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
        if (target == null || !canRead()) {
            return null;
        }
        MatrixCursor cursor = new MatrixCursor(new String[]{"_display_name", "_size"});
        File wallpaper = WallpaperStore.file(getContext(), target);
        if (WallpaperStore.loadConfig(getContext(), target).enabled && wallpaper.isFile()) {
            cursor.addRow(new Object[]{wallpaper.getName(), wallpaper.length()});
        }
        return cursor;
    }

    @Override
    public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        WallpaperTarget target = WallpaperTarget.fromKey(arg);
        if (!"config".equals(method) || target == null || !canRead()) {
            return null;
        }
        WallpaperConfig config = WallpaperStore.loadConfig(getContext(), target);
        android.os.Bundle result = new android.os.Bundle();
        result.putBoolean("enabled", config.enabled);
        result.putFloat("scale", config.transform.scale);
        result.putFloat("offset_x", config.transform.offsetX);
        result.putFloat("offset_y", config.transform.offsetY);
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
        return Uri.parse("content://" + AUTHORITY + "/" + target.key);
    }

    private WallpaperTarget targetFor(Uri uri) {
        if (!AUTHORITY.equals(uri.getAuthority()) || uri.getPathSegments().size() != 1) {
            return null;
        }
        return WallpaperTarget.fromKey(uri.getLastPathSegment());
    }

    private boolean canRead() {
        if (Binder.getCallingUid() == android.os.Process.myUid()) {
            return true;
        }
        String[] packages = getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (packages == null) {
            return false;
        }
        for (String packageName : packages) {
            if ("com.pvr.appmanager".equals(packageName) || "com.picovr.settings".equals(packageName)) {
                return true;
            }
        }
        return false;
    }
}
