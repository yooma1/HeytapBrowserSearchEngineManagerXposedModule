// app/src/main/java/com/upuaut/xposedsearch/ui/MainScreen.kt
package com.upuaut.xposedsearch.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.upuaut.xposedsearch.SearchEngineConfig
import com.upuaut.xposedsearch.coui.*
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshEngines()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val builtinEngines = remember(uiState.engines) {
        uiState.engines.filter { it.isBuiltin }
    }
    val customEngines = remember(uiState.engines) {
        uiState.engines.filter { !it.isBuiltin }
    }

    // 与热门网站完全一致的拖拽排序模型（编辑/完成、Menu 拖动把手、弹簧缩放与回弹）
    var isEditMode by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val builtinList = remember { mutableStateListOf<SearchEngineConfig>() }
    val customList = remember { mutableStateListOf<SearchEngineConfig>() }

    var draggingKey by remember { mutableStateOf<String?>(null) }
    var heldKey by remember { mutableStateOf<String?>(null) }
    val dragOffset = remember { Animatable(0f) }
    var itemHeightPx by remember { mutableIntStateOf(with(density) { (72 + 12).dp.roundToPx() }) }

    LaunchedEffect(uiState.engines) {
        if (draggingKey == null) {
            builtinList.clear()
            builtinList.addAll(builtinEngines)
            customList.clear()
            customList.addAll(customEngines)
        }
    }

    fun commitOrder() {
        viewModel.reorderEngines(builtinList.map { it.key } + customList.map { it.key })
    }

    val showAddDialog = remember { mutableStateOf(uiState.showAddDialog) }
    val showEditDialog = remember { mutableStateOf(uiState.editingEngine != null) }
    val showDeleteDialog = remember { mutableStateOf(uiState.deletingEngine != null) }
    val showUpdateDialog = remember { mutableStateOf(uiState.updatingEngine != null) }
    val showRemovedDialog = remember { mutableStateOf(uiState.removedEngine != null) }
    val showConflictDialog = remember { mutableStateOf(uiState.conflictEngine != null) }

    LaunchedEffect(uiState.showAddDialog) { showAddDialog.value = uiState.showAddDialog }
    LaunchedEffect(uiState.editingEngine) { showEditDialog.value = uiState.editingEngine != null }
    LaunchedEffect(uiState.deletingEngine) { showDeleteDialog.value = uiState.deletingEngine != null }
    LaunchedEffect(uiState.updatingEngine) { showUpdateDialog.value = uiState.updatingEngine != null }
    LaunchedEffect(uiState.removedEngine) { showRemovedDialog.value = uiState.removedEngine != null }
    LaunchedEffect(uiState.conflictEngine) { showConflictDialog.value = uiState.conflictEngine != null }

    val topAppBarScrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "搜索引擎",
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog(true) }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "添加引擎",
                    tint = Color.White
                )
            }
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
                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            overscrollEffect = null
        ) {
            if (uiState.engines.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无搜索引擎\n\n请先打开浏览器的搜索引擎设置页面\n模块会自动获取引擎列表",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
            }

            if (builtinList.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTitle(text = "浏览器内置引擎")
                        Text(
                            text = if (isEditMode) "完成" else "编辑",
                            color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (isEditMode) commitOrder()
                                    isEditMode = !isEditMode
                                }
                                .padding(horizontal = 24.dp)
                        )
                    }
                }
                itemsIndexed(
                    items = builtinList,
                    key = { _, e -> e.key }
                ) { _, engine ->
                    val isDragging = draggingKey == engine.key
                    val isHeld = heldKey == engine.key

                    DraggableEngineItem(
                        engine = engine,
                        isEditMode = isEditMode,
                        isDragging = isDragging,
                        isHeld = isHeld,
                        dragOffset = if (isDragging) dragOffset.value else 0f,
                        onEnabledChange = { enabled ->
                            viewModel.updateEngineEnabled(engine.key, enabled)
                        },
                        onEdit = { viewModel.showEditDialog(engine) },
                        onDelete = null,
                        onReset = if (engine.canReset()) {
                            { viewModel.resetEngine(engine.key) }
                        } else null,
                        onUpdate = if (engine.hasPendingUpdate()) {
                            { viewModel.showUpdateDialog(engine) }
                        } else null,
                        onRemoved = if (engine.isRemovedFromBrowser) {
                            { viewModel.showRemovedDialog(engine) }
                        } else null,
                        onConflict = null,
                        onDragStart = {
                            draggingKey = engine.key
                            heldKey = engine.key
                            scope.launch { dragOffset.snapTo(0f) }
                        },
                        onDrag = { delta ->
                            scope.launch {
                                val currentOffset = dragOffset.value + delta
                                dragOffset.snapTo(currentOffset)

                                val currentIndex = builtinList.indexOfFirst { it.key == engine.key }
                                if (currentIndex == -1) return@launch

                                val threshold = itemHeightPx.toFloat()
                                if (currentOffset > threshold) {
                                    if (currentIndex < builtinList.lastIndex) {
                                        val item = builtinList.removeAt(currentIndex)
                                        builtinList.add(currentIndex + 1, item)
                                        dragOffset.snapTo(currentOffset - threshold)
                                    }
                                } else if (currentOffset < -threshold) {
                                    if (currentIndex > 0) {
                                        val item = builtinList.removeAt(currentIndex)
                                        builtinList.add(currentIndex - 1, item)
                                        dragOffset.snapTo(currentOffset + threshold)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            heldKey = null
                            scope.launch {
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                draggingKey = null
                            }
                        },
                        onSizeChanged = { height ->
                            val spacingPx = with(density) { 12.dp.roundToPx() }
                            itemHeightPx = height + spacingPx
                        },
                        modifier = if (isDragging) Modifier else Modifier.animateItem()
                    )
                }
            }

            if (customList.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallTitle(text = "自定义引擎")
                        if (builtinList.isEmpty()) {
                            Text(
                                text = if (isEditMode) "完成" else "编辑",
                                color = MiuixTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (isEditMode) commitOrder()
                                        isEditMode = !isEditMode
                                    }
                                    .padding(horizontal = 24.dp)
                            )
                        }
                    }
                }
                itemsIndexed(
                    items = customList,
                    key = { _, e -> e.key }
                ) { _, engine ->
                    val isDragging = draggingKey == engine.key
                    val isHeld = heldKey == engine.key

                    DraggableEngineItem(
                        engine = engine,
                        isEditMode = isEditMode,
                        isDragging = isDragging,
                        isHeld = isHeld,
                        dragOffset = if (isDragging) dragOffset.value else 0f,
                        onEnabledChange = { enabled ->
                            viewModel.updateEngineEnabled(engine.key, enabled)
                        },
                        onEdit = { viewModel.showEditDialog(engine) },
                        onDelete = { viewModel.showDeleteDialog(engine) },
                        onReset = null,
                        onUpdate = null,
                        onRemoved = null,
                        onConflict = if (engine.hasBuiltinConflict()) {
                            { viewModel.showConflictDialog(engine) }
                        } else null,
                        onDragStart = {
                            draggingKey = engine.key
                            heldKey = engine.key
                            scope.launch { dragOffset.snapTo(0f) }
                        },
                        onDrag = { delta ->
                            scope.launch {
                                val currentOffset = dragOffset.value + delta
                                dragOffset.snapTo(currentOffset)

                                val currentIndex = customList.indexOfFirst { it.key == engine.key }
                                if (currentIndex == -1) return@launch

                                val threshold = itemHeightPx.toFloat()
                                if (currentOffset > threshold) {
                                    if (currentIndex < customList.lastIndex) {
                                        val item = customList.removeAt(currentIndex)
                                        customList.add(currentIndex + 1, item)
                                        dragOffset.snapTo(currentOffset - threshold)
                                    }
                                } else if (currentOffset < -threshold) {
                                    if (currentIndex > 0) {
                                        val item = customList.removeAt(currentIndex)
                                        customList.add(currentIndex - 1, item)
                                        dragOffset.snapTo(currentOffset + threshold)
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            heldKey = null
                            scope.launch {
                                dragOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                draggingKey = null
                            }
                        },
                        onSizeChanged = { height ->
                            val spacingPx = with(density) { 12.dp.roundToPx() }
                            itemHeightPx = height + spacingPx
                        },
                        modifier = if (isDragging) Modifier else Modifier.animateItem()
                    )
                }
            }
        }

        // OverlayDialog 依赖 Scaffold 注入的宿主 CompositionLocal，写在外层不会渲染。
        AddEngineDialog(
            show = showAddDialog,
            onDismiss = { viewModel.showAddDialog(false) },
            onConfirm = { key, name, url ->
                val success = viewModel.addCustomEngine(key, name, url)
                if (success) {
                    viewModel.showAddDialog(false)
                } else {
                    Toast.makeText(context, "标识符已存在", Toast.LENGTH_SHORT).show()
                }
            }
        )

        uiState.editingEngine?.let { engine ->
            EditEngineDialog(
                show = showEditDialog,
                engine = engine,
                onDismiss = { viewModel.showEditDialog(null) },
                onConfirm = { newKey, name, url ->
                    if (engine.isBuiltin) {
                        viewModel.updateEngine(engine.key, name, url, engine.enabled)
                        viewModel.showEditDialog(null)
                    } else {
                        val success = viewModel.updateCustomEngine(engine.key, newKey, name, url, engine.enabled)
                        if (success) {
                            viewModel.showEditDialog(null)
                        } else {
                            Toast.makeText(context, "标识符已存在", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onKeyConflict = {
                    Toast.makeText(context, "标识符已存在", Toast.LENGTH_SHORT).show()
                }
            )
        }

        uiState.deletingEngine?.let { engine ->
            DeleteConfirmDialog(
                show = showDeleteDialog,
                engineName = engine.name,
                onDismiss = { viewModel.showDeleteDialog(null) },
                onConfirm = {
                    viewModel.deleteEngine(engine.key)
                    viewModel.showDeleteDialog(null)
                }
            )
        }

        uiState.updatingEngine?.let { engine ->
            UpdateAvailableDialog(
                show = showUpdateDialog,
                engine = engine,
                onDismiss = { viewModel.showUpdateDialog(null) },
                onApply = {
                    viewModel.applyPendingUpdate(engine.key)
                    Toast.makeText(context, "已更新", Toast.LENGTH_SHORT).show()
                    viewModel.showUpdateDialog(null)
                },
                onIgnore = {
                    viewModel.ignorePendingUpdate(engine.key)
                    Toast.makeText(context, "已忽略更新", Toast.LENGTH_SHORT).show()
                    viewModel.showUpdateDialog(null)
                }
            )
        }

        uiState.removedEngine?.let { engine ->
            EngineRemovedDialog(
                show = showRemovedDialog,
                engine = engine,
                onDismiss = { viewModel.showRemovedDialog(null) },
                onConvertToCustom = {
                    viewModel.convertToCustomEngine(engine.key)
                    Toast.makeText(context, "已转为自定义引擎", Toast.LENGTH_SHORT).show()
                    viewModel.showRemovedDialog(null)
                },
                onDelete = {
                    viewModel.deleteEngine(engine.key)
                    Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    viewModel.showRemovedDialog(null)
                }
            )
        }

        uiState.conflictEngine?.let { engine ->
            BuiltinConflictDialog(
                show = showConflictDialog,
                engine = engine,
                onDismiss = { viewModel.showConflictDialog(null) },
                onConvertToBuiltin = {
                    viewModel.convertCustomToBuiltin(engine.key)
                    Toast.makeText(context, "已转为内置引擎", Toast.LENGTH_SHORT).show()
                    viewModel.showConflictDialog(null)
                },
                onCreateCopy = {
                    val newKey = viewModel.createCustomEngineCopy(engine.key)
                    if (newKey != null) {
                        Toast.makeText(context, "已创建副本: $newKey", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "创建副本失败", Toast.LENGTH_SHORT).show()
                    }
                    viewModel.showConflictDialog(null)
                }
            )
        }
    }
}

private val CardPadding = 16.dp
private val ActionButtonColor = Color(0xFFBDBDBD)

@Composable
fun DraggableEngineItem(
    engine: SearchEngineConfig,
    isEditMode: Boolean,
    isDragging: Boolean,
    isHeld: Boolean,
    dragOffset: Float,
    onEnabledChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
    onReset: (() -> Unit)?,
    onUpdate: (() -> Unit)?,
    onRemoved: (() -> Unit)?,
    onConflict: (() -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onSizeChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val scale by animateFloatAsState(
        targetValue = if (isHeld) 1.04f else 1f,
        animationSpec = springSpec,
        label = "scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isHeld) 8.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "elevation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onSizeChanged { onSizeChanged(it.height) }
            .zIndex(if (isDragging) 10f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = scale
                scaleY = scale
                shadowElevation = with(density) { elevation.toPx() }
                shape = RoundedCornerShape(16.dp)
                clip = false
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = CardPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 拖动把手（与热门网站完全一致的滑入动效与手势）
            AnimatedVisibility(
                visible = isEditMode,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "拖动排序",
                    tint = ActionButtonColor,
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                }
                            )
                        }
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = engine.name,
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                if (!engine.searchUrl.isNullOrBlank()) {
                    Text(
                        text = engine.searchUrl!!,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                onRemoved?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "已从浏览器消失",
                            tint = Color(0xFFF44336)
                        )
                    }
                }

                onConflict?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "与内置引擎冲突",
                            tint = Color(0xFFFF9800)
                        )
                    }
                }

                onUpdate?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.Outlined.SystemUpdate,
                            contentDescription = "有可用更新",
                            tint = Color(0xFF2196F3)
                        )
                    }
                }

                onDelete?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = ActionButtonColor
                        )
                    }
                }

                onReset?.let {
                    IconButton(onClick = it) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "恢复默认",
                            tint = ActionButtonColor
                        )
                    }
                }

                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "编辑",
                        tint = ActionButtonColor
                    )
                }

                Switch(
                    checked = engine.enabled,
                    onCheckedChange = onEnabledChange
                )
            }
        }
    }
}

@Composable
fun UpdateAvailableDialog(
    show: MutableState<Boolean>,
    engine: SearchEngineConfig,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onIgnore: () -> Unit
) {
    SuperDialog(
        title = "发现更新",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "浏览器中的 \"${engine.name}\" 有更新：",
                color = MiuixTheme.colorScheme.onSurface
            )

            Surface(
                color = MiuixTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = engine.updateDescription,
                    modifier = Modifier.padding(12.dp),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "忽略",
                    onClick = onIgnore,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "应用更新",
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun EngineRemovedDialog(
    show: MutableState<Boolean>,
    engine: SearchEngineConfig,
    onDismiss: () -> Unit,
    onConvertToCustom: () -> Unit,
    onDelete: () -> Unit
) {
    SuperDialog(
        title = "引擎已从浏览器消失",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "\"${engine.name}\" 已不再是浏览器的内置引擎。\n\n你可以将其转为自定义引擎继续使用，或者删除它。",
                color = MiuixTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "删除",
                    onClick = onDelete,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "转为自定义",
                    onClick = onConvertToCustom,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun BuiltinConflictDialog(
    show: MutableState<Boolean>,
    engine: SearchEngineConfig,
    onDismiss: () -> Unit,
    onConvertToBuiltin: () -> Unit,
    onCreateCopy: () -> Unit
) {
    SuperDialog(
        title = "发现同名内置引擎",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "浏览器新增了标识符同为「${engine.key}」的内置引擎。",
                color = MiuixTheme.colorScheme.onSurface
            )

            Surface(
                color = MiuixTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "内置引擎信息：",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                    if (engine.conflictBuiltinName != null) {
                        Text(
                            text = "名称: ${engine.conflictBuiltinName}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                    if (engine.conflictBuiltinSearchUrl != null) {
                        Text(
                            text = "URL: ${engine.conflictBuiltinSearchUrl}",
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2
                        )
                    }
                }
            }

            Text(
                text = "你可以选择：\n• 转为内置：保留你的自定义设置，但作为内置引擎的修改版\n• 创建副本：保留自定义引擎并改名，同时添加内置引擎",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                style = MiuixTheme.textStyles.body2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "转为内置",
                    onClick = onConvertToBuiltin,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "创建副本",
                    onClick = onCreateCopy,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}

@Composable
fun AddEngineDialog(
    show: MutableState<Boolean>,
    onDismiss: () -> Unit,
    onConfirm: (key: String, name: String, url: String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var key by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var urlError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(show.value) {
        if (show.value) {
            key = ""
            name = ""
            url = ""
            keyError = null
            nameError = null
            urlError = null
        }
    }

    SuperDialog(
        title = "添加自定义搜索引擎",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextField(
                value = key,
                onValueChange = {
                    key = it
                    keyError = null
                },
                label = "标识符",
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            if (keyError != null) {
                Text(text = keyError!!, color = Color(0xFFF44336))
            }

            TextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = "显示名称",
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            if (nameError != null) {
                Text(text = nameError!!, color = Color(0xFFF44336))
            }

            TextField(
                value = url,
                onValueChange = {
                    url = it
                    urlError = null
                },
                label = "搜索 URL · {searchTerms}",
                singleLine = false,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
            if (urlError != null) {
                Text(text = urlError!!, color = Color(0xFFF44336))
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "添加",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        var hasError = false
                        if (key.isBlank()) {
                            keyError = "标识符不能为空"
                            hasError = true
                        }
                        if (name.isBlank()) {
                            nameError = "名称不能为空"
                            hasError = true
                        }
                        if (url.isBlank()) {
                            urlError = "URL不能为空"
                            hasError = true
                        } else if (!url.contains("%s") && !url.contains("{searchTerms}")) {
                            urlError = "URL必须包含 %s 或 {searchTerms}"
                            hasError = true
                        }

                        if (!hasError) {
                            onConfirm(key.trim(), name.trim(), url.trim())
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun EditEngineDialog(
    show: MutableState<Boolean>,
    engine: SearchEngineConfig,
    onDismiss: () -> Unit,
    onConfirm: (key: String, name: String, url: String) -> Unit,
    onKeyConflict: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var key by remember(engine) { mutableStateOf(engine.key) }
    var name by remember(engine) { mutableStateOf(engine.name) }
    var url by remember(engine) { mutableStateOf(engine.searchUrl ?: "") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }

    val isBuiltin = engine.isBuiltin

    SuperDialog(
        title = "编辑搜索引擎",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (isBuiltin) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MiuixTheme.colorScheme.disabledSecondaryVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Column {
                        Text(
                            text = "标识符",
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = engine.key,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.disabledOnSecondaryVariant
                        )
                    }
                }
            } else {
                TextField(
                    value = key,
                    onValueChange = {
                        key = it
                        keyError = null
                    },
                    label = "标识符",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                if (keyError != null) {
                    Text(text = keyError!!, color = Color(0xFFF44336))
                }
            }

            TextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = "显示名称",
                modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            if (nameError != null) {
                Text(text = nameError!!, color = Color(0xFFF44336))
            }

            TextField(
                value = url,
                onValueChange = { url = it },
                label = "搜索 URL",
                maxLines = 2,
                singleLine = false, modifier = Modifier.fillMaxWidth(),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "保存",
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    onClick = {
                        var hasError = false

                        if (!isBuiltin && key.isBlank()) {
                            keyError = "标识符不能为空"
                            hasError = true
                        }

                        if (name.isBlank()) {
                            nameError = "名称不能为空"
                            hasError = true
                        }

                        if (!hasError) {
                            val finalKey = if (isBuiltin) engine.key else key.trim()
                            onConfirm(finalKey, name.trim(), url.trim())
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun DeleteConfirmDialog(
    show: MutableState<Boolean>,
    engineName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    SuperDialog(
        title = "删除搜索引擎",
        show = show,
        onDismissRequest = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "确定要删除 \"$engineName\" 吗？",
                color = MiuixTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(
                    text = "取消",
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    text = "删除",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary()
                )
            }
        }
    }
}