package com.upuaut.xposedsearch.hooks;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.upuaut.xposedsearch.BrowserOptPrefsCache;

/**
 * 营销推送渠道静音。
 *
 * 实测 com.heytap.browser 的渠道（dumpsys notification）：
 *   browser_private_letter  资讯通知   importance=4  ← 营销
 *   browser_default         默认通知   importance=4  ← 下载/正常通知，必须保留
 *   Silent PUSH             系统静默   importance=2
 *
 * 两条路径同时下手：
 *   1) 渠道创建时把营销渠道压到 IMPORTANCE_NONE（应用侧一旦降低，自身无法再抬升）；
 *   2) 进程起来后对已存在的营销渠道补一次降级，覆盖"模块先装、渠道先建"的情况。
 * 只碰白名单里的营销渠道，browser_default 不动。
 */
public class NotificationChannelHook {

    private static final String TAG = "XposedSearch";

    /** 资讯/热榜/营销类渠道 id（前缀匹配，兼容版本改名）。 */
    private static final String[] MARKETING_CHANNEL_PREFIX = {
            "browser_private_letter",   // 资讯通知
            "browser_news",
            "browser_iflow",
            "browser_hot",
            "browser_recommend",
            "browser_operation",
            "browser_marketing",
            "browser_promotion",
            "browser_activity",
    };

    /** 绝不触碰的渠道（下载、文件、通用通知）。 */
    private static final String[] PROTECTED_CHANNEL_PREFIX = {
            "browser_default",
            "browser_download",
            "browser_file",
            "Silent PUSH",
    };

    private final XC_LoadPackage.LoadPackageParam lpparam;
    /** 传给 BrowserOptPrefsCache.refresh() 用；为 null 时不能传，否则会误触发熔断计数。 */
    private volatile Context appContext;

    public NotificationChannelHook(XC_LoadPackage.LoadPackageParam lpparam) {
        this.lpparam = lpparam;
    }

    public void setAppContext(Context ctx) {
        this.appContext = ctx;
    }

    private void refreshPrefs() {
        Context ctx = appContext;
        if (ctx != null) {
            BrowserOptPrefsCache.refresh(ctx);
        }
    }

    public void hook() {
        try {
            Class<?> nm = XposedHelpers.findClass("android.app.NotificationManager", lpparam.classLoader);

            XposedBridge.hookAllMethods(nm, "createNotificationChannel", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refreshPrefs();
                    if (!BrowserOptPrefsCache.isBlockMarketingChannels()) return;
                    if (param.args.length < 1 || !(param.args[0] instanceof NotificationChannel)) return;
                    NotificationChannel ch = (NotificationChannel) param.args[0];
                    if (isMarketingChannel(ch.getId())) {
                        downgrade(ch);
                        XposedBridge.log("[" + TAG + "] Marketing channel muted: " + ch.getId());
                    }
                }
            });

            XposedBridge.hookAllMethods(nm, "createNotificationChannels", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    refreshPrefs();
                    if (!BrowserOptPrefsCache.isBlockMarketingChannels()) return;
                    if (param.args.length < 1 || !(param.args[0] instanceof List)) return;
                    for (Object o : (List<?>) param.args[0]) {
                        if (o instanceof NotificationChannel) {
                            NotificationChannel ch = (NotificationChannel) o;
                            if (isMarketingChannel(ch.getId())) downgrade(ch);
                        }
                    }
                }
            });

            XposedBridge.log("[" + TAG + "] NotificationChannel hook installed");
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] NotificationChannel hook failed: " + t.getMessage());
        }
    }

    /** 进程起来后，把已经建好的营销渠道补降一次。 */
    public void applyExisting(Context ctx) {
        if (ctx == null) return;
        BrowserOptPrefsCache.refresh(ctx);
        if (!BrowserOptPrefsCache.isBlockMarketingChannels()) return;
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            List<NotificationChannel> channels = nm.getNotificationChannels();
            int muted = 0;
            for (NotificationChannel ch : channels) {
                if (!isMarketingChannel(ch.getId())) continue;
                if (ch.getImportance() == NotificationManager.IMPORTANCE_NONE) continue;
                NotificationChannel copy = new NotificationChannel(
                        ch.getId(), ch.getName(), NotificationManager.IMPORTANCE_NONE);
                copy.setDescription(ch.getDescription());
                copy.setShowBadge(false);
                nm.createNotificationChannel(copy);
                muted++;
            }
            if (muted > 0) {
                XposedBridge.log("[" + TAG + "] Muted " + muted + " existing marketing channel(s)");
            }
        } catch (Throwable t) {
            XposedBridge.log("[" + TAG + "] applyExisting channels failed: " + t.getMessage());
        }
    }

    private static void downgrade(NotificationChannel ch) {
        try {
            ch.setImportance(NotificationManager.IMPORTANCE_NONE);
            ch.setShowBadge(false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ch.setAllowBubbles(false);
            }
            // setSound(null, null) 需要 API 26+，minSdk 已是 33
            ch.setSound(null, null);
            ch.enableVibration(false);
            ch.setLockscreenVisibility(android.app.Notification.VISIBILITY_SECRET);
        } catch (Throwable ignored) {
        }
    }

    static boolean isMarketingChannel(String id) {
        if (id == null) return false;
        for (String p : PROTECTED_CHANNEL_PREFIX) {
            if (id.startsWith(p)) return false;
        }
        for (String p : MARKETING_CHANNEL_PREFIX) {
            if (id.startsWith(p)) return true;
        }
        return false;
    }
}
