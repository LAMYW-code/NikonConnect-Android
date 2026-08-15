package com.nikonconnect.app.connection

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nikonconnect.app.R
import com.nikonconnect.ptp.PtpObjectInfo
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun NikonConnectRoot(
    connection: ConnectionUiState,
    gallery: GalleryUiState,
    preview: PreviewUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSelection: (UInt) -> Unit,
    onDownload: () -> Unit,
    onFilterChange: (GalleryFilter) -> Unit,
    onRequestThumbnail: (UInt) -> Unit,
    onRequestPreview: (UInt) -> Unit,
    onDismissPreview: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = connection.stage == ConnectionStage.CONNECTED,
        transitionSpec = {
            if (reducedMotion) {
                fadeIn(tween(80)) togetherWith fadeOut(tween(60))
            } else if (targetState) {
                (fadeIn(tween(260, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 14 }) togetherWith
                    fadeOut(tween(160))
            } else {
                fadeIn(tween(220)) togetherWith
                    (fadeOut(tween(150)) + slideOutVertically(tween(180)) { it / 18 })
            }
        },
        label = "app-destination",
    ) { connected ->
        if (connected) {
            GalleryScreen(
                connection = connection,
                state = gallery,
                preview = preview,
                onDisconnect = onDisconnect,
                onRefresh = onRefresh,
                onLoadMore = onLoadMore,
                onToggleSelection = onToggleSelection,
                onDownload = onDownload,
                onFilterChange = onFilterChange,
                onRequestThumbnail = onRequestThumbnail,
                onRequestPreview = onRequestPreview,
                onDismissPreview = onDismissPreview,
            )
        } else {
            ConnectionScreen(connection, onConnect)
        }
    }
}

@Composable
private fun ConnectionScreen(state: ConnectionUiState, onConnect: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AmbientBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            BrandMark()
            Spacer(Modifier.height(56.dp))
            Text(
                text = "连接你的\nNikon 相机",
                modifier = Modifier.semantics { heading() },
                fontSize = 40.sp,
                lineHeight = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "选择相机 Wi-Fi，把 JPEG 与 RAW 照片直接带回手机。",
                fontSize = 17.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))
            SolidPanel {
                Column(Modifier.padding(22.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    painterResource(R.drawable.ic_camera_outline),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(
                            Modifier
                                .weight(1f)
                                .semantics { liveRegion = LiveRegionMode.Polite },
                        ) {
                            Text(state.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                state.detail,
                                fontSize = 14.sp,
                                lineHeight = 21.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    state.diagnosticCode?.let {
                        Surface(
                            modifier = Modifier.padding(top = 16.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = .10f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                "诊断代码：$it",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = onConnect,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("连接中…")
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_wifi_outline),
                                contentDescription = null,
                                modifier = Modifier.size(21.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("选择相机 Wi-Fi", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painterResource(R.drawable.ic_info_outline),
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    "系统只会显示名称以 NIKON 开头的网络，不限制相机型号。",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private val HeroExpandedHeight = 326.dp
private val HeroCollapsedHeight = 52.dp
private val HeroCollapseRange = HeroExpandedHeight - HeroCollapsedHeight

@Composable
private fun GalleryScreen(
    connection: ConnectionUiState,
    state: GalleryUiState,
    preview: PreviewUiState,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSelection: (UInt) -> Unit,
    onDownload: () -> Unit,
    onFilterChange: (GalleryFilter) -> Unit,
    onRequestThumbnail: (UInt) -> Unit,
    onRequestPreview: (UInt) -> Unit,
    onDismissPreview: () -> Unit,
) {
    val filters = GalleryFilter.entries
    val gridStates = listOf(
        rememberLazyGridState(),
        rememberLazyGridState(),
        rememberLazyGridState(),
    )
    val pagerState = rememberPagerState(
        initialPage = state.filter.ordinal,
        pageCount = { filters.size },
    )
    val scope = rememberCoroutineScope()
    val reducedMotion = rememberReducedMotion()
    val galleryBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val heroCollapseRangePx = with(density) { HeroCollapseRange.toPx() }
    var heroOffsetPx by remember { mutableFloatStateOf(0f) }
    val activeGridState = rememberUpdatedState(gridStates[pagerState.settledPage])
    val heroNestedScrollConnection = remember(heroCollapseRangePx) {
        object : NestedScrollConnection {
            private fun consumeHeroScroll(delta: Float): Float {
                val previous = heroOffsetPx
                heroOffsetPx = (heroOffsetPx + delta).coerceIn(-heroCollapseRangePx, 0f)
                return heroOffsetPx - previous
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val shouldCollapse = available.y < 0f && heroOffsetPx > -heroCollapseRangePx
                val shouldExpand = available.y > 0f &&
                    heroOffsetPx < 0f &&
                    !activeGridState.value.canScrollBackward
                return if (shouldCollapse || shouldExpand) {
                    Offset(x = 0f, y = consumeHeroScroll(available.y))
                } else {
                    Offset.Zero
                }
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset = if (available.y > 0f && heroOffsetPx < 0f) {
                Offset(x = 0f, y = consumeHeroScroll(available.y))
            } else {
                Offset.Zero
            }
        }
    }
    val heroCollapseProgress =
        (-heroOffsetPx / heroCollapseRangePx).coerceIn(0f, 1f)
    var dismissedTransferCount by remember { mutableIntStateOf(0) }
    var displayedSelectedCount by remember { mutableIntStateOf(state.selected.size) }
    var previewHandle by rememberSaveable { mutableStateOf<Long?>(null) }
    val dismissPreview = {
        previewHandle = null
        onDismissPreview()
    }
    val gridBottomPadding by animateDpAsState(
        targetValue = if (state.selected.isNotEmpty()) 158.dp else 94.dp,
        animationSpec = tween(if (reducedMotion) 0 else 220, easing = FastOutSlowInEasing),
        label = "gallery-liquid-bar-inset",
    )
    val showTransferComplete = state.completedTransfers > 0 &&
        state.completedTransfers != dismissedTransferCount

    LaunchedEffect(state.filter) {
        val targetPage = state.filter.ordinal
        if (pagerState.settledPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> onFilterChange(filters[page]) }
    }

    LaunchedEffect(state.selected.size) {
        if (state.selected.isNotEmpty()) displayedSelectedCount = state.selected.size
    }

    LaunchedEffect(state.completedTransfers) {
        if (state.completedTransfers > 0) {
            delay(1_350)
            dismissedTransferCount = state.completedTransfers
        } else {
            dismissedTransferCount = 0
        }
    }

    LaunchedEffect(previewHandle, state.items) {
        val handle = previewHandle ?: return@LaunchedEffect
        if (state.items.none { it.info.handle.toLong() == handle }) dismissPreview()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { GalleryHeader(onDisconnect) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .layerBackdrop(galleryBackdrop)
                    .background(MaterialTheme.colorScheme.background)
                    .nestedScroll(heroNestedScrollConnection),
            ) {
                Box(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                ) {
                    CameraHeroCard(
                        connection = connection,
                        collapseProgress = heroCollapseProgress,
                    )
                }
                Spacer(Modifier.height(12.dp))
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = !state.isTransferring,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    key = { filters[it].name },
                ) { page ->
                    val pageFilter = filters[page]
                    GalleryPage(
                        state = state,
                        filter = pageFilter,
                        gridState = gridStates[page],
                        bottomPadding = gridBottomPadding,
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore,
                        onToggleSelection = onToggleSelection,
                        onRequestThumbnail = onRequestThumbnail,
                        onOpenPreview = {
                            previewHandle = it.toLong()
                            onRequestPreview(it)
                        },
                    )
                }
            }
            GalleryLiquidBar(
                filters = filters,
                selectedPage = pagerState.currentPage,
                pagerPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                pagerTargetPage = pagerState.targetPage,
                pagerIsScrolling = pagerState.isScrollInProgress,
                selectedCount = displayedSelectedCount,
                hasSelection = state.selected.isNotEmpty(),
                enabled = !state.isTransferring,
                backdrop = galleryBackdrop,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f),
                onFilterSelected = { page ->
                    if (page != pagerState.currentPage) {
                        scope.launch {
                            if (reducedMotion) pagerState.scrollToPage(page)
                            else pagerState.animateScrollToPage(page)
                        }
                    }
                },
                onDownload = onDownload,
            )
            AnimatedVisibility(
                visible = state.transferName != null || showTransferComplete,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .zIndex(2f)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                enter = if (reducedMotion) {
                    fadeIn(tween(80))
                } else {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        slideInVertically(tween(280, easing = FastOutSlowInEasing)) { -it }
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(60))
                } else {
                    fadeOut(tween(150)) + slideOutVertically(tween(180)) { -it / 2 }
                },
            ) {
                AnimatedContent(
                    targetState = state.transferName != null,
                    transitionSpec = {
                        fadeIn(tween(if (reducedMotion) 0 else 140)) togetherWith
                            fadeOut(tween(if (reducedMotion) 0 else 100))
                    },
                    label = "transfer-popup-state",
                ) { isTransferring ->
                    if (isTransferring) {
                        TransferProgressPopup(state, galleryBackdrop)
                    } else {
                        TransferCompletePopup(state.completedTransfers, galleryBackdrop)
                    }
                }
            }
            }
        }
        AnimatedContent(
            targetState = previewHandle,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f),
            transitionSpec = {
                if (reducedMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else if (targetState != null) {
                    (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                        scaleIn(tween(300, easing = FastOutSlowInEasing), initialScale = .94f)) togetherWith
                        fadeOut(tween(80))
                } else {
                    fadeIn(tween(0)) togetherWith
                        (fadeOut(tween(160)) + scaleOut(tween(210), targetScale = .96f))
                }
            },
            contentAlignment = Alignment.Center,
            label = "fullscreen-photo-preview",
        ) { handle ->
            if (handle != null) {
                state.items.firstOrNull { it.info.handle.toLong() == handle }?.let { asset ->
                    FullscreenAssetPreview(
                        asset = asset,
                        preview = preview.takeIf { it.requestedHandle == asset.info.handle }
                            ?: PreviewUiState(),
                        onDismiss = dismissPreview,
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryPage(
    state: GalleryUiState,
    filter: GalleryFilter,
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleSelection: (UInt) -> Unit,
    onRequestThumbnail: (UInt) -> Unit,
    onOpenPreview: (UInt) -> Unit,
) {
    val pageItems = remember(state.items, filter) { state.items.forFilter(filter) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(156.dp),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = bottomPadding,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "gallery-controls", span = { GridItemSpan(maxLineSpan) }) {
            GalleryControls(
                state = state,
                filter = filter,
                visibleCount = pageItems.size,
                onRefresh = onRefresh,
            )
        }

        if (state.error != null && state.items.isNotEmpty()) {
            item(key = "inline-error", span = { GridItemSpan(maxLineSpan) }) {
                InlineError(state.error)
            }
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                item(key = "loading", span = { GridItemSpan(maxLineSpan) }) {
                    LoadingGalleryPlaceholder(state)
                }
            }
            state.error != null && state.items.isEmpty() -> {
                item(key = "error", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyGalleryMessage(
                        title = "无法读取相机图库",
                        detail = state.error,
                        actionLabel = "重试",
                        onAction = onRefresh,
                    )
                }
            }
            state.items.isEmpty() -> {
                item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyGalleryMessage(
                        title = "相机中还没有照片",
                        detail = "拍摄照片后刷新图库，JPEG 与 RAW 会显示在这里。",
                    )
                }
            }
            pageItems.isEmpty() -> {
                item(key = "filtered-empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyGalleryMessage(
                        title = if (state.isInitialScan) "正在查找 ${filter.label}" else "没有 ${filter.label} 照片",
                        detail = if (state.isInitialScan) {
                            "扫描仍在继续，找到照片后会自动显示。"
                        } else {
                            "可以切换格式或继续加载更早的照片。"
                        },
                        actionLabel = if (state.hasMore && !state.isInitialScan) "继续加载" else null,
                        onAction = onLoadMore,
                    )
                }
            }
            else -> {
                items(
                    items = pageItems,
                    key = { it.info.handle.toLong() },
                ) { asset ->
                    LaunchedEffect(asset.info.handle, asset.thumbnail) {
                        if (asset.thumbnail == null) onRequestThumbnail(asset.info.handle)
                    }
                    AssetCard(
                        asset = asset,
                        selected = asset.info.handle in state.selected,
                        disabled = state.isTransferring,
                        onClick = { onToggleSelection(asset.info.handle) },
                        onDoubleClick = { onOpenPreview(asset.info.handle) },
                    )
                }
                if (state.hasMore) {
                    item(key = "load-more", span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedButton(
                            onClick = onLoadMore,
                            enabled = !state.isLoading,
                            modifier = Modifier.fillMaxWidth().height(54.dp),
                            shape = RoundedCornerShape(17.dp),
                        ) {
                            if (state.isLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(9.dp))
                                Text("读取中…")
                            } else {
                                Text("加载更早的照片")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<GalleryAssetUi>.forFilter(filter: GalleryFilter): List<GalleryAssetUi> =
    when (filter) {
        GalleryFilter.ALL -> this
        GalleryFilter.JPEG -> filter { it.info.assetKind == com.nikonconnect.ptp.CameraAssetKind.JPEG }
        GalleryFilter.RAW -> filter { it.info.assetKind == com.nikonconnect.ptp.CameraAssetKind.RAW }
    }

@Composable
private fun GalleryHeader(onDisconnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "NIKON CONNECT",
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                "我的相机",
                modifier = Modifier.semantics { heading() },
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-.7).sp,
            )
        }
        TextButton(
            onClick = onDisconnect,
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_disconnect_outline),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text("断开")
        }
    }
}

@Composable
private fun CameraHeroCard(
    connection: ConnectionUiState,
    collapseProgress: Float,
) {
    val collapse = collapseProgress.coerceIn(0f, 1f)
    val expandedContentAlpha = 1f - collapse
    val heroHeight = HeroExpandedHeight - (HeroCollapseRange * collapse)
    val horizontalPadding = 22.dp - (8.dp * collapse)
    val verticalPadding = 20.dp - (9.dp * collapse)
    val heroCornerRadius = 28.dp - (6.dp * collapse)
    val statusHeight = 34.dp - (4.dp * collapse)
    val productHeight = 210.dp * expandedContentAlpha
    val reducedMotion = rememberReducedMotion()
    var cameraVisible by remember(connection.title) { mutableStateOf(reducedMotion) }
    var infoVisible by remember(connection.title) { mutableStateOf(reducedMotion) }
    var statusVisible by remember(connection.title) { mutableStateOf(reducedMotion) }
    val cameraProgress by animateFloatAsState(
        targetValue = if (cameraVisible) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 320, easing = FastOutSlowInEasing),
        label = "camera-hero-product-entrance",
    )
    val infoProgress by animateFloatAsState(
        targetValue = if (infoVisible) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 240, easing = FastOutSlowInEasing),
        label = "camera-hero-info-entrance",
    )

    LaunchedEffect(connection.title, reducedMotion) {
        if (!reducedMotion) delay(80)
        cameraVisible = true
        if (!reducedMotion) delay(120)
        infoVisible = true
        if (!reducedMotion) delay(100)
        statusVisible = true
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RoundedCornerShape(heroCornerRadius))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.tertiary.copy(alpha = .18f),
                        MaterialTheme.colorScheme.primary.copy(alpha = .08f),
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            )
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = .55f),
                RoundedCornerShape(heroCornerRadius),
            ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    Modifier
                        .weight(1f)
                        .graphicsLayer {
                            alpha = infoProgress
                            translationY =
                                8.dp.toPx() * (1f - infoProgress) +
                                    5.dp.toPx() * collapse
                        },
                ) {
                    Text(
                        connection.title,
                        fontSize = (25f - 7f * collapse).sp,
                        lineHeight = (30f - 10f * collapse).sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (collapse > .5f) 1 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(5.dp * expandedContentAlpha))
                    Row(
                        modifier = Modifier
                            .height(20.dp * expandedContentAlpha)
                            .graphicsLayer {
                                alpha = expandedContentAlpha
                                clip = true
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_wifi_outline),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            connection.connectedSsid ?: "NIKON CAMERA WI-FI",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                AnimatedVisibility(
                    modifier = Modifier.width(82.dp).height(statusHeight),
                    visible = statusVisible,
                    enter = if (reducedMotion) {
                        fadeIn(tween(60))
                    } else {
                        fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it / 2 } +
                            scaleIn(tween(240, easing = FastOutSlowInEasing), initialScale = .85f)
                    },
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxHeight(),
                            color = MaterialTheme.colorScheme.tertiary,
                            contentColor = MaterialTheme.colorScheme.onTertiary,
                            shape = RoundedCornerShape(50),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.onTertiary, CircleShape))
                                Spacer(Modifier.width(7.dp))
                                Text(
                                    "已连接",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 13.sp,
                                        lineHeight = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(productHeight),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(cameraProductDrawable(connection.title)),
                    contentDescription = if (collapse < .8f) {
                        "${connection.title} 相机实物图"
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .graphicsLayer {
                            alpha = cameraProgress * expandedContentAlpha
                            translationY = 24.dp.toPx() * (1f - cameraProgress)
                            val entranceScale = .92f + (.08f * cameraProgress)
                            scaleX = entranceScale
                            scaleY = entranceScale
                        },
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }
}

@Composable
private fun GalleryControls(
    state: GalleryUiState,
    filter: GalleryFilter,
    visibleCount: Int,
    onRefresh: () -> Unit,
) {
    SolidPanel {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (filter == GalleryFilter.ALL) "相机图库" else "${filter.label} 图库",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        "$visibleCount / ${state.items.size} 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .76f),
                    )
                }
                FilledTonalIconButton(
                    onClick = onRefresh,
                    enabled = !state.isLoading && !state.isTransferring,
                    modifier = Modifier.size(48.dp),
                ) {
                    RefreshIcon(isLoading = state.isLoading)
                }
            }
            if (state.isInitialScan) {
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "JPEG ${state.jpegCount}/${state.jpegTarget}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "RAW ${state.rawCount}/${state.rawTarget}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(7.dp))
                LinearProgressIndicator(
                    progress = {
                        if (state.totalCandidates > 0) {
                            (state.scannedCandidates.toFloat() / state.totalCandidates).coerceIn(0f, 1f)
                        } else 0f
                    },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                )
            }
        }
    }
}

@Composable
private fun RefreshIcon(isLoading: Boolean) {
    val reducedMotion = rememberReducedMotion()
    AnimatedContent(
        targetState = isLoading,
        transitionSpec = {
            fadeIn(tween(if (reducedMotion) 0 else 150)) togetherWith
                fadeOut(tween(if (reducedMotion) 0 else 100))
        },
        label = "gallery-refresh-state",
    ) { loading ->
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(21.dp),
                strokeWidth = 2.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_refresh_outline),
                contentDescription = "刷新相机图库",
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun GlassTransferPopupSurface(
    backdrop: Backdrop,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val shape = remember { ContinuousRoundedRectangle(24.dp) }
    val surfaceTint = MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) .68f else .64f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(6.dp.toPx())
                    lens(
                        refractionHeight = 18.dp.toPx(),
                        refractionAmount = 18.dp.toPx(),
                        depthEffect = true,
                        chromaticAberration = false,
                    )
                },
                highlight = {
                    Highlight.Ambient.copy(
                        width = 1.dp,
                        blurRadius = .5.dp,
                        alpha = if (darkTheme) .52f else .72f,
                    )
                },
                shadow = {
                    Shadow(
                        radius = 18.dp,
                        color = Color.Black.copy(alpha = if (darkTheme) .26f else .14f),
                    )
                },
                onDrawSurface = { drawRect(surfaceTint) },
            )
            .border(1.dp, accentColor.copy(alpha = .38f), shape)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        content()
    }
}

@Composable
private fun TransferProgressPopup(
    state: GalleryUiState,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val animatedProgress by animateFloatAsState(
        targetValue = state.transferProgress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 220),
        label = "transfer-progress",
    )
    GlassTransferPopupSurface(
        backdrop = backdrop,
        accentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = state.transferName,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        fadeIn(tween(if (reducedMotion) 0 else 180)) togetherWith
                            fadeOut(tween(if (reducedMotion) 0 else 120))
                    },
                    label = "transfer-thumbnail",
                ) { fileName ->
                    val current = state.items.firstOrNull { it.info.fileName == fileName }
                    val bitmap = remember(current?.thumbnail) {
                        current?.thumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap.asImageBitmap(),
                            contentDescription = fileName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                painterResource(R.drawable.ic_download_outline),
                                contentDescription = null,
                                modifier = Modifier.size(26.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (state.transferProgress <= 0f && state.transferTotal > 0) {
                            "下载已开始 ${state.transferIndex}/${state.transferTotal}"
                        } else if (state.transferTotal > 0) {
                            "正在下载 ${state.transferIndex}/${state.transferTotal}"
                        } else {
                            "正在下载"
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(animatedProgress * 100).toInt()}%",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                AnimatedContent(
                    targetState = state.transferName.orEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    transitionSpec = {
                        fadeIn(tween(if (reducedMotion) 0 else 170)) togetherWith
                            fadeOut(tween(if (reducedMotion) 0 else 100))
                    },
                    label = "transfer-file-name",
                ) { fileName ->
                    Text(
                        fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
                )
            }
        }
    }
}

@Composable
private fun TransferCompletePopup(completedCount: Int, backdrop: Backdrop) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    var iconVisible by remember { mutableStateOf(reducedMotion) }
    val iconProgress by animateFloatAsState(
        targetValue = if (iconVisible) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 240, easing = FastOutSlowInEasing),
        label = "transfer-complete-icon",
    )

    LaunchedEffect(Unit) {
        if (!reducedMotion) delay(60)
        iconVisible = true
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    GlassTransferPopupSurface(
        backdrop = backdrop,
        accentColor = MaterialTheme.colorScheme.tertiary,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(72.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = .12f),
                contentColor = MaterialTheme.colorScheme.tertiary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(R.drawable.ic_check_outline),
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .graphicsLayer {
                                alpha = iconProgress
                                val completeScale = .70f + (.30f * iconProgress)
                                scaleX = completeScale
                                scaleY = completeScale
                            },
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("下载完成", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "已保存 $completedCount 张照片到手机",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun FullscreenAssetPreview(
    asset: GalleryAssetUi,
    preview: PreviewUiState,
    onDismiss: () -> Unit,
) {
    val activity = LocalContext.current.findActivity()
    val view = LocalView.current
    val reducedMotion = rememberReducedMotion()
    val thumbnail = remember(asset.thumbnail) {
        asset.thumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    val highResolutionResult by produceState<Result<android.graphics.Bitmap>?>(
        initialValue = null,
        key1 = preview.filePath,
    ) {
        value = preview.filePath?.let { path ->
            runCatching {
                withContext(Dispatchers.IO) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.memorySizePolicy = ImageDecoder.MEMORY_POLICY_LOW_RAM
                    }
                }
            }
        }
    }
    val highResolution = highResolutionResult?.getOrNull()
    val displayedImageSize = remember(highResolution, thumbnail) {
        (highResolution ?: thumbnail)?.let { bitmap ->
            IntSize(bitmap.width, bitmap.height)
        } ?: IntSize.Zero
    }
    var previewViewport by remember(asset.info.handle) { mutableStateOf(IntSize.Zero) }
    var previewScale by remember(asset.info.handle) { mutableFloatStateOf(1f) }
    var previewPan by remember(asset.info.handle) { mutableStateOf(Offset.Zero) }
    val highResolutionAlpha by animateFloatAsState(
        targetValue = if (highResolution != null) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reducedMotion) 0 else 180,
            easing = FastOutSlowInEasing,
        ),
        label = "preview-high-resolution-crossfade",
    )
    val decodeError = highResolutionResult?.exceptionOrNull()?.let {
        "高清预览解码失败，已继续显示缩略图。"
    }
    val previewError = preview.error ?: decodeError
    val isPreparing = preview.isLoading ||
        (preview.filePath != null && highResolutionResult == null)

    LaunchedEffect(previewViewport) {
        if (previewViewport != IntSize.Zero) {
            previewScale = 1f
            previewPan = Offset.Zero
        }
    }

    LaunchedEffect(displayedImageSize) {
        previewPan = clampPreviewPan(
            proposed = previewPan,
            scale = previewScale,
            viewport = previewViewport,
            image = displayedImageSize,
        )
    }

    DisposableEffect(activity) {
        if (activity == null) return@DisposableEffect onDispose {}
        val previousOrientation = activity.requestedOrientation
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = previousOrientation
        }
    }

    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { previewViewport = it }
                .pointerInput(asset.info.handle) {
                    detectTapGestures(
                        onDoubleTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onDismiss()
                        },
                    )
                }
                .pointerInput(asset.info.handle, previewViewport, displayedImageSize) {
                    detectTransformGestures(
                        panZoomLock = true,
                    ) { centroid, pan, zoom, _ ->
                        if (displayedImageSize == IntSize.Zero || previewViewport == IntSize.Zero) {
                            return@detectTransformGestures
                        }
                        val oldScale = previewScale
                        val newScale = (oldScale * zoom).coerceIn(1f, MAX_PREVIEW_ZOOM)
                        if (newScale <= MIN_PREVIEW_ZOOM_SNAP) {
                            previewScale = 1f
                            previewPan = Offset.Zero
                            return@detectTransformGestures
                        }

                        val viewportCenter = Offset(
                            previewViewport.width / 2f,
                            previewViewport.height / 2f,
                        )
                        val centroidFromCenter = centroid - viewportCenter
                        val scaleChange = newScale / oldScale
                        val proposedPan = Offset(
                            x = previewPan.x * scaleChange +
                                centroidFromCenter.x * (1f - scaleChange) + pan.x,
                            y = previewPan.y * scaleChange +
                                centroidFromCenter.y * (1f - scaleChange) + pan.y,
                        )
                        previewScale = newScale
                        previewPan = clampPreviewPan(
                            proposed = proposedPan,
                            scale = newScale,
                            viewport = previewViewport,
                            image = displayedImageSize,
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = previewScale
                        scaleY = previewScale
                        translationX = previewPan.x
                        translationY = previewPan.y
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = asset.info.fileName,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 1f - highResolutionAlpha },
                        contentScale = ContentScale.Fit,
                    )
                }
                if (highResolution != null) {
                    Image(
                        bitmap = highResolution.asImageBitmap(),
                        contentDescription = asset.info.fileName,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = highResolutionAlpha },
                        contentScale = ContentScale.Fit,
                    )
                }
                if (thumbnail == null && highResolution == null) {
                    if (previewError == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("正在准备预览…", color = Color.White.copy(alpha = .82f))
                        }
                    } else {
                        Text(
                            "暂无可用预览",
                            color = Color.White.copy(alpha = .74f),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = (isPreparing && thumbnail != null) || previewError != null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 26.dp),
            enter = fadeIn(tween(if (reducedMotion) 0 else 140)) +
                slideInVertically(tween(if (reducedMotion) 0 else 180)) { it / 3 },
            exit = fadeOut(tween(if (reducedMotion) 0 else 110)),
        ) {
            PreviewStatusPill(
                isLoading = isPreparing,
                progress = preview.progress,
                usesPairedJpeg = preview.usesPairedJpeg,
                error = previewError,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = ContinuousCapsule,
                color = Color.Black.copy(alpha = .48f),
                contentColor = Color.White,
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 9.dp)) {
                    Text(
                        asset.info.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "双指缩放 · 双击图片返回图库",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = .72f),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = .48f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_close_outline),
                    contentDescription = "关闭全屏预览",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

internal fun clampPreviewPan(
    proposed: Offset,
    scale: Float,
    viewport: IntSize,
    image: IntSize,
): Offset {
    if (viewport == IntSize.Zero || image == IntSize.Zero || scale <= 1f) return Offset.Zero
    val fitScale = minOf(
        viewport.width.toFloat() / image.width,
        viewport.height.toFloat() / image.height,
    )
    val scaledWidth = image.width * fitScale * scale
    val scaledHeight = image.height * fitScale * scale
    val maxX = ((scaledWidth - viewport.width) / 2f).coerceAtLeast(0f)
    val maxY = ((scaledHeight - viewport.height) / 2f).coerceAtLeast(0f)
    return Offset(
        x = proposed.x.coerceIn(-maxX, maxX),
        y = proposed.y.coerceIn(-maxY, maxY),
    )
}

private const val MAX_PREVIEW_ZOOM = 4f
private const val MIN_PREVIEW_ZOOM_SNAP = 1.015f

@Composable
private fun PreviewStatusPill(
    isLoading: Boolean,
    progress: Float,
    usesPairedJpeg: Boolean,
    error: String?,
) {
    Surface(
        shape = ContinuousCapsule,
        color = Color.Black.copy(alpha = .62f),
        contentColor = Color.White,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isLoading && error == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = .24f),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = error ?: when {
                    progress >= 1f -> "正在优化高清预览…"
                    usesPairedJpeg -> "正在读取同名 JPEG · ${(progress * 100).toInt()}%"
                    else -> "正在载入高清预览 · ${(progress * 100).toInt()}%"
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = if (error == null) Color.White else Color.White.copy(alpha = .88f),
            )
        }
    }
}

@Composable
private fun AssetCard(
    asset: GalleryAssetUi,
    selected: Boolean,
    disabled: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
) {
    val reducedMotion = rememberReducedMotion()
    val duration = if (reducedMotion) 0 else 190
    val interactionSource = remember { MutableInteractionSource() }
    val indication = LocalIndication.current
    val view = LocalView.current
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) .97f else 1f,
        animationSpec = tween(if (reducedMotion) 0 else if (isPressed) 90 else 170),
        label = "asset-press-scale",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.5.dp else 1.dp,
        animationSpec = tween(duration),
        label = "asset-border-width",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = .55f)
        },
        animationSpec = tween(duration),
        label = "asset-border-color",
    )
    val badgeColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = .90f)
        },
        animationSpec = tween(duration),
        label = "asset-badge-color",
    )
    val badgeContentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(duration),
        label = "asset-badge-content-color",
    )
    val elevation by animateDpAsState(
        targetValue = when {
            isPressed -> 1.dp
            selected -> 6.dp
            else -> 2.dp
        },
        animationSpec = tween(duration),
        label = "asset-elevation",
    )
    val selectionOverlayAlpha by animateFloatAsState(
        targetValue = if (selected) .12f else 0f,
        animationSpec = tween(duration, easing = FastOutSlowInEasing),
        label = "asset-selection-overlay",
    )
    val bitmap = remember(asset.thumbnail) {
        asset.thumbnail?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
    var thumbnailVisible by remember(asset.info.handle, asset.thumbnail) {
        mutableStateOf(reducedMotion)
    }
    val thumbnailProgress by animateFloatAsState(
        targetValue = if (thumbnailVisible) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else 220, easing = FastOutSlowInEasing),
        label = "asset-thumbnail-entrance",
    )

    LaunchedEffect(asset.info.handle, asset.thumbnail, reducedMotion) {
        if (bitmap != null) thumbnailVisible = true
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics { this.selected = selected }
            .combinedClickable(
                enabled = !disabled,
                interactionSource = interactionSource,
                indication = indication,
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onClick()
                },
                onDoubleClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onDoubleClick()
                },
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_camera_outline),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                )
            }
            if (bitmap != null) {
                Image(
                    bitmap.asImageBitmap(),
                    asset.info.fileName,
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = thumbnailProgress
                            val revealScale = 1.04f - (.04f * thumbnailProgress)
                            scaleX = revealScale
                            scaleY = revealScale
                        },
                    contentScale = ContentScale.Crop,
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = selectionOverlayAlpha }
                    .background(MaterialTheme.colorScheme.primary),
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = RoundedCornerShape(10.dp),
                color = badgeColor,
                contentColor = badgeContentColor,
            ) {
                AnimatedContent(
                    targetState = selected,
                    transitionSpec = {
                        if (reducedMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (fadeIn(tween(170, easing = FastOutSlowInEasing)) +
                                scaleIn(tween(210, easing = FastOutSlowInEasing), initialScale = .72f)) togetherWith
                                fadeOut(tween(100))
                        }
                    },
                    label = "asset-selection-badge",
                ) { isSelected ->
                    if (isSelected) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_check_outline),
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("已选", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Text(
                            asset.info.assetKind?.badge.orEmpty(),
                            Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(
                asset.info.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                formatBytes(asset.info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GalleryLiquidBar(
    filters: List<GalleryFilter>,
    selectedPage: Int,
    pagerPosition: Float,
    pagerTargetPage: Int,
    pagerIsScrolling: Boolean,
    selectedCount: Int,
    hasSelection: Boolean,
    enabled: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onFilterSelected: (Int) -> Unit,
    onDownload: () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val reducedMotion = rememberReducedMotion()
    val actionShape = remember { ContinuousRoundedRectangle(24.dp) }
    val actionGlassTint = MaterialTheme.colorScheme.surface.copy(alpha = if (darkTheme) .38f else .34f)

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(127.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            LiquidGalleryFilterTabs(
                filters = filters,
                selectedPage = selectedPage,
                pagerPosition = pagerPosition,
                pagerTargetPage = pagerTargetPage,
                pagerIsScrolling = pagerIsScrolling,
                enabled = enabled,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                onFilterSelected = onFilterSelected,
            )
            AnimatedVisibility(
                visible = hasSelection,
                enter = if (reducedMotion) {
                    fadeIn(tween(60))
                } else {
                    fadeIn(tween(160)) + expandVertically(expandFrom = Alignment.Bottom)
                },
                exit = if (reducedMotion) {
                    fadeOut(tween(50))
                } else {
                    fadeOut(tween(100)) + shrinkVertically(shrinkTowards = Alignment.Bottom)
                },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 7.dp)
                        .height(56.dp)
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { actionShape },
                            effects = {
                                vibrancy()
                                blur(6.dp.toPx())
                                lens(18.dp.toPx(), 18.dp.toPx())
                            },
                            highlight = { Highlight.Ambient },
                            shadow = {
                                Shadow(
                                    radius = 14.dp,
                                    color = Color.Black.copy(alpha = if (darkTheme) .22f else .12f),
                                )
                            },
                            onDrawSurface = { drawRect(actionGlassTint) },
                        )
                        .padding(start = 16.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "已选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "$selectedCount 项",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Button(
                        onClick = onDownload,
                        enabled = enabled,
                        modifier = Modifier.height(52.dp),
                        shape = ContinuousCapsule,
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_download_outline),
                            contentDescription = null,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text("保存到手机", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun LiquidGalleryFilterTabs(
    filters: List<GalleryFilter>,
    selectedPage: Int,
    pagerPosition: Float,
    pagerTargetPage: Int,
    pagerIsScrolling: Boolean,
    enabled: Boolean,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onFilterSelected: (Int) -> Unit,
) {
    val isLightTheme = !isSystemInDarkTheme()
    val reducedMotion = rememberReducedMotion()
    val accentColor = if (isLightTheme) Color(0xFF0088FF) else Color(0xFF0091FF)
    val containerColor = if (isLightTheme) {
        Color(0xFFFAFAFA).copy(alpha = .40f)
    } else {
        Color(0xFF121212).copy(alpha = .40f)
    }
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val motionProgress by animateFloatAsState(
        targetValue = if (pagerIsScrolling) 1f else 0f,
        animationSpec = if (reducedMotion) {
            tween(0)
        } else {
            spring(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = .001f)
        },
        label = "gallery-liquid-motion",
    )
    val boundedPosition = pagerPosition.fastCoerceIn(0f, (filters.lastIndex).toFloat())
    val motionVelocity = ((pagerTargetPage - boundedPosition) * 2f).fastCoerceIn(-1f, 1f)
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterStart,
    ) {
        val tabWidthPx = (constraints.maxWidth.toFloat() - with(LocalDensity.current) { 8.dp.toPx() }) /
            filters.size

        Row(
            modifier = Modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(24.dp.toPx(), 24.dp.toPx())
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            filters.forEachIndexed { index, filter ->
                val isSelected = selectedPage == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(ContinuousCapsule)
                        .clickable(enabled = enabled) { onFilterSelected(index) }
                        .semantics { selected = isSelected },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = filter.label,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .clearAndSetSemantics { }
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(8.dp.toPx())
                        lens(
                            24.dp.toPx() * motionProgress,
                            24.dp.toPx() * motionProgress,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = motionProgress) },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            filters.forEach { filter ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = filter.label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX = if (isLtr) {
                        boundedPosition * tabWidthPx
                    } else {
                        size.width - (boundedPosition + 1f) * tabWidthPx
                    }
                }
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { Capsule() },
                    effects = {
                        lens(
                            10.dp.toPx() * motionProgress,
                            14.dp.toPx() * motionProgress,
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = motionProgress) },
                    shadow = { Shadow(alpha = motionProgress) },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * motionProgress,
                            alpha = motionProgress,
                        )
                    },
                    layerBlock = {
                        val scale = lerp(1f, 78f / 56f, motionProgress)
                        scaleX = scale /
                            (1f - (motionVelocity * .75f).fastCoerceIn(-.2f, .2f))
                        scaleY = scale *
                            (1f - (motionVelocity * .25f).fastCoerceIn(-.2f, .2f))
                    },
                    onDrawSurface = {
                        drawRect(
                            if (isLightTheme) Color.Black.copy(alpha = .10f)
                            else Color.White.copy(alpha = .10f),
                            alpha = 1f - motionProgress,
                        )
                        drawRect(Color.Black.copy(alpha = .03f * motionProgress))
                    },
                )
                .height(56.dp)
                .fillMaxWidth(1f / filters.size),
        )
    }
}

@Composable
private fun LoadingGalleryPlaceholder(state: GalleryUiState) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (state.isInitialScan) "正在整理 JPEG 与 RAW…" else "正在读取相机图库…",
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(2) {
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(.82f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@Composable
private fun EmptyGalleryMessage(
    title: String,
    detail: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f)),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painterResource(R.drawable.ic_camera_outline),
                contentDescription = null,
                modifier = Modifier.size(30.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                detail,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 21.sp,
            )
            actionLabel?.let {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(15.dp),
                ) { Text(it) }
            }
        }
    }
}

@Composable
private fun InlineError(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.error.copy(alpha = .10f),
        contentColor = MaterialTheme.colorScheme.error,
    ) {
        Text(message, modifier = Modifier.padding(14.dp), lineHeight = 20.sp)
    }
}

@Composable
private fun SolidPanel(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .60f)),
        shadowElevation = 3.dp,
        content = content,
    )
}

@Composable
private fun BrandMark() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(38.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.onBackground,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(R.drawable.ic_camera_outline),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("NIKON CONNECT", fontSize = 12.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AmbientBackground() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = .08f),
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    )
}

@Composable
private fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) <= 0f
        }.getOrDefault(false)
    }
}

private fun cameraProductDrawable(model: String): Int {
    val normalized = model
        .uppercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)
    return when {
        "ZFC" in normalized -> R.drawable.camera_nikon_zfc
        "ZF" in normalized -> R.drawable.camera_nikon_zf
        else -> R.drawable.camera_product_render
    }
}

private fun formatBytes(info: PtpObjectInfo): String {
    val mb = info.compressedSize / 1_048_576.0
    return String.format(
        Locale.ROOT,
        if (mb >= 1) "%.1f MB" else "%.0f KB",
        if (mb >= 1) mb else info.compressedSize / 1024.0,
    )
}
