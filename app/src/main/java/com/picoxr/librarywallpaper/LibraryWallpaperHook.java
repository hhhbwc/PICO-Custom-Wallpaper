package com.picoxr.librarywallpaper;

import com.picoxr.librarywallpaper.hook.AppManagerHook;
import com.picoxr.librarywallpaper.hook.HookContext;
import com.picoxr.librarywallpaper.hook.SettingsHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed entry point (referenced from {@code assets/xposed_init} — keep the FQN stable).
 * Dispatches to the per-target hooks; one instance per target process.
 */
public class LibraryWallpaperHook implements IXposedHookLoadPackage {
    private static AppManagerHook appManagerHook;
    private static SettingsHook settingsHook;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (HookContext.APP_MANAGER.equals(lpparam.packageName)) {
                synchronized (LibraryWallpaperHook.class) {
                    if (appManagerHook == null) {
                        appManagerHook = new AppManagerHook();
                    }
                }
                appManagerHook.install(lpparam);
            } else if (HookContext.SETTINGS.equals(lpparam.packageName)) {
                synchronized (LibraryWallpaperHook.class) {
                    if (settingsHook == null) {
                        settingsHook = new SettingsHook();
                    }
                }
                settingsHook.install(lpparam);
            }
        } catch (Throwable throwable) {
            HookContext.log("hook installation failed: " + throwable);
        }
    }
}
