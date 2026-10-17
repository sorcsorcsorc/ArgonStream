package com.argon.stream.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/** Bottom-bar / rail destinations. Movies and Series are filters on Home to keep the bar compact. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("home", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    SEARCH("search", "Search", Icons.Filled.Search, Icons.Outlined.Search),
    LIBRARY("library", "Library", Icons.Filled.VideoLibrary, Icons.Outlined.VideoLibrary),
    DOWNLOADS("downloads", "Downloads", Icons.Filled.Download, Icons.Outlined.Download),
    ADDONS("addons", "Add-ons", Icons.Filled.Extension, Icons.Outlined.Extension),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}
