package com.aiassistant.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aiassistant.ui.theme.EchoTokens
import dev.chrisbanes.haze.HazeState

/**
 * Echo 标准脚手架 (EchoScaffold)
 * 统一承载背景壁纸渲染、毛玻璃顶栏、安全区域 Insets 派发与状态栏协调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backgroundBitmap: Bitmap? = null,
    hazeState: HazeState = rememberEchoHazeState(),
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (PaddingValues) -> Unit
) {
    EchoWallpaperBackground(
        backgroundBitmap = backgroundBitmap,
        hazeState = hazeState,
        modifier = modifier
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                EchoGlassTopAppBar(
                    title = title,
                    subtitle = subtitle,
                    navigationIcon = navigationIcon,
                    actions = actions,
                    hazeState = hazeState
                )
            },
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            content = { paddingValues ->
                content(paddingValues)
            }
        )
    }
}

/**
 * Echo 悬浮毛玻璃顶栏 (EchoGlassTopAppBar)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EchoGlassTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    hazeState: HazeState
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .echoHazePanel(
                hazeState = hazeState,
                shape = androidx.compose.ui.graphics.RectangleShape,
                tint = MaterialTheme.colorScheme.surface.copy(alpha = EchoTokens.Glass.panelAlphaLight),
                blurRadius = EchoTokens.Glass.blurRadiusStandard
            ),
        color = Color.Transparent
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            navigationIcon = navigationIcon ?: {},
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
