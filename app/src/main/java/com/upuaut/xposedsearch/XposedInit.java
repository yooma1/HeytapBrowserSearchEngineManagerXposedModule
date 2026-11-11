package com.upuaut.xposedsearch;

import android.app.Application;
import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.upuaut.xposedsearch.hooks.SearchEngineHook;
import com.upuaut.xposedsearch.hooks.HotSitesHook;
import com.upuaut.xposedsearch.hooks.DarkWordHook;
import com.upuaut.xposedsearch.hooks.BrowserCleanHook;
import com.upuaut.xposedsearch.hooks.NotificationChannelHook;
import com.upuaut.xposedsearch.hooks.ErrorPageAdHook;

public class XposedInit implements IXposedHookLoadPackage {

    private static final String TAG = "XposedSearch";
    private static final String TARGET_PACKAGE = "com.heytap.browser";
    private static final String SELF_PACKAGE = "com.upuaut.xposedsearch";

    private SearchEngineHook searchEngineHook;
    private HotSitesHook hotSitesHook;
    private DarkWordHook darkWordHook;
    private BrowserCleanHook browserCleanHook;
    private NotificationChannelHook notificationChannelHook;
    private ErrorPageAdHook errorPageAdHook;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (SELF_PACKAGE.equals(lpparam.packageName)) {
            hookSelf(lpparam);
            return;
        }
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log("[" + TAG + "] Loaded in " + lpparam.packageName);

        searchEngineHook = new SearchEngineHook(lpparam);
        hotSitesHook = new HotSitesHook(lpparam);
        darkWordHook = new DarkWordHook(lpparam);
        browserCleanHook = new BrowserCleanHook(lpparam);
        notificationChannelHook = new NotificationChannelHook(lpparam);
        errorPageAdHook = new ErrorPageAdHook(lpparam);

        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Application app = (Application) param.thisObject;
                Context ctx = app.getApplicationContext();
                XposedBridge.log("[" + TAG + "] Got Application context");

                searchEngineHook.setAppContext(ctx);
                hotSitesHook.setAppContext(ctx);
                darkWordHook.setAppContext(ctx);
                browserCleanHook.setAppContext(ctx);
                notificationChannelHook.setAppContext(ctx);
                errorPageAdHook.setAppContext(ctx);
                notificationChannelHook.applyExisting(ctx);
            }
        });

        safeInit("SearchEngineHook", new Runnable() {
            public void run() { searchEngineHook.hook(); }
        });
        safeInit("HotSitesHook", new Runnable() {
            public void run() { hotSitesHook.hook(); }
        });
        safeInit("DarkWordHook", new Runnable() {
            public void run() { darkWordHook.hook(); }
        });
        safeInit("BrowserCleanHook", new Runnable() {
            public void run() { browserCleanHook.hook(); }
        });
        safeInit("NotificationChannelHook", new Runnable() {
            public void run() { notificationChannelHook.hook(); }
        });
        safeInit("ErrorPageAdHook", new Runnable() {
            public void run() { errorPageAdHook.hook(); }
        });
    }

    private void safeInit(String name, Runnable action) {
        try {
            action.run();
            XposedBridge.log("[" + TAG + "] " + name + " initialized");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to init " + name + ": " + t.getMessage());
        }
    }

    private void hookSelf(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.upuaut.xposedsearch.ui.MainViewModelKt",
                    lpparam.classLoader,
                    "isXposedModuleActive",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            return true;
                        }
                    }
            );
            XposedBridge.log("[" + TAG + "] Hooked isXposedModuleActive in self");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] Failed to hook self: " + t.getMessage());
        }
    }
}
