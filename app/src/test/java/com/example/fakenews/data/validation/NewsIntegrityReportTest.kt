package com.example.fakenews.data.validation

import com.example.fakenews.data.remote.YtnPipelineDebugItem
import com.example.fakenews.data.remote.YtnPipelineDebugSnapshot
import com.example.fakenews.data.remote.YtnRepositoryDebugTrace
import com.example.fakenews.data.remote.YtnUiStateDebugTrace
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsIntegrityReportTest {
    @Test
    fun ytnDebugSectionSeparatesExcludedItemsFromFinalDisplayItems() {
        val finalUrl = "https://www.ytn.co.kr/_ln/0101_202606120001"
        val snapshot = YtnPipelineDebugSnapshot(
            feedUrls = listOf("https://news.google.com/rss/search?q=site:ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko"),
            items = listOf(
                item(index = 1, rawTitle = "YTN final title", resolvedUrl = finalUrl, final = true),
                item(index = 2, rawTitle = "YTN", resolvedUrl = finalUrl, reason = "title_not_article_like"),
                item(index = 3, rawTitle = "YTN list", resolvedUrl = "https://www.ytn.co.kr/news/list.php", reason = "ytn_missing_ln_path")
            )
        )

        val markdown = snapshot.toMarkdownSection()
        val excludedSection = markdown.substringAfter("### Excluded Items").substringBefore("### Final Display Items")

        assertTrue(markdown.contains("YTN final display article count: 1"))
        assertTrue(markdown.contains("https://www.ytn.co.kr/news/list.php"))
        assertFalse(excludedSection.contains(finalUrl))
    }

    @Test
    fun ytnDebugSectionIncludesRepositoryAndUiStateCounts() {
        val snapshot = YtnPipelineDebugSnapshot(
            feedUrls = listOf("https://news.google.com/rss/search?q=site:ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko"),
            items = listOf(item(index = 1, rawTitle = "YTN final title", resolvedUrl = "https://www.ytn.co.kr/_ln/0101_202606120001", final = true)),
            repositoryTrace = YtnRepositoryDebugTrace(
                collectedArticleCount = 3,
                displayableArticleCount = 3,
                afterPressFilterCount = 3,
                afterDedupCount = 2,
                afterKeywordCount = 2,
                finalRepositoryArticleCount = 1,
                failedPress = false,
                keywords = emptyList()
            ),
            uiStateTrace = YtnUiStateDebugTrace(
                selected = true,
                uiArticleCount = 1,
                failedPress = false,
                keywords = emptyList(),
                emptyMessage = null,
                infoMessage = null
            )
        )

        val markdown = snapshot.toMarkdownSection()

        assertTrue(markdown.contains("### Runtime Stage Counts"))
        assertTrue(markdown.contains("repository collected=3 displayable=3 afterPress=3 afterDedup=2 afterKeyword=2 final=1"))
        assertTrue(markdown.contains("uiState selected=true articleCount=1 failedPress=false"))
    }

    private fun item(
        index: Int,
        rawTitle: String,
        resolvedUrl: String,
        final: Boolean = false,
        reason: String? = null
    ): YtnPipelineDebugItem =
        YtnPipelineDebugItem(
            index = index,
            feedUrl = "feed",
            rawTitle = rawTitle,
            rawLink = "https://news.google.com/rss/articles/$index",
            rawPubDate = "Fri, 12 Jun 2026 12:00:00 +0900",
            rawDescriptionLength = 10,
            resolvedUrl = resolvedUrl,
            resolveSuccess = true,
            host = "www.ytn.co.kr",
            path = if (resolvedUrl.contains("/_ln/")) "/_ln/0101_202606120001" else "/news/list.php",
            allowedDomainPass = true,
            articleUrlPolicyPass = resolvedUrl.contains("/_ln/"),
            excludeReason = reason,
            cleanedTitle = rawTitle,
            titleValid = reason != "title_not_article_like",
            titleInvalidReason = reason.takeIf { it == "title_not_article_like" },
            finalDisplay = final,
            finalId = if (final) "final-id" else null,
            finalTitle = if (final) rawTitle else null,
            finalPublishedAt = if (final) 1L else null,
            finalBodyPreviewLength = if (final) 120 else null,
            cardDetailOriginalUrlMatch = if (final) true else null
        )
}
