package com.picoxr.librarywallpaper.hook;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;

import com.picoxr.librarywallpaper.core.CornerRadius;
import com.picoxr.librarywallpaper.core.WallpaperTarget;
import com.picoxr.librarywallpaper.draw.GlassDrawable;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Wallpaper for the PICO settings window ({@code com.picovr.settings}): the main
 * settings activity, the quick-settings panel and any other activity that hosts the same
 * two-pane shell.
 *
 * <p>The single most important rule lives in {@link #backgroundHook}: <b>when no
 * wallpaper is configured, this module must not touch a single view</b>. Earlier
 * revisions cleared control backgrounds unconditionally, which washed out the whole
 * settings UI (rows, dividers, chips) even with the feature off.</p>
 */
public final class SettingsHook implements HookContext.Refreshable {
    private static final String UNITY_ACTIVITY = "com.picovr.vrsettingslib.UnityActivity";
    private static final String QUICK_SETTINGS_ACTIVITY =
            "com.picovr.quicksettings.QuickSettingsActivity";

    /** Row containers the settings pages are built from (PICO custom views). */
    private static final String[] SETTINGS_CONTROL_TYPES = {
            "com.picovr.view.SwitchView",
            "android.widget.Switch",
            "androidx.appcompat.widget.SwitchCompat",
            "com.picovr.customviews.DropdownOptionView",
            "com.picovr.customviews.ConfigSwitchLayout",
            "com.picovr.customviews.ConfigItemView",
            "com.picovr.customviews.ConfigItemLayout",
    };
    private static final String[] SETTINGS_NAVIGATION_ITEMS = {
            "item_wifi", "item_controller", "item_bluetooth", "item_brightness",
            "item_lab", "item_general", "item_developer",
    };

    private final Set<Activity> knownActivities =
            Collections.newSetFromMap(new WeakHashMap<>());
    private final Set<Activity> pendingRefresh = ConcurrentHashMap.newKeySet();
    private final Set<View> pendingRows = ConcurrentHashMap.newKeySet();
    private final Set<ViewGroup> hierarchyListeners = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Activity, Integer> refreshSignatures =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Activity, ViewGroup> settingsContainerByActivity =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Activity, ViewGroup> quickContainerByActivity =
            Collections.synchronizedMap(new WeakHashMap<>());

    public SettingsHook() {
        HookContext.register(this);
    }

    public void install(XC_LoadPackage.LoadPackageParam lpparam) {
        hookUnityActivity(lpparam);
        hookViewBackgrounds();
        hookQuickSettings(lpparam);
        hookGenericActivities();
        HookContext.log("Settings hook installed in " + lpparam.processName);
    }

    // ------------------------------------------------------------------ hooks

    private void hookUnityActivity(XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> activity = XposedHelpers.findClass(UNITY_ACTIVITY, lpparam.classLoader);
        XposedHelpers.findAndHookMethod(activity, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity attached = (Activity) param.thisObject;
                knownActivities.add(attached);
                retrySettingsInstall(attached, 0);
            }
        });
    }

    /**
     * Watches every background change in the settings process so re-inflated views stay
     * cleared while a wallpaper is active. Skipped entirely when the target is inactive.
     */
    private void hookViewBackgrounds() {
        XC_MethodHook backgroundHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                View view = (View) param.thisObject;
                if (ViewApplier.isMutating(view) || !HookContext.isSettingsActive()) {
                    return;
                }
                if (isSettingsControl(view)) {
                    ViewApplier.clearWithHoverFeedback(view);
                } else {
                    ViewApplier.restoreSettingsSurface(view);
                }
            }
        };
        XposedHelpers.findAndHookMethod(View.class, "setBackgroundResource",
                Integer.TYPE, backgroundHook);
        XposedHelpers.findAndHookMethod(View.class, "setBackgroundColor",
                Integer.TYPE, backgroundHook);
        XposedHelpers.findAndHookMethod(View.class, "setBackground",
                Drawable.class, backgroundHook);
    }

    private void hookQuickSettings(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            Class<?> quickSettings = XposedHelpers.findClass(QUICK_SETTINGS_ACTIVITY, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(quickSettings, "onCreate", Bundle.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Activity attached = (Activity) param.thisObject;
                    knownActivities.add(attached);
                    retryQuickInstall(attached, 0);
                }
            });
            HookContext.log("Quick settings wallpaper hook installed in " + lpparam.processName);
        } catch (Throwable throwable) {
            HookContext.debugLog("Quick settings activity unavailable: " + throwable);
        }
    }

    private void hookGenericActivities() {
        XposedHelpers.findAndHookMethod(Activity.class, "onCreate", Bundle.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Activity attached = (Activity) param.thisObject;
                if (isDedicatedActivity(attached)) {
                    return;
                }
                knownActivities.add(attached);
                retryGenericInstall(attached, 0);
            }
        });
    }

    private boolean isDedicatedActivity(Activity activity) {
        ClassLoader classLoader = activity.getClass().getClassLoader();
        for (String name : new String[]{UNITY_ACTIVITY, QUICK_SETTINGS_ACTIVITY}) {
            try {
                if (XposedHelpers.findClass(name, classLoader).isInstance(activity)) {
                    return true;
                }
            } catch (Throwable ignored) {
                // class not present in this build
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ install passes

    private void retrySettingsInstall(Activity activity, int attempt) {
        activity.getWindow().getDecorView().postDelayed(
                () -> {
                    int containerId = activity.getResources()
                            .getIdentifier("main_container", "id", HookContext.SETTINGS);
                    View container = containerId == 0 ? null : activity.findViewById(containerId);
                    if (container instanceof ViewGroup
                            && container.getWidth() > 0 && container.getHeight() > 0) {
                        installSettings(activity, (ViewGroup) container);
                    } else if (attempt < 10) {
                        retrySettingsInstall(activity, attempt + 1);
                    } else {
                        retryGenericInstall(activity, 0);
                    }
                },
                attempt == 0 ? 0L : 16L);
    }

    private void retryGenericInstall(Activity activity, int attempt) {
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
                settingsContainerByActivity.put(activity, root);
                installLayoutListener(activity, root);
                applySettingsPage(activity, root);
            } else if (attempt < 10) {
                retryGenericInstall(activity, attempt + 1);
            } else {
                HookContext.log("settings secondary window target unavailable: "
                        + activity.getClass().getName());
            }
        }, attempt == 0 ? 0L : 16L);
    }

    private void retryQuickInstall(Activity activity, int attempt) {
        activity.getWindow().getDecorView().postDelayed(() -> {
            if (activity.isDestroyed() || activity.isFinishing()) {
                return;
            }
            ViewGroup content = activity.findViewById(android.R.id.content);
            ViewGroup root = quickPanelContainer(activity, content);
            if (root != null && root.getWidth() > 0 && root.getHeight() > 0) {
                quickContainerByActivity.put(activity, root);
                root.getViewTreeObserver().addOnGlobalLayoutListener(
                        () -> requestQuickRefresh(activity, root));
                applyQuickPanel(activity, root);
            } else if (attempt < 10) {
                retryQuickInstall(activity, attempt + 1);
            } else {
                HookContext.log("Quick settings wallpaper target unavailable");
            }
        }, attempt == 0 ? 0L : 16L);
    }

    private ViewGroup quickPanelContainer(Activity activity, ViewGroup content) {
        if (content == null) {
            return null;
        }
        int containerId = activity.getResources()
                .getIdentifier("quicksetting_container", "id", HookContext.SETTINGS);
        if (containerId != 0) {
            View card = activity.findViewById(containerId);
            if (card instanceof ViewGroup) {
                return (ViewGroup) card;
            }
        }
        return findBackgroundedContainer(content, 0);
    }

    private ViewGroup findBackgroundedContainer(ViewGroup group, int depth) {
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

    private void installSettings(Activity activity, ViewGroup container) {
        installLayoutListener(activity, container);
        settingsContainerByActivity.put(activity, container);
        synchronized (hierarchyListeners) {
            if (hierarchyListeners.add(container)) {
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

    private void installLayoutListener(final Activity activity, final ViewGroup container) {
        View decor = activity.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        requestSettingsRefresh(activity, container);
                    }
                });
    }

    private void requestSettingsRefresh(final Activity activity, final ViewGroup container) {
        synchronized (pendingRefresh) {
            if (pendingRefresh.add(activity)) {
                activity.getWindow().getDecorView().postOnAnimation(
                        () -> {
                            synchronized (pendingRefresh) {
                                pendingRefresh.remove(activity);
                            }
                            applySettingsPage(activity, container);
                        });
            }
        }
    }

    private void requestQuickRefresh(final Activity activity, final ViewGroup root) {
        synchronized (pendingRefresh) {
            if (pendingRefresh.add(activity)) {
                root.postOnAnimation(() -> {
                    synchronized (pendingRefresh) {
                        pendingRefresh.remove(activity);
                    }
                    applyQuickPanel(activity, root);
                });
            }
        }
    }

    // ------------------------------------------------------------------ main window

    private void applySettingsPage(Activity activity, ViewGroup container) {
        HookContext.warm(activity, WallpaperTarget.SETTINGS);
        if (!HookContext.isSettingsActive()) {
            return; // no wallpaper configured: leave PICO's own UI completely alone
        }
        ViewGroup contentRoot = ViewApplier.parentGroup(container);
        if (contentRoot == null) {
            return;
        }
        installNavigation(activity, contentRoot);
        ViewApplier.install(activity, container, WallpaperTarget.SETTINGS, contentRoot,
                rightRounded(activity));

        int signature = pageSignature(container, contentRoot);
        Integer previous = refreshSignatures.get(activity);
        if (previous != null && previous == signature) {
            return;
        }
        refreshSignatures.put(activity, signature);
        TextStyler.styleTree(activity, contentRoot, contentRoot, WallpaperTarget.SETTINGS);
        for (int index = 0; index < container.getChildCount(); index++) {
            View pageRoot = container.getChildAt(index);
            if (pageRoot != null && pageRoot.getVisibility() == View.VISIBLE) {
                ViewApplier.clearToTransparent(pageRoot);
                installControlRows(activity, pageRoot, contentRoot);
            }
        }
        HookContext.log("Settings wallpaper refreshed for " + container.getChildCount() + " page roots");
    }

    /** Two panes keep PICO's corner layout: rail rounded left, tabs/items transparent. */
    private void installNavigation(Activity activity, ViewGroup contentRoot) {
        View sideContainer = findById(activity, "main_sideContainer");
        View tabs = findById(activity, "main_tabs");
        ViewApplier.install(activity, sideContainer, WallpaperTarget.SETTINGS, contentRoot,
                leftRounded(activity));
        if (tabs != null) {
            ViewApplier.clearToTransparent(tabs);
        }
        for (String item : SETTINGS_NAVIGATION_ITEMS) {
            View navigation = findById(activity, item);
            if (navigation != null) {
                ViewApplier.clearToTransparent(navigation);
            }
        }
    }

    private int pageSignature(ViewGroup container, ViewGroup contentRoot) {
        int signature = contentRoot.getWidth() * 31 + contentRoot.getHeight();
        for (int index = 0; index < container.getChildCount(); index++) {
            View child = container.getChildAt(index);
            signature = signature * 31 + child.getVisibility() * 31
                    + child.getWidth() + child.getHeight()
                    + (child instanceof ViewGroup ? ((ViewGroup) child).getChildCount() : 0);
        }
        return signature;
    }

    private void installControlRows(Activity activity, View pageRoot, ViewGroup contentRoot) {
        if (!(pageRoot instanceof ViewGroup)) {
            return;
        }
        scanControls(activity, (ViewGroup) pageRoot, contentRoot);
        scanSettingsRows((ViewGroup) pageRoot);
    }

    /** Row-shaped containers (PICO sizes them 600–850 × 60–140 px) lose their card bg. */
    private void scanSettingsRows(ViewGroup group) {
        if (group.getVisibility() != View.VISIBLE) {
            return;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (isSettingsRow(child)) {
                ViewApplier.clearSubtree(child, true);
            }
            if (child instanceof ViewGroup) {
                scanSettingsRows((ViewGroup) child);
            }
        }
    }

    private void scanControls(final Activity activity, ViewGroup group, ViewGroup contentRoot) {
        if (group.getVisibility() != View.VISIBLE) {
            return;
        }
        for (int index = 0; index < group.getChildCount(); index++) {
            final View child = group.getChildAt(index);
            if (isSettingsControl(child)) {
                final View row = controlRowOf(child, contentRoot);
                if (row != null && row.getWidth() > 0 && row.getHeight() > 0) {
                    ViewApplier.clearSubtree(row, true);
                } else if (row != null) {
                    synchronized (pendingRows) {
                        if (pendingRows.add(row)) {
                            row.postOnAnimation(() -> {
                                synchronized (pendingRows) {
                                    pendingRows.remove(row);
                                }
                                if (row.getWidth() > 0 && row.getHeight() > 0) {
                                    ViewApplier.clearSubtree(row, true);
                                }
                            });
                        }
                    }
                } else {
                    ViewApplier.clearSubtree(child, true);
                }
            }
            if (child instanceof ViewGroup) {
                scanControls(activity, (ViewGroup) child, contentRoot);
            }
        }
    }

    private View controlRowOf(View control, ViewGroup contentRoot) {
        ViewParent parent = control.getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) parent;
            if (group != contentRoot
                    && (group.getId() == -1 || contentRoot.getId() == -1
                    || group.getId() != contentRoot.getId())) {
                return group;
            }
        }
        return null;
    }

    private boolean isSettingsRow(View view) {
        if (!(view instanceof ViewGroup)) {
            return false;
        }
        int width = view.getWidth();
        int height = view.getHeight();
        return width >= 600 && width <= 850 && height >= 60 && height <= 140;
    }

    boolean isSettingsControl(View view) {
        String className = view.getClass().getName();
        for (String type : SETTINGS_CONTROL_TYPES) {
            if (type.equals(className)
                    || className.endsWith("." + type.substring(type.lastIndexOf('.') + 1))) {
                return true;
            }
        }
        String resourceName = ViewApplier.resourceName(view);
        return resourceName.endsWith("Toggle")
                || resourceName.endsWith("Switch")
                || resourceName.contains("powerModeOption")
                || resourceName.contains("Option")
                || (resourceName.startsWith("controller_") && resourceName.endsWith("Bg"))
                || resourceName.equals("controller_preferredLeft")
                || resourceName.equals("controller_preferredRight");
    }

    private View findById(Activity activity, String name) {
        int id = activity.getResources().getIdentifier(name, "id", HookContext.SETTINGS);
        return id == 0 ? null : activity.findViewById(id);
    }

    private CornerRadius leftRounded(Activity activity) {
        return CornerRadius.leftRounded(AppManagerHook.panelCornerRadius(activity, "Main_ConnerSize"));
    }

    private CornerRadius rightRounded(Activity activity) {
        return CornerRadius.rightRounded(AppManagerHook.panelCornerRadius(activity, "Main_ConnerSize"));
    }

    // ------------------------------------------------------------------ quick panel

    private void applyQuickPanel(Activity activity, ViewGroup root) {
        HookContext.warm(activity, WallpaperTarget.SETTINGS);
        if (!HookContext.isSettingsActive()) {
            return;
        }
        if (!root.isAttachedToWindow() || root.getWidth() == 0 || root.getHeight() == 0) {
            return;
        }
        clipQuickPanelToOriginalCorners(root);
        ViewApplier.install(activity, root, WallpaperTarget.SETTINGS, root, CornerRadius.SQUARE);
        TextStyler.styleTree(activity, root, root, WallpaperTarget.SETTINGS);
        glassQuickPanelButtons(root);
    }

    /** Keeps the card's rounded outline so the wallpaper does not square it off. */
    private void clipQuickPanelToOriginalCorners(ViewGroup card) {
        Drawable background = card.getBackground();
        if (!(background instanceof android.graphics.drawable.GradientDrawable)) {
            return;
        }
        try {
            float radius = ((android.graphics.drawable.GradientDrawable) background).getCornerRadius();
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
        } catch (Throwable throwable) {
            HookContext.debugLog("quick panel corner clip failed: " + throwable);
        }
    }

    private void glassQuickPanelButtons(ViewGroup card) {
        Bitmap blurred = HookContext.blurred(WallpaperTarget.SETTINGS);
        Bitmap full = HookContext.wallpaper(card.getContext(), WallpaperTarget.SETTINGS);
        if (blurred == null || full == null || full.isRecycled()
                || !HookContext.isSettingsActive()
                || card.getWidth() == 0 || card.getHeight() == 0) {
            return;
        }
        float radius = defaultGlassRadius(card);
        glassTree(card, card, blurred, full, radius);
    }

    private void glassTree(ViewGroup card, ViewGroup group, Bitmap blurred, Bitmap full, float defaultRadius) {
        for (int index = 0; index < group.getChildCount(); index++) {
            View child = group.getChildAt(index);
            if (child == null || child.getVisibility() != View.VISIBLE) {
                continue;
            }
            if (isGlassButton(child) || isQuickPanelListItem(child)) {
                float radius = originalRadius(child, defaultRadius);
                ViewApplier.applyGlassSurface(child, card, blurred, full,
                        HookContext.config(card.getContext(), WallpaperTarget.SETTINGS).getTransform(),
                        radius);
                if (child instanceof ViewGroup) {
                    clearNativePillBackground((ViewGroup) child);
                }
            } else if (child instanceof ViewGroup) {
                glassTree(card, (ViewGroup) child, blurred, full, defaultRadius);
            }
        }
    }

    /** Glass candidates: every tile, button and seek layout of the quick settings package. */
    boolean isGlassButton(View view) {
        String className = view.getClass().getName();
        if (!className.startsWith("com.picovr.quicksettings.")) {
            return false;
        }
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return simpleName.endsWith("Tile")
                || simpleName.endsWith("Button")
                || simpleName.endsWith("SeekLayout");
    }

    private boolean isQuickPanelListItem(View view) {
        ViewParent parent = view.getParent();
        return parent != null
                && "androidx.recyclerview.widget.RecyclerView".equals(parent.getClass().getName());
    }

    private float defaultGlassRadius(ViewGroup card) {
        try {
            int id = card.getResources().getIdentifier("Main_ConnerSize", "dimen", HookContext.SETTINGS);
            if (id != 0) {
                return card.getResources().getDimension(id);
            }
        } catch (Throwable throwable) {
            HookContext.debugLog("glass radius lookup failed: " + throwable);
        }
        return card.getResources().getDisplayMetrics().density * 28.0f;
    }

    private float originalRadius(View view, float defaultRadius) {
        try {
            Drawable background = view.getBackground();
            if (background instanceof android.graphics.drawable.GradientDrawable) {
                float radius = ((android.graphics.drawable.GradientDrawable) background).getCornerRadius();
                if (radius > 0.0f) {
                    return radius;
                }
            }
        } catch (Throwable throwable) {
            HookContext.debugLog("glass original radius failed: " + throwable);
        }
        return defaultRadius;
    }

    private void clearNativePillBackground(ViewGroup glassed) {
        for (int index = 0; index < glassed.getChildCount(); index++) {
            View child = glassed.getChildAt(index);
            if (child instanceof ViewGroup) {
                Drawable background = child.getBackground();
                if (background instanceof android.graphics.drawable.StateListDrawable
                        || background instanceof android.graphics.drawable.NinePatchDrawable) {
                    ViewApplier.clearToTransparent(child);
                }
            }
        }
    }

    // ------------------------------------------------------------------ refresh

    @Override
    public void refreshAll() {
        for (Activity activity : knownActivities.toArray(new Activity[0])) {
            try {
                ViewGroup quickRoot = quickContainerByActivity.get(activity);
                if (quickRoot != null) {
                    applyQuickPanel(activity, quickRoot);
                } else {
                    ViewGroup container = settingsContainerByActivity.get(activity);
                    if (container != null) {
                        applySettingsPage(activity, container);
                    }
                }
            } catch (Throwable throwable) {
                HookContext.debugLog("settings refresh failed: " + throwable);
            }
        }
    }
}
