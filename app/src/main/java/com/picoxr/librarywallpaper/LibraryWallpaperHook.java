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
import java.util.Map;
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
    private static final String[] SETTINGS_NAVIGATION_ITEMS = {
            "item_wifi", "item_controller", "item_bluetooth", "item_brightness", "item_lab",
            "item_general", "item_developer"
    };
    private static final Set<ViewGroup> SETTINGS_CONTAINERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, WallpaperSurface> SETTINGS_SURFACES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> APPLYING_SETTINGS_SURFACES =
            Collections.newSetFromMap(new WeakHashMap<>());

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
        XposedHelpers.findAndHookMethod(View.class, "setBackgroundResource", int.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        View view = (View) param.thisObject;
                        WallpaperSurface surface = SETTINGS_SURFACES.get(view);
                        synchronized (APPLYING_SETTINGS_SURFACES) {
                            if (surface != null && !APPLYING_SETTINGS_SURFACES.contains(view)) {
                                install(surface.activity, view, WallpaperTarget.SETTINGS,
                                        surface.contentRoot, true);
                            }
                        }
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
        ViewGroup contentRoot = parentGroup(container);
        if (contentRoot == null) {
            return;
        }
        installSettingsNavigation(activity, contentRoot);
        install(activity, container, WallpaperTarget.SETTINGS, contentRoot, true);
        int children = container.getChildCount();
        for (int index = 0; index < children; index++) {
            View pageRoot = container.getChildAt(index);
            if (pageRoot != null && pageRoot.getVisibility() == View.VISIBLE) {
                install(activity, pageRoot, WallpaperTarget.SETTINGS, contentRoot, true);
            }
        }
        log("Settings wallpaper refreshed for " + children + " page roots");
    }

    private static void installSettingsNavigation(Activity activity, ViewGroup contentRoot) {
        installSettingsSurface(activity, findById(activity, "main_sideContainer"), contentRoot);
        View tabs = findById(activity, "main_tabs");
        installSettingsSurface(activity, tabs, contentRoot);
        if (!(tabs instanceof ViewGroup)) {
            return;
        }
        for (String item : SETTINGS_NAVIGATION_ITEMS) {
            installSettingsSurface(activity, findById(activity, item), contentRoot);
        }
    }

    private static View findById(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", SETTINGS);
        return id == 0 ? null : activity.findViewById(id);
    }

    private static void installSettingsSurface(Activity activity, View view, ViewGroup contentRoot) {
        if (view == null) {
            return;
        }
        SETTINGS_SURFACES.put(view, new WallpaperSurface(activity, contentRoot));
        install(activity, view, WallpaperTarget.SETTINGS, contentRoot, false);
    }

    private static void installAppManager(Activity activity) {
        View root = activity.findViewById(activity.getResources().getIdentifier("rootView", "id", APP_MANAGER));
        if (!(root instanceof ViewGroup)) {
            log("AppManager wallpaper root unavailable");
            return;
        }
        ViewGroup contentRoot = (ViewGroup) root;
        install(activity, activity.findViewById(activity.getResources().getIdentifier(
                "mAppManiMenuTab", "id", APP_MANAGER)), WallpaperTarget.LIBRARY, contentRoot, false);
        install(activity, activity.findViewById(activity.getResources().getIdentifier(
                "app_container", "id", APP_MANAGER)), WallpaperTarget.LIBRARY, contentRoot, false);
        install(activity, activity.findViewById(activity.getResources().getIdentifier(
                "loadingView", "id", APP_MANAGER)), WallpaperTarget.LIBRARY, contentRoot, false);
    }

    private static ViewGroup parentGroup(View view) {
        ViewParent parent = view.getParent();
        return parent instanceof ViewGroup ? (ViewGroup) parent : null;
    }

    private static void install(Activity activity, View view, WallpaperTarget target, ViewGroup contentRoot,
            boolean settingsSurface) {
        if (view == null || contentRoot == null || contentRoot.getWidth() == 0 || contentRoot.getHeight() == 0) {
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
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        contentRoot.getLocationInWindow(rootLocation);
        view.getLocationInWindow(viewLocation);
        int viewportX = viewLocation[0] - rootLocation[0];
        int viewportY = viewLocation[1] - rootLocation[1];
        if (settingsSurface) {
            synchronized (APPLYING_SETTINGS_SURFACES) {
                APPLYING_SETTINGS_SURFACES.add(view);
                try {
                    view.setBackground(new CenterCropDrawable(bitmap, config.transform, contentRoot.getWidth(),
                            contentRoot.getHeight(), viewportX, viewportY));
                } finally {
                    APPLYING_SETTINGS_SURFACES.remove(view);
                }
            }
            SETTINGS_SURFACES.put(view, new WallpaperSurface(activity, contentRoot));
        } else {
            view.setBackground(new CenterCropDrawable(bitmap, config.transform, contentRoot.getWidth(),
                    contentRoot.getHeight(), viewportX, viewportY));
        }
        log(target.key + " wallpaper applied to " + describe(view) + " at "
                + viewportX + "," + viewportY + " in " + contentRoot.getWidth() + "x"
                + contentRoot.getHeight());
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

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static final class WallpaperSurface {
        final Activity activity;
        final ViewGroup contentRoot;

        WallpaperSurface(Activity activity, ViewGroup contentRoot) {
            this.activity = activity;
            this.contentRoot = contentRoot;
        }
    }
}
