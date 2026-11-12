package com.upuaut.xposedsearch.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.upuaut.xposedsearch.AdComponentController
import com.upuaut.xposedsearch.AppUtils
import com.upuaut.xposedsearch.BrowserOptConfigManager
import com.upuaut.xposedsearch.ConfigManager
import com.upuaut.xposedsearch.RootUtils
import com.upuaut.xposedsearch.SearchEngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一条可切换的净化规则，直接映射 BrowserOptConfigManager 的偏好键。 */
data class OptToggle(
    val key: String,
    val title: String,
    val summary: String,
    val enabled: Boolean = true
)

data class MainUiState(
    val engines: List<SearchEngineConfig> = emptyList(),
    val hasRootAccess: Boolean = false,
    val isCheckingRoot: Boolean = true,
    val isRequestingRoot: Boolean = false,
    val isForceStoppingBrowser: Boolean = false,
    val isIconHidden: Boolean = false,
    val isXposedActive: Boolean = false,
    val showAddDialog: Boolean = false,
    val editingEngine: SearchEngineConfig? = null,
    val deletingEngine: SearchEngineConfig? = null,
    val showHideIconConfirmDialog: Boolean = false,
    val updatingEngine: SearchEngineConfig? = null,
    val removedEngine: SearchEngineConfig? = null,
    // 新增状态
    val conflictEngine: SearchEngineConfig? = null, // 显示冲突对话框
    // 净化开关
    val optToggles: List<OptToggle> = emptyList(),
    val isTogglingAds: Boolean = false,
    val adComponentsSuspended: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TARGET_PACKAGE = "com.heytap.browser"
        /** 与 AppUtils 同一偏好文件，保持一处存储。 */
        private const val PREF_APP_SETTINGS = "app_settings"
        /** 历史挂起状态的一次性修复标记，置位后冷启动不再执行恢复流程。 */
        private const val REPAIR_DONE_KEY = "ad_component_repair_done"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val context get() = getApplication<Application>()

    init {
        refreshAll()
    }

    fun refreshAll() {
        refreshEngines()
        checkRootStatus()
        refreshIconState()
        checkXposedStatus()
        refreshOptToggles()
    }

    fun refreshEngines() {
        val engines = ConfigManager.loadEngines(context)
        _uiState.value = _uiState.value.copy(engines = engines)
    }

    // ---------------- 净化开关 ----------------

    fun refreshOptToggles() {
        val prefs = context.getSharedPreferences(
            BrowserOptConfigManager.PREF_NAME, android.content.Context.MODE_PRIVATE
        )
        fun v(key: String) = prefs.getBoolean(key, true)

        val toggles = listOf(
            OptToggle(
                BrowserOptConfigManager.KEY_REMOVE_SPLASH_AD, "移除开屏广告",
                "跳过 StepSplash / SplashConsumer 的在线开屏请求"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_BYPASS_WEB_LIMIT, "解除网页浏览限制",
                "放行 UrlBlocker 拦截与安全弹窗"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_REMOVE_SEARCH_APP_PROMO, "移除搜索框 App 推广",
                "过滤 sugNaturalApp / sugAd 等推荐项"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_REMOVE_WINDOW_LIMIT, "移除窗口数量限制",
                "标签页上限提升到 999"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_BOTTOM_AD, "移除下载页底部推荐",
                "隐藏下载管理页「大家还下载了 / 游戏推荐」"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_DIALOG_AD, "移除下载弹窗官方推荐与广告",
                "跳过应用商店官方推荐替换与弹窗底部推广"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_DISABLE_NEWS_FEED, "关闭资讯流 / 热榜",
                "关闭底层 iflow、热榜与首页推荐"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_FORCE_SIMPLE_MODE, "强制简洁主页",
                "锁定简洁模式，主页只保留网站入口"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_BLOCK_MARKETING_CHANNELS, "关闭营销推送渠道",
                "只静音资讯类渠道，下载等正常通知保留"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_FILTER_CLOUD_ENGINE_CONFIG, "过滤云控引擎配置",
                "阻止云端下发覆盖本地搜索引擎配置"
            ),
            OptToggle(
                BrowserOptConfigManager.KEY_BLOCK_ERROR_PAGE_RECOMMEND, "关闭离线页推荐",
                "错误页不再插入「你可能想搜 / 猜您可能想知道」"
            )
        ).map { it.copy(enabled = v(it.key)) }
        _uiState.value = _uiState.value.copy(optToggles = toggles)
    }

    /** 一键把全部净化开关设为同一状态（系统广告组件单独按 Root 可用性处理）。 */
    fun setAllOptToggles(enabled: Boolean) {
        val allKeys = listOf(
            BrowserOptConfigManager.KEY_REMOVE_SPLASH_AD,
            BrowserOptConfigManager.KEY_BYPASS_WEB_LIMIT,
            BrowserOptConfigManager.KEY_REMOVE_SEARCH_APP_PROMO,
            BrowserOptConfigManager.KEY_REMOVE_WINDOW_LIMIT,
            BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_BOTTOM_AD,
            BrowserOptConfigManager.KEY_REMOVE_DOWNLOAD_DIALOG_AD,
            BrowserOptConfigManager.KEY_DISABLE_NEWS_FEED,
            BrowserOptConfigManager.KEY_FORCE_SIMPLE_MODE,
            BrowserOptConfigManager.KEY_BLOCK_MARKETING_CHANNELS,
            BrowserOptConfigManager.KEY_FILTER_CLOUD_ENGINE_CONFIG,
            BrowserOptConfigManager.KEY_BLOCK_ERROR_PAGE_RECOMMEND
        )
        val prefs = context.getSharedPreferences(
            BrowserOptConfigManager.PREF_NAME, android.content.Context.MODE_PRIVATE
        )
        prefs.edit().apply {
            allKeys.forEach { putBoolean(it, enabled) }
        }.commit()
        refreshOptToggles()
    }

    fun setOptToggle(key: String, enabled: Boolean) {
        BrowserOptConfigManager.setOption(context, key, enabled)
        refreshOptToggles()
    }

    /**
     * 事故恢复入口：把保护名单里的系统组件全部解除挂起。
     * 2026-11-11 起不再提供「挂起系统广告组件」开关——pm suspend com.opos.ads
     * 会让应用管理 / 权限页 / 第三方 App 点击白屏并触发 system_server watchdog 重启。
     *
     * 2026-11-12：改为一次性迁移。历史版本可能留下 suspended 状态，只在首次升级后
     * 跑一次恢复，成功后写入 REPAIR_DONE_KEY，之后冷启动不再 fork 任何 shell。
     */
    fun restoreSystemComponents() {
        if (!_uiState.value.hasRootAccess) return
        val prefs = context.getSharedPreferences(PREF_APP_SETTINGS, android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(REPAIR_DONE_KEY, false)) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTogglingAds = true)
            val restored = withContext(Dispatchers.IO) {
                AdComponentController.restoreCriticalPackages()
            }
            android.util.Log.d("XposedSearch", "restoreSystemComponents -> " + restored)
            // 无论是否有包被恢复，本次检查已完成，标记后不再重复
            prefs.edit().putBoolean(REPAIR_DONE_KEY, true).apply()
            _uiState.value = _uiState.value.copy(
                isTogglingAds = false,
                adComponentsSuspended = false
            )
            refreshOptToggles()
        }
    }

    private fun refreshIconState() {
        _uiState.value = _uiState.value.copy(
            isIconHidden = AppUtils.isIconHidden(context)
        )
    }

    private fun checkXposedStatus() {
        _uiState.value = _uiState.value.copy(
            isXposedActive = isXposedModuleActive()
        )
    }

    private fun checkRootStatus() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCheckingRoot = true)
            // 静默探测：无 root 设备不会产生 su 进程，也不弹授权窗
            val hasRoot = withContext(Dispatchers.IO) {
                RootUtils.detectRootQuietly()
            }
            _uiState.value = _uiState.value.copy(
                hasRootAccess = hasRoot,
                isCheckingRoot = false
            )
            // restoreSystemComponents 内部已做 REPAIR_DONE_KEY 一次性判断，
            // 冷启动不会重复 fork 32 次 dumpsys
            if (hasRoot) restoreSystemComponents()
        }
    }

    fun forceStopBrowser(): Boolean {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isForceStoppingBrowser = true)
            withContext(Dispatchers.IO) {
                if (RootUtils.hasRootCached() || RootUtils.detectRootQuietly()) {
                    RootUtils.forceStopApp(TARGET_PACKAGE)
                } else {
                    // 无 root 降级：只能结束后台进程；浏览器在前台时系统会忽略，但不报错、不弹窗
                    try {
                        val am = context.getSystemService(
                            android.content.Context.ACTIVITY_SERVICE
                        ) as android.app.ActivityManager
                        am.killBackgroundProcesses(TARGET_PACKAGE)
                    } catch (e: Exception) {
                        android.util.Log.w("XposedSearch", "killBackgroundProcesses failed", e)
                    }
                }
            }
            _uiState.value = _uiState.value.copy(isForceStoppingBrowser = false)
        }
        return true
    }

    fun setIconHidden(hidden: Boolean) {
        AppUtils.setIconHidden(context, hidden)
        _uiState.value = _uiState.value.copy(isIconHidden = hidden)
    }

    fun showHideIconConfirmDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showHideIconConfirmDialog = show)
    }

    /** 用户拖动后提交新的引擎顺序（只影响当前分组）。 */
    fun reorderEngines(orderedKeys: List<String>) {
        ConfigManager.reorderEngines(context, orderedKeys)
        refreshEngines()
    }

    fun updateEngineEnabled(key: String, enabled: Boolean) {
        ConfigManager.updateEngineEnabled(context, key, enabled)
        refreshEngines()
    }

    fun addCustomEngine(key: String, name: String, searchUrl: String): Boolean {
        val success = ConfigManager.addCustomEngine(context, key, name, searchUrl)
        if (success) refreshEngines()
        return success
    }

    fun updateEngine(key: String, name: String, searchUrl: String, enabled: Boolean) {
        ConfigManager.updateEngineByUser(context, key, name, searchUrl, enabled)
        refreshEngines()
    }

    fun updateCustomEngine(oldKey: String, newKey: String, name: String, url: String, enabled: Boolean): Boolean {
        val success = ConfigManager.updateCustomEngineWithKey(context, oldKey, newKey, name, url, enabled)
        if (success) refreshEngines()
        return success
    }

    fun deleteEngine(key: String): Boolean {
        val success = ConfigManager.deleteEngine(context, key)
        if (success) refreshEngines()
        return success
    }

    fun resetEngine(key: String): Boolean {
        val success = ConfigManager.resetEngine(context, key)
        if (success) refreshEngines()
        return success
    }

    fun applyPendingUpdate(key: String) {
        ConfigManager.applyPendingUpdate(context, key)
        refreshEngines()
    }

    fun ignorePendingUpdate(key: String) {
        ConfigManager.ignorePendingUpdate(context, key)
        refreshEngines()
    }

    fun convertToCustomEngine(key: String) {
        ConfigManager.convertToCustomEngine(context, key)
        refreshEngines()
    }

    fun convertCustomToBuiltin(key: String) {
        ConfigManager.convertCustomToBuiltin(context, key)
        refreshEngines()
    }

    fun createCustomEngineCopy(key: String): String? {
        val newKey = ConfigManager.createCustomEngineCopy(context, key)
        if (newKey != null) refreshEngines()
        return newKey
    }

    fun showAddDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showAddDialog = show)
    }

    fun showEditDialog(engine: SearchEngineConfig?) {
        _uiState.value = _uiState.value.copy(editingEngine = engine)
    }

    fun showDeleteDialog(engine: SearchEngineConfig?) {
        _uiState.value = _uiState.value.copy(deletingEngine = engine)
    }

    fun showUpdateDialog(engine: SearchEngineConfig?) {
        _uiState.value = _uiState.value.copy(updatingEngine = engine)
    }

    fun showRemovedDialog(engine: SearchEngineConfig?) {
        _uiState.value = _uiState.value.copy(removedEngine = engine)
    }

    fun showConflictDialog(engine: SearchEngineConfig?) {
        _uiState.value = _uiState.value.copy(conflictEngine = engine)
    }

    fun openBrowser() {
        val intent = context.packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

private fun isXposedModuleActive(): Boolean {
    return try {
        val methods = Class.forName("de.robv.android.xposed.XposedBridge")
            .declaredMethods
        methods.isNotEmpty()
    } catch (e: ClassNotFoundException) {
        false
    }
}
