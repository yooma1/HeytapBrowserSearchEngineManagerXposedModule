package com.upuaut.xposedsearch.hooks;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.upuaut.xposedsearch.BrowserOptPrefsCache;

public class BrowserCleanHook {

    private static final String TAG = "XposedSearch";
    private static final String PREFS_BROWSER = "com.heytap.browser_preferences";

    private Context appContext;
    private final XC_LoadPackage.LoadPackageParam lpparam;
    /** 统计开屏相关类实际命中数，便于从 LSPosed 日志判断混淆名是否漂移。 */
    private int splashEnums = 0;

    public BrowserCleanHook(XC_LoadPackage.LoadPackageParam lpparam) {
        this.lpparam = lpparam;
    }

    public void setAppContext(Context context) {
        this.appContext = context;
        if (context == null) return;
        BrowserOptPrefsCache.refresh(context);
        applyBrowserPrefs(context);
    }

    public void hook() {
        hookSplashAd();
        hookWebSecurity();
        hookNewsFeed();
        hookSearchAppPromo();
        hookWindowLimit();
        hookDownloadAds();
    }

    // ---------------- 1. 开屏广告 ----------------
    private void hookSplashAd() {
        // StepSplash.z() —— 跳过在线开屏请求
        for (String cls : new String[]{
                "com.android.browser.root.StepSplash",
                "com.android.browser.startup.StepSplash"}) {
            try {
                Class<?> c = XposedHelpers.findClass(cls, lpparam.classLoader);
                splashEnums++;
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getName().equals("z") && m.getParameterCount() == 0) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                BrowserOptPrefsCache.refresh(appContext);
                                if (!BrowserOptPrefsCache.isRemoveSplashAd()) return;
                                try {
                                    XposedHelpers.setBooleanField(param.thisObject, "isSplashRequested", false);
                                } catch (Throwable ignored) {}
                                param.setResult(null);
                            }
                        });
                    }
                }
            } catch (Throwable ignored) {}
        }

        // SplashConsumer —— 过滤请求
        try {
            Class<?> c = XposedHelpers.findClass("com.heytap.browser.guide.SplashConsumer", lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "j", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isRemoveSplashAd()) param.setResult(false);
                }
            });
            XposedBridge.hookAllMethods(c, "b", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (!BrowserOptPrefsCache.isRemoveSplashAd()) return;
                    try {
                        List<?> reqList = (List<?>) XposedHelpers.getObjectField(param.thisObject, "requestList");
                        if (reqList != null) reqList.clear();
                    } catch (Throwable ignored) {}
                    param.setResult(true);
                }
            });
        } catch (Throwable ignored) {}

        // SplashFeedAdImpl.k() —— 不请求在线开屏
        try {
            Class<?> c = XposedHelpers.findClass("com.heytap.browser.guide.feedad.SplashFeedAdImpl", lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "k", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isRemoveSplashAd()) param.setResult(null);
                }
            });
            XposedBridge.log("[" + TAG + "] SplashFeedAdImpl hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] SplashFeedAdImpl hook failed: " + describe(t));
        }
        XposedBridge.log("[" + TAG + "] SplashAd hooks installed (splashEnums=" + splashEnums + ")");
    }

    /** 页面类短名不稳定的情况下，按"类名包含 StepSplash/SplashConsumer"兜底探测。 */
    private static String describe(Throwable t) {
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }

    // ---------------- 2. 网页浏览限制 ----------------
    private void hookWebSecurity() {
        String clsName = "com.heytap.browser.web.security.block.t";
        try {
            Class<?> c = XposedHelpers.findClass(clsName, lpparam.classLoader);
            for (Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                if (n.equals("Z") || n.equals("W") || n.equals("X") || n.equals("O")
                        || n.equals("Q") || n.equals("P") || n.equals("i0") || n.equals("a")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            BrowserOptPrefsCache.refresh(appContext);
                            if (BrowserOptPrefsCache.isBypassWebLimit()) param.setResult(null);
                        }
                    });
                }
            }
            XposedBridge.log("[" + TAG + "] WebSecurity UrlBlocker hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] UrlBlocker hook failed: " + t.getMessage());
        }

        for (String dialogCls : new String[]{
                "com.heytap.browser.webview.settings.WebSecurityDialogHelper",
                "com.heytap.browser.webview.utils.WebSecurityDialogHelper"}) {
            try {
                Class<?> c = XposedHelpers.findClass(dialogCls, lpparam.classLoader);
                XposedBridge.hookAllMethods(c, "x", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        BrowserOptPrefsCache.refresh(appContext);
                        if (BrowserOptPrefsCache.isBypassWebLimit()) param.setResult(null);
                    }
                });
                XposedBridge.hookAllMethods(c, "v", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        BrowserOptPrefsCache.refresh(appContext);
                        if (BrowserOptPrefsCache.isBypassWebLimit()) param.setResult(null);
                    }
                });
            } catch (Throwable ignored) {}
        }
    }

    // ---------------- 3. 资讯流 / 热榜 / 简洁模式 ----------------
    private void hookNewsFeed() {
        try {
            Class<?> c = XposedHelpers.findClass("com.heytap.browser.platform.settings.BaseSettings", lpparam.classLoader);
            final String[] boolMethods = {"d", "R1", "S0", "u1", "v1", "g0",
                    "S", "U", "Y", "a0", "h0", "m0", "H0", "K0"};
            for (Method m : c.getDeclaredMethods()) {
                if (m.getReturnType() != boolean.class || m.getParameterCount() != 0) continue;
                if (!Arrays.asList(boolMethods).contains(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        BrowserOptPrefsCache.refresh(appContext);
                        String n = ((Method) param.method).getName();
                        if (n.equals("d")) {
                            if (BrowserOptPrefsCache.isForceSimpleMode()) param.setResult(true);
                        } else {
                            if (BrowserOptPrefsCache.isDisableNewsFeed()) param.setResult(false);
                        }
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] BaseSettings hook failed: " + t.getMessage());
        }

        try {
            Class<?> c = XposedHelpers.findClass("com.heytap.browser.search.suggest.data.SuggestDataRepository", lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "K", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isDisableNewsFeed()) param.setResult(true);
                }
            });
            XposedBridge.hookAllMethods(c, "B", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isDisableNewsFeed()) param.setResult(new ArrayList<>());
                }
            });
            XposedBridge.hookAllMethods(c, "P", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isDisableNewsFeed()) param.setResult(null);
                }
            });
        } catch (Throwable ignored) {}
    }

    // ---------------- 4. 搜索框 App 推广 ----------------
    private void hookSearchAppPromo() {
        try {
            Class<?> c = XposedHelpers.findClass("com.heytap.browser.search.suggest.view.q", lpparam.classLoader);
            XC_MethodHook filterHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (!BrowserOptPrefsCache.isRemoveSearchAppPromo()) return;
                    if (param.args.length < 1 || !(param.args[0] instanceof List)) return;
                    List<?> src = (List<?>) param.args[0];
                    List<Object> out = new ArrayList<>();
                    for (Object o : src) {
                        if (!isPromotionItem(o)) out.add(o);
                    }
                    param.args[0] = out;
                }
            };
            XposedBridge.hookAllMethods(c, "L0", filterHook);
            XposedBridge.hookAllMethods(c, "o1", filterHook);
            XposedBridge.log("[" + TAG + "] SugMultiAdapter promo filter hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] SearchAppPromo hook failed: " + t.getMessage());
        }
    }

    private boolean isPromotionItem(Object o) {
        if (o == null) return false;
        String name = o.getClass().getName();
        if (name.equals("com.heytap.browser.search.suggest.data.b0")
                || name.equals("com.heytap.browser.search.suggest.data.a0")
                || name.equals("com.heytap.browser.search.suggest.data.o0")
                || name.equals("com.heytap.browser.search.suggest.data.m0")) {
            return true;
        }
        try {
            Object loc = XposedHelpers.callMethod(o, "g");
            if (loc instanceof String) {
                String s = (String) loc;
                if (s.equals("sugNaturalApp") || s.equals("sugAd")
                        || s.equals("sugAppBooking") || s.equals("sugInstantApp")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Object cardId = XposedHelpers.callMethod(o, "getCardId");
            if ("promotion".equals(cardId)) return true;
        } catch (Throwable ignored) {}
        return false;
    }

    // ---------------- 5. 窗口数量限制 ----------------
    private void hookWindowLimit() {
        try {
            Class<?> c = XposedHelpers.findClass("com.android.browser.TabManager", lpparam.classLoader);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getReturnType() != int.class || m.getParameterCount() != 0) continue;
                final String n = m.getName();
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        BrowserOptPrefsCache.refresh(appContext);
                        if (!BrowserOptPrefsCache.isRemoveWindowLimit()) return;
                        if (!"u".equals(n)) return;
                        param.setResult(999);
                    }
                });
            }
            XposedBridge.log("[" + TAG + "] TabManager window limit hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] WindowLimit hook failed: " + t.getMessage());
        }
    }

    // ---------------- 6. 下载广告 ----------------
    private void hookDownloadAds() {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.heytap.browser.download.ui.downloadlist.model.RecommendConfig", lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "isDisplay", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isRemoveDownloadBottomAd()) param.setResult(false);
                }
            });
            XposedBridge.log("[" + TAG + "] RecommendConfig.isDisplay hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] RecommendConfig hook failed: " + describe(t));
        }

        try {
            Class<?> c = XposedHelpers.findClass("fh1.i", lpparam.classLoader);
            XC_MethodHook hideHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (!BrowserOptPrefsCache.isRemoveDownloadBottomAd()) return;
                    try {
                        XposedHelpers.setBooleanField(param.thisObject, "f20527i", true);
                        XposedHelpers.setBooleanField(param.thisObject, "f20528j", true);
                        Object bottom = XposedHelpers.getObjectField(param.thisObject, "f20524f");
                        if (bottom instanceof View) ((View) bottom).setVisibility(View.GONE);
                        Object root = XposedHelpers.getObjectField(param.thisObject, "f20520b");
                        if (root instanceof View) ((View) root).setVisibility(View.GONE);
                    } catch (Throwable ignored) {}
                    param.setResult(null);
                }
            };
            XposedBridge.hookAllMethods(c, "F", hideHook);
            XposedBridge.hookAllMethods(c, "C", hideHook);
            XposedBridge.hookAllMethods(c, "t", hideHook);
        } catch (Throwable ignored) {}

        try {
            Class<?> c = XposedHelpers.findClass("iv1.d", lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "g", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isRemoveDownloadDialogAd()) param.setResult(null);
                }
            });
            XposedBridge.hookAllMethods(c, "q", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    BrowserOptPrefsCache.refresh(appContext);
                    if (BrowserOptPrefsCache.isRemoveDownloadDialogAd()) param.setResult(null);
                }
            });
            XposedBridge.log("[" + TAG + "] Download dialog ad hooked (iv1.d)");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Download dialog ad hook failed: " + describe(t));
        }
        XposedBridge.log("[" + TAG + "] DownloadAd hooks installed");
    }

    // ---------------- 应用启动时清理浏览器本地限制状态 ----------------
    private void applyBrowserPrefs(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS_BROWSER, Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = sp.edit();
            if (BrowserOptPrefsCache.isBypassWebLimit()) {
                ed.putInt("security_level", 0);
                ed.putString("XT_WEBSITE_TYPE_STORAGE_KEY", "{}");
            }
            if (BrowserOptPrefsCache.isForceSimpleMode()) {
                ed.putBoolean("pref_setting_simple_mode", true);
                ed.putBoolean("pref_setting_simple_mode_clicked", true);
            }
            if (BrowserOptPrefsCache.isDisableNewsFeed()) {
                ed.putBoolean("pref.key.advertisement.iflow", false);
                ed.putBoolean("pref.key.iflow_list_refresh", false);
                ed.putBoolean("pref.key.advertisement.splash", false);
                ed.putBoolean("pref.key.advertisement.shake", false);
            }
            if (BrowserOptPrefsCache.isBlockMarketingChannels()) {
                ed.putBoolean("pref_notification_switch", false);
                for (String k : new String[]{"perf_msg_personal", "perf_msg_novel", "perf_msg_interact",
                        "perf_msg_local", "perf_msg_weather", "perf_msg_lockscreen_news",
                        "perf_msg_wonderful", "perf_msg_other", "perf_msg_important"}) {
                    ed.putBoolean(k, false);
                }
            }
            ed.apply();
            XposedBridge.log("[" + TAG + "] Browser local prefs patched");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] applyBrowserPrefs failed: " + t.getMessage());
        }
    }
}
