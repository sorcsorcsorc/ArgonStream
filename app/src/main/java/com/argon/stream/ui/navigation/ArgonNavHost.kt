package com.argon.stream.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.ui.screens.addons.AddonManagerScreen
import com.argon.stream.ui.screens.details.TitleDetailsScreen
import com.argon.stream.ui.screens.home.HomeScreen
import com.argon.stream.ui.screens.library.LibraryScreen
import com.argon.stream.ui.screens.player.PlayerScreen
import com.argon.stream.ui.screens.search.SearchScreen
import com.argon.stream.ui.screens.settings.SettingsScreen
import com.argon.stream.ui.screens.streams.StreamSelectionScreen

private const val PLAYER_ROUTE_PREFIX = "player"

@Composable
fun ArgonNavHost(
    windowSizeClass: WindowSizeClass
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val isPlayer = currentRoute?.startsWith(PLAYER_ROUTE_PREFIX) == true
    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }

    // Phones in portrait get a bottom bar; landscape phones and tablets get a side rail.
    val useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact ||
        windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    val showBottomBar = !useRail && isTopLevel
    val showRail = useRail && !isPlayer

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.isOn(destination)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(destination.label, maxLines = 1, softWrap = false) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            if (showRail) {
                NavigationRail {
                    Spacer(modifier = Modifier.height(8.dp))
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination.isOn(destination)
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = null
                                )
                            },
                            label = { Text(destination.label, maxLines = 1, softWrap = false) },
                            alwaysShowLabel = true
                        )
                    }
                }
            }

            val contentInsets = when {
                isPlayer -> Modifier
                else -> {
                    val horizontal = WindowInsets.safeDrawing.only(
                        if (showRail) WindowInsetsSides.End else WindowInsetsSides.Horizontal
                    )
                    val bottom = if (showBottomBar) WindowInsets(0, 0, 0, 0)
                    else WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                    Modifier
                        .windowInsetsPadding(horizontal.union(bottom))
                        .imePadding()
                }
            }

            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.HOME.route,
                enterTransition = { fadeIn() },
                exitTransition = { fadeOut() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(contentInsets)
            ) {
                val openDetails: (MediaItem) -> Unit = { item ->
                    navController.navigate("details/${item.type.name}/${Uri.encode(item.id)}")
                }

                composable(TopLevelDestination.HOME.route) {
                    HomeScreen(
                        onOpenMediaDetails = openDetails,
                        onOpenSettings = { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) },
                        onOpenSearch = { navController.navigateToTopLevel(TopLevelDestination.SEARCH) }
                    )
                }

                composable(TopLevelDestination.SEARCH.route) {
                    SearchScreen(onOpenMediaDetails = openDetails)
                }

                composable(TopLevelDestination.LIBRARY.route) {
                    LibraryScreen(onOpenMediaDetails = openDetails)
                }

                composable(TopLevelDestination.ADDONS.route) {
                    AddonManagerScreen()
                }

                composable(TopLevelDestination.SETTINGS.route) {
                    SettingsScreen()
                }

                composable(
                    route = "details/{type}/{mediaId}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("mediaId") { type = NavType.StringType }
                    )
                ) { entry ->
                    val type = entry.arguments?.getString("type").orEmpty()
                    val mediaId = entry.arguments?.getString("mediaId").orEmpty()
                    TitleDetailsScreen(
                        onBack = { navController.popBackStack() },
                        onSelectStreams = { stremioVideoId, displayTitle ->
                            navController.navigate(
                                "streams/$type/${Uri.encode(mediaId)}/${Uri.encode(stremioVideoId)}/${Uri.encode(displayTitle)}"
                            )
                        }
                    )
                }

                composable(
                    route = "streams/{type}/{mediaId}/{videoId}/{title}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("mediaId") { type = NavType.StringType },
                        navArgument("videoId") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType }
                    )
                ) { entry ->
                    val videoId = entry.arguments?.getString("videoId").orEmpty()
                    StreamSelectionScreen(
                        onBack = { navController.popBackStack() },
                        onOpenAddons = { navController.navigateToTopLevel(TopLevelDestination.ADDONS) },
                        onOpenSettings = { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) },
                        onLaunchResolvedStream = { event ->
                            navController.navigate(
                                "$PLAYER_ROUTE_PREFIX?videoId=${Uri.encode(videoId)}" +
                                    "&streamUrl=${Uri.encode(event.streamUrl)}" +
                                    "&streamTitle=${Uri.encode(event.title)}"
                            )
                        }
                    )
                }

                composable(
                    route = "$PLAYER_ROUTE_PREFIX?videoId={videoId}&streamUrl={streamUrl}&streamTitle={streamTitle}",
                    arguments = listOf(
                        navArgument("videoId") { type = NavType.StringType; defaultValue = "" },
                        navArgument("streamUrl") { type = NavType.StringType; defaultValue = "" },
                        navArgument("streamTitle") { type = NavType.StringType; defaultValue = "" }
                    )
                ) {
                    PlayerScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.route == destination.route } == true

private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
