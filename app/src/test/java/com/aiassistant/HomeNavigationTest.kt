package com.aiassistant

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.composable
import androidx.navigation.createGraph
import androidx.navigation.navArgument
import com.aiassistant.ui.components.EchoDestination
import com.aiassistant.ui.components.openEchoDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Real navigation-controller regressions; no copied navigation-policy implementation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class HomeNavigationTest {
    @Test fun switchingAllRootDestinationsNeverAccumulatesPreviouslySelectedTabs() {
        NavigationFixture().use { fixture ->
            repeat(3) {
                for (destination in EchoDestination.entries) {
                    fixture.open(destination)
                    assertEquals(destination.route, fixture.controller.currentDestination?.route)
                    val expectedRoutes = if (destination == EchoDestination.CONVERSATIONS) {
                        listOf("home")
                    } else {
                        listOf("home", destination.route)
                    }
                    assertEquals(expectedRoutes, fixture.activeRoutes())
                }
            }
        }
    }

    @Test fun selectingCurrentTabKeepsEntryIdentityAndDoesNotDispatchAnotherNavigation() {
        NavigationFixture().use { fixture ->
            fixture.open(EchoDestination.CREATION)
            val entry = requireNotNull(fixture.controller.currentBackStackEntry)
            entry.savedStateHandle["synthetic_story_draft"] = "未寄出的合成书信"
            var destinationEvents = 0
            fixture.controller.addOnDestinationChangedListener { _, _, _ -> destinationEvents++ }
            val eventsBeforeReselection = destinationEvents

            repeat(8) { fixture.open(EchoDestination.CREATION) }

            assertSame(entry, fixture.controller.currentBackStackEntry)
            assertEquals(eventsBeforeReselection, destinationEvents)
            assertEquals("未寄出的合成书信", entry.savedStateHandle.get<String>("synthetic_story_draft"))
            assertEquals(listOf("home", "roleplay_studio"), fixture.activeRoutes())
        }
    }

    @Test fun returningToTabRestoresItsSavedStateAndOriginalEntryId() {
        NavigationFixture().use { fixture ->
            fixture.open(EchoDestination.SETTINGS)
            val settingsEntry = requireNotNull(fixture.controller.currentBackStackEntry)
            val settingsId = settingsEntry.id
            settingsEntry.savedStateHandle["selected_section"] = "appearance"
            settingsEntry.savedStateHandle["synthetic_scroll_position"] = 9

            fixture.open(EchoDestination.STATISTICS)
            fixture.open(EchoDestination.CREATION)
            fixture.open(EchoDestination.SETTINGS)

            val restored = requireNotNull(fixture.controller.currentBackStackEntry)
            assertEquals(settingsId, restored.id)
            assertEquals("appearance", restored.savedStateHandle.get<String>("selected_section"))
            assertEquals(9, restored.savedStateHandle.get<Int>("synthetic_scroll_position"))
            assertEquals(listOf("home", "settings"), fixture.activeRoutes())
        }
    }

    @Test fun savedTabStateSurvivesControllerRecreationWithANewViewModelStore() {
        val savedNavigationState = NavigationFixture().use { fixture ->
            fixture.open(EchoDestination.SETTINGS)
            requireNotNull(fixture.controller.currentBackStackEntry)
                .savedStateHandle["synthetic_pending_section"] = "appearance"
            fixture.open(EchoDestination.STATISTICS)
            requireNotNull(fixture.controller.saveState())
        }

        NavigationFixture(savedNavigationState).use { fixture ->
            assertEquals("stats", fixture.controller.currentDestination?.route)
            fixture.open(EchoDestination.SETTINGS)
            assertEquals(
                "appearance",
                fixture.controller.currentBackStackEntry?.savedStateHandle
                    ?.get<String>("synthetic_pending_section")
            )
            assertEquals(listOf("home", "settings"), fixture.activeRoutes())
        }
    }

    @Test fun backFromARootTabReturnsToConversationsRatherThanPreviouslySelectedTabs() {
        NavigationFixture().use { fixture ->
            fixture.open(EchoDestination.CREATION)
            fixture.open(EchoDestination.STATISTICS)
            fixture.open(EchoDestination.SETTINGS)

            assertTrue(fixture.pop())
            assertEquals("home", fixture.controller.currentDestination?.route)
            assertEquals(listOf("home"), fixture.activeRoutes())
            assertNull(fixture.controller.previousBackStackEntry)

            // The root BackHandler invokes the same public app action as selecting 会话.
            fixture.open(EchoDestination.CREATION)
            fixture.open(EchoDestination.CONVERSATIONS)
            assertEquals(listOf("home"), fixture.activeRoutes())
            assertNull(fixture.controller.previousBackStackEntry)
        }
    }

    @Test fun chatAndEditorHideRootShellAndBackRestoresTheirOwningRoot() {
        NavigationFixture().use { fixture ->
            fixture.open(EchoDestination.CREATION)
            val creationEntryId = requireNotNull(fixture.controller.currentBackStackEntry).id
            val childRoutes = listOf(
                "chat/7301" to "conversationId",
                "roleplay_character_editor?characterId=7301" to "characterId"
            )

            for ((route, argumentName) in childRoutes) {
                fixture.navigate(route)
                val child = requireNotNull(fixture.controller.currentBackStackEntry)
                assertNull(EchoDestination.fromRoute(child.destination.route))
                assertEquals(7301L, child.arguments?.getLong(argumentName))
                assertEquals(creationEntryId, fixture.controller.previousBackStackEntry?.id)

                assertTrue(fixture.pop())
                assertEquals(creationEntryId, fixture.controller.currentBackStackEntry?.id)
                assertEquals(
                    EchoDestination.CREATION,
                    EchoDestination.fromRoute(fixture.controller.currentDestination?.route)
                )
                assertEquals(listOf("home", "roleplay_studio"), fixture.activeRoutes())
            }
        }
    }

    private class NavigationFixture(restoredState: Bundle? = null) : AutoCloseable {
        private val owner = NavigationLifecycleOwner()
        private val viewModelStore = ViewModelStore()
        private val navigator = ComposeNavigator()
        val controller = NavHostController(RuntimeEnvironment.getApplication()).apply {
            navigatorProvider.addNavigator(navigator)
            setLifecycleOwner(owner)
            setViewModelStore(viewModelStore)
            if (restoredState != null) restoreState(restoredState)
            graph = createGraph(startDestination = "home") {
                EchoDestination.entries.forEach { destination -> composable(destination.route) {} }
                composable(
                    route = "chat/{conversationId}",
                    arguments = listOf(navArgument("conversationId") { type = NavType.LongType })
                ) {}
                composable(
                    route = "roleplay_character_editor?characterId={characterId}",
                    arguments = listOf(navArgument("characterId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    })
                ) {}
            }
        }

        init { completeTransitions(emptyList()) }

        fun open(destination: EchoDestination) {
            val previousEntries = navigator.backStack.value.toList()
            controller.openEchoDestination(destination)
            completeTransitions(previousEntries)
        }

        fun navigate(route: String) {
            val previousEntries = navigator.backStack.value.toList()
            controller.navigate(route)
            completeTransitions(previousEntries)
        }

        fun pop(): Boolean {
            val previousEntries = navigator.backStack.value.toList()
            val popped = controller.popBackStack()
            completeTransitions(previousEntries)
            return popped
        }

        fun activeRoutes(): List<String?> = navigator.backStack.value.map { it.destination.route }

        private fun completeTransitions(previousEntries: List<NavBackStackEntry>) {
            // NavHost normally completes animated transitions. These tests exercise routing
            // without rendering, so complete the real navigator's transition lifecycle here.
            (previousEntries + navigator.backStack.value + controller.visibleEntries.value)
                .distinctBy { it.id }
                .forEach(navigator::onTransitionComplete)
        }

        override fun close() {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private class NavigationLifecycleOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }
}
