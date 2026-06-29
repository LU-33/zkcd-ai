package com.example.aicreationassistant.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem(
        route = NavRoutes.HOME,
        label = "首页",
        icon = Icons.Default.Home
    )

    object Favorites : BottomNavItem(
        route = NavRoutes.FAVORITES,
        label = "收藏",
        icon = Icons.Default.Favorite
    )

    object History : BottomNavItem(
        route = NavRoutes.HISTORY,
        label = "历史",
        icon = Icons.Default.History
    )

    companion object {
        val entries = listOf(Home, Favorites, History)
    }
}
