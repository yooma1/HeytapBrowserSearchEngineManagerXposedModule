package com.upuaut.xposedsearch;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;

import de.robv.android.xposed.XposedBridge;

public class BrowserOptPrefsCache {

    private static final String TAG = "XposedSearch";
    private static final String PROVIDER_URI = "content://com.upuaut.xposedsearch.provider/browser_opt";
    private static final String LOCAL_CACHE_PREF = "xposed_browser_opt_cache";

    private static boolean removeSplashAd = true;
    private static boolean bypassWebLimit = true;
    private static boolean disableNewsFeed = true;
    private static boolean forceSimpleMode = true;
    private static boolean removeSearchAppPromo = true;
    private static boolean removeWindowLimit = true;
    private static boolean removeDownloadBottomAd = true;
    private static boolean removeDownloadDialogAd = true;
    private static boolean filterCloudEngineConfig = true;
    private static boolean blockMarketingChannels = true;
    private static boolean blockErrorPageRecommend = true;

    private static final long CACHE_TTL_MS = 3000L;
    /** 熔断冷却：到点后放一次探测，成功即关闭熔断。 */
    private static final long CIRCUIT_COOLDOWN_MS = 15000L;
    private static long lastLoadTime = 0L;
    private static long lastCircuitOpenTime = 0L;
    private static int providerFailureCount = 0;
    private static final int MAX_FAILURES = 3;
    private static boolean providerCircuitOpen = false;

    public static boolean isRemoveSplashAd() { return removeSplashAd; }
    public static boolean isBypassWebLimit() { return bypassWebLimit; }
    public static boolean isDisableNewsFeed() { return disableNewsFeed; }
    public static boolean isForceSimpleMode() { return forceSimpleMode; }
    public static boolean isRemoveSearchAppPromo() { return removeSearchAppPromo; }
    public static boolean isRemoveWindowLimit() { return removeWindowLimit; }
    public static boolean isRemoveDownloadBottomAd() { return removeDownloadBottomAd; }
    public static boolean isRemoveDownloadDialogAd() { return removeDownloadDialogAd; }
    public static boolean isFilterCloudEngineConfig() { return filterCloudEngineConfig; }
    public static boolean isBlockMarketingChannels() { return blockMarketingChannels; }
    public static boolean isBlockErrorPageRecommend() { return blockErrorPageRecommend; }

    public static void refresh(Context context) {
        long now = System.currentTimeMillis();
        if (now - lastLoadTime < CACHE_TTL_MS) {
            return;
        }
        // 半开重试：熔断后不是永久放弃，而是每隔 CIRCUIT_COOLDOWN_MS 放一次探测。
        // 原实现一旦打开就再也不查 Provider，浏览器冷启动抢在模块 Provider 就绪之前
        // 连打 3 次失败后，UI 上的开关改了也永远同步不过去（实测踩到）。
        if (providerCircuitOpen && now - lastCircuitOpenTime < CIRCUIT_COOLDOWN_MS) {
            if (lastLoadTime == 0) {
                loadFromLocalCache(context);
                lastLoadTime = now;
            }
            return;
        }
        if (loadFromProvider(context)) {
            lastLoadTime = now;
            providerFailureCount = 0;
            providerCircuitOpen = false;
            saveToLocalCache(context);
            return;
        }
        providerFailureCount++;
        if (providerFailureCount >= MAX_FAILURES && !providerCircuitOpen) {
            providerCircuitOpen = true;
            lastCircuitOpenTime = now;
            XposedBridge.log("[" + TAG + "] PrefsCache: Provider failed " + providerFailureCount
                    + " times. Circuit OPEN, retry in " + (CIRCUIT_COOLDOWN_MS / 1000) + "s.");
        }
        if (lastLoadTime == 0) {
            loadFromLocalCache(context);
            lastLoadTime = now;
        }
    }

    private static boolean loadFromProvider(Context context) {
        if (context == null) return false;
        Cursor cursor = null;
        try {
            ContentResolver resolver = context.getContentResolver();
            cursor = resolver.query(Uri.parse(PROVIDER_URI), null, null, null, null);
            if (cursor == null) return false;
            if (cursor.moveToFirst()) {
                removeSplashAd = readBool(cursor, BrowserOptConfigManager.KEY_REMOVE_SPLASH_AD, true);
                bypassWebLimit = readBool(cursor, BrowserOptConfigManager.KEY_BYPASS_WEB_LIMIT, true);
                disableNewsFeed = readBool(cursor, BrowserOptConfigManager.KEY_DISABLE_NEWS_FEED, true);
                forceSimpleMode = readBool(cursor, BrowserOptConfigManager.KEY_FORCE_SIMPLE_MODE, true);
                removeSearchAppPromo = readBool(cursor, BrowserOptConfigManager.KEY_REMOVE_SEARCH_APP_PROMO, true);
                removeWindowLimit = readBool(cursor, BrowserOptConfigManager.KEY_REMOVE_WINDOW_LIMIT, true);
                removeDownloadBottomAd = readBool(cursor, BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_BOTTOM_AD, true);
                removeDownloadDialogAd = readBool(cursor, BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_DIALOG_AD, true);
                filterCloudEngineConfig = readBool(cursor, BrowserOptConfigManager.KEY_FILTER_CLOUD_ENGINE_CONFIG, true);
                blockMarketingChannels = readBool(cursor, BrowserOptConfigManager.KEY_BLOCK_MARKETING_CHANNELS, true);
                blockErrorPageRecommend = readBool(cursor, BrowserOptConfigManager.KEY_BLOCK_ERROR_PAGE_RECOMMEND, true);
                return true;
            }
            return false;
        } catch (Throwable t) {
            // 不能静默吞：浏览器进程内查询失败与 shell 查询结果可能不同（SELinux / UID）
            if (providerFailureCount == 0) {
                XposedBridge.log("[" + TAG + "] loadFromProvider failed: "
                        + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            return false;
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static boolean readBool(Cursor c, String col, boolean def) {
        int idx = c.getColumnIndex(col);
        return idx >= 0 ? c.getInt(idx) == 1 : def;
    }

    private static void saveToLocalCache(Context context) {
        if (context == null) return;
        try {
            SharedPreferences sp = context.getSharedPreferences(LOCAL_CACHE_PREF, Context.MODE_PRIVATE);
            sp.edit()
                    .putBoolean(BrowserOptConfigManager.KEY_REMOVE_SPLASH_AD, removeSplashAd)
                    .putBoolean(BrowserOptConfigManager.KEY_BYPASS_WEB_LIMIT, bypassWebLimit)
                    .putBoolean(BrowserOptConfigManager.KEY_DISABLE_NEWS_FEED, disableNewsFeed)
                    .putBoolean(BrowserOptConfigManager.KEY_FORCE_SIMPLE_MODE, forceSimpleMode)
                    .putBoolean(BrowserOptConfigManager.KEY_REMOVE_SEARCH_APP_PROMO, removeSearchAppPromo)
                    .putBoolean(BrowserOptConfigManager.KEY_REMOVE_WINDOW_LIMIT, removeWindowLimit)
                    .putBoolean(BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_BOTTOM_AD, removeDownloadBottomAd)
                    .putBoolean(BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_DIALOG_AD, removeDownloadDialogAd)
                    .putBoolean(BrowserOptConfigManager.KEY_FILTER_CLOUD_ENGINE_CONFIG, filterCloudEngineConfig)
                    .putBoolean(BrowserOptConfigManager.KEY_BLOCK_MARKETING_CHANNELS, blockMarketingChannels)
                    .putBoolean(BrowserOptConfigManager.KEY_BLOCK_ERROR_PAGE_RECOMMEND, blockErrorPageRecommend)
                    .apply();
        } catch (Throwable ignored) {}
    }

    private static void loadFromLocalCache(Context context) {
        if (context == null) return;
        try {
            SharedPreferences sp = context.getSharedPreferences(LOCAL_CACHE_PREF, Context.MODE_PRIVATE);
            removeSplashAd = sp.getBoolean(BrowserOptConfigManager.KEY_REMOVE_SPLASH_AD, true);
            bypassWebLimit = sp.getBoolean(BrowserOptConfigManager.KEY_BYPASS_WEB_LIMIT, true);
            disableNewsFeed = sp.getBoolean(BrowserOptConfigManager.KEY_DISABLE_NEWS_FEED, true);
            forceSimpleMode = sp.getBoolean(BrowserOptConfigManager.KEY_FORCE_SIMPLE_MODE, true);
            removeSearchAppPromo = sp.getBoolean(BrowserOptConfigManager.KEY_REMOVE_SEARCH_APP_PROMO, true);
            removeWindowLimit = sp.getBoolean(BrowserOptConfigManager.KEY_REMOVE_WINDOW_LIMIT, true);
            removeDownloadBottomAd = sp.getBoolean(BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_BOTTOM_AD, true);
            removeDownloadDialogAd = sp.getBoolean(BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_DIALOG_AD, true);
            filterCloudEngineConfig = sp.getBoolean(BrowserOptConfigManager.KEY_FILTER_CLOUD_ENGINE_CONFIG, true);
            blockMarketingChannels = sp.getBoolean(BrowserOptConfigManager.KEY_BLOCK_MARKETING_CHANNELS, true);
            blockErrorPageRecommend = sp.getBoolean(BrowserOptConfigManager.KEY_BLOCK_ERROR_PAGE_RECOMMEND, true);
        } catch (Throwable ignored) {}
    }
}
