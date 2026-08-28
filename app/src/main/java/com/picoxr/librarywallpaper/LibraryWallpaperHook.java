package com.picoxr.librarywallpaper;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
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
    private static final String[] SETTINGS_CONTROL_TYPES = {
            "com.picovr.view.SwitchView",
            "android.widget.Switch",
            "androidx.appcompat.widget.SwitchCompat",
            "com.picovr.customviews.DropdownOptionView",
            "com.picovr.customviews.ConfigSwitchLayout",
            "com.picovr.customviews.ConfigItemView",
            "com.picovr.customviews.ConfigItemLayout"
    };
    private static final Set<ViewGroup> SETTINGS_CONTAINERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Activity> SETTINGS_LAYOUT_LISTENERS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, WallpaperSurface> SETTINGS_SURFACES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> APPLYING_SETTINGS_SURFACES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Activity, Map<WallpaperTarget, Bitmap>> BITMAP_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Activity, Map<WallpaperTarget, WallpaperConfig>> CONFIG_CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> TRANSPARENT_SETTINGS_SURFACES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<TextView, ColorStateList> ORIGINAL_TEXT_COLORS =
            Collections.synchronizedMap(new WeakHashMap<>());

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
                installAppManager(activity);
                styleAppManagerText(activity);
                activity.getWindow().getDecorView().postOnAnimation(() -> {
                    installAppManager(activity);
                    styleAppManagerText(activity);
                });
            }
        });
        log("AppManager hook installed in " + lpparam.processName);
    }

    private static void hookSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> activity = XposedHelpers.findClass("com.picovr.vrsettingslib.UnityActivity", lpparam.classLoader);
        XposedHelpers.findAndHookMethod(activity, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                retrySettingsInstall(activity, 0);
                activity.getWindow().getDecorView().postOnAnimation(
                        () -> retrySettingsInstall(activity, 0));
            }
        });
        XC_MethodHook backgroundHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                if (isSettingsControl(view)) {
                    clearControlBackground(view);
                    return;
                }
                restoreSettingsSurface(view);
            }
        };
        XposedHelpers.findAndHookMethod(View.class, "setBackgroundResource", int.class, backgroundHook);
        XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor", int.class, backgroundHook);
        XposedHelpers.findAndHookMethod(View.class, "setBackground", android.graphics.drawable.Drawable.class,
                backgroundHook);
        log("Settings hook installed in " + lpparam.processName);
    }

    private static void restoreSettingsSurface(View view) {
        WallpaperSurface surface = SETTINGS_SURFACES.get(view);
        synchronized (APPLYING_SETTINGS_SURFACES) {
            if (surface == null || APPLYING_SETTINGS_SURFACES.contains(view)) {
                return;
            }
            APPLYING_SETTINGS_SURFACES.add(view);
            try {
                if (surface.transparent) {
                    view.setBackgroundColor(Color.TRANSPARENT);
                } else {
                    install(surface.activity, view, WallpaperTarget.SETTINGS,
                            surface.contentRoot, true);
                }
            } finally {
                APPLYING_SETTINGS_SURFACES.remove(view);
            }
        }
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
        }, attempt == 0 ? 0L : 16L);
    }

    private static void installSettings(Activity activity, ViewGroup container) {
        installSettingsLayoutListener(activity, container);
        synchronized (SETTINGS_CONTAINERS) {
            if (SETTINGS_CONTAINERS.add(container)) {
                container.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        applySettingsPage(activity, container);
                        child.postOnAnimation(() -> applySettingsPage(activity, container));
                    }

                    @Override
                    public void onChildViewRemoved(View parent, View child) {
                    }
                });
            }
        }
        applySettingsPage(activity, container);
    }

    private static void installSettingsLayoutListener(Activity activity, ViewGroup container) {
        synchronized (SETTINGS_LAYOUT_LISTENERS) {
            if (!SETTINGS_LAYOUT_LISTENERS.add(activity)) {
                return;
            }
        }
        activity.getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(
                () -> applySettingsPage(activity, container));
    }

    private static void applySettingsPage(Activity activity, ViewGroup container) {
        ViewGroup contentRoot = parentGroup(container);
        if (contentRoot == null) {
            return;
        }
        installSettingsNavigation(activity, contentRoot);
        install(activity, container, WallpaperTarget.SETTINGS, contentRoot, true);
        scanControls(activity, contentRoot, contentRoot);
        scanSettingsRows(activity, contentRoot, contentRoot);
        styleTextTree(activity, contentRoot, contentRoot, WallpaperTarget.SETTINGS);
        int children = container.getChildCount();
        for (int index = 0; index < children; index++) {
            View pageRoot = container.getChildAt(index);
            if (pageRoot != null && pageRoot.getVisibility() == View.VISIBLE) {
                makeSettingsPageTransparent(activity, pageRoot, contentRoot);
                installControlRows(activity, pageRoot, contentRoot);
                styleTextTree(activity, pageRoot, contentRoot, WallpaperTarget.SETTINGS);
            }
        }
        log("Settings wallpaper refreshed for " + children + " page roots");
    }

    private static void makeSettingsPageTransparent(Activity activity, View pageRoot, ViewGroup contentRoot) {
        synchronized (APPLYING_SETTINGS_SURFACES) {
            if (APPLYING_SETTINGS_SURFACES.add(pageRoot)) {
                try {
                    pageRoot.setBackgroundColor(Color.TRANSPARENT);
                } finally {
                    APPLYING_SETTINGS_SURFACES.remove(pageRoot);
                }
            }
        }
        SETTINGS_SURFACES.put(pageRoot, new WallpaperSurface(activity, contentRoot, true));
    }

    private static void installControlRows(Activity activity, View pageRoot, ViewGroup contentRoot) {
        if (!(pageRoot instanceof ViewGroup)) {
            return;
        }
        scanControls(activity, (ViewGroup) pageRoot, contentRoot);
        scanSettingsRows(activity, (ViewGroup) pageRoot, contentRoot);
    }

    private static void scanSettingsRows(Activity activity, ViewGroup group, ViewGroup contentRoot) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (isSettingsRow(child, contentRoot)) {
                clearControlBackgroundTree(child);
            }
            if (child instanceof ViewGroup) {
                scanSettingsRows(activity, (ViewGroup) child, contentRoot);
            }
        }
    }

    private static boolean isSettingsRow(View view, ViewGroup contentRoot) {
        if (!(view instanceof ViewGroup) || view == contentRoot) {
            return false;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        return width >= 600 && width <= 850 && height >= 60 && height <= 140;
    }

    private static void scanControls(Activity activity, ViewGroup group, ViewGroup contentRoot) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (isSettingsControl(child)) {
                clearControlBackground(child);
                View row = findControlRow(child, contentRoot);
                if (row != null) {
                    if (row.getHeight() > 0 && row.getHeight() <= 140) {
                        clearControlBackgroundTree(row);
                    }

                    if (row.getWidth() > 0 && row.getHeight() > 0) {
                        clearControlBackgroundTree(row);
                        log("Settings control row made transparent for " + describe(child) + " -> " + describe(row));
                    } else {
                        row.postOnAnimation(() -> applyControlRow(activity, child, row, contentRoot));
                        log("Settings control row pending: " + describe(child) + " -> " + describe(row));
                    }
                } else {
                    log("Settings control detected: " + describe(child) + " parents=" + describeParents(child, contentRoot));
                }
            }
            if (child instanceof ViewGroup) {
                scanControls(activity, (ViewGroup) child, contentRoot);
            }
        }
    }

    private static void applyControlRow(Activity activity, View control, View row, ViewGroup contentRoot) {
        if (row.getWidth() > 0 && row.getHeight() > 0) {
            if (row.getHeight() <= 140) {
                clearControlBackgroundTree(row);
            }
            clearControlBackgroundTree(row);
            log("Settings control row made transparent for " + describe(control) + " -> " + describe(row));
        }
    }

    private static String describeParents(View view, ViewGroup contentRoot) {
        StringBuilder result = new StringBuilder();
        View current = view;
        while (current != null && current != contentRoot) {
            if (result.length() > 0) {
                result.append(" <- ");
            }
            result.append(describe(current));
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return result.toString();
    }

    private static void clearControlBackgroundTree(View view) {
        clearControlBackground(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                clearControlBackgroundTree(group.getChildAt(index));
            }
        }
    }

    private static boolean isSettingsControl(View view) {
        String className = view.getClass().getName();
        for (String type : SETTINGS_CONTROL_TYPES) {
            if (type.equals(className) || className.endsWith("." + type.substring(type.lastIndexOf('.') + 1))) {
                return true;
            }
        }
        String resourceName = resourceName(view);
        return resourceName.endsWith("Toggle") || resourceName.endsWith("Switch")
                || resourceName.contains("powerModeOption") || resourceName.contains("Option")
                || resourceName.startsWith("controller_") && resourceName.endsWith("Bg")
                || resourceName.equals("controller_preferredLeft")
                || resourceName.equals("controller_preferredRight");
    }

    private static boolean isSwitchControl(View view) {
        String className = view.getClass().getName();
        return className.endsWith("SwitchView") || className.endsWith("SwitchCompat")
                || className.equals("android.widget.Switch") || className.equals("android.widget.SwitchButton");
    }

    private static void clearControlBackground(View view) {
        synchronized (APPLYING_SETTINGS_SURFACES) {
            if (APPLYING_SETTINGS_SURFACES.add(view)) {
                try {
                    view.setBackgroundColor(Color.TRANSPARENT);
                    view.setBackgroundTintList(null);
                    if (isOpaqueSettingsContainer(view)
                            || isHandednessOptionContainer(view)) {
                        view.setForeground(null);
                    }
                } finally {
                    APPLYING_SETTINGS_SURFACES.remove(view);
                }
            }
        }
    }

    private static boolean isHandednessOptionContainer(View view) {
        if (!(view.getParent() instanceof ViewGroup)) {
            return false;
        }
        ViewParent parent = view.getParent();
        if (!(parent instanceof ViewGroup)) {
            return false;
        }
        ViewGroup group = (ViewGroup) parent;
        for (int index = 0; index < group.getChildCount(); index++) {
            String name = resourceName(group.getChildAt(index));
            if (name.equals("controller_preferredLeft") || name.equals("controller_preferredRight")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isOpaqueSettingsContainer(View view) {
        String className = view.getClass().getName();
        String name = resourceName(view);
        return className.endsWith("ConfigItemView")
                || className.endsWith("ConfigItemLayout")
                || className.endsWith("HoveredLinearLayout")
                || className.endsWith("OSUILinearLayout")
                || name.equals("controller_preferredLeft")
                || name.equals("controller_preferredRight");
    }

    private static View findControlRow(View control, ViewGroup contentRoot) {
        ViewParent parent = control.getParent();
        if (!(parent instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) parent;
        if (group == contentRoot
                || (group.getId() != View.NO_ID
                && contentRoot.getId() != View.NO_ID
                && group.getId() == contentRoot.getId())) {
            return null;
        }
        return group;
    }

    private static void installSettingsNavigation(Activity activity, ViewGroup contentRoot) {
        View sideContainer = findById(activity, "main_sideContainer");
        installSettingsSurface(activity, sideContainer, contentRoot, false);
        View tabs = findById(activity, "main_tabs");
        installSettingsSurface(activity, tabs, contentRoot, true);
        if (!(tabs instanceof ViewGroup)) {
            return;
        }
        for (String item : SETTINGS_NAVIGATION_ITEMS) {
            installSettingsSurface(activity, findById(activity, item), contentRoot, true);
        }
    }

    private static View findById(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", SETTINGS);
        return id == 0 ? null : activity.findViewById(id);
    }

    private static void installSettingsSurface(Activity activity, View view, ViewGroup contentRoot,
            boolean transparent) {
        if (view == null) {
            return;
        }
        SETTINGS_SURFACES.put(view, new WallpaperSurface(activity, contentRoot, transparent));
        if (transparent) {
            synchronized (APPLYING_SETTINGS_SURFACES) {
                APPLYING_SETTINGS_SURFACES.add(view);
                try {
                    view.setBackgroundColor(Color.TRANSPARENT);
                } finally {
                    APPLYING_SETTINGS_SURFACES.remove(view);
                }
            }
        } else {
            install(activity, view, WallpaperTarget.SETTINGS, contentRoot, true);
        }
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

    private static void styleAppManagerText(Activity activity) {
        View root = activity.findViewById(activity.getResources().getIdentifier("rootView", "id", APP_MANAGER));
        if (root instanceof ViewGroup) {
            styleTextTree(activity, root, (ViewGroup) root, WallpaperTarget.LIBRARY);
        }
    }

    private static void styleTextTree(Activity activity, View view, ViewGroup contentRoot, WallpaperTarget target) {
        if (view instanceof TextView && shouldStyleText(view)) {
            styleTextView(activity, (TextView) view, contentRoot, target);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                styleTextTree(activity, group.getChildAt(index), contentRoot, target);
            }
        }
    }

    private static boolean shouldStyleText(View view) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        String className = view.getClass().getName();
        return !className.endsWith("SwitchView") && !className.endsWith("SwitchCompat");
    }

    private static void styleTextView(Activity activity, TextView textView, ViewGroup contentRoot,
            WallpaperTarget target) {
        Bitmap bitmap = cachedWallpaper(activity, target);
        WallpaperConfig config = cachedConfig(activity, target);
        if (bitmap == null || !config.enabled || contentRoot.getWidth() <= 0 || contentRoot.getHeight() <= 0) {
            return;
        }
        int[] rootLocation = new int[2];
        int[] viewLocation = new int[2];
        contentRoot.getLocationInWindow(rootLocation);
        textView.getLocationInWindow(viewLocation);
        float viewportX = viewLocation[0] - rootLocation[0];
        float viewportY = viewLocation[1] - rootLocation[1];
        WallpaperTransform.RenderValues values = config.transform.render(
                bitmap.getWidth(), bitmap.getHeight(), contentRoot.getWidth(), contentRoot.getHeight());
        float sourceX = (viewportX + textView.getWidth() / 2f - values.translateX) / values.scale;
        float sourceY = (viewportY + textView.getHeight() / 2f - values.translateY) / values.scale;
        boolean light = TextContrast.isLightRegion(bitmap, sourceX, sourceY, 12);
        ColorStateList original = ORIGINAL_TEXT_COLORS.get(textView);
        if (original == null) {
            original = textView.getTextColors();
            ORIGINAL_TEXT_COLORS.put(textView, original);
        }
        if (light) {
            textView.setTextColor(darkenColor(original.getColorForState(
                    textView.getDrawableState(), original.getDefaultColor())));
            textView.setShadowLayer(2.2f, 0f, 1f, Color.argb(155, 255, 255, 255));
        } else {
            textView.setTextColor(original);
            textView.setShadowLayer(2.2f, 0f, 1f, Color.argb(185, 0, 0, 0));
        }
    }

    private static int darkenColor(int color) {
        return Color.argb(Color.alpha(color),
                (int) (Color.red(color) * 0.24f),
                (int) (Color.green(color) * 0.24f),
                (int) (Color.blue(color) * 0.24f));
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
        WallpaperConfig config = cachedConfig(activity, target);
        if (!config.enabled) {
            return;
        }
        if (settingsSurface) {
            SETTINGS_SURFACES.put(view, new WallpaperSurface(activity, contentRoot, false));
        }
        Bitmap bitmap = cachedWallpaper(activity, target);
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
            SETTINGS_SURFACES.put(view, new WallpaperSurface(activity, contentRoot, false));
        } else {
            view.setBackground(new CenterCropDrawable(bitmap, config.transform, contentRoot.getWidth(),
                    contentRoot.getHeight(), viewportX, viewportY));
        }
        log(target.key + " wallpaper applied to " + describe(view) + " at "
                + viewportX + "," + viewportY + " in " + contentRoot.getWidth() + "x"
                + contentRoot.getHeight());
    }

    private static WallpaperConfig cachedConfig(Activity activity, WallpaperTarget target) {
        Map<WallpaperTarget, WallpaperConfig> cache = CONFIG_CACHE.get(activity);
        if (cache == null) {
            cache = new java.util.EnumMap<>(WallpaperTarget.class);
            CONFIG_CACHE.put(activity, cache);
        }
        WallpaperConfig config = cache.get(target);
        if (config == null) {
            config = readConfig(activity, target);
            cache.put(target, config);
        }
        return config;
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

    private static Bitmap cachedWallpaper(Activity activity, WallpaperTarget target) {
        Map<WallpaperTarget, Bitmap> cache = BITMAP_CACHE.get(activity);
        if (cache == null) {
            cache = new java.util.EnumMap<>(WallpaperTarget.class);
            BITMAP_CACHE.put(activity, cache);
        }
        Bitmap bitmap = cache.get(target);
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        bitmap = loadWallpaper(activity, target);
        if (bitmap != null) {
            cache.put(target, bitmap);
        }
        return bitmap;
    }

    private static Bitmap loadWallpaper(Activity activity, WallpaperTarget target) {
        try (InputStream stream = activity.getContentResolver().openInputStream(WallpaperProvider.uriFor(target))) {
            return BitmapFactory.decodeStream(stream);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String resourceName(View view) {
        if (view == null || view.getId() == View.NO_ID) {
            return "no-id";
        }
        try {
            return view.getResources().getResourceEntryName(view.getId());
        } catch (Throwable ignored) {
            return "no-id";
        }
    }

    private static String describe(View view) {
        String resourceName = resourceName(view);
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
        final boolean transparent;

        WallpaperSurface(Activity activity, ViewGroup contentRoot, boolean transparent) {
            this.activity = activity;
            this.contentRoot = contentRoot;
            this.transparent = transparent;
        }
    }
}
