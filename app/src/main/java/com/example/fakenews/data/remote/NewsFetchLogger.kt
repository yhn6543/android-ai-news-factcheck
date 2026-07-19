package com.example.fakenews.data.remote

import android.util.Log
import com.example.fakenews.BuildConfig
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import java.net.URI

object NewsFetchLogger {
    fun logAttempt(
        tag: String,
        press: NewsPress,
        sourceType: NewsSourceType,
        sourceName: String,
        url: String?,
        success: Boolean,
        articleCount: Int,
        errorMessage: String? = null
    ) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val loggedUrl = safeUrlForLog(url)
            Log.d(
                tag,
                "press=${press.displayName} sourceType=$sourceType sourceName=$sourceName " +
                    "url=$loggedUrl success=$success articleCount=$articleCount " +
                    "error=${errorMessage.orEmpty()}"
            )
        }
    }

    fun safeUrlForLog(url: String?): String {
        val candidate = url.orEmpty().trim()
        if (candidate.isBlank()) return ""

        return runCatching {
            val uri = URI(candidate)
            val host = uri.host.orEmpty()
            val path = uri.rawPath.orEmpty()
            if (host.isNotBlank()) "$host$path" else candidate.substringBefore('?')
        }.getOrElse {
            candidate.substringBefore('?')
        }
    }

    fun logDetail(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_DETAIL, message)
        }
    }

    fun logArticleExtract(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_ARTICLE_EXTRACT, message)
        }
    }

    fun logArticleLinkCheck(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_ARTICLE_LINK_CHECK, message)
        }
    }

    fun logDetailLinkCheck(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_DETAIL_LINK_CHECK, message)
        }
    }

    fun logValidation(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_NEWS_VALIDATION, message)
        }
    }

    fun logYtnRuntime(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_YTN_RUNTIME, message)
        }
    }

    fun logYtnRepository(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_YTN_REPOSITORY, message)
        }
    }

    fun logYtnUiState(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_YTN_UI_STATE, message)
        }
    }

    fun logSearchPerf(message: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            Log.d(TAG_SEARCH_PERF, message)
        }
    }

    const val TAG_FETCH = "NEWS_FETCH"
    const val TAG_RSS = "NEWS_RSS"
    const val TAG_SEARCH_RSS = "NEWS_SEARCH_RSS"
    const val TAG_SEARCH_PERF = "NEWS_SEARCH_PERF"
    const val TAG_CRAWLING = "NEWS_CRAWLING"
    const val TAG_FALLBACK = "NEWS_FALLBACK"
    const val TAG_DETAIL = "NEWS_DETAIL"
    const val TAG_ARTICLE_EXTRACT = "ARTICLE_EXTRACT"
    const val TAG_ARTICLE_LINK_CHECK = "ARTICLE_LINK_CHECK"
    const val TAG_DETAIL_LINK_CHECK = "NEWS_DETAIL_NAV"
    const val TAG_NEWS_VALIDATION = "NEWS_VALIDATION"
    const val TAG_YTN_RUNTIME = "YTN_RUNTIME"
    const val TAG_YTN_REPOSITORY = "YTN_REPOSITORY"
    const val TAG_YTN_UI_STATE = "YTN_UI_STATE"
}
