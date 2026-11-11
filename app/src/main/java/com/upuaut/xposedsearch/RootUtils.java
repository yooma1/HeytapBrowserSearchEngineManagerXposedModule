package com.upuaut.xposedsearch;

import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;

public class RootUtils {

    private static final String TAG = "XposedSearch";

    /** 探测结果三态：-1 未探测 / 0 无 root / 1 有 root。探测一次后永久缓存。 */
    private static volatile int sRootState = -1;

    /** 常见 su 二进制落点：Magisk / KernelSU / APatch / 传统 root。 */
    private static final String[] SU_PATHS = {
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/product/bin/su",
            "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su",
            "/data/adb/ap/bin/su",
            "/data/adb/magisk/su"
    };

    /**
     * 纯 shell 探测 su 是否存在：走 /system/bin/sh，不经过 su 本身，
     * 因此不会触发 Magisk/KernelSU 的授权请求。返回 true 表示环境里有 su 可供后续使用。
     */
    private static boolean probeSuBinary() {
        // 1) shell 内 command -v su（覆盖 PATH 里的非常规落点）
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"/system/bin/sh", "-c", "command -v su 2>/dev/null || true"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = r.readLine();
            r.close();
            p.waitFor();
            if (line != null && !line.trim().isEmpty()) return true;
        } catch (Exception ignored) {
        }
        // 2) 回退：逐个硬编码落点看是否存在
        for (String path : SU_PATHS) {
            try {
                if (new java.io.File(path).exists()) return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    /**
     * 静默探测 Root：先确认环境里存在 su 二进制，只有存在时才真正 exec 一次。
     * 无 root 设备上调用本方法不会产生任何 su 进程，因此不会触发授权管理器弹窗。
     * 结果带缓存，冷启动最多 exec 一次。
     */
    public static boolean detectRootQuietly() {
        int cached = sRootState;
        if (cached >= 0) return cached == 1;

        if (!probeSuBinary()) {
            sRootState = 0;
            Log.d(TAG, "detectRootQuietly: no su binary found, treat as non-root");
            return false;
        }

        boolean hasRoot = checkRootAccess();
        sRootState = hasRoot ? 1 : 0;
        Log.d(TAG, "detectRootQuietly: su binary present, checkRootAccess=" + hasRoot);
        return hasRoot;
    }

    /** 读取已缓存的探测结果，不做任何 IO。未探测时返回 false。 */
    public static boolean hasRootCached() {
        return sRootState == 1;
    }

    /** 重置缓存，供用户在系统里授予/撤销 root 后手动刷新。 */
    public static void resetRootCache() {
        sRootState = -1;
    }

    /**
     * 检查是否有 Root 权限
     */
    public static boolean checkRootAccess() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec("su -c id");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            reader.close();
            int exitCode = process.waitFor();
            boolean ok = exitCode == 0 && line != null && line.contains("uid=0");
            sRootState = ok ? 1 : 0;
            return ok;
        } catch (Exception e) {
            Log.e(TAG, "checkRootAccess failed: " + e.getMessage());
            return false;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 请求 Root 权限
     */
    public static boolean requestRootAccess() {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes("id\n");
            os.writeBytes("exit\n");
            os.flush();
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (Exception e) {
            Log.e(TAG, "requestRootAccess failed: " + e.getMessage());
            return false;
        } finally {
            try {
                if (os != null) os.close();
            } catch (Exception ignored) {}
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 执行 Root 命令
     */
    public static CommandResult executeRootCommand(String... commands) {
        Process process = null;
        DataOutputStream os = null;
        BufferedReader successReader = null;
        BufferedReader errorReader = null;

        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());

            for (String command : commands) {
                os.writeBytes(command + "\n");
            }
            os.writeBytes("exit\n");
            os.flush();

            int exitCode = process.waitFor();

            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();

            successReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = successReader.readLine()) != null) {
                output.append(line).append("\n");
            }

            errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorReader.readLine()) != null) {
                error.append(line).append("\n");
            }

            return new CommandResult(exitCode == 0, output.toString().trim(), error.toString().trim());

        } catch (Exception e) {
            Log.e(TAG, "executeRootCommand failed: " + e.getMessage());
            return new CommandResult(false, "", e.getMessage());
        } finally {
            try {
                if (os != null) os.close();
                if (successReader != null) successReader.close();
                if (errorReader != null) errorReader.close();
            } catch (Exception ignored) {}
            if (process != null) {
                process.destroy();
            }
        }
    }

    /**
     * 强制停止应用
     */
    public static boolean forceStopApp(String packageName) {
        CommandResult result = executeRootCommand("am force-stop " + packageName);
        Log.d(TAG, "forceStopApp " + packageName + ": " + result.success);
        return result.success;
    }

    /**
     * 命令执行结果
     */
    public static class CommandResult {
        public final boolean success;
        public final String output;
        public final String error;

        public CommandResult(boolean success, String output, String error) {
            this.success = success;
            this.output = output;
            this.error = error;
        }
    }
}