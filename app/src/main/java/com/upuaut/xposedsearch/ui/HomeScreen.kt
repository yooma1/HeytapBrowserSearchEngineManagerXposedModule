// app/src/main/java/com/upuaut/xposedsearch/ui/HomeScreen.kt
package com.upuaut.xposedsearch.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upuaut.xposedsearch.coui.*
import io.github.suqi8.coui.kmp.basic.CardListPosition

@Composable
fun HomeScreen(
    listState: androidx.compose.foundation.lazy.LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    viewModel: MainViewModel = viewModel(),
    onNavigateToEngines: () -> Unit,
    onNavigateToHotSites: () -> Unit,
    onNavigateToDarkWord: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAll()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "HeyTap 浏览器管理",
                scrollBehavior = topAppBarScrollBehavior
            )
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            overscrollEffect = null
        ) {
            item {
                StatusCard(
                    hasRootAccess = uiState.hasRootAccess,
                    isCheckingRoot = uiState.isCheckingRoot,
                    isXposedActive = uiState.isXposedActive
                )
            }

            item {
                SmallTitle(text = "功能管理")
            }

            item {
                FunctionCard(
                    onNavigateToEngines = onNavigateToEngines,
                    onNavigateToHotSites = onNavigateToHotSites,
                    onNavigateToDarkWord = onNavigateToDarkWord
                )
            }

            item {
                SmallTitle(text = "快捷操作")
            }

            item {
                SettingsCard(
                    isIconHidden = uiState.isIconHidden,
                    isForceStoppingBrowser = uiState.isForceStoppingBrowser,
                    onIconHiddenChange = { hidden ->
                        if (hidden) {
                            viewModel.setIconHidden(true)
                            Toast.makeText(context, "桌面图标已隐藏\n可通过 LSPosed 打开本应用", Toast.LENGTH_LONG).show()
                        } else {
                            viewModel.setIconHidden(false)
                        }
                    },
                    onForceStop = {
                        viewModel.forceStopBrowser()
                        val viaRoot = uiState.hasRootAccess
                        Toast.makeText(
                            context,
                            if (viaRoot) "浏览器已强制停止" else "已请求系统结束后台进程",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onOpenBrowser = {
                        viewModel.openBrowser()
                    }
                )
            }

            item {
                SmallTitle(text = "浏览器净化")
            }

            item {
                OptTogglesCard(
                    toggles = uiState.optToggles,
                    onToggle = { key, enabled -> viewModel.setOptToggle(key, enabled) },
                    onAllOn = { viewModel.setAllOptToggles(true) },
                    onAllOff = { viewModel.setAllOptToggles(false) }
                )
            }
        }
    }
}

private val CardHorizontalMargin = 16.dp

@Composable
fun StatusCard(
    hasRootAccess: Boolean,
    isCheckingRoot: Boolean,
    isXposedActive: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusItem(
                label = "Root 权限",
                isActive = hasRootAccess,
                isLoading = isCheckingRoot,
                activeText = "已授权",
                inactiveText = "未使用",
                loadingText = "检测中..."
            )

            HorizontalDivider()

            StatusItem(
                label = "Xposed 模块",
                isActive = isXposedActive,
                isLoading = false,
                activeText = "已激活",
                inactiveText = "未激活",
                loadingText = ""
            )
        }
    }
}

@Composable
private fun StatusItem(
    label: String,
    isActive: Boolean,
    isLoading: Boolean,
    activeText: String,
    inactiveText: String,
    loadingText: String
) {
    val badgeBg = when {
        isLoading -> MiuixTheme.colorScheme.secondaryVariant
        isActive -> Color(0x1F2E7D32)
        else -> Color(0x1FC62828)
    }
    val badgeFg = when {
        isLoading -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        isActive -> Color(0xFF2E7D32)
        else -> Color(0xFFC62828)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(badgeBg)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!isLoading) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(badgeFg)
                )
            }
            Text(
                text = when {
                    isLoading -> loadingText
                    isActive -> activeText
                    else -> inactiveText
                },
                style = MiuixTheme.textStyles.footnote1,
                color = badgeFg,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SettingsCard(
    isIconHidden: Boolean,
    isForceStoppingBrowser: Boolean,
    onIconHiddenChange: (Boolean) -> Unit,
    onForceStop: () -> Unit,
    onOpenBrowser: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SuperSwitch(
                title = "隐藏桌面图标",
                summary = "隐藏后可通过 LSPosed 模块页打开",
                checked = isIconHidden,
                onCheckedChange = onIconHiddenChange,
                cardListPosition = CardListPosition.Head
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = if (isForceStoppingBrowser) "停止中..." else "强行停止",
                    onClick = onForceStop,
                    enabled = !isForceStoppingBrowser,
                    modifier = Modifier.weight(1f)
                )

                TextButton(
                    text = "打开浏览器",
                    onClick = onOpenBrowser,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
private fun FeatureLeadingIcon(
    icon: ImageVector,
    tint: Color,
    containerColor: Color
) {
    Box(
        modifier = Modifier
            .padding(end = 12.dp)
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun FunctionCard(
    onNavigateToEngines: () -> Unit,
    onNavigateToHotSites: () -> Unit,
    onNavigateToDarkWord: () -> Unit
) {
    val primary = MiuixTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SuperArrow(
                title = "搜索引擎",
                summary = "排序、编辑与添加自定义搜索引擎",
                onClick = onNavigateToEngines,
                cardListPosition = CardListPosition.Head,
                startAction = {
                    FeatureLeadingIcon(
                        icon = Icons.Default.Search,
                        tint = primary,
                        containerColor = primary.copy(alpha = 0.12f)
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 66.dp, end = 16.dp))

            SuperArrow(
                title = "热门网站",
                summary = "自定义浏览器主页宫格快捷导航",
                onClick = onNavigateToHotSites,
                cardListPosition = CardListPosition.Middle,
                startAction = {
                    FeatureLeadingIcon(
                        icon = Icons.Default.Public,
                        tint = Color(0xFF00897B),
                        containerColor = Color(0x1F00897B)
                    )
                }
            )

            HorizontalDivider(modifier = Modifier.padding(start = 66.dp, end = 16.dp))

            SuperArrow(
                title = "搜索热词",
                summary = "控制或禁用搜索栏底纹热词推荐",
                onClick = onNavigateToDarkWord,
                cardListPosition = CardListPosition.Tail,
                startAction = {
                    FeatureLeadingIcon(
                        icon = Icons.Default.TrendingUp,
                        tint = Color(0xFFFB8C00),
                        containerColor = Color(0x1FFB8C00)
                    )
                }
            )
        }
    }
}

// ---------------- 净化开关卡片 ----------------

@Composable
fun OptTogglesCard(
    toggles: List<OptToggle>,
    onToggle: (String, Boolean) -> Unit,
    onAllOn: () -> Unit = {},
    onAllOff: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            toggles.forEachIndexed { index, toggle ->
                val pos = when {
                    index == 0 -> CardListPosition.Head
                    else -> CardListPosition.Middle
                }
                SuperSwitch(
                    title = toggle.title,
                    summary = toggle.summary,
                    checked = toggle.enabled,
                    onCheckedChange = { v -> onToggle(toggle.key, v) },
                    enabled = true,
                    cardListPosition = pos
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            // 一键全开 / 全关：COUI 的 TextButton 胶囊按钮，左右各占一半
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "一键开启全部",
                    onClick = onAllOn,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
                TextButton(
                    text = "全部关闭",
                    onClick = onAllOff,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
