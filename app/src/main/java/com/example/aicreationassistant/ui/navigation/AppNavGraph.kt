package com.example.aicreationassistant.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.aicreationassistant.AiCreationApp
import com.example.aicreationassistant.ui.detail.DetailScreen
import com.example.aicreationassistant.ui.favorites.FavoritesScreen
import com.example.aicreationassistant.ui.history.HistoryScreen
import com.example.aicreationassistant.ui.home.HomeScreen
import com.example.aicreationassistant.ui.imagedesc.ImageDescScreen
import com.example.aicreationassistant.ui.imageedit.ImageEditScreen
import com.example.aicreationassistant.ui.textcreation.TextCreationScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // 底部导航只在首页/收藏/历史时显示
    val bottomNavRoutes = listOf(NavRoutes.HOME, NavRoutes.FAVORITES, NavRoutes.HISTORY)
    val showBottomBar = currentRoute in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    BottomNavItem.entries.forEach { item ->
                        val selected = backStackEntry?.destination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = NavRoutes.HOME,
            modifier = Modifier.padding(innerPadding)
        ) {
            // 首页
            composable(NavRoutes.HOME) {
                HomeScreen(
                    onCreationCardTap = { creationType ->
                        navController.navigate(NavRoutes.textCreation(creationType.key))
                    },
                    onImageDescTap = {
                        navController.navigate(NavRoutes.IMAGE_DESC)
                    }
                )
            }

            // 文本创作（朋友圈文案 / 商品描述）
            composable(
                route = NavRoutes.TEXT_CREATION,
                arguments = listOf(
                    navArgument("creationType") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val creationType = backStackEntry.arguments?.getString("creationType") ?: "social_media"
                TextCreationScreen(
                    creationTypeKey = creationType,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { contentId ->
                        navController.navigate(NavRoutes.detail(contentId))
                    },
                    onNavigateToImageEdit = { imageUri ->
                        navController.navigate(NavRoutes.imageEdit(imageUri))
                    }
                )
            }

            // 图片描述
            composable(NavRoutes.IMAGE_DESC) { navBackStackEntry ->
                val editedUri = navBackStackEntry.savedStateHandle.get<String>("edited_image_uri")
                ImageDescScreen(
                    editedImageUriResult = editedUri,
                    onClearEditedResult = {
                        navBackStackEntry.savedStateHandle.remove<String>("edited_image_uri")
                    },
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToDetail = { contentId ->
                        navController.navigate(NavRoutes.detail(contentId))
                    },
                    onNavigateToImageEdit = { imageUri ->
                        navController.navigate(NavRoutes.imageEdit(imageUri))
                    }
                )
            }

            // 图片编辑
            composable(
                route = NavRoutes.IMAGE_EDIT,
                arguments = listOf(
                    navArgument("sourceUri") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val sourceUri = backStackEntry.arguments?.getString("sourceUri") ?: ""
                ImageEditScreen(
                    sourceUri = sourceUri,
                    onNavigateBack = { navController.popBackStack() },
                    onSaveCompleted = { editedUri ->
                        // 将编辑后的 URI 回传给上一个页面
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set("edited_image_uri", editedUri)
                        navController.popBackStack()
                    }
                )
            }

            // 收藏
            composable(NavRoutes.FAVORITES) {
                FavoritesScreen(
                    onNavigateToDetail = { contentId ->
                        navController.navigate(NavRoutes.detail(contentId))
                    }
                )
            }

            // 历史
            composable(NavRoutes.HISTORY) {
                HistoryScreen(
                    onNavigateToDetail = { contentId ->
                        navController.navigate(NavRoutes.detail(contentId))
                    }
                )
            }

            // 详情
            composable(
                route = NavRoutes.DETAIL,
                arguments = listOf(
                    navArgument("contentId") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val contentId = backStackEntry.arguments?.getLong("contentId") ?: return@composable
                DetailScreen(
                    contentId = contentId,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
