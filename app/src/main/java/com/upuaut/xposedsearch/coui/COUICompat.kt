// app/src/main/java/com/upuaut/xposedsearch/coui/COUICompat.kt
//
// ColorOS 17 (COUI) 设计语言接入层。
// 目的：把界面层的组件调用一次性切到 io.github.suqi8.coui.kmp（COUI 1.1.0），
// 同时保留原 miuix 风格的参数名，使 4 个 Screen 的业务代码无需逐行重写。
// 这里只做「名称映射 + 参数适配」，不放任何业务逻辑。

package com.upuaut.xposedsearch.coui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import io.github.suqi8.coui.kmp.basic.BasicComponent as CouiBasicComponent
import io.github.suqi8.coui.kmp.basic.BasicComponentDefaults
import io.github.suqi8.coui.kmp.basic.Card as CouiCard
import io.github.suqi8.coui.kmp.basic.CardDefaults
import io.github.suqi8.coui.kmp.basic.CardListPosition
import io.github.suqi8.coui.kmp.basic.COUIScrollBehavior
import io.github.suqi8.coui.kmp.basic.DividerDefaults
import io.github.suqi8.coui.kmp.basic.FloatingActionButton as CouiFab
import io.github.suqi8.coui.kmp.basic.HorizontalDivider as CouiDivider
import io.github.suqi8.coui.kmp.basic.Icon as CouiIcon
import io.github.suqi8.coui.kmp.basic.IconButton as CouiIconButton
import io.github.suqi8.coui.kmp.basic.Scaffold as CouiScaffold
import io.github.suqi8.coui.kmp.basic.ScrollBehavior
import io.github.suqi8.coui.kmp.basic.SmallTitle as CouiSmallTitle
import io.github.suqi8.coui.kmp.basic.Surface as CouiSurface
import io.github.suqi8.coui.kmp.basic.Switch as CouiSwitch
import io.github.suqi8.coui.kmp.basic.Text as CouiText
import io.github.suqi8.coui.kmp.basic.TextButton as CouiTextButton
import io.github.suqi8.coui.kmp.basic.TextButtonColors
import io.github.suqi8.coui.kmp.basic.TextField as CouiTextField
import io.github.suqi8.coui.kmp.basic.TopAppBar as CouiTopAppBar
import io.github.suqi8.coui.kmp.icon.COUIIcons
import io.github.suqi8.coui.kmp.icon.basic.ArrowRight
import io.github.suqi8.coui.kmp.overlay.OverlayDialog
import io.github.suqi8.coui.kmp.theme.COUITheme
import io.github.suqi8.coui.kmp.theme.ColorSchemeMode
import io.github.suqi8.coui.kmp.theme.ThemeController

/**
 * COUI 主题根节点。
 *
 * 使用 [ColorSchemeMode.MonetSystem]：跟随系统深浅色，并直接取 ColorOS 的
 * 动态取色（壁纸主题色），这是 ColorOS 17 原生界面的默认行为。
 */
@Composable
fun MiuixTheme(
    content: @Composable () -> Unit,
) {
    val controller = remember {
        ThemeController(colorSchemeMode = ColorSchemeMode.MonetSystem)
    }
    val base = COUITheme.textStyles
    val textStyles = remember(base) {
        val sans = androidx.compose.ui.text.font.FontFamily.SansSerif
        io.github.suqi8.coui.kmp.theme.TextStyles(
            base.main.copy(fontFamily = sans),
            base.paragraph.copy(fontFamily = sans),
            base.body1.copy(fontFamily = sans),
            base.body2.copy(fontFamily = sans),
            base.button.copy(fontFamily = sans),
            base.footnote1.copy(fontFamily = sans),
            base.footnote2.copy(fontFamily = sans),
            base.headline1.copy(fontFamily = sans),
            base.headline2.copy(fontFamily = sans),
            base.subtitle.copy(fontFamily = sans),
            base.title1.copy(fontFamily = sans),
            base.title2.copy(fontFamily = sans),
            base.title3.copy(fontFamily = sans),
            base.title4.copy(fontFamily = sans),
        )
    }
    COUITheme(controller = controller, textStyles = textStyles) {
        content()
    }
}

/** 主题色板 / 字体层级，字段名与既有调用点保持一致。 */
object MiuixTheme {
    val colorScheme: io.github.suqi8.coui.kmp.theme.Colors
        @Composable @ReadOnlyComposable
        get() = COUITheme.colorScheme

    val textStyles: io.github.suqi8.coui.kmp.theme.TextStyles
        @Composable @ReadOnlyComposable
        get() = COUITheme.textStyles
}

/** 折叠式 TopAppBar 滚动行为。 */
@Composable
fun MiuixScrollBehavior(): ScrollBehavior = COUIScrollBehavior()

/** COUI 已由 COUIOverscrollFactory 统一接管回弹，此处保留为语义占位。 */
fun Modifier.overScrollVertical(): Modifier = this

/** 同上：边缘触感由 COUI 组件内部处理。 */
fun Modifier.scrollEndHaptic(): Modifier = this

// ---------------- 基础容器 ----------------

@Composable
fun Card(
    modifier: Modifier = Modifier,
    insideMargin: PaddingValues = CardDefaults.InsideMargin,
    cornerRadius: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    CouiCard(
        modifier = modifier,
        cornerRadius = cornerRadius,
        insideMargin = insideMargin,
        content = content,
    )
}

@Composable
fun Surface(
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit,
) {
    CouiSurface(modifier = modifier, shape = shape, color = color, content = content)
}

@Composable
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    CouiScaffold(
        modifier = modifier,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

/**
 * ColorOS 顶部标题栏。
 * 注意：COUI 的 TopAppBar 默认 color = Color.Transparent，列表上滑时文字会与标题重叠。
 * 这里默认使用 colorScheme.background 不透明背景，保证滚动时不穿透。
 */
@Composable
fun TopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    color: Color = MiuixTheme.colorScheme.background,
    scrollBehavior: ScrollBehavior? = null,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    CouiTopAppBar(
        title = title,
        modifier = modifier,
        color = color,
        subtitle = subtitle,
        scrollBehavior = scrollBehavior,
        navigationIcon = navigationIcon,
        actions = actions,
    )
}

@Composable
fun SmallTitle(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    insideMargin: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 6.dp),
) {
    CouiSmallTitle(
        text = text,
        modifier = modifier,
        textColor = textColor,
        insideMargin = insideMargin,
    )
}

// ---------------- 文本 / 图标 ----------------

@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    style: TextStyle = MiuixTheme.textStyles.main,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    overflow: TextOverflow = if (maxLines == 1) TextOverflow.Ellipsis else TextOverflow.Clip,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    CouiText(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = overflow,
        textAlign = textAlign,
    )
}

@Composable
fun Icon(
    imageVector: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = Color.Unspecified,
) {
    CouiIcon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    CouiIconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content,
    )
}

@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CouiFab(onClick = onClick, modifier = modifier, content = content)
}

@Composable
fun HorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = DividerDefaults.Thickness,
) {
    CouiDivider(modifier = modifier, thickness = thickness)
}

// ---------------- 按钮 ----------------

@Composable
fun TextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: TextButtonColors = ButtonDefaults.textButtonColors(),
) {
    CouiTextButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
    )
}

object ButtonDefaults {
    @Composable
    fun textButtonColors(): TextButtonColors =
        io.github.suqi8.coui.kmp.basic.ButtonDefaults.textButtonColors()

    @Composable
    fun textButtonColorsPrimary(): TextButtonColors =
        io.github.suqi8.coui.kmp.basic.ButtonDefaults.textButtonColorsPrimary()

    @Composable
    fun textButtonColorsBorderless(): TextButtonColors =
        io.github.suqi8.coui.kmp.basic.ButtonDefaults.textButtonColorsBorderless()
}

// ---------------- 输入 ----------------

/**
 * COUI 输入框。
 * 注意：COUI TextField 内部包含浮动/外置 label 与 16x12dp 内边距，
 * 切勿在外部强加固定 `.height(68.dp)`，否则多行或聚焦时文字会被截断出框。
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    enabled: Boolean = true,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 3,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    CouiTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        enabled = enabled,
        isError = isError,
        singleLine = singleLine && maxLines == 1,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}

@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    CouiSwitch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
    )
}

// ---------------- 偏好项（COUI 原生 BasicComponent） ----------------

/**
 * COUI 原生设置开关行：直接复用 [CouiBasicComponent]，
 * 具备 ColorOS 17 标准 16dp×12dp 内边距、双行排版（body1 16sp + body2 14sp）及整行点击切换。
 */
@Composable
fun SuperSwitch(
    title: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    cardListPosition: CardListPosition = CardListPosition.None,
    startAction: (@Composable () -> Unit)? = null,
) {
    CouiBasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        startAction = startAction,
        endActions = {
            CouiSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        insideMargin = insideMargin,
        cardListPosition = cardListPosition,
        onClick = if (enabled && onCheckedChange != null) {
            { onCheckedChange(!checked) }
        } else null,
        enabled = enabled,
    )
}

/**
 * COUI 原生跳转行：直接复用 [CouiBasicComponent] + [COUIIcons.Basic.ArrowRight] 矢量箭头，
 * 支持左侧前置图标、按压高亮与列表首尾圆角留白。
 */
@Composable
fun SuperArrow(
    title: String,
    summary: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    insideMargin: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 13.dp),
    cardListPosition: CardListPosition = CardListPosition.None,
    startAction: (@Composable () -> Unit)? = null,
    endText: String? = null,
) {
    CouiBasicComponent(
        modifier = modifier,
        title = title,
        summary = summary,
        startAction = startAction,
        endActions = {
            if (!endText.isNullOrEmpty()) {
                CouiText(
                    text = endText,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            CouiIcon(
                imageVector = COUIIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        },
        insideMargin = insideMargin,
        cardListPosition = cardListPosition,
        onClick = onClick,
        enabled = enabled,
    )
}

/**
 * COUI 对话框（底部滑入 / 大屏居中）。
 * 保持 `show` 为 MutableState，与既有调用点一致。
 */
@Composable
fun SuperDialog(
    title: String,
    show: androidx.compose.runtime.MutableState<Boolean>,
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    OverlayDialog(
        show = show.value,
        title = title,
        onDismissRequest = {
            show.value = false
            onDismissRequest()
        },
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}
