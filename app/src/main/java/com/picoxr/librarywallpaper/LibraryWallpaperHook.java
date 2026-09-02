package com.picoxr.librarywallpaper;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Choreographer;
import android.view.View;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.ViewParent;

import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class LibraryWallpaperHook implements IXposedHookLoadPackage {
    private static final String TAG = "PicoLibraryWallpaper";
    private static final boolean DEBUG_LOGGING = false;
    private static final String APP_MANAGER = "com.pvr.appmanager";
    private static final String SETTINGS = "com.picovr.settings";
    private static final String UNITY_ACTIVITY = "com.picovr.vrsettingslib.UnityActivity";
    private static final String QUICK_SETTINGS_ACTIVITY =
            "com.picovr.quicksettings.QuickSettingsActivity";
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
    private static final Set<Activity> SETTINGS_REFRESH_PENDING =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Activity, Integer> SETTINGS_REFRESH_SIGNATURES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> SETTINGS_PENDING_ROWS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, WallpaperSurface> SETTINGS_SURFACES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> APPLYING_SETTINGS_SURFACES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<View, GlassSurface> GLASS_SURFACES =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<View> TRANSPARENT_SETTINGS_SURFACES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<TextView, ColorStateList> ORIGINAL_TEXT_COLORS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<TextView, Integer> TEXT_STYLE_SIGNATURE =
            Collections.synchronizedMap(new WeakHashMap<>());
    // 进程级壁纸缓存与异步加载:位图解码和配置 binder 调用都不再占用主线程
    private static final Map<WallpaperTarget, Bitmap> WALLPAPER_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<WallpaperTarget, Bitmap> BLURRED_CACHE =
            new ConcurrentHashMap<>();
    private static final Map<WallpaperTarget, WallpaperConfig> WALLPAPER_CONFIG =
            new ConcurrentHashMap<>();
    private static final Map<WallpaperTarget, Long> CONFIG_FETCHED_AT =
            new ConcurrentHashMap<>();
    private static final Set<WallpaperTarget> PENDING_BITMAP_LOADS =
            ConcurrentHashMap.newKeySet();
    private static final Set<WallpaperTarget> BITMAP_UNAVAILABLE_WARNED =
            ConcurrentHashMap.newKeySet();
    private static final Set<WallpaperTarget> PENDING_CONFIG_LOADS =
            ConcurrentHashMap.newKeySet();
    private static final Set<Activity> KNOWN_ACTIVITIES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<Activity> APP_MANAGER_ACTIVITIES =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Activity, ViewGroup> SETTINGS_CONTAINER_BY_ACTIVITY =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Activity, ViewGroup> QUICK_CONTAINER_BY_ACTIVITY =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final ExecutorService BACKGROUND_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "PicoLibraryWallpaper-Loader");
                thread.setDaemon(true);
                return thread;
            });
    // 模块在 Zygote fork 早期加载,主 Looper 尚未就绪,Handler 必须延迟到主线程可用时再创建
    private static volatile Handler mainHandler;
    private static final long CONFIG_TTL_MS = 2000L;

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
                KNOWN_ACTIVITIES.add(activity);
                APP_MANAGER_ACTIVITIES.add(activity);
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
                KNOWN_ACTIVITIES.add(activity);
                retrySettingsInstall(activity, 0);
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
        try {
            Class<?> quickSettings = XposedHelpers.findClass(QUICK_SETTINGS_ACTIVITY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(quickSettings, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity activity = (Activity) param.thisObject;
                    KNOWN_ACTIVITIES.add(activity);
                    retryQuickInstall(activity, 0);
                }
            });
            log("Quick settings wallpaper hook installed in " + lpparam.processName);
        } catch (Throwable throwable) {
            debugLog("Quick settings activity unavailable: " + throwable);
        }
        // 设置进程里的其他二级窗口:通用铺贴,专用路径处理的类除外
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity activity = (Activity) param.thisObject;
                if (isDedicatedActivity(activity)) {
                    return;
                }
                KNOWN_ACTIVITIES.add(activity);
                retryGenericInstall(activity, 0);
            }
        });
        log("Settings hook installed in " + lpparam.processName);
    }

    private static boolean isDedicatedActivity(Activity activity) {
        ClassLoader classLoader = activity.getClass().getClassLoader();
        try {
            if (XposedHelpers.findClass(UNITY_ACTIVITY, classLoader).isInstance(activity)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            if (XposedHelpers.findClass(QUICK_SETTINGS_ACTIVITY, classLoader).isInstance(activity)) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void restoreSettingsSurface(View view) {
        GlassSurface glass = GLASS_SURFACES.get(view);
        if (glass != null && glass.cardRoot != null) {
            reapplyGlassSurface(view, glass);
            return;
        }
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
                // 部分二级页面没有 main_container,退化为通用容器铺贴
                retryGenericInstall(activity, 0);
            }
        }, attempt == 0 ? 0L : 16L);
    }

    private static void retryGenericInstall(Activity activity, int attempt) {
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) {
                return;
            }
            ViewGroup content = activity.findViewById(android.R.id.content);
            ViewGroup root = null;
            if (content != null && content.getChildCount() > 0
                    && content.getChildAt(0) instanceof ViewGroup) {
                root = (ViewGroup) content.getChildAt(0);
            }
            if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
                SETTINGS_CONTAINER_BY_ACTIVITY.put(activity, root);
                installSettingsLayoutListener(activity, root);
                applySettingsPage(activity, root);
                return;
            }
            if (attempt < 10) {
                retryGenericInstall(activity, attempt + 1);
            } else {
                log("settings secondary window target unavailable: " + activity.getClass().getName());
            }
        }, attempt == 0 ? 0L : 16L);
    }

    private static void retryQuickInstall(Activity activity, int attempt) {
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) {
                return;
            }
            ViewGroup content = activity.findViewById(android.R.id.content);
            ViewGroup root = quickPanelContainer(activity, content);
            if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
                QUICK_CONTAINER_BY_ACTIVITY.put(activity, root);
                root.getViewTreeObserver().addOnGlobalLayoutListener(
                        () -> requestQuickRefresh(activity, root));
                applyQuickPanel(activity, root);
                return;
            }
            if (attempt < 10) {
                retryQuickInstall(activity, attempt + 1);
            } else {
                log("Quick settings wallpaper target unavailable");
            }
        }, attempt == 0 ? 0L : 16L);
    }

    private static ViewGroup quickPanelContainer(Activity activity, ViewGroup content) {
        if (content == null) {
            return null;
        }
        // 面板卡片是窗口内居中的 quicksetting_container,不能铺到整个透明窗口根上
        int containerId = activity.getResources().getIdentifier("quicksetting_container", "id", SETTINGS);
        if (containerId != 0) {
            View card = activity.findViewById(containerId);
            if (card instanceof ViewGroup) {
                return (ViewGroup) card;
            }
        }
        return findBackgroundedContainer(content, 0);
    }

    private static ViewGroup findBackgroundedContainer(ViewGroup group, int depth) {
        if (depth > 3) {
            return null;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child instanceof ViewGroup && child.getBackground() != null) {
                return (ViewGroup) child;
            }
            if (child instanceof ViewGroup) {
                ViewGroup found = findBackgroundedContainer((ViewGroup) child, depth + 1);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void requestQuickRefresh(Activity activity, ViewGroup root) {
        synchronized (SETTINGS_REFRESH_PENDING) {
            if (!SETTINGS_REFRESH_PENDING.add(activity)) {
                return;
            }
        }
        root.postOnAnimation(() -> {
            synchronized (SETTINGS_REFRESH_PENDING) {
                SETTINGS_REFRESH_PENDING.remove(activity);
            }
            applyQuickPanel(activity, root);
        });
    }

    private static void applyQuickPanel(Activity activity, ViewGroup root) {
        if (!root.isAttachedToWindow() || root.getWidth() == 0 || root.getHeight() == 0) {
            return;
        }
        clipQuickPanelToOriginalCorners(root);
        // contentRoot 取面板自身:设置壁纸以面板窗口为视口铺满,文字对比度按面板区域统一决策
        install(activity, root, WallpaperTarget.SETTINGS, root, true);
        styleTextTree(activity, root, root, WallpaperTarget.SETTINGS);
        glassQuickPanelButtons(root);
        applyQuestDeviceImages(activity, root);
    }

    // 把快捷面板左侧的 PICO 设备插画换成 Quest 3 的正视渲染图(模块 assets 内嵌)
    private static void applyQuestDeviceImages(Activity activity, ViewGroup root) {
        try {
            replaceDeviceImage(activity, root, "quickSettings_iconHmd", "quest3_hmd.png");
            replaceDeviceImage(activity, root, "quickSettings_iconControllerLeft",
                    "quest3_controller_left.png");
            replaceDeviceImage(activity, root, "quickSettings_iconControllerRight",
                    "quest3_controller_right.png");
        } catch (Throwable throwable) {
            debugLog("quest device images failed: " + throwable);
        }
    }

    private static void replaceDeviceImage(Activity activity, ViewGroup root, String idName,
            String assetName) {
        int id = activity.getResources().getIdentifier(idName, "id", SETTINGS);
        if (id == 0) {
            return;
        }
        View view = root.findViewById(id);
        if (!(view instanceof android.widget.ImageView)) {
            return;
        }
        android.widget.ImageView imageView = (android.widget.ImageView) view;
        try (InputStream stream = activity.getAssets().open(assetName)) {
            android.graphics.drawable.Drawable drawable =
                    android.graphics.drawable.Drawable.createFromStream(stream, assetName);
            if (drawable != null && imageView.getDrawable() != drawable) {
                imageView.setImageDrawable(drawable);
            }
        } catch (Throwable throwable) {
            debugLog("replace " + idName + " failed: " + throwable);
        }
    }

    // 面板快捷格/滑条加毛玻璃:采样壁纸模糊图中按钮背后的区域,叠加高光让控件从壁纸上凸显
    private static void glassQuickPanelButtons(ViewGroup card) {
        Bitmap blurred = BLURRED_CACHE.get(WallpaperTarget.SETTINGS);
        Bitmap full = WALLPAPER_CACHE.get(WallpaperTarget.SETTINGS);
        WallpaperConfig config = WALLPAPER_CONFIG.get(WallpaperTarget.SETTINGS);
        if (blurred == null || full == null || full.isRecycled()
                || config == null || !config.enabled
                || card.getWidth() == 0 || card.getHeight() == 0) {
            return;
        }
        float defaultRadius = defaultGlassRadius(card);
        glassTree(card, card, blurred, full, config.transform, defaultRadius);
    }

    private static void glassTree(ViewGroup card, ViewGroup group, Bitmap blurred, Bitmap full,
            WallpaperTransform transform, float defaultRadius) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (isGlassButton(child) || isQuickPanelListItem(child)) {
                applyGlassSurface(child, card, blurred, full, transform,
                        originalRadius(child, defaultRadius));
                if (child instanceof ViewGroup) {
                    clearNativePillBackground((ViewGroup) child);
                }
                continue;
            }
            if (child instanceof ViewGroup) {
                glassTree(card, (ViewGroup) child, blurred, full, transform, defaultRadius);
            }
        }
    }

    private static boolean isQuickPanelListItem(View view) {
        ViewParent parent = view.getParent();
        return parent != null
                && "androidx.recyclerview.widget.RecyclerView".equals(parent.getClass().getName());
    }

    private static boolean isGlassButton(View view) {
        String className = view.getClass().getName();
        if (!className.startsWith("com.picovr.quicksettings.")) {
            return false;
        }
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return simpleName.endsWith("Tile") || simpleName.endsWith("SettingButton")
                || simpleName.endsWith("SeekLayout");
    }

    private static float defaultGlassRadius(ViewGroup card) {
        try {
            int id = card.getResources().getIdentifier("Main_ConnerSize", "dimen", SETTINGS);
            if (id != 0) {
                return card.getResources().getDimension(id);
            }
        } catch (Throwable ignored) {
        }
        return 28f * card.getResources().getDisplayMetrics().density;
    }

    private static float originalRadius(View view, float defaultRadius) {
        try {
            Drawable background = view.getBackground();
            if (background instanceof android.graphics.drawable.GradientDrawable) {
                float radius = ((android.graphics.drawable.GradientDrawable) background)
                        .getCornerRadius();
                if (radius > 0f) {
                    return radius;
                }
            }
        } catch (Throwable ignored) {
        }
        return defaultRadius;
    }

    private static void applyGlassSurface(View view, ViewGroup card, Bitmap blurred, Bitmap full,
            WallpaperTransform transform, float radius) {
        // 记录被替换背景的固有尺寸与常态:固有尺寸保住 wrap_content 布局,常态用于识别默认药丸
        Drawable replaced = view.getBackground();
        int intrinsicWidth = replaced == null ? -1 : replaced.getIntrinsicWidth();
        int intrinsicHeight = replaced == null ? -1 : replaced.getIntrinsicHeight();
        android.graphics.drawable.Drawable.ConstantState defaultState =
                replaced == null ? null : replaced.getConstantState();
        GlassSurface existing = GLASS_SURFACES.get(view);
        if (existing != null && existing.cardRoot == card) {
            Drawable background = view.getBackground();
            if (background instanceof GlassDrawable
                    && ((GlassDrawable) background).matches(blurred, transform)) {
                return;
            }
        }
        GLASS_SURFACES.put(view, new GlassSurface(card, blurred, transform, full.getWidth(),
                full.getHeight(), radius, intrinsicWidth, intrinsicHeight, defaultState));
        startGlassFrameLoop();
        synchronized (APPLYING_SETTINGS_SURFACES) {
            if (APPLYING_SETTINGS_SURFACES.contains(view)) {
                return;
            }
            APPLYING_SETTINGS_SURFACES.add(view);
            try {
                view.setBackground(new GlassDrawable(view, card, blurred, transform,
                        full.getWidth(), full.getHeight(), radius,
                        intrinsicWidth, intrinsicHeight));
            } finally {
                APPLYING_SETTINGS_SURFACES.remove(view);
            }
        }
    }

    // 清掉玻璃按钮内层的原生药丸背景(9-patch 自带浅色描边),避免与玻璃边框形成双框
    private static void clearNativePillBackground(ViewGroup glassed) {
        for (int index = 0; index < glassed.getChildCount(); index++) {
            View child = glassed.getChildAt(index);
            if (!(child instanceof ViewGroup)) {
                continue;
            }
            Drawable background = child.getBackground();
            if (background instanceof android.graphics.drawable.StateListDrawable
                    || background instanceof android.graphics.drawable.NinePatchDrawable) {
                child.setBackground(null);
            }
        }
    }

    private static void reapplyGlassSurface(View view, GlassSurface glass) {
        synchronized (APPLYING_SETTINGS_SURFACES) {
            if (APPLYING_SETTINGS_SURFACES.contains(view) || !view.isAttachedToWindow()
                    || glass.cardRoot.getWidth() == 0) {
                return;
            }
            // 系统在玻璃之后换上的背景是状态图案(如开启态高亮),叠加保留;默认药丸则不叠加
            Drawable incoming = view.getBackground();
            Drawable stateOverlay = null;
            if (incoming != null && !sameConstantState(incoming, glass.defaultState)) {
                stateOverlay = incoming;
            }
            APPLYING_SETTINGS_SURFACES.add(view);
            try {
                GlassDrawable glassDrawable = new GlassDrawable(view, glass.cardRoot, glass.blurred,
                        glass.transform, glass.imageWidth, glass.imageHeight, glass.radius,
                        glass.intrinsicWidth, glass.intrinsicHeight);
                if (stateOverlay != null) {
                    glassDrawable.setStateOverlay(stateOverlay);
                }
                view.setBackground(glassDrawable);
            } finally {
                APPLYING_SETTINGS_SURFACES.remove(view);
            }
        }
    }

    private static boolean sameConstantState(Drawable first,
            android.graphics.drawable.Drawable.ConstantState second) {
        if (first == null || second == null) {
            return false;
        }
        android.graphics.drawable.Drawable.ConstantState firstState = first.getConstantState();
        return firstState != null && firstState.equals(second);
    }

    // 硬件加速下滚动只回放子 View 的显示列表,不会调用 draw():逐帧比对玻璃按钮的
    // 屏幕位置,变化才 invalidate,强制重录渲染指令,让玻璃区域实时跟随内容移动
    private static volatile boolean glassFrameLoopRunning;

    private static void startGlassFrameLoop() {
        if (glassFrameLoopRunning) {
            return;
        }
        glassFrameLoopRunning = true;
        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                try {
                    tickGlassViews();
                } catch (Throwable throwable) {
                    debugLog("glass frame tick failed: " + throwable);
                }
                if (!GLASS_SURFACES.isEmpty()) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    glassFrameLoopRunning = false;
                }
            }
        });
    }

    private static void tickGlassViews() {
        View[] views = GLASS_SURFACES.keySet().toArray(new View[0]);
        ViewGroup lastCard = null;
        int cardX = 0;
        int cardY = 0;
        for (View view : views) {
            GlassSurface glass = GLASS_SURFACES.get(view);
            if (glass == null || !view.isAttachedToWindow()
                    || !(view.getBackground() instanceof GlassDrawable)) {
                GLASS_SURFACES.remove(view);
                continue;
            }
            GlassDrawable drawable = (GlassDrawable) view.getBackground();
            view.getLocationInWindow(glass.viewViewport);
            // 面板卡片只有一个,卡片位置每帧只查一次
            if (glass.cardRoot != lastCard) {
                lastCard = glass.cardRoot;
                lastCard.getLocationInWindow(glass.cardViewport);
                cardX = glass.cardViewport[0];
                cardY = glass.cardViewport[1];
            }
            float relativeX = glass.viewViewport[0] - cardX;
            float relativeY = glass.viewViewport[1] - cardY;
            float quantum = drawable.samplingQuantum();
            boolean moved = Float.isNaN(glass.lastX)
                    || Math.abs(relativeX - glass.lastX) >= quantum
                    || Math.abs(relativeY - glass.lastY) >= quantum;
            boolean resized = glass.cardWidth != glass.cardRoot.getWidth()
                    || glass.cardHeight != glass.cardRoot.getHeight();
            if (moved || resized) {
                glass.lastX = relativeX;
                glass.lastY = relativeY;
                glass.cardWidth = glass.cardRoot.getWidth();
                glass.cardHeight = glass.cardRoot.getHeight();
                view.invalidate();
            }
        }
    }

    // 壁纸替换掉原圆角背景后,按原背景的圆角半径裁剪,保持面板原生外形
    private static void clipQuickPanelToOriginalCorners(final ViewGroup card) {
        Drawable background = card.getBackground();
        if (!(background instanceof android.graphics.drawable.GradientDrawable)) {
            return;
        }
        final float radius;
        try {
            radius = ((android.graphics.drawable.GradientDrawable) background).getCornerRadius();
        } catch (Throwable throwable) {
            return;
        }
        if (radius <= 0f) {
            return;
        }
        card.setClipToOutline(true);
        card.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
    }

    private static void installSettings(Activity activity, ViewGroup container) {
        installSettingsLayoutListener(activity, container);
        SETTINGS_CONTAINER_BY_ACTIVITY.put(activity, container);
        synchronized (SETTINGS_CONTAINERS) {
            if (SETTINGS_CONTAINERS.add(container)) {
                container.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                    @Override
                    public void onChildViewAdded(View parent, View child) {
                        requestSettingsRefresh(activity, container);
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
                () -> requestSettingsRefresh(activity, container));
    }

    private static void requestSettingsRefresh(Activity activity, ViewGroup container) {
        synchronized (SETTINGS_REFRESH_PENDING) {
            if (!SETTINGS_REFRESH_PENDING.add(activity)) {
                return;
            }
        }
        activity.getWindow().getDecorView().postOnAnimation(() -> {
            synchronized (SETTINGS_REFRESH_PENDING) {
                SETTINGS_REFRESH_PENDING.remove(activity);
            }
            applySettingsPage(activity, container);
        });
    }

    private static void applySettingsPage(Activity activity, ViewGroup container) {
        ViewGroup contentRoot = parentGroup(container);
        if (contentRoot == null) {
            return;
        }
        // install 本身已带去重(参数未变就跳过 setBackground),每次布局都执行开销极小
        installSettingsNavigation(activity, contentRoot);
        install(activity, container, WallpaperTarget.SETTINGS, contentRoot, true);
        int signature = settingsPageSignature(container, contentRoot);
        Integer previousSignature = SETTINGS_REFRESH_SIGNATURES.get(activity);
        if (previousSignature != null && previousSignature == signature) {
            return;
        }
        SETTINGS_REFRESH_SIGNATURES.put(activity, signature);
        styleTextTree(activity, contentRoot, contentRoot, WallpaperTarget.SETTINGS);
        int children = container.getChildCount();
        for (int index = 0; index < children; index++) {
            View pageRoot = container.getChildAt(index);
            if (pageRoot != null && pageRoot.getVisibility() == View.VISIBLE) {
                makeSettingsPageTransparent(activity, pageRoot, contentRoot);
                installControlRows(activity, pageRoot, contentRoot);
            }
        }
        log("Settings wallpaper refreshed for " + children + " page roots");
    }

    private static int settingsPageSignature(ViewGroup container, ViewGroup contentRoot) {
        int signature = 31 * contentRoot.getWidth() + contentRoot.getHeight();
        int children = container.getChildCount();
        for (int index = 0; index < children; index++) {
            View child = container.getChildAt(index);
            signature = 31 * signature + (child.getVisibility() * 31)
                    + child.getWidth() + child.getHeight()
                    + (child instanceof ViewGroup ? ((ViewGroup) child).getChildCount() : 0);
        }
        return signature;
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
        if (group.getVisibility() != View.VISIBLE) {
            return;
        }
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
        if (group.getVisibility() != View.VISIBLE) {
            return;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (isSettingsControl(child)) {
                clearControlBackground(child);
                View row = findControlRow(child, contentRoot);
                if (row != null) {
                    if (row.getWidth() > 0 && row.getHeight() > 0) {
                        clearControlBackgroundTree(row);
                        debugLog("Settings control row made transparent for " + describe(child) + " -> " + describe(row));
                    } else {
                        synchronized (SETTINGS_PENDING_ROWS) {
                            if (SETTINGS_PENDING_ROWS.add(row)) {
                                row.postOnAnimation(() -> {
                                    synchronized (SETTINGS_PENDING_ROWS) {
                                        SETTINGS_PENDING_ROWS.remove(row);
                                    }
                                    applyControlRow(activity, child, row, contentRoot);
                                });
                            }
                        }
                        debugLog("Settings control row pending: " + describe(child) + " -> " + describe(row));
                    }
                } else {
                    debugLog("Settings control detected: " + describe(child) + " parents=" + describeParents(child, contentRoot));
                }
            }
            if (child instanceof ViewGroup) {
                scanControls(activity, (ViewGroup) child, contentRoot);
            }
        }
    }

    private static void applyControlRow(Activity activity, View control, View row, ViewGroup contentRoot) {
        if (row.getWidth() > 0 && row.getHeight() > 0) {
            clearControlBackgroundTree(row);
            debugLog("Settings control row made transparent for " + describe(control) + " -> " + describe(row));
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
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        Bitmap bitmap = cachedWallpaper(activity, target);
        WallpaperConfig config = cachedConfig(activity, target);
        if (bitmap == null || !config.enabled
                || contentRoot.getWidth() <= 0 || contentRoot.getHeight() <= 0) {
            return;
        }
        // 整页统一决策:同一界面的文字颜色深浅保持一致,不再逐个文字按局部亮度变化
        boolean light = pageTextLight(bitmap, config, contentRoot);
        styleTextTree(view, target, light);
    }

    private static void styleTextTree(View view, WallpaperTarget target, boolean light) {
        if (view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view instanceof TextView && shouldStyleText(view)) {
            styleTextView((TextView) view, target, light);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                styleTextTree(group.getChildAt(index), target, light);
            }
        }
    }

    private static boolean pageTextLight(Bitmap bitmap, WallpaperConfig config, ViewGroup contentRoot) {
        int width = contentRoot.getWidth();
        int height = contentRoot.getHeight();
        WallpaperTransform.RenderValues values = config.transform.render(
                bitmap.getWidth(), bitmap.getHeight(), width, height);
        float left = -values.translateX / values.scale;
        float top = -values.translateY / values.scale;
        float right = (width - values.translateX) / values.scale;
        float bottom = (height - values.translateY) / values.scale;
        // 壁纸带深色遮罩,判断阈值按遮罩后的实际亮度放大
        float threshold = TextContrast.LIGHT_THRESHOLD / (1f - CenterCropDrawable.SCRIM_ALPHA);
        return TextContrast.isLightArea(bitmap, left, top, right, bottom, 5, threshold);
    }

    private static boolean shouldStyleText(View view) {
        if (view.getVisibility() != View.VISIBLE || view.getWidth() <= 0 || view.getHeight() <= 0) {
            return false;
        }
        String className = view.getClass().getName();
        return !className.endsWith("SwitchView") && !className.endsWith("SwitchCompat");
    }

    private static void styleTextView(TextView textView, WallpaperTarget target, boolean light) {
        int signature = (light ? 1 : 0) * 31 + target.ordinal();
        Integer previousSignature = TEXT_STYLE_SIGNATURE.get(textView);
        if (previousSignature != null && previousSignature == signature) {
            return;
        }
        TEXT_STYLE_SIGNATURE.put(textView, signature);
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
            if (BITMAP_UNAVAILABLE_WARNED.add(target)) {
                log(target.key + " wallpaper image unavailable");
            }
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
                    applyBackground(view, bitmap, config, contentRoot, viewportX, viewportY, target);
                } finally {
                    APPLYING_SETTINGS_SURFACES.remove(view);
                }
            }
            SETTINGS_SURFACES.put(view, new WallpaperSurface(activity, contentRoot, false));
        } else {
            applyBackground(view, bitmap, config, contentRoot, viewportX, viewportY, target);
        }
    }

    private static void applyBackground(View view, Bitmap bitmap, WallpaperConfig config,
            ViewGroup contentRoot, int viewportX, int viewportY, WallpaperTarget target) {
        android.graphics.drawable.Drawable existing = view.getBackground();
        if (existing instanceof CenterCropDrawable
                && ((CenterCropDrawable) existing).matches(bitmap, config.transform,
                contentRoot.getWidth(), contentRoot.getHeight(), viewportX, viewportY)) {
            return;
        }
        view.setBackground(new CenterCropDrawable(bitmap, config.transform, contentRoot.getWidth(),
                contentRoot.getHeight(), viewportX, viewportY));
        log(target.key + " wallpaper applied to " + describe(view) + " at "
                + viewportX + "," + viewportY + " in " + contentRoot.getWidth() + "x"
                + contentRoot.getHeight());
    }

    private static WallpaperConfig cachedConfig(Activity activity, WallpaperTarget target) {
        WallpaperConfig config = WALLPAPER_CONFIG.get(target);
        long now = SystemClock.elapsedRealtime();
        Long fetchedAt = CONFIG_FETCHED_AT.get(target);
        if (fetchedAt == null || now - fetchedAt > CONFIG_TTL_MS) {
            requestConfigRefresh(activity, target);
        }
        return config == null ? WallpaperConfig.disabled() : config;
    }

    private static void requestConfigRefresh(Activity activity, WallpaperTarget target) {
        if (!PENDING_CONFIG_LOADS.add(target)) {
            return;
        }
        WallpaperConfig previous = WALLPAPER_CONFIG.get(target);
        Context context = activity.getApplicationContext();
        BACKGROUND_EXECUTOR.execute(() -> {
            try {
                WallpaperConfig fresh = readConfig(context, target);
                CONFIG_FETCHED_AT.put(target, SystemClock.elapsedRealtime());
                PENDING_CONFIG_LOADS.remove(target);
                if (configEquals(previous, fresh)) {
                    return;
                }
                WALLPAPER_CONFIG.put(target, fresh);
                postRefresh();
            } catch (Throwable throwable) {
                PENDING_CONFIG_LOADS.remove(target);
                debugLog("config refresh failed: " + throwable);
            }
        });
    }

    private static boolean configEquals(WallpaperConfig first, WallpaperConfig second) {
        if (first == null || second == null) {
            return first == second;
        }
        return first.enabled == second.enabled
                && first.transform.scale == second.transform.scale
                && first.transform.offsetX == second.transform.offsetX
                && first.transform.offsetY == second.transform.offsetY;
    }

    private static WallpaperConfig readConfig(Context context, WallpaperTarget target) {
        try {
            Bundle result = context.getContentResolver().call(
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
        Bitmap bitmap = WALLPAPER_CACHE.get(target);
        if (bitmap != null && !bitmap.isRecycled()) {
            return bitmap;
        }
        requestWallpaperLoad(activity, target);
        return null;
    }

    private static void requestWallpaperLoad(Activity activity, WallpaperTarget target) {
        if (!PENDING_BITMAP_LOADS.add(target)) {
            return;
        }
        Context context = activity.getApplicationContext();
        BACKGROUND_EXECUTOR.execute(() -> {
            try {
                Bitmap loaded = loadWallpaper(context, target);
                PENDING_BITMAP_LOADS.remove(target);
                if (loaded == null) {
                    return;
                }
                BITMAP_UNAVAILABLE_WARNED.remove(target);
                WALLPAPER_CACHE.put(target, loaded);
                BLURRED_CACHE.put(target, blurredThumbnail(loaded));
                // 新位图内容与旧的不同,文字对比度需要重新决策
                TEXT_STYLE_SIGNATURE.clear();
                postRefresh();
            } catch (Throwable throwable) {
                PENDING_BITMAP_LOADS.remove(target);
                debugLog("wallpaper load failed: " + throwable);
            }
        });
    }

    private static Handler mainHandler() {
        Handler handler = mainHandler;
        if (handler == null) {
            synchronized (LibraryWallpaperHook.class) {
                handler = mainHandler;
                if (handler == null) {
                    mainHandler = handler = new Handler(Looper.getMainLooper());
                }
            }
        }
        return handler;
    }

    private static void postRefresh() {
        try {
            mainHandler().post(LibraryWallpaperHook::refreshKnownActivities);
        } catch (Throwable throwable) {
            debugLog("post refresh failed: " + throwable);
        }
    }

    private static Bitmap blurredThumbnail(Bitmap bitmap) {
        try {
            int quarterWidth = Math.max(1, bitmap.getWidth() / 4);
            int quarterHeight = Math.max(1, bitmap.getHeight() / 4);
            Bitmap quarter = Bitmap.createScaledBitmap(bitmap, quarterWidth, quarterHeight, true);
            int blurWidth = Math.max(1, bitmap.getWidth() / 16);
            Bitmap blurred = Bitmap.createScaledBitmap(quarter, blurWidth,
                    Math.max(1, blurWidth * quarter.getHeight() / quarter.getWidth()), true);
            if (blurred != quarter) {
                quarter.recycle();
            }
            // 玻璃采样与带遮罩的壁纸保持一致
            Canvas scrimCanvas = new Canvas(blurred);
            scrimCanvas.drawColor(Math.round(255 * CenterCropDrawable.SCRIM_ALPHA) << 24);
            return blurred;
        } catch (Throwable throwable) {
            debugLog("blur thumbnail failed: " + throwable);
            return null;
        }
    }

    private static void refreshKnownActivities() {
        for (Activity activity : KNOWN_ACTIVITIES.toArray(new Activity[0])) {
            try {
                ViewGroup quickRoot = QUICK_CONTAINER_BY_ACTIVITY.get(activity);
                if (quickRoot != null) {
                    applyQuickPanel(activity, quickRoot);
                    continue;
                }
                ViewGroup container = SETTINGS_CONTAINER_BY_ACTIVITY.get(activity);
                if (container != null) {
                    applySettingsPage(activity, container);
                } else if (APP_MANAGER_ACTIVITIES.contains(activity)) {
                    installAppManager(activity);
                    styleAppManagerText(activity);
                }
            } catch (Throwable throwable) {
                debugLog("refresh failed: " + throwable);
            }
        }
    }

    private static Bitmap loadWallpaper(Context context, WallpaperTarget target) {
        try (InputStream stream = context.getContentResolver().openInputStream(WallpaperProvider.uriFor(target))) {
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

    private static void debugLog(String message) {
        if (DEBUG_LOGGING) {
            XposedBridge.log(TAG + ": " + message);
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }

    private static final class GlassSurface {
        final ViewGroup cardRoot;
        final Bitmap blurred;
        final WallpaperTransform transform;
        final int imageWidth;
        final int imageHeight;
        final float radius;
        final int intrinsicWidth;
        final int intrinsicHeight;
        final android.graphics.drawable.Drawable.ConstantState defaultState;
        final int[] viewViewport = new int[2];
        final int[] cardViewport = new int[2];
        float lastX = Float.NaN;
        float lastY = Float.NaN;
        int cardWidth;
        int cardHeight;

        GlassSurface(ViewGroup cardRoot, Bitmap blurred, WallpaperTransform transform,
                int imageWidth, int imageHeight, float radius, int intrinsicWidth,
                int intrinsicHeight, android.graphics.drawable.Drawable.ConstantState defaultState) {
            this.cardRoot = cardRoot;
            this.blurred = blurred;
            this.transform = transform;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.radius = radius;
            this.intrinsicWidth = intrinsicWidth;
            this.intrinsicHeight = intrinsicHeight;
            this.defaultState = defaultState;
        }
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
