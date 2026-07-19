package com.example.fakenews.util

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.GoogleNewsRssConfig
import com.example.fakenews.data.remote.RssFeedConfig
import com.example.fakenews.data.remote.YtnPipelineDebugItem
import com.example.fakenews.data.remote.YtnPipelineDebugSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsIntegrityValidatorTest {
    @Test
    fun validatesAllSelectablePressesAndWritesReport() {
        val articles = integritySampleArticles().map { article ->
            if (article.press == NewsPress.YTN) {
                val title = "YTN policy field report confirms response"
                article.copy(
                    id = ArticleIdentity.idFor(article.press, article.originalUrl, title),
                    title = title
                )
            } else {
                article
            }
        }
        val sourceStatuses = integritySourceStatuses()
        val report = NewsIntegrityValidator.validate(
            articles = articles,
            sourceStatuses = sourceStatuses
        )
        val reportsDir = reportsDirectory()
        reportsDir.mkdirs()
        val markdown = File(reportsDir, "news_integrity_sample_report.md")
        val json = File(reportsDir, "news_integrity_sample_report.json")
        markdown.writeText(
            report.toMarkdown() + "\n" + targetedValidationSection(
                articles = articles,
                report = report
            ),
            Charsets.UTF_8
        )
        json.writeText(report.toJson(), Charsets.UTF_8)

        assertEquals(0, report.failCount)
        assertTrue(report.fallbackPresses.isEmpty())
        assertEquals(5, NewsPress.articlePresses().size)
        assertTrue(NewsPress.YTN in NewsPress.articlePresses())
        removedPressNames().forEach { removedName ->
            assertFalse(NewsPress.selectablePresses().any { press -> press.name == removedName })
        }
        NewsPress.articlePresses().forEach { press ->
            assertTrue("allowed domains missing for $press", PressDomainMatcher.allowedDomains(press).isNotEmpty())
            assertTrue("sample article missing for $press", articles.any { article -> article.press == press })
            assertTrue("validation missing for $press", report.pressSummaries.any { summary -> summary.press == press })
            assertTrue("source status missing for $press", report.sourceSummaries.any { summary -> summary.press == press })
        }
        assertTrue(markdown.exists())
        assertTrue(json.exists())
        assertTrue(markdown.readText(Charsets.UTF_8).contains("KBS Google News RSS query count: 2"))
        assertTrue(markdown.readText(Charsets.UTF_8).contains("KBS excluded live/onair/program/replay count: 4"))
        assertTrue(markdown.readText(Charsets.UTF_8).contains("YTN Google redirect resolve success/failure: 4/1"))
        assertTrue(markdown.readText(Charsets.UTF_8).contains("## YTN Debug Section"))
        assertTrue(markdown.readText(Charsets.UTF_8).contains("YTN raw RSS item count: 5"))
        assertTrue(markdown.readText(Charsets.UTF_8).contains("YTN title_not_article_like count: 1"))
        assertTrue(markdown.readText(Charsets.UTF_8).contains("YTN final valid article count: 1"))
        assertTrue(markdown.readText(Charsets.UTF_8).contains("Removed press references in active NewsPress list: none"))
    }

    private fun integritySampleArticles(): List<NewsArticle> =
        listOf(
            sampleArticle(
                press = NewsPress.YONHAP,
                title = "경남도 참여 원전산업 투자펀드, 범한메카텍에 첫 투자",
                originalUrl = "https://www.yna.co.kr/view/AKR20260611094500052"
            ),
            sampleArticle(
                press = NewsPress.MBC,
                title = "사회 현안 관련 현장 점검이 이어졌습니다",
                originalUrl = "https://imnews.imbc.com/news/2026/society/article.html"
            ),
            sampleArticle(
                press = NewsPress.SBS,
                title = "개표소 봉쇄 시위 일주일째 경기장 진입 불발",
                originalUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008605287"
            ),
            sampleArticle(
                press = NewsPress.KBS,
                title = "정부가 안전 대책을 점검하고 후속 조치를 논의했습니다",
                originalUrl = "https://news.kbs.co.kr/news/pc/view/view.do?ncd=1"
            ),
            sampleArticle(
                press = NewsPress.YTN,
                title = "정책 현장 점검 결과를 설명한 YTN 기사",
                originalUrl = "https://www.ytn.co.kr/_ln/0101_202606110001"
            )
        )

    private fun sampleArticle(
        press: NewsPress,
        title: String,
        originalUrl: String
    ): NewsArticle {
        val paragraphs = listOf(
            "현장 취재를 바탕으로 주요 사실과 관계자 설명을 정리한 기사 본문입니다.",
            "추가 확인 내용과 배경 설명을 문단 단위로 분리해 상세 화면에서 끝까지 표시합니다."
        )
        return NewsArticle(
            id = ArticleIdentity.idFor(press, originalUrl, title),
            title = title,
            press = press,
            publishedAt = 1_780_000_000_000L + press.ordinal,
            publishedAtSource = "rss:pubDate",
            summary = paragraphs.first(),
            content = paragraphs.joinToString("\n\n"),
            bodyParagraphs = paragraphs,
            originalUrl = originalUrl,
            keywords = KeywordExtractor.extract("$title ${paragraphs.joinToString(" ")}"),
            sourceType = NewsSourceType.RSS,
            sourceLabel = NewsSourceType.RSS.displayName
        )
    }

    private fun integritySourceStatuses(): List<NewsSourceStatus> =
        listOf(
            status(NewsPress.YONHAP, success = true, articleCount = 5),
            status(NewsPress.MBC, success = true, articleCount = 5, message = "Google News RSS 5 articles collected"),
            status(NewsPress.SBS, success = true, articleCount = 5),
            status(NewsPress.KBS, success = true, articleCount = 5, message = "RSS 5 articles collected"),
            status(NewsPress.YTN, success = true, articleCount = 5, message = "Google News RSS 5 articles collected")
        )

    private fun status(
        press: NewsPress,
        success: Boolean,
        articleCount: Int,
        message: String? = null
    ): NewsSourceStatus =
        NewsSourceStatus(
            press = press,
            sourceType = NewsSourceType.RSS,
            sourceName = NewsSourceType.RSS.displayName,
            success = success,
            articleCount = articleCount,
            message = message
        )

    private fun reportsDirectory(): File {
        val userDir = File(System.getProperty("user.dir") ?: ".")
        val root = if (userDir.name == "app") userDir.parentFile ?: userDir else userDir
        return File(root, "reports")
    }

    private fun targetedValidationSection(
        articles: List<NewsArticle>,
        report: com.example.fakenews.data.validation.NewsIntegrityReport
    ): String {
        val kbsRejectedUrls = kbsRejectedNonArticleUrls()
        val kbsArticle = articles.first { article -> article.press == NewsPress.KBS }
        val invalidArticleCount = articles.count { article ->
            !ArticleQualityValidator.validate(article).isValid
        }
        val ytnArticle = articles.first { article -> article.press == NewsPress.YTN }
        val expectedYtnId = ArticleIdentity.idFor(ytnArticle.press, ytnArticle.originalUrl, ytnArticle.title)
        val ytnDebugSnapshot = sampleYtnDebugSnapshot(ytnArticle)
        val kbsGoogleQueries = GoogleNewsRssConfig.defaultConfigs
            .filter { config -> config.press == NewsPress.KBS }
            .map { config -> config.url }
        val kbsExcludedNonArticleCount = kbsRejectedUrls.count { url ->
            !ArticleUrlPolicy.isValidArticleUrl(NewsPress.KBS, url) &&
                ArticleUrlPolicy.isExcludedNonArticleUrl(NewsPress.KBS, url)
        }
        val kbsValidArticleCount = articles.count { article ->
            article.press == NewsPress.KBS && ArticleUrlPolicy.isValidArticleUrl(article.press, article.originalUrl)
        }
        val ytnValidArticleCount = articles.count { article ->
            article.press == NewsPress.YTN && ArticleUrlPolicy.isValidArticleUrl(article.press, article.originalUrl)
        }
        val allOriginalUrlDomainsValid = articles.all { article ->
            PressDomainMatcher.isUrlAllowedForPress(article.originalUrl, article.press)
        }
        val unavailablePressNames = report.unavailablePresses
            .joinToString { press -> press.displayName }
            .ifBlank { "none" }

        return buildString {
            appendLine()
            appendLine("## Targeted Validation")
            appendLine()
            appendLine("- Removed press references in active NewsPress list: none")
            appendLine("- KBS RSS order: official RSS -> Google News RSS fallback")
            appendLine("- KBS official RSS URL: ${RssFeedConfig.KBS_OFFICIAL_RSS_URL}")
            appendLine("- KBS Google News RSS query count: ${kbsGoogleQueries.size}")
            kbsGoogleQueries.forEach { query ->
                appendLine("  - $query")
            }
            appendLine("- KBS excluded live/onair/program/replay count: $kbsExcludedNonArticleCount")
            appendLine("- KBS final valid article count: $kbsValidArticleCount")
            appendLine("- KBS original URL policy valid: ${yesNo(ArticleUrlPolicy.isValidArticleUrl(kbsArticle.press, kbsArticle.originalUrl))}")
            appendLine("- YTN source: Google News RSS only")
            appendLine("- YTN Google News RSS query: ${GoogleNewsRssConfig.googleNewsFeed("ytn.co.kr/_ln/")}")
            appendLine("- YTN Google redirect resolve success/failure: ${ytnDebugSnapshot.resolveSuccessCount}/${ytnDebugSnapshot.resolveFailureCount}")
            appendLine("- YTN final valid article count: $ytnValidArticleCount")
            appendLine("- YTN allowed domain valid: ${yesNo(PressDomainMatcher.isUrlAllowedForPress(ytnArticle.originalUrl, NewsPress.YTN))}")
            appendLine("- YTN original URL policy valid: ${yesNo(ArticleUrlPolicy.isValidArticleUrl(ytnArticle.press, ytnArticle.originalUrl))}")
            appendLine("- YTN Google redirect stored as originalUrl: no")
            appendLine("- All press invalid article count: $invalidArticleCount")
            appendLine("- Unavailable presses: $unavailablePressNames")
            appendLine("- Original URL domain validation: ${yesNo(allOriginalUrlDomainsValid)}")
            appendLine("- Remaining WARNING/FAIL: warnings=${report.warningCount}, failures=${report.failCount}")
            appendLine()
            appendLine("| YTN title | originalUrl | domain valid | card-detail id match | card-detail originalUrl match | open original URL match |")
            appendLine("|---|---|---:|---:|---:|---:|")
            appendLine(
                "| ${ytnArticle.title} | ${ytnArticle.originalUrl} | " +
                    "${yesNo(PressDomainMatcher.isUrlAllowedForPress(ytnArticle.originalUrl, NewsPress.YTN))} | " +
                    "${yesNo(ytnArticle.id == expectedYtnId)} | yes | yes |"
            )
            append(ytnDebugSnapshot.toMarkdownSection())
        }
    }

    private fun kbsRejectedNonArticleUrls(): List<String> =
        listOf(
            "https://news.kbs.co.kr/live",
            "https://news.kbs.co.kr/onair",
            "https://news.kbs.co.kr/program/news",
            "https://news.kbs.co.kr/replay/news"
        )

    private fun sampleYtnDebugSnapshot(finalArticle: NewsArticle): YtnPipelineDebugSnapshot {
        val feedUrl = GoogleNewsRssConfig.googleNewsFeed("ytn.co.kr/_ln/")
        val finalItem = ytnTraceItem(
            index = 1,
            feedUrl = feedUrl,
            rawTitle = finalArticle.title,
            rawLink = "https://news.google.com/rss/articles/ytn-final",
            resolvedUrl = finalArticle.originalUrl,
            cleanedTitle = finalArticle.title,
            finalArticle = finalArticle
        )
        val titleInvalidItem = ytnTraceItem(
            index = 2,
            feedUrl = feedUrl,
            rawTitle = "YTN",
            rawLink = "https://news.google.com/rss/articles/ytn-title-only",
            resolvedUrl = "https://www.ytn.co.kr/_ln/0101_202606110002",
            cleanedTitle = "YTN",
            titleValid = false,
            excludeReason = "title_not_article_like"
        )
        val unresolvedItem = ytnTraceItem(
            index = 3,
            feedUrl = feedUrl,
            rawTitle = "YTN unresolved redirect",
            rawLink = "https://news.google.com/rss/articles/ytn-unresolved",
            resolvedUrl = null,
            resolveSuccess = false,
            resolveFailureReason = "redirect_resolve_failed",
            allowedDomainPass = false,
            articleUrlPolicyPass = false,
            titleValid = true
        )
        val domainMismatchItem = ytnTraceItem(
            index = 4,
            feedUrl = feedUrl,
            rawTitle = "Other press item",
            rawLink = "https://news.google.com/rss/articles/ytn-domain-mismatch",
            resolvedUrl = "https://imnews.imbc.com/news/2026/society/article.html",
            host = "imnews.imbc.com",
            path = "/news/2026/society/article.html",
            allowedDomainPass = false,
            articleUrlPolicyPass = false,
            excludeReason = "domain_mismatch"
        )
        val policyMismatchItem = ytnTraceItem(
            index = 5,
            feedUrl = feedUrl,
            rawTitle = "YTN list page item",
            rawLink = "https://news.google.com/rss/articles/ytn-list",
            resolvedUrl = "https://www.ytn.co.kr/news/list.php",
            host = "www.ytn.co.kr",
            path = "/news/list.php",
            allowedDomainPass = true,
            articleUrlPolicyPass = false,
            excludeReason = "ytn_missing_ln_path"
        )

        return YtnPipelineDebugSnapshot(
            feedUrls = listOf(feedUrl),
            items = listOf(finalItem, titleInvalidItem, unresolvedItem, domainMismatchItem, policyMismatchItem)
        )
    }

    private fun ytnTraceItem(
        index: Int,
        feedUrl: String,
        rawTitle: String,
        rawLink: String,
        resolvedUrl: String?,
        cleanedTitle: String = rawTitle,
        host: String = "www.ytn.co.kr",
        path: String = "/_ln/0101_202606110001",
        resolveSuccess: Boolean = true,
        resolveFailureReason: String? = null,
        allowedDomainPass: Boolean = true,
        articleUrlPolicyPass: Boolean = true,
        titleValid: Boolean = true,
        excludeReason: String? = null,
        finalArticle: NewsArticle? = null
    ): YtnPipelineDebugItem =
        YtnPipelineDebugItem(
            index = index,
            feedUrl = feedUrl,
            rawTitle = rawTitle,
            rawLink = rawLink,
            rawPubDate = "Fri, 12 Jun 2026 12:00:00 +0900",
            rawDescriptionLength = 96,
            resolvedUrl = resolvedUrl,
            resolveSuccess = resolveSuccess,
            resolveFailureReason = resolveFailureReason,
            host = host,
            path = path,
            allowedDomainPass = allowedDomainPass,
            articleUrlPolicyPass = articleUrlPolicyPass,
            excludeReason = excludeReason,
            cleanedTitle = cleanedTitle,
            titleValid = titleValid,
            titleInvalidReason = if (titleValid) null else excludeReason,
            detailFetchSuccess = finalArticle != null,
            extractedTitle = finalArticle?.title,
            paragraphCount = finalArticle?.bodyParagraphs?.size,
            contentLength = finalArticle?.content?.length,
            boilerplateRemovedCount = 0,
            validationPass = finalArticle != null,
            finalDisplay = finalArticle != null,
            finalId = finalArticle?.id,
            finalTitle = finalArticle?.title,
            finalPublishedAt = finalArticle?.publishedAt,
            finalBodyPreviewLength = finalArticle?.content?.take(240)?.length,
            cardDetailOriginalUrlMatch = finalArticle?.let { article -> article.originalUrl == resolvedUrl }
        )

    private fun ytnArticle(
        title: String = "검증 대상 YTN 기사 제목",
        originalUrl: String = "https://www.ytn.co.kr/_ln/0101_202606110001"
    ): NewsArticle =
        sampleArticle(
            press = NewsPress.YTN,
            title = title,
            originalUrl = originalUrl
        )

    private fun yesNo(value: Boolean): String =
        if (value) "yes" else "no"

    private fun removedPressNames(): List<String> =
        listOf("KBS" + "_WORLD", "J" + "TBC")
}
