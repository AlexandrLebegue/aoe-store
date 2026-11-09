package dev.proxboss.aoestore.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.proxboss.aoestore.AppContainer
import dev.proxboss.aoestore.ui.CatalogViewModel
import dev.proxboss.aoestore.ui.DetailViewModel
import dev.proxboss.aoestore.ui.SettingsViewModel
import dev.proxboss.aoestore.ui.screens.AppDetailScreen
import dev.proxboss.aoestore.ui.screens.HomeScreen
import dev.proxboss.aoestore.ui.screens.SettingsScreen
import dev.proxboss.aoestore.ui.theme.AoEStoreTheme

private object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val APP = "app/{slug}"
    fun app(slug: String): String = "app/${Uri.encode(slug)}"
}

@Composable
fun StoreApp(container: AppContainer) {
    val themeMode by container.settings.themeMode.collectAsStateWithLifecycle(initialValue = "dark")
    val darkTheme = when (themeMode) {
        "light" -> false
        "system" -> isSystemInDarkTheme()
        else -> true
    }

    AoEStoreTheme(darkTheme = darkTheme) {
        val navController = rememberNavController()
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(260)) { it / 14 }
            },
            exitTransition = { fadeOut(animationSpec = tween(160)) },
            popEnterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideInHorizontally(animationSpec = tween(260)) { -it / 14 }
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(160)) +
                    slideOutHorizontally(animationSpec = tween(220)) { it / 14 }
            },
        ) {
            composable(Routes.HOME) {
                HomeRoute(container = container, onOpenApp = { slug ->
                    navController.navigate(Routes.app(slug))
                }, onOpenSettings = {
                    navController.navigate(Routes.SETTINGS)
                })
            }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    container = container,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.APP,
                arguments = listOf(navArgument("slug") { type = NavType.StringType }),
            ) { entry ->
                DetailRoute(
                    container = container,
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun HomeRoute(
    container: AppContainer,
    onOpenApp: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val factory = remember(container) {
        viewModelFactory {
            initializer {
                CatalogViewModel(container.repository, container.settings)
            }
        }
    }
    val viewModel: CatalogViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    HomeScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelected = viewModel::onCategorySelected,
        onRefresh = viewModel::refresh,
        onOpenApp = onOpenApp,
        onOpenSettings = onOpenSettings,
    )
}

@Composable
private fun DetailRoute(
    container: AppContainer,
    slug: String,
    onBack: () -> Unit,
) {
    val factory = remember(container, slug) {
        viewModelFactory {
            initializer {
                DetailViewModel(
                    repository = container.repository,
                    settings = container.settings,
                    downloader = container.downloader,
                    installer = container.installer,
                    slug = slug,
                )
            }
        }
    }
    val viewModel: DetailViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppDetailScreen(
        state = state,
        onBack = onBack,
        onInstall = viewModel::install,
        onOpenApp = viewModel::openApp,
        onOpenUnknownSources = viewModel::openUnknownSourcesSettings,
        onCheckPermission = viewModel::onResume,
        onToggleHistory = viewModel::toggleHistory,
        onRetry = viewModel::load,
        onMessageConsumed = viewModel::consumeMessage,
    )
}

@Composable
private fun SettingsRoute(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val factory = remember(container) {
        viewModelFactory {
            initializer {
                SettingsViewModel(container.settings, container.repository)
            }
        }
    }
    val viewModel: SettingsViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        onBack = onBack,
        onServerUrlChange = viewModel::onServerUrlChange,
        onSaveServerUrl = viewModel::saveServerUrl,
        onTestConnection = viewModel::testConnection,
        onThemeModeChange = viewModel::setThemeMode,
    )
}
