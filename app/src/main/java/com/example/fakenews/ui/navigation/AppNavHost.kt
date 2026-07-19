package com.example.fakenews.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.fakenews.ui.detail.DetailScreen
import com.example.fakenews.ui.factcheck.FactCheckScreen
import com.example.fakenews.ui.main.MainScreen

@Composable
fun AppNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AppRoutes.Main.route,
        modifier = modifier
    ) {
        composable(AppRoutes.Main.route) {
            MainScreen(
                onFactCheckClick = {
                    navController.navigate(AppRoutes.FactCheck.route)
                },
                onArticleClick = { article ->
                    navController.navigate(AppRoutes.Detail.createRoute(article.id))
                }
            )
        }
        composable(AppRoutes.FactCheck.route) {
            FactCheckScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onArticleClick = { article ->
                    navController.navigate(AppRoutes.Detail.createRoute(article.id))
                }
            )
        }
        composable(
            route = AppRoutes.Detail.route,
            arguments = listOf(
                navArgument(AppRoutes.Detail.ARG_ARTICLE_ID) {
                    type = NavType.StringType
                }
            )
        ) { backStackEntry ->
            DetailScreen(
                articleId = backStackEntry.arguments?.getString(AppRoutes.Detail.ARG_ARTICLE_ID),
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
