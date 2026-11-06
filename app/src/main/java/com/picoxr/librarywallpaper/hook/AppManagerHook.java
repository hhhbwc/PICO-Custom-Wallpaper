package com.picoxr.librarywallpaper.hook;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;

import com.picoxr.librarywallpaper.core.CornerRadius;
import com.picoxr.librarywallpaper.core.WallpaperTarget;

import java.util.Collections;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Wallpaper for the PICO library window ({@code com.pvr.appmanager}).
 *
 * <p>Panel geometry from PICO's own layout: {@code mAppManiMenuTab} (navigation rail,
 * rounded on the LEFT corners) + {@code app_container}/{@code loadingView} (content pane,
 * rounded on the RIGHT corners) share one wallpaper through a single viewport, so the
 * image flows continuously across the seam.</p>
 */
public final class AppManagerHook implements HookContext.Refreshable {
    private static final String ALL_APP_ACTIVITY = "com.pvr.appmanager.AllAppActivity";

    private final Set<Activity> activities = Collections.newSetFromMap(new java.util.WeakHashMap<>());

    public AppManagerHook() {
        HookContext.register(this);
    }

    public void install(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> activity = XposedHelpers.findClass(ALL_APP_ACTIVITY, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(activity, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity attached = (Activity) param.thisObject;
                activities.add(attached);
                apply(attached);
                attached.getWindow().getDecorView().postOnAnimation(() -> apply(attached));
            }
        });
        HookContext.log("AppManager hook installed in " + lpparam.processName);
    }

    @Override
    public void refreshAll() {
        for (Activity activity : activities.toArray(new Activity[0])) {
            try {
                apply(activity);
            } catch (Throwable throwable) {
                HookContext.debugLog("appmanager refresh failed: " + throwable);
            }
        }
    }

    private void apply(Activity activity) {
        installWallpaper(activity);
        View root = activity.findViewById(id(activity, "rootView"));
        if (root instanceof ViewGroup) {
            TextStyler.styleTree(activity, root, (ViewGroup) root, WallpaperTarget.LIBRARY);
        }
    }

    private void installWallpaper(Activity activity) {
        View root = activity.findViewById(id(activity, "rootView"));
        if (!(root instanceof ViewGroup)) {
            HookContext.log("AppManager wallpaper root unavailable");
            return;
        }
        ViewGroup contentRoot = (ViewGroup) root;
        float radius = panelCornerRadius(activity, "main_content_corner_radius");
        View rail = activity.findViewById(id(activity, "mAppManiMenuTab"));
        View content = activity.findViewById(id(activity, "app_container"));
        View loading = activity.findViewById(id(activity, "loadingView"));
        ViewApplier.install(activity, rail, WallpaperTarget.LIBRARY, contentRoot, CornerRadius.leftRounded(radius));
        ViewApplier.install(activity, content, WallpaperTarget.LIBRARY, contentRoot, CornerRadius.rightRounded(radius));
        ViewApplier.install(activity, loading, WallpaperTarget.LIBRARY, contentRoot, CornerRadius.rightRounded(radius));
    }

    /** Panel corner radius in px; PICO defines it per-window-density (≈28px at 560dpi). */
    static float panelCornerRadius(Activity activity, String dimenName) {
        try {
            int id = activity.getResources().getIdentifier(dimenName, "dimen", HookContext.APP_MANAGER);
            if (id != 0) {
                return activity.getResources().getDimension(id);
            }
        } catch (Throwable throwable) {
            HookContext.debugLog("corner dimen lookup failed: " + throwable);
        }
        return activity.getResources().getDisplayMetrics().density * 8f;
    }

    private static int id(Activity activity, String name) {
        return activity.getResources().getIdentifier(name, "id", HookContext.APP_MANAGER);
    }
}
