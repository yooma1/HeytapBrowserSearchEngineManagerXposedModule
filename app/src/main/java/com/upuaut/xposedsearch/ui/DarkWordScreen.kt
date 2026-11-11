// app/src/main/java/com/upuaut/xposedsearch/ui/DarkWordScreen.kt
package com.upuaut.xposedsearch.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upuaut.xposedsearch.coui.*
import io.github.suqi8.coui.kmp.basic.CardListPosition

@Composable
fun DarkWordScreen(
    viewModel: DarkWordViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
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
                title = "搜索热词",
                scrollBehavior = topAppBarScrollBehavior,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
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
                DarkWordEnableCard(
                    isEnabled = uiState.isModuleEnabled,
                    onEnabledChange = { enabled: Boolean ->
                        viewModel.setModuleEnabled(enabled)
                    }
                )
            }

            item {
                SmallTitle(text = "热词策略")
            }

            item {
                DarkWordSettingsCard(
                    isDarkWordDisabled = uiState.isDarkWordDisabled,
                    moduleEnabled = uiState.isModuleEnabled,
                    onDarkWordDisabledChange = { disabled: Boolean ->
                        viewModel.setDarkWordDisabled(disabled)
                    }
                )
            }

            item {
                SmallTitle(text = "说明")
            }

            item {
                DarkWordInfoCard()
            }
        }
    }
}

private val CardHorizontalMargin = 16.dp

@Composable
private fun DarkWordEnableCard(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
    ) {
        SuperSwitch(
            title = "启用热词管理",
            summary = "开启后由模块接管浏览器搜索栏底纹热词",
            checked = isEnabled,
            onCheckedChange = onEnabledChange,
            cardListPosition = CardListPosition.Full
        )
    }
}

@Composable
private fun DarkWordSettingsCard(
    isDarkWordDisabled: Boolean,
    moduleEnabled: Boolean,
    onDarkWordDisabledChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
    ) {
        SuperSwitch(
            title = "禁用搜索栏热词",
            summary = "隐藏搜索框内的滚动热门词与运营底纹",
            checked = isDarkWordDisabled,
            onCheckedChange = onDarkWordDisabledChange,
            enabled = moduleEnabled,
            cardListPosition = CardListPosition.Full
        )
    }
}

@Composable
private fun DarkWordInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CardHorizontalMargin)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "• 启用「热词管理」后，本页规则才会注入浏览器。\n• 开启「禁用搜索栏热词」可彻底隐藏搜索框内的滚动推荐词。\n• 切换开关后建议在首页点击「强行停止」重启浏览器立即生效。",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2
            )
        }
    }
}
