package com.picoxr.librarywallpaper;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/** Persists and applies the module UI language (Chinese default, English, Russian). */
public final class AppLanguage {
    public static final String CHINESE = "zh-CN";
    public static final String ENGLISH = "en";
    public static final String RUSSIAN = "ru";

    private static final String KEY_LANGUAGE = "language_tag";
    private static final String PREFERENCES = "app_settings";

    private AppLanguage() {
    }

    public static Context localizedContext(Context base) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(read(base)));
        return base.createConfigurationContext(configuration);
    }

    public static String read(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, CHINESE);
    }

    public static void save(Context context, String languageTag) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_LANGUAGE, languageTag)
                .apply();
    }
}
