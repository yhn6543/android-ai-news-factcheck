package com.example.fakenews.ui.detail

import com.example.fakenews.MainDispatcherRule
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.repository.ArticleDetailRepository
import com.example.fakenews.data.repository.NewsRepository
import com.example.fakenews.util.ArticleIdentity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailLinkConsistencyTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun detailKeepsCardOriginalUrlWhenEnrichmentReturnsDifferentUrl() = runTest {
        val article = article(
            press = NewsPress.SBS,
            originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008605287"
        )
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(article),
            articleDetailRepository = RewritingDetailRepository(
                rewrittenOriginalUrl = "https://example.com/not-the-card-url"
            )
        )

        viewModel.loadArticle(article.id)

        assertEquals(article.id, viewModel.uiState.value.article?.id)
        assertEquals(article.press, viewModel.uiState.value.article?.press)
        assertEquals(article.originalUrl, viewModel.uiState.value.article?.originalUrl)
    }

    @Test
    fun ytnDetailStateUsesCardOriginalUrlAsOpenOriginalUrlSource() = runTest {
        val article = article(
            press = NewsPress.YTN,
            originalUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
        )
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(article),
            articleDetailRepository = RewritingDetailRepository(
                rewrittenOriginalUrl = "https://www.ytn.co.kr/_ln/0101_OTHER"
            )
        )

        viewModel.loadArticle(article.id)

        val detailArticle = viewModel.uiState.value.article
        assertEquals(article.id, detailArticle?.id)
        assertEquals(article.title, detailArticle?.title)
        assertEquals(article.originalUrl, detailArticle?.originalUrl)
    }

    @Test
    fun detailKeepsCardPublishedAtWhenEnrichmentReturnsDifferentTime() = runTest {
        val article = article(
            press = NewsPress.YTN,
            originalUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
        ).copy(
            publishedAt = 1_735_689_600_000L,
            publishedAtSource = "rss:pubDate"
        )
        val viewModel = DetailViewModel(
            repository = SingleArticleNewsRepository(article),
            articleDetailRepository = RewritingDetailRepository(
                rewrittenOriginalUrl = article.originalUrl,
                rewrittenPublishedAt = 1_735_776_000_000L
            )
        )

        viewModel.loadArticle(article.id)

        assertEquals(article.publishedAt, viewModel.uiState.value.article?.publishedAt)
        assertEquals(article.publishedAtSource, viewModel.uiState.value.article?.publishedAtSource)
    }

    @Test
    fun articleIdsAreBasedOnNormalizedOriginalUrlForAllPresses() {
        NewsPress.articlePresses().forEach { press ->
            val article = article(
                press = press,
                originalUrl = validOriginalUrlFor(press)
            )

            assertEquals(
                ArticleIdentity.idFor(press, article.originalUrl, article.title),
                article.id
            )
        }
    }

    private fun article(
        press: NewsPress,
        originalUrl: String
    ): NewsArticle {
        val title = "${press.displayName} 원본 URL 일치 검증 기사"
        val paragraphs = listOf(
            "카드에서 전달된 원본 URL이 상세 화면에서도 그대로 유지되는지 검증하는 본문입니다.",
            "원본 보러가기 버튼은 상세 화면의 article.originalUrl 값을 그대로 사용해야 합니다."
        )
        return NewsArticle(
            id = ArticleIdentity.idFor(press, originalUrl, title),
            title = title,
            press = press,
            publishedAt = 1L,
            summary = paragraphs.first(),
            content = paragraphs.joinToString("\n\n"),
            bodyParagraphs = paragraphs,
            originalUrl = originalUrl
        )
    }

    private fun validOriginalUrlFor(press: NewsPress): String =
        when (press) {
            NewsPress.ALL -> error("ALL is not an article press")
            NewsPress.YONHAP -> "https://www.yna.co.kr/view/AKR20260611094500052"
            NewsPress.MBC -> "https://imnews.imbc.com/news/2026/society/article.html"
            NewsPress.SBS -> "https://news.sbs.co.kr/news/endPage.do?news_id=N1008605287"
            NewsPress.KBS -> "https://news.kbs.co.kr/news/pc/view/view.do?ncd=1"
            NewsPress.YTN -> "https://www.ytn.co.kr/_ln/0101_202606110001"
        }

    private class SingleArticleNewsRepository(
        private val article: NewsArticle
    ) : NewsRepository {
        override suspend fun getLatestNews(): NewsFetchResult = NewsFetchResult(listOf(article))

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult = NewsFetchResult(listOf(article))

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            article.takeIf { it.id == articleId }
    }

    private class RewritingDetailRepository(
        private val rewrittenOriginalUrl: String,
        private val rewrittenPublishedAt: Long? = null
    ) : ArticleDetailRepository {
        override suspend fun enrichArticle(article: NewsArticle): NewsArticle =
            article.copy(
                originalUrl = rewrittenOriginalUrl,
                publishedAt = rewrittenPublishedAt,
                publishedAtSource = rewrittenPublishedAt?.let { "html:time[datetime]" },
                bodyParagraphs = listOf("상세 보강 본문입니다."),
                content = "상세 보강 본문입니다."
            )
    }
}
