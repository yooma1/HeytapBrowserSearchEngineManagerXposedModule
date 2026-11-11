package com.upuaut.xposedsearch;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SearchEngineProvider extends ContentProvider {

    private static final String TAG = "XposedSearch";
    public static final String AUTHORITY = ConfigManager.AUTHORITY;

    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/engines");
    public static final Uri DISCOVER_URI = Uri.parse("content://" + AUTHORITY + "/discover");
    public static final Uri DISCOVER_COMPLETE_URI = Uri.parse("content://" + AUTHORITY + "/discover_complete");
    public static final Uri HOTSITES_URI = Uri.parse("content://" + AUTHORITY + "/hotsites");
    public static final Uri HOTSITES_DISCOVER_URI = Uri.parse("content://" + AUTHORITY + "/hotsites_discover");
    public static final Uri DARKWORD_URI = Uri.parse("content://" + AUTHORITY + "/darkword");
    public static final Uri BROWSER_OPT_URI = Uri.parse("content://" + AUTHORITY + "/browser_opt");

    private static final int CODE_ENGINES = 1;
    private static final int CODE_DISCOVER = 2;
    private static final int CODE_DISCOVER_COMPLETE = 3;
    private static final int CODE_HOTSITES = 4;
    private static final int CODE_HOTSITES_DISCOVER = 5;
    private static final int CODE_DARKWORD = 6;
    private static final int CODE_BROWSER_OPT = 7;

    private static final UriMatcher uriMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    static {
        uriMatcher.addURI(AUTHORITY, "engines", CODE_ENGINES);
        uriMatcher.addURI(AUTHORITY, "discover", CODE_DISCOVER);
        uriMatcher.addURI(AUTHORITY, "discover_complete", CODE_DISCOVER_COMPLETE);
        uriMatcher.addURI(AUTHORITY, "hotsites", CODE_HOTSITES);
        uriMatcher.addURI(AUTHORITY, "hotsites_discover", CODE_HOTSITES_DISCOVER);
        uriMatcher.addURI(AUTHORITY, "darkword", CODE_DARKWORD);
        uriMatcher.addURI(AUTHORITY, "browser_opt", CODE_BROWSER_OPT);
    }

    private final Set<String> currentDiscoveredKeys = new HashSet<>();

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        int match = uriMatcher.match(uri);
        Context ctx = getContext();

        switch (match) {
            case CODE_ENGINES: {
                List<SearchEngineConfig> engines = ConfigManager.loadEngines(ctx);
                return buildEnginesCursor(engines);
            }
            case CODE_HOTSITES: {
                List<HotSiteConfig> sites = HotSiteConfigManager.loadSites(ctx);
                boolean moduleEnabled = HotSiteConfigManager.isModuleEnabled(ctx);
                return buildHotSitesCursor(sites, moduleEnabled);
            }
            case CODE_DARKWORD: {
                boolean moduleEnabled = DarkWordConfigManager.isModuleEnabled(ctx);
                boolean darkWordDisabled = DarkWordConfigManager.isDarkWordDisabled(ctx);
                return buildDarkWordCursor(moduleEnabled, darkWordDisabled);
            }
            case CODE_BROWSER_OPT: {
                return buildBrowserOptCursor(ctx);
            }
        }
        return null;
    }

    private MatrixCursor buildBrowserOptCursor(Context ctx) {
        String[] columns = {
                BrowserOptConfigManager.KEY_REMOVE_SPLASH_AD,
                BrowserOptConfigManager.KEY_BYPASS_WEB_LIMIT,
                BrowserOptConfigManager.KEY_DISABLE_NEWS_FEED,
                BrowserOptConfigManager.KEY_FORCE_SIMPLE_MODE,
                BrowserOptConfigManager.KEY_REMOVE_SEARCH_APP_PROMO,
                BrowserOptConfigManager.KEY_REMOVE_WINDOW_LIMIT,
                BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_BOTTOM_AD,
                BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_DIALOG_AD,
                BrowserOptConfigManager.KEY_FILTER_CLOUD_ENGINE_CONFIG,
                BrowserOptConfigManager.KEY_BLOCK_MARKETING_CHANNELS,
                BrowserOptConfigManager.KEY_BLOCK_ERROR_PAGE_RECOMMEND
        };
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(new Object[]{
                BrowserOptConfigManager.isRemoveSplashAd(ctx) ? 1 : 0,
                BrowserOptConfigManager.isBypassWebLimit(ctx) ? 1 : 0,
                BrowserOptConfigManager.isDisableNewsFeed(ctx) ? 1 : 0,
                BrowserOptConfigManager.isForceSimpleMode(ctx) ? 1 : 0,
                BrowserOptConfigManager.isRemoveSearchAppPromo(ctx) ? 1 : 0,
                BrowserOptConfigManager.isRemoveWindowLimit(ctx) ? 1 : 0,
                BrowserOptConfigManager.isRemoveDownloadBottomAd(ctx) ? 1 : 0,
                BrowserOptConfigManager.isRemoveDownloadDialogAd(ctx) ? 1 : 0,
                BrowserOptConfigManager.isFilterCloudEngineConfig(ctx) ? 1 : 0,
                BrowserOptConfigManager.isBlockMarketingChannels(ctx) ? 1 : 0,
                BrowserOptConfigManager.isBlockErrorPageRecommend(ctx) ? 1 : 0
        });
        return cursor;
    }

    private MatrixCursor buildEnginesCursor(List<SearchEngineConfig> engines) {
        String[] columns = {
                "key", "name", "searchUrl", "enabled",
                "isBuiltin", "isModified", "originalName", "originalSearchUrl",
                "hasUpdate", "pendingName", "pendingSearchUrl", "isRemovedFromBrowser",
                "hasBuiltinConflict", "conflictBuiltinName", "conflictBuiltinSearchUrl",
                "order"
        };
        MatrixCursor cursor = new MatrixCursor(columns);
        for (SearchEngineConfig cfg : engines) {
            cursor.addRow(new Object[]{
                    cfg.key, cfg.name, cfg.searchUrl, cfg.enabled ? 1 : 0,
                    cfg.isBuiltin ? 1 : 0, cfg.isModified ? 1 : 0,
                    cfg.originalName != null ? cfg.originalName : "",
                    cfg.originalSearchUrl != null ? cfg.originalSearchUrl : "",
                    cfg.hasUpdate ? 1 : 0,
                    cfg.pendingName != null ? cfg.pendingName : "",
                    cfg.pendingSearchUrl != null ? cfg.pendingSearchUrl : "",
                    cfg.isRemovedFromBrowser ? 1 : 0,
                    cfg.hasBuiltinConflict ? 1 : 0,
                    cfg.conflictBuiltinName != null ? cfg.conflictBuiltinName : "",
                    cfg.conflictBuiltinSearchUrl != null ? cfg.conflictBuiltinSearchUrl : "",
                    cfg.order
            });
        }
        return cursor;
    }

    private MatrixCursor buildHotSitesCursor(List<HotSiteConfig> sites, boolean moduleEnabled) {
        String[] columns = {"id", "name", "url", "iconUrl", "enabled", "order", "moduleEnabled"};
        MatrixCursor cursor = new MatrixCursor(columns);
        for (HotSiteConfig cfg : sites) {
            cursor.addRow(new Object[]{
                    cfg.id,
                    cfg.name != null ? cfg.name : "",
                    cfg.url != null ? cfg.url : "",
                    cfg.iconUrl != null ? cfg.iconUrl : "",
                    cfg.enabled ? 1 : 0,
                    cfg.order,
                    moduleEnabled ? 1 : 0
            });
        }
        return cursor;
    }

    private MatrixCursor buildDarkWordCursor(boolean moduleEnabled, boolean darkWordDisabled) {
        String[] columns = {"moduleEnabled", "darkWordDisabled"};
        MatrixCursor cursor = new MatrixCursor(columns);
        cursor.addRow(new Object[]{
                moduleEnabled ? 1 : 0,
                darkWordDisabled ? 1 : 0
        });
        return cursor;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (values == null) return null;
        int match = uriMatcher.match(uri);

        switch (match) {
            case CODE_DISCOVER: {
                String key = values.getAsString("key");
                String name = values.getAsString("name");
                String searchUrl = values.getAsString("searchUrl");
                if (key != null && !key.isEmpty()) {
                    currentDiscoveredKeys.add(key);
                    ConfigManager.handleDiscoveredEngine(getContext(), key, name, searchUrl);
                    return Uri.withAppendedPath(CONTENT_URI, key);
                }
                break;
            }
            case CODE_DISCOVER_COMPLETE: {
                if (!currentDiscoveredKeys.isEmpty()) {
                    ConfigManager.markMissingEnginesAsRemoved(getContext(), currentDiscoveredKeys);
                    currentDiscoveredKeys.clear();
                }
                return DISCOVER_COMPLETE_URI;
            }
            case CODE_HOTSITES_DISCOVER: {
                String sitesJson = values.getAsString("sites");
                if (sitesJson != null && !sitesJson.isEmpty()) {
                    try {
                        JSONArray array = new JSONArray(sitesJson);
                        List<HotSiteConfig> discoveredSites = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            JSONObject obj = array.getJSONObject(i);
                            HotSiteConfig site = new HotSiteConfig();
                            site.id = obj.optLong("id", System.currentTimeMillis() + i);
                            site.name = obj.optString("name", "");
                            site.url = obj.optString("url", "");
                            site.iconUrl = obj.optString("iconUrl", "");
                            site.enabled = true;
                            site.order = i;
                            if (!site.url.isEmpty()) {
                                discoveredSites.add(site);
                            }
                        }
                        HotSiteConfigManager.handleDiscoveredSites(getContext(), discoveredSites);
                    } catch (Exception e) {
                        Log.e(TAG, "[Provider] Failed to parse discovered sites: " + e.getMessage());
                    }
                }
                return HOTSITES_DISCOVER_URI;
            }
        }
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/vnd.xposedsearch";
    }
}
