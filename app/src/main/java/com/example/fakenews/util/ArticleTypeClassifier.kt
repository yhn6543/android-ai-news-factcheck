package com.example.fakenews.util

import com.example.fakenews.data.model.ArticleType
import java.net.URI
import java.util.Locale

object ArticleTypeClassifier {
    fun classify(
        title: String,
        originalUrl: String,
        bodyText: String = "",
        metaText: String = "",
        videoUrl: String? = null
    ): ArticleType {
        val normalizedTitle = title.normalizeForClassification()
        val combinedShortText = "$normalizedTitle $metaText".normalizeForClassification()
        val bodySample = bodyText.take(BODY_SAMPLE_LIMIT).normalizeForClassification()
        val path = normalizedPath(originalUrl)
        val fullUrl = originalUrl.lowercase(Locale.ROOT)
        val hasArticleSignal = hasArticleUrlSignal(path, fullUrl) ||
            hasMeaningfulArticleBody(bodySample)

        if (isReactionWidget(combinedShortText, path)) {
            return ArticleType.OPINION_OR_REACTION_WIDGET
        }
        if (isSchedulePage(combinedShortText, path, hasArticleSignal)) {
            return ArticleType.SCHEDULE_PAGE
        }
        if (isReplayPage(combinedShortText, path, hasArticleSignal)) {
            return ArticleType.REPLAY_PAGE
        }
        if (isProgramPage(combinedShortText, path, hasArticleSignal)) {
            return ArticleType.PROGRAM_PAGE
        }
        if (isLiveStream(combinedShortText, path, hasArticleSignal)) {
            return ArticleType.LIVE_STREAM
        }
        if (isListOrUtilityPage(combinedShortText, path, fullUrl, hasArticleSignal)) {
            return ArticleType.UNKNOWN_NON_ARTICLE
        }

        val isVideoNews = MediaUrlDetector.isPlayableVideoUrl(videoUrl) ||
            videoNewsKeywords.any { keyword -> combinedShortText.contains(keyword) }
        return if (isVideoNews) {
            ArticleType.VIDEO_NEWS_ARTICLE
        } else {
            ArticleType.NEWS_ARTICLE
        }
    }

    fun isNewsArticle(type: ArticleType): Boolean =
        type == ArticleType.NEWS_ARTICLE || type == ArticleType.VIDEO_NEWS_ARTICLE

    private fun isLiveStream(
        text: String,
        path: String,
        hasArticleSignal: Boolean
    ): Boolean {
        if (strongLivePhrases.any { phrase -> text.contains(phrase) }) return true
        return !hasArticleSignal && livePathKeywords.any { keyword -> path.contains(keyword) }
    }

    private fun isSchedulePage(
        text: String,
        path: String,
        hasArticleSignal: Boolean
    ): Boolean {
        if (schedulePhrases.any { phrase -> text.contains(phrase) }) return true
        return !hasArticleSignal && schedulePathKeywords.any { keyword -> path.contains(keyword) }
    }

    private fun isReplayPage(
        text: String,
        path: String,
        hasArticleSignal: Boolean
    ): Boolean {
        if (replayPhrases.any { phrase -> text.contains(phrase) }) return true
        return !hasArticleSignal && replayPathKeywords.any { keyword -> path.contains(keyword) }
    }

    private fun isProgramPage(
        text: String,
        path: String,
        hasArticleSignal: Boolean
    ): Boolean {
        if (programPhrases.any { phrase -> text.contains(phrase) }) return true
        return !hasArticleSignal && programPathKeywords.any { keyword -> path.contains(keyword) }
    }

    private fun isReactionWidget(
        text: String,
        path: String
    ): Boolean {
        val compactText = text.replace(" ", "")
        val shortWidgetText = compactText.length <= SHORT_WIDGET_TEXT_LIMIT &&
            reactionPhrases.any { phrase -> compactText.contains(phrase) }
        return shortWidgetText || reactionPathKeywords.any { keyword -> path.contains(keyword) }
    }

    private fun isListOrUtilityPage(
        text: String,
        path: String,
        fullUrl: String,
        hasArticleSignal: Boolean
    ): Boolean {
        if (strongListPathKeywords.any { keyword -> path.contains(keyword) || fullUrl.contains(keyword) }) return true
        if (listPagePhrases.any { phrase -> text.contains(phrase) }) return true
        if (hasArticleSignal) return false
        return listPathKeywords.any { keyword -> path.contains(keyword) || fullUrl.contains(keyword) }
    }

    private fun hasMeaningfulArticleBody(text: String): Boolean =
        text.length >= MIN_MEANINGFUL_BODY_LENGTH &&
            !ArticleBoilerplateCleaner.isBoilerplateParagraph(text.take(80))

    private fun hasArticleUrlSignal(
        path: String,
        fullUrl: String
    ): Boolean =
        articlePathSignals.any { signal -> path.contains(signal) } ||
            articleUrlSignals.any { signal -> fullUrl.contains(signal) }

    private fun normalizedPath(rawUrl: String): String =
        runCatching {
            val candidate = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                rawUrl
            } else {
                "https://$rawUrl"
            }
            URI(candidate).rawPath.orEmpty().lowercase(Locale.ROOT)
        }.getOrDefault("")

    private fun String.normalizeForClassification(): String =
        replace(Regex("\\s+"), " ")
            .trim()
            .lowercase(Locale.ROOT)

    private val strongLivePhrases = listOf(
        "실시간 방송",
        "실시간뉴스보기",
        "실시간 뉴스 보기",
        "라이브 방송",
        "라이브뉴스",
        "live 방송",
        "뉴스특보 라이브",
        "온에어"
    )

    private val schedulePhrases = listOf(
        "편성표",
        "방송 편성",
        "방송편성"
    )

    private val replayPhrases = listOf(
        "뉴스 다시보기",
        "다시보기",
        "vod 목록",
        "vod 리스트"
    )

    private val programPhrases = listOf(
        "프로그램 소개",
        "프로그램 안내",
        "프로그램정보",
        "제보 페이지",
        "구독하기",
        "알림받기"
    )

    private val reactionPhrases = listOf(
        "공감",
        "평가하기",
        "사용자평가",
        "댓글",
        "좋아요",
        "싫어요"
    )

    private val listPagePhrases = listOf(
        "검색 결과",
        "랭킹뉴스",
        "추천뉴스",
        "인기뉴스",
        "많이 본 뉴스",
        "공지사항",
        "이벤트"
    )

    private val videoNewsKeywords = listOf(
        "영상 뉴스",
        "동영상 뉴스",
        "[영상]",
        "영상으로 보는"
    )

    private val livePathKeywords = listOf("live", "onair", "on-air")
    private val schedulePathKeywords = listOf("schedule", "timetable")
    private val replayPathKeywords = listOf("replay", "vod/list", "vodlist")
    private val programPathKeywords = listOf("program", "tv")
    private val reactionPathKeywords = listOf("comment", "reaction", "sympathy", "empathy", "rating", "vote")
    private val strongListPathKeywords = listOf(
        "list.aspx",
        "list.php",
        "newsflash.aspx",
        "newsflash.do"
    )
    private val listPathKeywords = listOf(
        "search",
        "ranking",
        "popular",
        "notice",
        "event",
        "category",
        "section",
        "newsflash",
        "news_main.htm",
        "main.html"
    )
    private val articlePathSignals = listOf(
        "article",
        "news_view",
        "view.do",
        "endpage",
        "/view/"
    )
    private val articleUrlSignals = listOf(
        "news_id=",
        "ncd=",
        "seq_code="
    )

    private const val BODY_SAMPLE_LIMIT = 600
    private const val MIN_MEANINGFUL_BODY_LENGTH = 80
    private const val SHORT_WIDGET_TEXT_LIMIT = 40
}
