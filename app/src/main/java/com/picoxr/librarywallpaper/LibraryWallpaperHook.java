package com.picoxr.librarywallpaper;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.io.InputStream;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class LibraryWallpaperHook implements IXposedHookLoadPackage {
    private static final String TAG = "PicoLibraryWallpaper";
    private static final String APP_MANAGER = "com.pvr.appmanager";
    private static final String SETTINGS = "com.picovr.settings";
    private static final String SIDE_NAVIGATION = "com.bytedance.osui.grouplist.OSUISideNavigation";
    private static final Set<ViewGroup> SETTINGS_CONTAINERS = Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (APP_MANAGER.equals(lpparam.packageName)) {
                hookAppManager(lpparam);
            } else if (SETTINGS.equals(lpparam.packageName)) {
                hookSettings(lpparam);
            }
        } catch (Throwable throwable) {
            log("hook installation failed: " + throwable);
        }
    }

    private static void hookAppManager(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> activity = XposedHelpers.findClass("com.pvr.appmanager.AllAppActivity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(activity, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                activity.getWindow().getDecorView().post(() -> installAppManager(activity));
            }
        });
        log("AppManager hook installed in " + lpparam.processName);
    }

    private static void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> activity = XposedHelpers.findClass("com.picovr.vrsettingslib.UnityActivity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(activity, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                retrySettingsInstall((Activity) param.thisObject, 0);
            }
        });
        log("Settings hook installed in " + lpparam.processName);
    }

    private static void retrySettingsInstall(Activity activity, int attempt) {
        activity.getWindow().getDecorView().postDelayed(() -> {
            int containerId = activity.getResources().getIdentifier("main_container", "id", SETTINGS);
            View container = containerId == 0 ? null : activity.findViewById(containerId);
            if (container instanceof ViewGroup && container.getWidth() > 0 && container.getHeight() > 0) {
                installSettings(activity, (ViewGroup) container);
            } else if (attempt < 10) {
                retrySettingsInstall(activity, attempt + 1);
            } else {
                log("Settings wallpaper target unavailable");
            }
        }, 150L);
    }

    private static void installSettings(Activity activity, ViewGroup container) {
        synchronized (SETTINGS_CONTAINERS) {
            if (SETTINGS_CONTAINERS.add(container)) {
                container.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        parent.post(() -> applySettingsPage(activity, container));
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {
                    }
                });
            }
        }
        applySettingsPage(activity, container);
    }

    private static void applySettingsPage(Activity activity, ViewGroup container) {
        install(activity, findSettingsNavigation(container), WallpaperTarget.SETTINGS);
        install(activity, container, WallpaperTarget.SETTINGS);
        int children = container.getChildCount();
        for (int index = 0; index < children; index++) {
            View pageRoot = container.getChildAt(index);
            if (pageRoot != null && pageRoot.getVisibility() == View.VISIBLE) {
                install(activity, pageRoot, WallpaperTarget.SETTINGS);
            }
        }
        log("Settings wallpaper refreshed for " + children + " page roots");
    }

    private static View findSettingsNavigation(View container) {
        View current = container;
        while (current != null) {
            if (isSideNavigation(current)) {
                return current;
            }
            ViewParent parent = current.getParent();
            if (!(parent instanceof ViewGroup)) {
                return null;
            }
            ViewGroup group = (ViewGroup) parent;
            for (int index = 0; index < group.getChildCount(); index++) {
                View navigation = findSideNavigation(group.getChildAt(index));
                if (navigation != null) {
                    return navigation;
                }
            }
            current = group;
        }
        return null;
    }

    private static View findSideNavigation(View view) {
        if (isSideNavigation(view)) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View navigation = findSideNavigation(group.getChildAt(index));
            if (navigation != null) {
                return navigation;
            }
        }
        return null;
    }

    private static boolean isSideNavigation(View view) {
        return view != null && SIDE_NAVIGATION.equals(view.getClass().getName());
    }

    private static void installAppManager(Activity activity) {
        int menuId = activity.getResources().getIdentifier("mAppManiMenuTab", "id", APP_MANAGER);
        int containerId = activity.getResources().getIdentifier("app_container", "id", APP_MANAGER);
        int loadingId = activity.getResources().getIdentifier("loadingView", "id", APP_MANAGER);
        install(activity, activity.findViewById(menuId), WallpaperTarget.LIBRARY);
        install(activity, activity.findViewById(containerId), WallpaperTarget.LIBRARY);
        install(activity, activity.findViewById(loadingId), WallpaperTarget.LIBRARY);
    }

    private static void install(Activity activity, View view, WallpaperTarget target) {
        if (view == null) {
            return;
        }
        WallpaperConfig config = readConfig(activity, target);
        if (!config.enabled) {
            return;
        }
        Bitmap bitmap = loadWallpaper(activity, target);
        if (bitmap == null) {
            log(target.key + " wallpaper image unavailable");
            return;
        }
        view.setBackground(new CenterCropDrawable(bitmap, config.transform));
        log(target.key + " wallpaper applied to " + describe(view));
    }

    private static String describe(View view) {
        String resourceName = "no-id";
        if (view.getId() != View.NO_ID) {
            try {
                resourceName = view.getResources().getResourceEntryName(view.getId());
            } catch (Throwable ignored) {
                resourceName = "id=" + view.getId();
            }
        }
        return resourceName + " " + view.getClass().getSimpleName() + " "
                + view.getWidth() + "x" + view.getHeight();
    }

    private static WallpaperConfig readConfig(Activity activity, WallpaperTarget target) {
        try {
            Bundle result = activity.getContentResolver().call(
                    WallpaperProvider.uriFor(target), "config", target.key, null);
            if (result == null || !result.getBoolean("enabled", false)) {
                return WallpaperConfig.disabled();
            }
            return new WallpaperConfig(true, new WallpaperTransform(
                    result.getFloat("scale", 1f),
                    result.getFloat("offset_x", 0f),
                    result.getFloat("offset_y", 0f)));
        } catch (Throwable ignored) {
            return WallpaperConfig.disabled();
        }
    }

    private static Bitmap loadWallpaper(Activity activity, WallpaperTarget target) {
        try (InputStream stream = activity.getContentResolver().openInputStream(WallpaperProvider.uriFor(target))) {
            return BitmapFactory.decodeStream(stream);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
