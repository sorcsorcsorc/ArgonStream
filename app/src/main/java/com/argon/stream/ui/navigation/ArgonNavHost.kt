package com.argon.stream.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.argon.stream.ui.screens.profiles.ProfilesViewModel
import com.argon.stream.ui.screens.profiles.WhoIsWatchingScreen
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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import com.argon.stream.domain.model.MediaItem
import com.argon.stream.ui.components.ConnectivityViewModel
import com.argon.stream.ui.components.UpdatePrompt
import com.argon.stream.ui.screens.addons.AddonManagerScreen
import com.argon.stream.ui.screens.catalog.CatalogGridScreen
import com.argon.stream.ui.screens.details.TitleDetailsScreen
import com.argon.stream.ui.screens.downloads.DownloadsScreen
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

    // Phones in portrait get a bottom bar; landscape phones and tablets get a side rail.
    val useRail = windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact ||
        windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
    // The bar stays visible on details/genre pages too, so Home is always one tap away.
    val showBottomBar = !useRail && !isPlayer
    val showRail = useRail && !isPlayer

    val profilesViewModel: ProfilesViewModel = hiltViewModel()
    val profileState by profilesViewModel.uiState.collectAsStateWithLifecycle()
    val connectivityViewModel: ConnectivityViewModel = hiltViewModel()
    val isOnline by connectivityViewModel.isOnline.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (showBottomBar) {
                    ArgonBottomBar(
                        isSelected = { currentDestination.isOn(it) },
                        onNavigate = { navController.navigateToTopLevel(it) }
                    )
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
                    val openStreams: (type: String, mediaId: String, videoId: String, title: String, mode: String) -> Unit =
                        { type, mediaId, videoId, title, mode ->
                            navController.navigate(
                                "streams/$type/${Uri.encode(mediaId)}/${Uri.encode(videoId)}/${Uri.encode(title)}?mode=$mode"
                            )
                        }
                    val playFile: (Uri, String) -> Unit = { uri, title ->
                        navController.navigate(
                            "$PLAYER_ROUTE_PREFIX?videoId=&streamUrl=${Uri.encode(uri.toString())}&streamTitle=${Uri.encode(title)}"
                        )
                    }

                    composable(TopLevelDestination.HOME.route) {
                        HomeScreen(
                            onOpenMediaDetails = openDetails,
                            onResume = { progress ->
                                // Continue Watching reopens the last stream directly (details if it has no IMDb id).
                                val media = progress.mediaItem
                                val imdbId = media.imdbId ?: media.id.takeIf { it.startsWith("tt") }
                                if (imdbId == null) {
                                    openDetails(media)
                                } else {
                                    val season = progress.seasonNumber
                                    val episode = progress.episodeNumber
                                    val videoId = if (season != null && episode != null) "$imdbId:$season:$episode" else imdbId
                                    val title = if (season != null && episode != null) "${media.title} S${season}E$episode" else media.title
                                    openStreams(media.type.name, media.id, videoId, title, "resume")
                                }
                            },
                            onOpenCatalog = { query, title ->
                                navController.navigate(
                                    "catalog/${query.kind.name}/${query.type?.name ?: "ALL"}" +
                                        "?genre=${Uri.encode(query.genre.orEmpty())}&title=${Uri.encode(title)}" +
                                        "&anime=${Uri.encode(query.animeCatalog.orEmpty())}"
                                )
                            },
                            onOpenLibrary = { navController.navigateToTopLevel(TopLevelDestination.LIBRARY) },
                            onOpenSettings = { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) },
                            onOpenSearch = { navController.navigateToTopLevel(TopLevelDestination.SEARCH) }
                        )
                    }

                    composable(TopLevelDestination.SEARCH.route) {
                        SearchScreen(onOpenMediaDetails = openDetails)
                    }

                    composable(TopLevelDestination.LIBRARY.route) {
                        LibraryScreen(onOpenMediaDetails = openDetails, onPlayFile = playFile)
                    }

                    composable("downloads") {
                        DownloadsScreen(onPlayFile = playFile)
                    }

                    composable("addons") {
                        AddonManagerScreen(onBack = { navController.popBackStack() })
                    }

                    composable(TopLevelDestination.SETTINGS.route) {
                        SettingsScreen(onOpenAddons = { navController.navigate("addons") })
                    }

                    composable(
                        route = "catalog/{kind}/{type}?genre={genre}&title={title}&anime={anime}",
                        arguments = listOf(
                            navArgument("kind") { type = NavType.StringType },
                            navArgument("type") { type = NavType.StringType },
                            navArgument("genre") { type = NavType.StringType; defaultValue = "" },
                            navArgument("title") { type = NavType.StringType; defaultValue = "" },
                            navArgument("anime") { type = NavType.StringType; defaultValue = "" }
                        )
                    ) {
                        CatalogGridScreen(onBack = { navController.popBackStack() }, onOpenMediaDetails = openDetails)
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
                                openStreams(type, mediaId, stremioVideoId, displayTitle, "play")
                            },
                            onDownload = { stremioVideoId, displayTitle ->
                                openStreams(type, mediaId, stremioVideoId, displayTitle, "download")
                            }
                        )
                    }

                    composable(
                        route = "streams/{type}/{mediaId}/{videoId}/{title}?mode={mode}",
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType },
                            navArgument("mediaId") { type = NavType.StringType },
                            navArgument("videoId") { type = NavType.StringType },
                            navArgument("title") { type = NavType.StringType },
                            navArgument("mode") { type = NavType.StringType; defaultValue = "play" }
                        )
                    ) { entry ->
                        val videoId = entry.arguments?.getString("videoId").orEmpty()
                        StreamSelectionScreen(
                            onBack = { navController.popBackStack() },
                            onOpenAddons = { navController.navigate("addons") },
                            onOpenSettings = { navController.navigateToTopLevel(TopLevelDestination.SETTINGS) },
                            onLaunchResolvedStream = { event ->
                                // Replace the stream list so Back from the player returns to the title, not the list.
                                navController.popBackStack()
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
                        PlayerScreen(
                        onBack = { navController.popBackStack() },
                        onOpenStreams = { type, mediaId, videoId, title ->
                            navController.popBackStack()
                            navController.navigate(
                                "streams/${type.name}/${Uri.encode(mediaId)}/${Uri.encode(videoId)}/${Uri.encode(title)}"
                            )
                        }
                    )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = !isOnline && !isPlayer && !profileState.showPicker,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = if (showBottomBar) 88.dp else 16.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(50),
                shadowElevation = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("No internet connection. Downloads still play.", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // "Who's watching?" sits above the whole app so navigation state survives switching profiles.
        if (profileState.showPicker) {
            WhoIsWatchingScreen(state = profileState, actions = profilesViewModel)
        }

        // Asks to update when a newer release exists (checked once per app start, never during playback).
        if (profileState.ready && !profileState.showPicker && !isPlayer) {
            UpdatePrompt()
        }
    }
}

/** Bottom navigation for phones in portrait. Labels shrink slightly so six tabs fit on narrow screens. */
@Composable
internal fun ArgonBottomBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit
) {
    val narrow = LocalConfiguration.current.screenWidthDp < 400
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        letterSpacing = 0.sp,
        fontSize = if (narrow) 10.sp else 11.sp
    )
    Column {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp
    ) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = isSelected(destination)
            NavigationBarItem(
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = null
                    )
                },
                label = {
                    Text(
                        text = destination.label,
                        style = labelStyle,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}
}

private fun NavDestination?.isOn(destination: TopLevelDestination): Boolean =
    this?.hierarchy?.any { it.route == destination.route } == true

/**
 * Tab navigation. Home always goes back to the Home screen itself (never a details page opened from it);
 * other tabs keep their state.
 */
private fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    val current = currentBackStackEntry?.destination?.route
    if (destination == TopLevelDestination.HOME || current == destination.route) {
        if (popBackStack(destination.route, inclusive = false)) return
    }
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
