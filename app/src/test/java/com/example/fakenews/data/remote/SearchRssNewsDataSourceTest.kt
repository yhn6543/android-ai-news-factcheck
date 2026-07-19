package com.example.fakenews.data.remote

import com.example.fakenews.data.model.NewsPress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRssNewsDataSourceTest {
    @Test
    fun requestedMbcButSbsDomainIsExcluded() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "MBC mentioned in title",
                    link = "https://news.sbs.co.kr/news/endPage.do?news_id=1"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.MBC, keywords = listOf("MBC"))

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun requestedSbsAndSbsDomainIsIncluded() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "SBS article title",
                    link = "https://news.sbs.co.kr/news/endPage.do?news_id=1"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.SBS, keywords = emptyList())

        assertEquals(1, result.articles.size)
        assertEquals(NewsPress.SBS, result.articles.single().press)
    }

    @Test
    fun requestedYtnAndYtnDomainIsIncluded() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "YTN article title",
                    link = "https://www.ytn.co.kr/_ln/0101_202606110001"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, keywords = emptyList())

        assertEquals(1, result.articles.size)
        assertEquals(NewsPress.YTN, result.articles.single().press)
    }

    @Test
    fun requestedYtnButRegularKbsDomainIsExcluded() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "YTN wording on regular KBS",
                    link = "https://news.kbs.co.kr/news/pc/view/view.do?ncd=1"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, keywords = emptyList())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun requestedYtnAndMobileLnPathIsIncluded() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "YTN mobile article title",
                    link = "https://m.ytn.co.kr/_ln/0101_202606110001"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, keywords = emptyList())

        assertEquals(1, result.articles.size)
        assertEquals(NewsPress.YTN, result.articles.single().press)
    }

    @Test
    fun requestedYtnButLegacyNewsViewPathIsExcluded() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "YTN legacy view title",
                    link = "https://m.ytn.co.kr/news_view.php?s_mcd=0101&key=202606110001"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, keywords = emptyList())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun requestedYtnButOtherDomainIsExcluded() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "YTN wording on other domain",
                    link = "https://example.com/news/1"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.YTN, keywords = emptyList())

        assertTrue(result.articles.isEmpty())
    }

    @Test
    fun titleContainingMbcDoesNotCreateMbcArticleWithoutMbcDomain() = runTest {
        val dataSource = SearchRssNewsDataSource(
            fetcher = FakeRssFeedFetcher(
                sampleRss(
                    title = "MBC exclusive wording",
                    link = "https://example.com/news/1"
                )
            )
        )

        val result = dataSource.fetch(NewsPress.MBC, keywords = listOf("MBC"))

        assertTrue(result.articles.isEmpty())
    }

    private class FakeRssFeedFetcher(
        private val xml: String
    ) : RssFeedFetcher {
        override suspend fun fetch(url: String): String = xml
    }

    private fun sampleRss(
        title: String,
        link: String
    ): String =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0">
          <channel>
            <item>
              <title>$title</title>
              <link>$link</link>
              <pubDate>Wed, 01 Jan 2025 00:00:00 GMT</pubDate>
              <description>Search RSS summary</description>
            </item>
          </channel>
        </rss>
        """.trimIndent()
}
