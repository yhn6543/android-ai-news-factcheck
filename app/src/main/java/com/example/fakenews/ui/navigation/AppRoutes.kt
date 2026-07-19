package com.example.fakenews.ui.navigation

import android.net.Uri

sealed class AppRoutes(val route: String) {
    data object Main : AppRoutes("main")
    data object FactCheck : AppRoutes("factcheck")

    data object Detail : AppRoutes("detail/{articleId}") {
        const val ARG_ARTICLE_ID = "articleId"

        fun createRoute(articleId: String): String = "detail/${Uri.encode(articleId)}"
    }
}
