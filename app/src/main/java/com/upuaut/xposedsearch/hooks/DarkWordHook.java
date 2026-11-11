package com.upuaut.xposedsearch.hooks;

import android.content.Context;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.upuaut.xposedsearch.DarkWordPrefsCache;

public class DarkWordHook {

    private static final String TAG = "XposedSearch";

    private Context appContext;
    private final XC_LoadPackage.LoadPackageParam lpparam;

    public DarkWordHook(XC_LoadPackage.LoadPackageParam lpparam) {
        this.lpparam = lpparam;
    }

    public void setAppContext(Context context) {
        this.appContext = context;
        DarkWordPrefsCache.refresh(appContext);
    }

    public void hook() {
        hookLegacy();
        hookNewDarkWordBean();
        hookNewRepository();
        hookNewUpdater();
    }

    private boolean shouldBlock() {
        if (appContext == null) return false;
        DarkWordPrefsCache.refresh(appContext);
        return DarkWordPrefsCache.isModuleEnabled() && DarkWordPrefsCache.isDarkWordDisabled();
    }

    // ===== 兼容旧版 =====
    private void hookLegacy() {
        try {
            Class<?> c = XposedHelpers.findClass("com.heytap.browser.search.darkword.n", lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "w", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!shouldBlock()) return;
                    if (param.getResult() != null) clearDarkWordList(param.getResult());
                }
            });
        } catch (Throwable ignored) {}

        try {
            Class<?> c = XposedHelpers.findClass("com.heytap.browser.api.search.page.p", lpparam.classLoader);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getReturnType() != List.class || m.getParameterCount() != 0) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!shouldBlock()) return;
                        Object r = param.getResult();
                        if (r instanceof List && !((List<?>) r).isEmpty()) {
                            Object first = ((List<?>) r).get(0);
                            if (first != null && first.getClass().getName().contains("DarkWord")) {
                                ((List<?>) r).clear();
                            }
                        }
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    // ===== 新版 DarkWordBean（v40.10.25.2）=====
    private void hookNewDarkWordBean() {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.heytap.browser.api.search.darkword.bean.DarkWordBean", lpparam.classLoader);

            XposedBridge.hookAllMethods(c, "getWordList", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldBlock()) param.setResult(new ArrayList<>());
                }
            });
            XposedBridge.hookAllMethods(c, "hadWords", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldBlock()) param.setResult(false);
                }
            });
            XposedBridge.hookAllMethods(c, "hadDarkWordAd", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldBlock()) param.setResult(false);
                }
            });
            XposedBridge.hookAllMethods(c, "getAvailableHint", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldBlock()) param.setResult("");
                }
            });
            XposedBridge.log("[" + TAG + "] DarkWordBean hooked (v40.10.25.2)");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] DarkWordBean hook failed: " + t.getMessage());
        }
    }

    private void hookNewRepository() {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.heytap.browser.search.impl.darkword.repository.DarkWordRepository",
                    lpparam.classLoader);
            for (Method m : c.getDeclaredMethods()) {
                if (!"b".equals(m.getName())) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!shouldBlock()) return;
                        Object bean = param.getResult();
                        if (bean == null) return;
                        try {
                            XposedHelpers.callMethod(bean, "clearAllItem");
                        } catch (Throwable ignored) {}
                        clearDarkWordList(bean);
                    }
                });
            }
            XposedBridge.log("[" + TAG + "] DarkWordRepository hooked");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] DarkWordRepository hook failed: " + t.getMessage());
        }
    }

    private void hookNewUpdater() {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.heytap.browser.search.impl.darkword.repository.DarkWordUpdater",
                    lpparam.classLoader);
            XposedBridge.hookAllMethods(c, "Q0", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (shouldBlock()) param.setResult(false);
                }
            });
        } catch (Throwable ignored) {}
    }

    private boolean clearDarkWordList(Object container) {
        if (container == null) return false;
        for (String fn : new String[]{"e", "d", "f", "list", "mList", "darkWords", "wordList"}) {
            try {
                Object v = XposedHelpers.getObjectField(container, fn);
                if (v instanceof List && !((List<?>) v).isEmpty()) {
                    Object first = ((List<?>) v).get(0);
                    if (first != null && first.getClass().getName().contains("DarkWord")) {
                        ((List<?>) v).clear();
                        return true;
                    }
                }
            } catch (Throwable ignored) {}
        }
        try {
            for (java.lang.reflect.Field f : container.getClass().getDeclaredFields()) {
                if (!List.class.isAssignableFrom(f.getType())) continue;
                f.setAccessible(true);
                Object v = f.get(container);
                if (v instanceof List && !((List<?>) v).isEmpty()) {
                    Object first = ((List<?>) v).get(0);
                    if (first != null && first.getClass().getName().contains("DarkWord")) {
                        ((List<?>) v).clear();
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
