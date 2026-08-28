package com.picoxr.librarywallpaper;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

final class AppLanguage {
    static final String CHINESE = "zh-CN";
    static final String ENGLISH = "en";
    static final String RUSSIAN = "ru";
    private static final String PREFERENCES = "app_settings";
    private static final String KEY_LANGUAGE = "language_tag";

    private AppLanguage() {
    }

    static Context localizedContext(Context base) {
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        configuration.setLocale(Locale.forLanguageTag(read(base)));
        return base.createConfigurationContext(configuration);
    }

    static String read(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(KEY_LANGUAGE, CHINESE);
    }

    static void save(Context context, String languageTag) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                .putString(KEY_LANGUAGE, languageTag)
                .apply();
    }
}
