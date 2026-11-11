package com.upuaut.xposedsearch;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import java.io.File;

public class BrowserOptConfigManager {

    private static final String TAG = "XposedSearch";
    public static final String PREF_NAME = "xposed_browser_opt";
    public static final String AUTHORITY = "com.upuaut.xposedsearch.provider";

    public static final String KEY_REMOVE_SPLASH_AD = "remove_splash_ad";
    public static final String KEY_BYPASS_WEB_LIMIT = "bypass_web_limit";
    public static final String KEY_DISABLE_NEWS_FEED = "disable_news_feed";
    public static final String KEY_FORCE_SIMPLE_MODE = "force_simple_mode";
    public static final String KEY_REMOVE_SEARCH_APP_PROMO = "remove_search_app_promo";
    public static final String KEY_REMOVE_WINDOW_LIMIT = "remove_window_limit";
    public static final String KEY_REMOVE_DOWNLOAD_BOTTOM_AD = "remove_download_bottom_ad";
    public static final String KEY_REMOVE_DOWNLOAD_DIALOG_AD = "remove_download_dialog_ad";
    public static final String KEY_FILTER_CLOUD_ENGINE_CONFIG = "filter_cloud_engine_config";
    public static final String KEY_BLOCK_MARKETING_CHANNELS = "block_marketing_channels";
    public static final String KEY_BLOCK_ERROR_PAGE_RECOMMEND = "block_error_page_recommend";

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean getOption(Context context, String key, boolean defaultValue) {
        if (context == null) return defaultValue;
        return getPrefs(context).getBoolean(key, defaultValue);
    }

    public static void setOption(Context context, String key, boolean value) {
        if (context == null) return;
        getPrefs(context).edit().putBoolean(key, value).commit();
        makePrefsWorldReadable(context);
        notifyChange(context);
    }

    public static boolean isRemoveSplashAd(Context context) {
        return getOption(context, KEY_REMOVE_SPLASH_AD, true);
    }

    public static boolean isBypassWebLimit(Context context) {
        return getOption(context, KEY_BYPASS_WEB_LIMIT, true);
    }

    public static boolean isDisableNewsFeed(Context context) {
        return getOption(context, KEY_DISABLE_NEWS_FEED, true);
    }

    public static boolean isForceSimpleMode(Context context) {
        return getOption(context, KEY_FORCE_SIMPLE_MODE, true);
    }

    public static boolean isRemoveSearchAppPromo(Context context) {
        return getOption(context, KEY_REMOVE_SEARCH_APP_PROMO, true);
    }

    public static boolean isRemoveWindowLimit(Context context) {
        return getOption(context, KEY_REMOVE_WINDOW_LIMIT, true);
    }

    public static boolean isRemoveDownloadBottomAd(Context context) {
        return getOption(context, KEY_REMOVE_DOWNLOAD_BOTTOM_AD, true);
    }

    public static boolean isRemoveDownloadDialogAd(Context context) {
        return getOption(context, KEY_REMOVE_DOWNLOAD_DIALOG_AD, true);
    }

    public static boolean isFilterCloudEngineConfig(Context context) {
        return getOption(context, KEY_FILTER_CLOUD_ENGINE_CONFIG, true);
    }

    public static boolean isBlockErrorPageRecommend(Context context) {
        return getOption(context, KEY_BLOCK_ERROR_PAGE_RECOMMEND, true);
    }

    public static boolean isBlockMarketingChannels(Context context) {
        return getOption(context, KEY_BLOCK_MARKETING_CHANNELS, true);
    }

    private static void makePrefsWorldReadable(Context context) {
        try {
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
            File prefsFile = new File(prefsDir, PREF_NAME + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
            }
        } catch (Exception e) {
            Log.e(TAG, "makePrefsWorldReadable failed: " + e.getMessage());
        }
    }

    private static void notifyChange(Context context) {
        try {
            Uri uri = Uri.parse("content://" + AUTHORITY + "/browser_opt");
            context.getContentResolver().notifyChange(uri, null);
        } catch (Exception e) {
            Log.e(TAG, "notifyChange failed: " + e.getMessage());
        }
    }
}
