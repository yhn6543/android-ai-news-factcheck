package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.repository.MultiSourceNewsRepository
import com.example.fakenews.data.repository.NewsRepository
import com.example.fakenews.domain.usecase.GetFilteredNewsUseCase
import com.example.fakenews.ui.main.MainViewModel
import com.example.fakenews.util.ArticleUrlPolicy
import com.example.fakenews.util.ArticleTitleCleaner
import com.example.fakenews.util.ArticleTypeClassifier
import com.example.fakenews.util.PressDomainMatcher
import com.example.fakenews.util.UrlNormalizer
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.jsoup.Jsoup

@OptIn(ExperimentalCoroutinesApi::class)
class YtnRuntimePipelineTest {
    @get:Rule
    val mainDispatcherRule = com.example.fakenews.MainDispatcherRule()

    @Test
    fun ytnRuntimePipelineWritesRuntimeIntegrityReportWhenEnabled() = runTest {
        if (!isRuntimeEnabled()) return@runTest

        val startedAt = System.currentTimeMillis()
        val htmlProbe = runCatching { probeYtnHtmlFallback() }.getOrElse { error ->
            YtnHtmlFallbackProbe(
                targetUrl = HtmlCrawlingTarget.defaultTargets.first { target -> target.press == NewsPress.YTN }.url,
                htmlLength = 0,
                hrefCount = 0,
                ytnViewHrefCount = 0,
                parserArticleCount = 0,
                ytnPolicyPassCount = 0,
                ytnTypePassCount = 0,
                firstHrefs = emptyList(),
                firstYtnCandidates = emptyList(),
                firstParserUrls = emptyList(),
                error = error.message
            )
        }
        val repository = MultiSourceNewsRepository()
        val result = repository.searchNews(setOf(NewsPress.YTN), emptyList())
        val uiStateYtnCount = ytnUiStateCountFor(result)
        val snapshot = YtnPipelineDebugStore.latestSnapshot()
        val cachedOriginalUrlMatches = result.articles
            .filter { article -> article.press == NewsPress.YTN }
            .all { article -> repository.getNewsArticleById(article.id)?.originalUrl == article.originalUrl }
        val markdown = buildRuntimeReport(
            startedAt = startedAt,
            result = result,
            snapshot = snapshot,
            htmlProbe = htmlProbe,
            uiStateYtnCount = uiStateYtnCount,
            cachedOriginalUrlMatches = cachedOriginalUrlMatches
        )

        val reportFile = File(reportsDirectory(), "news_integrity_report.md")
        reportFile.parentFile?.mkdirs()
        reportFile.writeText(markdown, Charsets.UTF_8)

        val ytnArticles = result.articles.filter { article -> article.press == NewsPress.YTN }
        assertTrue(reportFile.readText(Charsets.UTF_8).contains("Report type: runtime"))
        assertTrue(
            "YTN runtime fetch produced 0 display cards; see reports/news_integrity_report.md",
            ytnArticles.isNotEmpty()
        )
        assertTrue(ytnArticles.all { article -> PressDomainMatcher.isUrlAllowedForPress(article.originalUrl, NewsPress.YTN) })
        assertTrue(ytnArticles.all { article -> ArticleUrlPolicy.isValidArticleUrl(article.press, article.originalUrl) })
        assertFalse(ytnArticles.any { article -> article.originalUrl.contains("news.google.com") })
        assertTrue(uiStateYtnCount == ytnArticles.size)
        assertTrue(cachedOriginalUrlMatches)
    }

    private fun buildRuntimeReport(
        startedAt: Long,
        result: NewsFetchResult,
        snapshot: YtnPipelineDebugSnapshot,
        htmlProbe: YtnHtmlFallbackProbe,
        uiStateYtnCount: Int,
        cachedOriginalUrlMatches: Boolean
    ): String {
        val ytnArticles = result.articles.filter { article -> article.press == NewsPress.YTN }
        val rssStatus = result.sourceStatuses.firstOrNull { status ->
            status.press == NewsPress.YTN && status.sourceType == NewsSourceType.RSS
        }
        val htmlStatus = result.sourceStatuses.firstOrNull { status ->
            status.press == NewsPress.YTN && status.sourceType == NewsSourceType.HTML_CRAWLING
        }
        val notFoundStatus = result.sourceStatuses.firstOrNull { status ->
            status.press == NewsPress.YTN && status.sourceType == NewsSourceType.NOT_FOUND
        }
        val invalidArticles = ytnArticles.filterNot { article ->
            PressDomainMatcher.isUrlAllowedForPress(article.originalUrl, NewsPress.YTN) &&
                ArticleUrlPolicy.isValidArticleUrl(article.press, article.originalUrl) &&
                !article.originalUrl.contains("news.google.com")
        }
        val duplicatedUrlCount = ytnArticles.size - ytnArticles.map { article ->
            UrlNormalizer.normalize(article.originalUrl)
        }.toSet().size
        val status = if (ytnArticles.isNotEmpty() && invalidArticles.isEmpty()) "PASS" else "FAIL"
        val runtimeUnavailable = ytnArticles.isEmpty() && snapshot.rawItemCount == 0 && notFoundStatus != null

        return buildString {
            appendLine("# News Integrity Report")
            appendLine()
            appendLine("- Report type: runtime")
            appendLine("- Generated at epochMillis: $startedAt")
            appendLine("- Runtime selected presses: YTN")
            appendLine("- Runtime status: $status")
            appendLine("- YTN runtime fetch unavailable: ${yesNo(runtimeUnavailable)}")
            appendLine("- YTN final display card count: ${ytnArticles.size}")
            appendLine("- YTN MainViewModel uiState article count: $uiStateYtnCount")
            appendLine("- YTN card-detail originalUrl cache match: ${yesNo(cachedOriginalUrlMatches)}")
            appendLine("- YTN duplicate normalized originalUrl count: $duplicatedUrlCount")
            appendLine("- YTN invalid displayed article count: ${invalidArticles.size}")
            appendLine("- Remaining WARNING/FAIL: warnings=0, failures=${if (status == "PASS") 0 else 1}")
            appendLine()
            appendLine("## HTML Fallback Probe")
            appendLine("- targetUrl: ${htmlProbe.targetUrl}")
            appendLine("- htmlLength: ${htmlProbe.htmlLength}")
            appendLine("- hrefCount: ${htmlProbe.hrefCount}")
            appendLine("- ytnViewHrefCount: ${htmlProbe.ytnViewHrefCount}")
            appendLine("- parserArticleCount: ${htmlProbe.parserArticleCount}")
            appendLine("- ytnPolicyPassCount: ${htmlProbe.ytnPolicyPassCount}")
            appendLine("- ytnTypePassCount: ${htmlProbe.ytnTypePassCount}")
            appendLine("- error: ${htmlProbe.error.orEmpty()}")
            appendLine("- first hrefs:")
            htmlProbe.firstHrefs.ifEmpty { listOf("none") }.forEach { href ->
                appendLine("  - ${href.markdownCell()}")
            }
            appendLine("- first YTN candidates:")
            htmlProbe.firstYtnCandidates.ifEmpty { listOf("none") }.forEach { candidate ->
                appendLine("  - ${candidate.markdownCell()}")
            }
            appendLine("- first parser urls:")
            htmlProbe.firstParserUrls.ifEmpty { listOf("none") }.forEach { url ->
                appendLine("  - ${url.markdownCell()}")
            }
            appendLine()
            appendLine("## Source Statuses")
            appendStatus("RSS", rssStatus)
            appendStatus("HTML crawling", htmlStatus)
            appendStatus("NOT_FOUND", notFoundStatus)
            appendLine()
            appendLine("## Runtime Display Articles")
            if (ytnArticles.isEmpty()) {
                appendLine("- none")
            } else {
                appendLine("| # | title | originalUrl | domain valid | policy valid | google redirect stored | publishedAt | publishedAtSource | sourceType | body paragraphs | body length |")
                appendLine("|---:|---|---|---:|---:|---:|---:|---|---|---:|---:|")
                ytnArticles.forEachIndexed { index, article ->
                    appendArticleRow(index + 1, article)
                }
            }
            appendLine()
            appendLine("## Runtime Checks")
            appendLine("- MBC/JTBC/KBS untouched by this runtime check: yes")
            appendLine("- RSS is attempted before HTML crawling: ${yesNo(rssStatus != null)}")
            appendLine("- HTML crawling used after RSS failure: ${yesNo(rssStatus?.success == false && htmlStatus != null)}")
            appendLine("- originalUrl is real publisher URL, not Google redirect: ${yesNo(ytnArticles.none { it.originalUrl.contains("news.google.com") })}")
            appendLine("- originalUrl allowed domain: ${yesNo(ytnArticles.all { PressDomainMatcher.isUrlAllowedForPress(it.originalUrl, NewsPress.YTN) })}")
            appendLine("- article URL policy valid: ${yesNo(ytnArticles.all { ArticleUrlPolicy.isValidArticleUrl(it.press, it.originalUrl) })}")
            appendLine("- card title contains title only: ${yesNo(ytnArticles.all { it.title.isNotBlank() && !it.title.contains('\n') })}")
            appendLine("- card body preview available: ${yesNo(ytnArticles.all { it.content.isNotBlank() || it.summary.isNotBlank() })}")
            appendLine("- detail body paragraphs available: ${yesNo(ytnArticles.all { it.bodyParagraphs.isNotEmpty() })}")
            appendLine("- card and detail originalUrl are same cached article: ${yesNo(cachedOriginalUrlMatches)}")
            appendLine("- MainViewModel uiState count matches repository result: ${yesNo(uiStateYtnCount == ytnArticles.size)}")
            appendLine("- publishedAt source is not collection time: ${yesNo(ytnArticles.all { it.publishedAtSource != null && it.publishedAtSource != "collection_time" })}")
            appendLine("- non-article page excluded before display: ${yesNo(invalidArticles.isEmpty())}")
            appendLine("- duplicate articles removed: ${yesNo(duplicatedUrlCount == 0)}")
            append(snapshot.toMarkdownSection())
        }
    }

    private fun StringBuilder.appendStatus(
        label: String,
        status: NewsSourceStatus?
    ) {
        if (status == null) {
            appendLine("- $label: not recorded")
        } else {
            appendLine(
                "- $label: success=${status.success} articleCount=${status.articleCount} " +
                    "message=${status.message.orEmpty()}"
            )
        }
    }

    private fun StringBuilder.appendArticleRow(
        index: Int,
        article: NewsArticle
    ) {
        appendLine(
            "| $index | ${article.title.markdownCell()} | ${article.originalUrl.markdownCell()} | " +
                "${yesNo(PressDomainMatcher.isUrlAllowedForPress(article.originalUrl, NewsPress.YTN))} | " +
                "${yesNo(ArticleUrlPolicy.isValidArticleUrl(article.press, article.originalUrl))} | " +
                "${yesNo(article.originalUrl.contains("news.google.com"))} | " +
                "${article.publishedAt?.toString().orEmpty()} | ${article.publishedAtSource.orEmpty()} | " +
                "${article.sourceType} | ${article.bodyParagraphs.size} | ${article.content.length} |"
        )
    }

    private fun isRuntimeEnabled(): Boolean =
        System.getProperty("RUN_YTN_RUNTIME_TEST")?.equals("true", ignoreCase = true) == true ||
            System.getenv("RUN_YTN_RUNTIME_TEST")?.equals("true", ignoreCase = true) == true

    private fun ytnUiStateCountFor(result: NewsFetchResult): Int {
        val viewModel = MainViewModel(
            newsRepositoryStatusProvider = null,
            getFilteredNewsUseCase = GetFilteredNewsUseCase(FixedNewsRepository(result))
        )
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        return viewModel.uiState.value.articles.count { article -> article.press == NewsPress.YTN }
    }

    private suspend fun probeYtnHtmlFallback(): YtnHtmlFallbackProbe {
        val targetUrl = HtmlCrawlingTarget.defaultTargets.first { target -> target.press == NewsPress.YTN }.url
        val fetcher = OkHttpHtmlPageFetcher(
            OkHttpClient.Builder()
                .connectTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NewsFetchConfig.HTML_CRAWLING_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        )
        val html = fetcher.fetch(targetUrl)
        val document = Jsoup.parse(html, targetUrl)
        val hrefs = document.select("a[href]")
            .map { element -> element.absUrl("href").ifBlank { element.attr("href") } }
        val ytnCandidates = document.select("a[href*=news_view.php]")
            .map { element -> element to (element.absUrl("href").ifBlank { element.attr("href") }) }
            .filter { (_, href) -> href.startsWith("http://") || href.startsWith("https://") }
            .map { (element, href) ->
                val title = ArticleTitleCleaner.cleanTitle(element.ownText().ifBlank { element.text() })
                val canonicalUrl = canonicalYtnUrlForProbe(href)
                val bodyText = element.parent()?.text().orEmpty()
                val articleType = ArticleTypeClassifier.classify(
                    title = title,
                    originalUrl = canonicalUrl,
                    bodyText = bodyText,
                    metaText = title
                )
                YtnCandidateProbe(
                    href = href,
                    title = title,
                    canonicalUrl = canonicalUrl,
                    policyPass = ArticleUrlPolicy.isValidArticleUrl(NewsPress.YTN, canonicalUrl),
                    typePass = ArticleTypeClassifier.isNewsArticle(articleType),
                    typeName = articleType.name
                )
            }
        val parsedArticles = HtmlCrawlerParser.parse(
            html = html,
            baseUrl = targetUrl,
            press = NewsPress.YTN,
            enforceAllowedDomain = true,
            maxArticles = NewsFetchConfig.MAX_ARTICLES_PER_PRESS * 6
        )
        return YtnHtmlFallbackProbe(
            targetUrl = targetUrl,
            htmlLength = html.length,
            hrefCount = hrefs.size,
            ytnViewHrefCount = hrefs.count { href ->
                href.contains("news_view.php") && (href.startsWith("http://") || href.startsWith("https://"))
            },
            parserArticleCount = parsedArticles.size,
            ytnPolicyPassCount = ytnCandidates.count { candidate -> candidate.policyPass },
            ytnTypePassCount = ytnCandidates.count { candidate -> candidate.typePass },
            firstHrefs = hrefs.take(12),
            firstYtnCandidates = ytnCandidates.take(8).map { candidate ->
                "title=${candidate.title} canonical=${candidate.canonicalUrl} policy=${candidate.policyPass} " +
                    "type=${candidate.typeName} typePass=${candidate.typePass} href=${candidate.href}"
            },
            firstParserUrls = parsedArticles.map { article -> article.originalUrl }.take(12),
            error = null
        )
    }

    private fun canonicalYtnUrlForProbe(rawUrl: String): String {
        val normalized = rawUrl.replace("&amp;", "&")
        val key = Regex("[?&]key=([^&]+)").find(normalized)?.groupValues?.getOrNull(1)
        val section = Regex("[?&]s_mcd=([^&]+)").find(normalized)?.groupValues?.getOrNull(1)
        return if (key.isNullOrBlank() || section.isNullOrBlank()) {
            rawUrl
        } else {
            "https://www.ytn.co.kr/_ln/${section}_$key"
        }
    }

    private fun reportsDirectory(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val root = if (userDir.name == "app") userDir.parentFile ?: userDir else userDir
        return File(root, "reports")
    }

    private fun String.markdownCell(): String =
        replace("|", "\\|")
            .replace("\n", " ")
            .replace("\r", " ")
            .take(180)

    private fun yesNo(value: Boolean): String =
        if (value) "yes" else "no"

    private data class YtnHtmlFallbackProbe(
        val targetUrl: String,
        val htmlLength: Int,
        val hrefCount: Int,
        val ytnViewHrefCount: Int,
        val parserArticleCount: Int,
        val ytnPolicyPassCount: Int,
        val ytnTypePassCount: Int,
        val firstHrefs: List<String>,
        val firstYtnCandidates: List<String>,
        val firstParserUrls: List<String>,
        val error: String?
    )

    private data class YtnCandidateProbe(
        val href: String,
        val title: String,
        val canonicalUrl: String,
        val policyPass: Boolean,
        val typePass: Boolean,
        val typeName: String
    )

    private class FixedNewsRepository(
        private val result: NewsFetchResult
    ) : NewsRepository {
        override suspend fun getLatestNews(): NewsFetchResult = result

        override suspend fun searchNews(
            selectedPresses: Set<NewsPress>,
            keywords: List<String>
        ): NewsFetchResult = result

        override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
            result.articles.firstOrNull { article -> article.id == articleId }
    }
}
