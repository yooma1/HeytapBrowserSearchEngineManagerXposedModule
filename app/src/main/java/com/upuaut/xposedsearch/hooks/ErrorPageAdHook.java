package com.upuaut.xposedsearch.hooks;

import android.content.Context;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.upuaut.xposedsearch.BrowserOptPrefsCache;

/**
 * 离线错误页推荐内容清理。
 *
 * 定位出的完整链路：
 * 1) HtmlPageManagerImpl (com.heytap.browser.kernel.d)：
 *    - C(key, cacheInMemory, isCollection, httpStatusCode)
 *    - v(key, cacheInMemory)
 *    网页加载失败时，浏览器并非调用系统 android.webkit.WebView，而是调用自研内核封装，
 *    通过 HtmlPageManagerImpl 读取 "html_unified_error_page" 的 HTML 源码字符串注入内核。
 *    直接在该返回值里的 <head> 注入 <style> 隐藏 #card-box / #news-box / #suggest-box / #searchFrame。
 *
 * 2) BrowserIFlowJsObject.getConfigValue(String key)：
 *    unified_error_page.html 初始化时调用 window.BrowserIFlow.getConfigValue("CommonConfig_errorPageSearchSettingcard")
 *    解析出 {enable: true, setting: "relsearch-hotList-searchResult-..."} 来渲染「你可能想搜」和「为您推荐」。
 *    拦截该方法，当请求 errorPage 相关配置时返回 "{\"enable\":false}"。
 *
 * 3) BrowserErrorJsObject：
 *    拦截 isErrorPageShowSearch() / needShowIFlow() 返回 false，getErrorPageSearchConfig() 返回 "{}"。
 *
 * 4) ErrorPageUtils (com.heytap.browser.webview.utils.f) 与 ErrorPageInsertAdHelper (com.heytap.browser.web.security.block.c)。
 */
public class ErrorPageAdHook {

    private static final String TAG = "XposedSearch";
    private static final String UTILS_CLASS = "com.heytap.browser.webview.utils.f";
    private static final String HELPER_CLASS = "com.heytap.browser.web.security.block.c";
    private static final String HTML_MGR_CLASS = "com.heytap.browser.kernel.d";
    private static final String IFLOW_JS_CLASS = "com.heytap.browser.iflow.js.BrowserIFlowJsObject";
    private static final String ERROR_JS_CLASS = "com.heytap.browser.webview.jsapi.newjs.BrowserErrorJsObject";
    private static final String UNIFIED_JS_CLASS = "com.heytap.browser.webview.jsapi.newjs.UnifiedErrorPageJsObject";

    private static final String INJECT_CLEAN_STYLE =
            "<style id=\"coui-clean-error-page\">"
            + "#card-box, #news-box, #suggest-box, #news-app, #searchFrame, #mask-container, "
            + ".news-box-mask, .search-suggest, .hotword { display: none !important; height: 0 !important; visibility: hidden !important; pointer-events: none !important; }"
            + "</style>";

    private final XC_LoadPackage.LoadPackageParam lpparam;
    private volatile Context appContext;

    public ErrorPageAdHook(XC_LoadPackage.LoadPackageParam lpparam) {
        this.lpparam = lpparam;
    }

    public void setAppContext(Context ctx) {
        this.appContext = ctx;
    }

    public void hook() {
        hookHtmlPageManager();
        hookBrowserIFlowJs();
        hookBrowserErrorJs();
        hookErrorPageUtils();
        hookInsertAdHelper();
    }

    /** 1) 在 HTML 模板源头注入隐藏样式，通杀任何内核 WebView。 */
    private void hookHtmlPageManager() {
        try {
            Class<?> mgr = XposedHelpers.findClass(HTML_MGR_CLASS, lpparam.classLoader);
            XC_MethodHook htmlHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1 || !(param.args[0] instanceof String)) return;
                    String key = (String) param.args[0];
                    if (key == null || !key.contains("error") && !key.contains("forbidden") && !key.contains("warning") && !key.contains("disconnect")) {
                        return;
                    }
                    refresh();
                    if (!BrowserOptPrefsCache.isBlockErrorPageRecommend()) return;
                    Object res = param.getResult();
                    if (!(res instanceof String)) return;
                    String html = (String) res;
                    if (html.isEmpty() || html.contains("coui-clean-error-page")) return;
                    if (html.contains("<head>")) {
                        param.setResult(html.replaceFirst("<head>", "<head>" + INJECT_CLEAN_STYLE));
                    } else {
                        param.setResult(INJECT_CLEAN_STYLE + html);
                    }
                }
            };
            XposedBridge.hookAllMethods(mgr, "C", htmlHook);
            XposedBridge.hookAllMethods(mgr, "v", htmlHook);
            XposedBridge.log("[" + TAG + "] HtmlPageManager hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] HtmlPageManager hook failed: " + t.getMessage());
        }
    }

    /** 2) 拦截 JS 桥读取 CommonConfig_errorPageSearchSettingcard。 */
    private void hookBrowserIFlowJs() {
        try {
            Class<?> cls = XposedHelpers.findClass(IFLOW_JS_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(cls, "getConfigValue", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1 || !(param.args[0] instanceof String)) return;
                    String key = (String) param.args[0];
                    if (key == null) return;
                    if (key.contains("errorPage") || key.contains("ErrorPage")) {
                        refresh();
                        if (!BrowserOptPrefsCache.isBlockErrorPageRecommend()) return;
                        param.setResult("{\"enable\":false}");
                    }
                }
            });
            XposedBridge.hookAllMethods(cls, "x1", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length < 1 || !(param.args[0] instanceof String)) return;
                    String key = (String) param.args[0];
                    if (key == null) return;
                    if (key.contains("errorPage") || key.contains("ErrorPage")) {
                        refresh();
                        if (!BrowserOptPrefsCache.isBlockErrorPageRecommend()) return;
                        param.setResult("{\"enable\":false}");
                    }
                }
            });
            XposedBridge.log("[" + TAG + "] BrowserIFlowJsObject errorPage config hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] BrowserIFlowJsObject hook failed: " + t.getMessage());
        }
    }

    /** 3) 拦截 BrowserErrorJsObject & UnifiedErrorPageJsObject 的开关方法。 */
    private void hookBrowserErrorJs() {
        try {
            Class<?> cls = XposedHelpers.findClass(ERROR_JS_CLASS, lpparam.classLoader);
            XC_MethodHook falseHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refresh();
                    if (BrowserOptPrefsCache.isBlockErrorPageRecommend()) {
                        param.setResult(false);
                    }
                }
            };
            XposedBridge.hookAllMethods(cls, "isErrorPageShowSearch", falseHook);
            XposedBridge.hookAllMethods(cls, "needShowIFlow", falseHook);

            XC_MethodHook emptyStrHook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refresh();
                    if (BrowserOptPrefsCache.isBlockErrorPageRecommend()) {
                        param.setResult("");
                    }
                }
            };
            XposedBridge.hookAllMethods(cls, "getErrorPageSearchConfig", emptyStrHook);
            XposedBridge.hookAllMethods(cls, "getErrorPageSearchURL", emptyStrHook);
            XposedBridge.hookAllMethods(cls, "getErrorPageSearchQuery", emptyStrHook);
            XposedBridge.hookAllMethods(cls, "getIFlowList", emptyStrHook);
            XposedBridge.log("[" + TAG + "] BrowserErrorJsObject hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] BrowserErrorJsObject hook failed: " + t.getMessage());
        }

        try {
            Class<?> uCls = XposedHelpers.findClass(UNIFIED_JS_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(uCls, "isIFlowListEnabled", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refresh();
                    if (BrowserOptPrefsCache.isBlockErrorPageRecommend()) {
                        param.setResult(false);
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookErrorPageUtils() {
        try {
            Class<?> c = XposedHelpers.findClass(UTILS_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "h", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refresh();
                    if (!BrowserOptPrefsCache.isBlockErrorPageRecommend()) return;
                    param.setResult(false);
                }
            });
            XposedBridge.hookAllMethods(c, "c", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    refresh();
                    if (!BrowserOptPrefsCache.isBlockErrorPageRecommend()) return;
                    Object setting = param.getResult();
                    if (setting == null) return;
                    try {
                        XposedHelpers.setBooleanField(setting, "errorPageShowSearchResult", false);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable ignored) {}
    }

    private void hookInsertAdHelper() {
        try {
            Class<?> helper = XposedHelpers.findClass(HELPER_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(helper, "c", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refresh();
                    if (!BrowserOptPrefsCache.isBlockErrorPageRecommend()) return;
                    param.setResult(null);
                }
            });
            XposedBridge.hookAllMethods(helper, "g", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refresh();
                    if (BrowserOptPrefsCache.isBlockErrorPageRecommend()) param.setResult(false);
                }
            });
        } catch (Throwable ignored) {}
    }

    private void refresh() {
        Context ctx = appContext;
        if (ctx != null) BrowserOptPrefsCache.refresh(ctx);
    }
}
