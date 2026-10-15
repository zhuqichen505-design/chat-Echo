package com.aiassistant.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.aiassistant.utils.EchoHomeLayoutStyle
import androidx.navigation.NavHostController

/** 两种布局共享同一份目的地定义，避免入口、顺序与选中态分叉。 */
internal enum class EchoDestination(val route: String, val title: String) {
    CONVERSATIONS("home", "会话"),
    CREATION("roleplay_studio", "创作"),
    STATISTICS("stats", "统计"),
    SETTINGS("settings", "设置");

    companion object {
        fun fromRoute(route: String?): EchoDestination? = entries.firstOrNull { it.route == route }
    }
}

internal fun NavHostController.openEchoDestination(destination: EchoDestination) {
    if (currentDestination?.route == destination.route) return
    navigate(destination.route) {
        popUpTo(EchoDestination.CONVERSATIONS.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private val EchoDestination.icon: ImageVector
    get() = when (this) {
        EchoDestination.CONVERSATIONS -> Icons.AutoMirrored.Filled.Chat
        EchoDestination.CREATION -> Icons.Default.AutoStories
        EchoDestination.STATISTICS -> Icons.Default.BarChart
        EchoDestination.SETTINGS -> Icons.Default.Settings
    }

/** NavHost 始终处于同一组合位置；更换导航方向不会重建页面或丢弃正在编辑的状态。 */
@Composable
internal fun EchoAppShell(
    layoutStyle: String,
    selected: EchoDestination?,
    onSelect: (EchoDestination) -> Unit,
    content: @Composable () -> Unit
) {
    val rootPage = selected != null
    val useRail = rootPage && EchoHomeLayoutStyle.sanitize(layoutStyle) == EchoHomeLayoutStyle.WORKBENCH
    val useBottomBar = rootPage && !useRail
    Row(Modifier.fillMaxSize()) {
        if (useRail) {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical))
                        .width(68.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Echo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 12.dp))
                    EchoDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = selected == destination,
                            onClick = { onSelect(destination) },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.title) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            val insetsModifier = when {
                useBottomBar -> Modifier
                    .consumeWindowInsets(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                useRail -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom))
                else -> Modifier
            }
            Box(Modifier.weight(1f).fillMaxWidth().then(insetsModifier)) { content() }
            if (useBottomBar) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .heightIn(min = 56.dp).selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EchoDestination.entries.forEach { destination ->
                        val active = selected == destination
                        val color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        Column(
                            Modifier.weight(1f).heightIn(min = 56.dp)
                                .selectable(selected = active, role = Role.Tab, onClick = { onSelect(destination) })
                                .padding(vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(shape = MaterialTheme.shapes.small,
                                color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                Icon(destination.icon, contentDescription = null, tint = color,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp).size(21.dp))
                            }
                            Text(destination.title, color = color, style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }
                }
            }
        }
    }
}
