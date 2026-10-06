@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.aiassistant

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aiassistant.domain.model.CharacterProfile
import com.aiassistant.domain.model.RoleplayScenario
import com.aiassistant.ui.screens.chat.ChatScreen
import com.aiassistant.ui.screens.history.HistoryScreen
import com.aiassistant.ui.screens.home.FolderManagerScreen
import com.aiassistant.ui.screens.home.HomeScreen
import com.aiassistant.ui.screens.roleplay.CharacterEditorScreen
import com.aiassistant.ui.screens.roleplay.NewRoleplaySessionScreen
import com.aiassistant.ui.screens.roleplay.RoleplayMemoryScreen
import com.aiassistant.ui.screens.roleplay.RoleplayStudioScreen
import com.aiassistant.ui.screens.roleplay.RoleplayViewModel
import com.aiassistant.ui.screens.roleplay.ScenarioEditorScreen
import com.aiassistant.ui.screens.settings.SettingsScreen
import com.aiassistant.ui.screens.stats.StatsScreen
import com.aiassistant.ui.theme.AiApiAssistantTheme
import com.aiassistant.utils.AppThemeMode
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        setContent {
            val themeManager = remember { AiAssistantApp.instance.themePreferenceManager }
            var themeMode by remember { mutableStateOf(themeManager.getThemeMode()) }
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                AppThemeMode.System -> systemDark
                AppThemeMode.Light -> false
                AppThemeMode.Dark -> true
            }

            AiApiAssistantTheme(darkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.foundation.LocalOverscrollConfiguration provides null
                    ) {
                        AiAssistantNavigation(
                            themeMode = themeMode,
                            onThemeModeChange = { mode ->
                                if (themeManager.saveThemeMode(mode)) {
                                    themeMode = mode
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AiAssistantNavigation(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val roleplayViewModel: RoleplayViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "home",
        enterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300))
        },
        exitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(250))
        },
        popEnterTransition = {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 4 },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(300))
        },
        popExitTransition = {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(250))
        }
    ) {
        // 主页
        composable("home") {
            HomeScreen(
                onNavigateToChat = { conversationId ->
                    navController.navigate("chat/$conversationId") {
                        // v2.6.8 需求 4：同一会话只保留一个对话页实例，避免连点会话卡片产生
                        // 两个 ChatViewModel 互相取消/清理生成会话，打断正在连接或回复的请求
                        launchSingleTop = true
                    }
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToHistory = {
                    navController.navigate("history")
                },
                onNavigateToStats = {
                    navController.navigate("stats")
                },
                onNavigateToFolders = {
                    navController.navigate("folders")
                },
                onNavigateToRoleplayStudio = {
                    navController.navigate("roleplay_studio")
                }
            )
        }

        // 对话页面
        composable(
            route = "chat/{conversationId}",
            arguments = listOf(
                navArgument("conversationId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getLong("conversationId") ?: return@composable
            ChatScreen(
                conversationId = conversationId,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { newConversationId ->
                    navController.navigate("chat/$newConversationId") {
                        // v2.6.8 需求 4：同上，避免重复压栈生成多个对话页实例
                        launchSingleTop = true
                    }
                },
                onNavigateToRoleplayMemory = { sessionId ->
                    navController.navigate("roleplay_memory/$sessionId")
                }
            )
        }

        // 角色扮演工作室
        composable("roleplay_studio") {
            RoleplayStudioScreen(
                viewModel = roleplayViewModel,
                onNavigateToCharacters = {},
                onNavigateToScenarios = {},
                onNavigateToCharacterEditor = { character ->
                    if (character != null) {
                        navController.navigate("roleplay_character_editor?characterId=${character.id}")
                    } else {
                        navController.navigate("roleplay_character_editor")
                    }
                },
                onNavigateToScenarioEditor = { scenario ->
                    if (scenario != null) {
                        navController.navigate("roleplay_scenario_editor?scenarioId=${scenario.id}")
                    } else {
                        navController.navigate("roleplay_scenario_editor")
                    }
                },
                onNavigateToSession = { conversationId ->
                    navController.navigate("chat/$conversationId") {
                        // v2.6.8 需求 4：同一会话只保留一个对话页实例，避免连点会话卡片产生
                        // 两个 ChatViewModel 互相取消/清理生成会话，打断正在连接或回复的请求
                        launchSingleTop = true
                    }
                },
                onCreateNewSession = {
                    navController.navigate("roleplay_new_session")
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 新建角色扮演会话
        composable("roleplay_new_session") {
            val apiConfigs by AiAssistantApp.instance.database.apiConfigDao().getAllConfigs().collectAsState(initial = emptyList())
            NewRoleplaySessionScreen(
                viewModel = roleplayViewModel,
                apiConfigs = apiConfigs,
                onStartSession = { characterIds, scenarioId, apiConfigId, modelName, narrativeMode ->
                    roleplayViewModel.createStorySessionAndStart(
                        characterIds = characterIds,
                        scenarioId = scenarioId,
                        apiConfigId = apiConfigId,
                        modelName = modelName,
                        narrativeMode = narrativeMode,
                        onSuccess = { conversationId ->
                            navController.navigate("chat/$conversationId") {
                                popUpTo("roleplay_studio")
                                // v2.6.8 需求 4：同一会话只保留一个对话页实例，避免连点会话卡片
                                // 产生两个 ChatViewModel 互相取消/清理生成会话，导致正在连接或回复的请求被中断
                                launchSingleTop = true
                            }
                        }
                    )
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 角色卡编辑/创建
        composable(
            route = "roleplay_character_editor?characterId={characterId}",
            arguments = listOf(
                navArgument("characterId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getLong("characterId") ?: 0L
            val characters by roleplayViewModel.characters.collectAsState()
            val character = if (characterId > 0) characters.find { it.id == characterId } else null

            CharacterEditorScreen(
                character = character,
                onSave = { savedCharacter ->
                    roleplayViewModel.saveCharacter(savedCharacter) { success, errorMsg ->
                        if (success) {
                            navController.popBackStack()
                        } else {
                            android.widget.Toast.makeText(context, "保存失败: ${errorMsg ?: "未知错误"}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = character?.let {
                    {
                        roleplayViewModel.deleteCharacter(it)
                        navController.popBackStack()
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 场景卡编辑/创建
        composable(
            route = "roleplay_scenario_editor?scenarioId={scenarioId}",
            arguments = listOf(
                navArgument("scenarioId") {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val scenarioId = backStackEntry.arguments?.getLong("scenarioId") ?: 0L
            val scenarios by roleplayViewModel.scenarios.collectAsState()
            val scenario = if (scenarioId > 0) scenarios.find { it.id == scenarioId } else null

            ScenarioEditorScreen(
                scenario = scenario,
                onSave = { savedScenario ->
                    roleplayViewModel.saveScenario(savedScenario) { success, errorMsg ->
                        if (success) {
                            navController.popBackStack()
                        } else {
                            android.widget.Toast.makeText(context, "保存失败: ${errorMsg ?: "未知错误"}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = scenario?.let {
                    {
                        roleplayViewModel.deleteScenario(it)
                        navController.popBackStack()
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 记忆管理页面
        composable(
            route = "roleplay_memory/{sessionId}",
            arguments = listOf(
                navArgument("sessionId") { type = NavType.LongType }
            )
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
            RoleplayMemoryScreen(
                viewModel = roleplayViewModel,
                sessionId = sessionId,
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 设置页面
        composable("settings") {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { conversationId ->
                    navController.navigate("chat/$conversationId") {
                        // v2.6.8 需求 4：同一会话只保留一个对话页实例，避免连点会话卡片产生
                        // 两个 ChatViewModel 互相取消/清理生成会话，打断正在连接或回复的请求
                        launchSingleTop = true
                    }
                },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange
            )
        }

        // 历史记录页面
        composable("history") {
            HistoryScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToChat = { conversationId ->
                    navController.navigate("chat/$conversationId") {
                        // v2.6.8 需求 4：同一会话只保留一个对话页实例，避免连点会话卡片产生
                        // 两个 ChatViewModel 互相取消/清理生成会话，打断正在连接或回复的请求
                        launchSingleTop = true
                    }
                }
            )
        }

        // 统计页面
        composable("stats") {
            StatsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 文件夹管理页面
        composable("folders") {
            FolderManagerScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onFolderSelected = {
                    navController.popBackStack()
                }
            )
        }
    }
}
