package com.upuaut.xposedsearch;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 系统组件挂起控制（Root）。
 *
 * 2026-11-11 事故复盘：
 *   com.opos.ads 不只是广告 SDK。它在 AndroidManifest 里注册了：
 *     - Service  com.coloros.appmanager.APPMANAGER_BIND（ColorAppServicesManagerClient，持 oppo.permission.OPPO_COMPONENT_SAFE）
 *     - Provider com.opos.adsprovider（AppStateProvider）
 *     - Provider com.opos.ads.user_permission（UserPermissionSwitchesProvider）
 *   系统「应用管理 / 应用详情 / 权限管理」页与多个第三方 App 通过它取应用状态与权限开关。
 *   `pm suspend` 会让这些组件直接不可用：调用方拿不到 binder 与 provider 应答，
 *   界面停在白屏（或空白 Activity）不返回；system_server 侧因 cancelNotification →
 *   PendingIntent → AMS 锁链叠加，最终触发 watchdog 重启。已实测 dump 佐证：
 *   system_server_watchdog 的 main 阻塞在 NotificationManagerService.cancelAllNotificationsByListLocked
 *   → PendingIntentRecord.sendInner 等 AMS 锁，锁持有者卡在 LmkdConnection.waitForConnection。
 *
 * 因此本类现在遵守三条硬规则：
 *   1. 默认清单为空——不再自动挂起任何系统组件；
 *   2. CRITICAL_DENYLIST 里的包一律拒绝挂起（含 com.opos.ads / heytap / oplus 全家桶）；
 *   3. 任何挂起动作之前，先做一次「依赖面探测」，命中系统服务 / ContentProvider 立即放弃。
 *
 * 不再推荐用 pm suspend 处理系统投放组件；要净化广告请走包内 Hook（见 BrowserCleanHook / ErrorPageAdHook）。
 */
public class AdComponentController {

    private static final String TAG = "XposedSearch";

    /**
     * 默认挂起清单：故意为空。
     * 历史版本这里是 {"com.opos.ads"}，已确认会造成系统白屏 + watchdog 重启，永久移除。
     */
    public static final String[] DEFAULT_AD_PACKAGES = {};

    /**
     * 系统关键组件保护名单：任何情况下都不允许挂起。
     * com.opos.ads —— 提供 APPMANAGER_BIND 服务与 2 个 ContentProvider，挂起即白屏。
     * 其余为系统 UI / 应用管理 / 权限 / 通知链路上的必需组件。
     */
    private static final Set<String> CRITICAL_DENYLIST = new HashSet<>(Arrays.asList(
            "com.opos.ads",
            "com.oplus.appplatform",
            "com.oplus.sau",
            "com.oplus.notificationmanager",
            "com.oplus.securitypermission",
            "com.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.android.settings",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.providers.settings",
            "com.android.providers.media",
            "com.heytap.browser",
            "com.heytap.market",
            "com.heytap.themestore",
            "com.oplus.themestore"
    ));

    public static boolean isSuspended(String pkg) {
        RootUtils.CommandResult r = RootUtils.executeRootCommand("dumpsys package " + pkg);
        if (!r.success) return false;
        String out = r.output;
        int idx = out.indexOf("suspended=true");
        return idx >= 0;
    }

    /** 该包是否被保护名单拦下。 */
    public static boolean isProtected(String pkg) {
        return pkg != null && CRITICAL_DENYLIST.contains(pkg);
    }

    /**
     * 依赖面探测：包里带 Service / ContentProvider 的，挂起会直接打断调用方，
     * 一律视为不安全，不做挂起。返回命中的组件描述，无命中返回 null。
     */
    private static String probeDependents(String pkg) {
        RootUtils.CommandResult r = RootUtils.executeRootCommand("dumpsys package " + pkg);
        if (!r.success) return "dumpsys 读取失败";
        String out = r.output;
        int svc = out.indexOf("Service Resolver Table:");
        int prov = out.indexOf("Registered ContentProviders:");
        if (svc >= 0) {
            int end = out.indexOf("Receivers", svc);
            if (end < 0) end = Math.min(out.length(), svc + 2000);
            if (end > svc) return "含对外 Service 绑定入口";
        }
        if (prov >= 0) return "含对外 ContentProvider";
        return null;
    }

    /** 返回实际变更的包名列表；已处于目标状态的不计入。保护名单与依赖面命中的会被跳过。 */
    public static List<String> suspendAll(String[] packages) {
        List<String> changed = new ArrayList<>();
        if (packages == null) return changed;
        for (String pkg : packages) {
            if (pkg == null || pkg.isEmpty()) continue;
            if (isProtected(pkg)) {
                Log.w(TAG, "refuse to suspend protected package: " + pkg);
                continue;
            }
            String dep = probeDependents(pkg);
            if (dep != null) {
                Log.w(TAG, "refuse to suspend " + pkg + " -> " + dep);
                continue;
            }
            if (isSuspended(pkg)) continue;
            RootUtils.CommandResult r = RootUtils.executeRootCommand("pm suspend --user 0 " + pkg);
            Log.d(TAG, "suspend " + pkg + " -> success=" + r.success + " err=" + r.error);
            if (r.success && isSuspended(pkg)) changed.add(pkg);
        }
        return changed;
    }

    /** 解除全部挂起（含保护名单，用于事故恢复）。 */
    public static List<String> unsuspendAll(String[] packages) {
        List<String> changed = new ArrayList<>();
        if (packages == null) return changed;
        for (String pkg : packages) {
            if (pkg == null || pkg.isEmpty()) continue;
            if (!isSuspended(pkg)) continue;
            RootUtils.CommandResult r = RootUtils.executeRootCommand("pm unsuspend --user 0 " + pkg);
            Log.d(TAG, "unsuspend " + pkg + " -> success=" + r.success + " err=" + r.error);
            if (r.success && !isSuspended(pkg)) changed.add(pkg);
        }
        return changed;
    }

    /**
     * 事故恢复：把保护名单里的包全部解除挂起，并清掉 stopped 标记。
     * 用于历史版本残留 suspended 状态的兜底。
     */
    public static List<String> restoreCriticalPackages() {
        List<String> restored = new ArrayList<>();
        for (String pkg : CRITICAL_DENYLIST) {
            if (isSuspended(pkg)) {
                RootUtils.CommandResult r = RootUtils.executeRootCommand(
                        "pm unsuspend --user 0 " + pkg,
                        "pm enable --user 0 " + pkg);
                Log.d(TAG, "restore " + pkg + " -> success=" + r.success);
                if (!isSuspended(pkg)) restored.add(pkg);
            }
        }
        return restored;
    }

    /** 当前处于挂起状态的默认清单条目，用于界面展示。 */
    public static List<String> suspendedState() {
        List<String> list = new ArrayList<>();
        for (String pkg : DEFAULT_AD_PACKAGES) {
            list.add(pkg + "=" + isSuspended(pkg));
        }
        return list;
    }
}
