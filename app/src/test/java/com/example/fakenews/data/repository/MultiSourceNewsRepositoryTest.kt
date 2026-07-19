package com.example.fakenews.data.repository

import com.example.fakenews.data.model.CollectionMode
import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.NewsFetchConfig
import com.example.fakenews.data.remote.NewsDataSource
import com.example.fakenews.util.ArticleQualityValidator
import com.example.fakenews.util.ArticleIdentity
import com.example.fakenews.util.NewsDeduplicator
import com.example.fakenews.util.NewsFilter
import com.example.fakenews.util.UrlNormalizer
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSourceNewsRepositoryTest {
    @Test
    fun parallelCollectionMatchesSequentialReferenceResult() = runTest {
        val rss = RecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            articles = listOf(
                article(NewsPress.MBC, "mbc-rss", publishedAt = 30),
                article(NewsPress.SBS, "sbs-rss", publishedAt = 20)
            ),
            successfulPresses = setOf(NewsPress.MBC, NewsPress.SBS)
        )
        val html = RecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            articles = listOf(article(NewsPress.KBS, "kbs-html", publishedAt = 10)),
            successfulPresses = setOf(NewsPress.KBS)
        )
        val selectedPresses = setOf(NewsPress.MBC, NewsPress.SBS, NewsPress.KBS)
        val repository = repositoryWith(rss, html)

        val parallelResult = repository.searchNews(
            selectedPresses = selectedPresses,
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )
        val sequentialArticles = collectSequentialReference(
            dataSources = listOf(rss, html),
            selectedPresses = selectedPresses,
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )
        val sequentialResult = NewsFilter.filter(
            articles = sequentialArticles,
            selectedPresses = selectedPresses,
            keywords = emptyList()
        )

        assertEquals(
            sequentialResult.map { article -> article.originalUrl to article.title },
            parallelResult.articles.map { article -> article.originalUrl to article.title }
        )
        assertEquals(
            sequentialResult.groupingBy { article -> article.press }.eachCount(),
            parallelResult.articles.groupingBy { article -> article.press }.eachCount()
        )
    }

    @Test
    fun rssSuccessSkipsHtml() = runTest {
        val rss = RecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            articles = listOf(article(NewsPress.MBC, "rss"))
        )
        val html = RecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            articles = listOf(article(NewsPress.MBC, "html"))
        )
        val repository = repositoryWith(rss, html)

        val result = repository.searchNews(setOf(NewsPress.MBC), emptyList())

        assertEquals(1, rss.callCount)
        assertEquals(0, html.callCount)
        assertEquals(NewsSourceType.RSS, result.articles.single().sourceType)
        assertTrue(result.failedPresses.isEmpty())
        assertFalse(result.usedMockFallback)
    }

    @Test
    fun rssFailureCallsHtml() = runTest {
        val rss = RecordingNewsDataSource(NewsSourceType.RSS, emptyList(), success = false)
        val html = RecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            articles = listOf(article(NewsPress.SBS, "html"))
        )
        val repository = repositoryWith(rss, html)

        val result = repository.searchNews(setOf(NewsPress.SBS), emptyList())

        assertEquals(1, rss.callCount)
        assertEquals(1, html.callCount)
        assertEquals(NewsSourceType.HTML_CRAWLING, result.articles.single().sourceType)
        assertTrue(result.failedPresses.isEmpty())
        assertFalse(result.usedMockFallback)
    }

    @Test
    fun fallbackOnlyArticleIsStillCollectedAfterParallelization() = runTest {
        val rss = RecordingNewsDataSource(NewsSourceType.RSS, emptyList(), success = false)
        val html = RecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            articles = listOf(article(NewsPress.KBS, "fallback-only"))
        )
        val repository = repositoryWith(rss, html)

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.KBS),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

        assertEquals(1, rss.callCount)
        assertEquals(1, html.callCount)
        assertEquals("https://news.kbs.co.kr/news/pc/view/view.do?ncd=fallback-only", result.articles.single().originalUrl)
    }

    @Test
    fun mainDefaultRssBelowLimitContinuesToHtmlFallback() = runTest {
        val rss = RecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            articles = (1..3).map { index ->
                article(NewsPress.MBC, "rss-$index", publishedAt = index.toLong())
            }
        )
        val html = RecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            articles = (1..3).map { index ->
                article(NewsPress.MBC, "html-$index", publishedAt = (100 + index).toLong())
            }
        )
        val repository = repositoryWith(rss, html)

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.MBC),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

        assertEquals(1, rss.callCount)
        assertEquals(1, html.callCount)
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertTrue(result.articles.any { article -> article.originalUrl.contains("html-") })
    }

    @Test
    fun mainDefaultRssAtLimitSkipsHtmlFallback() = runTest {
        val rss = RecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            articles = (1..NewsFilter.MAX_ARTICLES_PER_PRESS).map { index ->
                article(NewsPress.SBS, "rss-$index", publishedAt = index.toLong())
            }
        )
        val html = RecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            articles = listOf(article(NewsPress.SBS, "html"))
        )
        val repository = repositoryWith(rss, html)

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.SBS),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

        assertEquals(1, rss.callCount)
        assertEquals(0, html.callCount)
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertTrue(result.articles.all { article -> article.sourceType == NewsSourceType.RSS })
    }

    @Test
    fun rssAndHtmlFailureReturnsNoMockAndMarksFailedPress() = runTest {
        val repository = repositoryWith(
            RecordingNewsDataSource(NewsSourceType.RSS, emptyList(), success = false),
            RecordingNewsDataSource(NewsSourceType.HTML_CRAWLING, emptyList(), success = false)
        )

        val result = repository.searchNews(setOf(NewsPress.YTN), emptyList())

        assertTrue(result.articles.isEmpty())
        assertEquals(listOf(NewsPress.YTN), result.failedPresses)
        assertFalse(result.usedMockFallback)
        assertTrue(result.fallbackPresses.isEmpty())
        assertTrue(result.sourceStatuses.any { status -> status.sourceType == NewsSourceType.NOT_FOUND })
    }

    @Test
    fun onePressFailureDoesNotDiscardAnotherPressSuccess() = runTest {
        val repository = repositoryWith(
            RecordingNewsDataSource(
                sourceType = NewsSourceType.RSS,
                articles = listOf(article(NewsPress.MBC, "rss")),
                successfulPresses = setOf(NewsPress.MBC)
            ),
            RecordingNewsDataSource(NewsSourceType.HTML_CRAWLING, emptyList(), success = false)
        )

        val result = repository.searchNews(setOf(NewsPress.MBC, NewsPress.KBS), emptyList())

        assertEquals(listOf(NewsPress.KBS), result.failedPresses)
        assertEquals(setOf(NewsPress.MBC), result.articles.map { article -> article.press }.toSet())
        assertTrue(result.articles.all { article -> article.sourceType == NewsSourceType.RSS })
    }

    @Test
    fun emptySelectedPressesReturnsEmptyResultWithoutFetching() = runTest {
        val dataSource = RecordingNewsDataSource(NewsSourceType.RSS, emptyList(), success = false)
        val repository = repositoryWith(dataSource)

        val result = repository.searchNews(emptySet(), emptyList())

        assertTrue(result.articles.isEmpty())
        assertEquals("선택된 뉴스사가 없습니다.", result.message)
        assertEquals(0, dataSource.callCount)
    }

    @Test
    fun allSelectionResolvesToArticlePressesOnly() = runTest {
        val dataSource = PressRecordingDataSource(::article)
        val repository = repositoryWith(dataSource)

        repository.searchNews(setOf(NewsPress.ALL), emptyList())

        assertEquals(NewsPress.articlePresses().toSet(), dataSource.presses.toSet())
        assertFalse(dataSource.presses.contains(NewsPress.ALL))
    }

    @Test
    fun finalResultIsLimitedToFivePerPressAndSortedByLatest() = runTest {
        val articles = (1..7).map { index ->
            article(
                press = NewsPress.YTN,
                suffix = "rss-$index",
                publishedAt = index.toLong()
            )
        }
        val repository = repositoryWith(RecordingNewsDataSource(NewsSourceType.RSS, articles))

        val result = repository.searchNews(setOf(NewsPress.YTN), emptyList())

        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertEquals(
            result.articles.mapNotNull { article -> article.publishedAt }.sortedDescending(),
            result.articles.mapNotNull { article -> article.publishedAt }
        )
    }

    @Test
    fun invalidLivePageIsFilteredBeforeDisplayAndBecomesNotFound() = runTest {
        val repository = repositoryWith(
            RecordingNewsDataSource(
                sourceType = NewsSourceType.RSS,
                articles = listOf(
                    article(
                        press = NewsPress.YTN,
                        suffix = "live",
                        title = "[Mock] YTN live page",
                        originalUrl = "https://www.ytn.co.kr/live.php"
                    )
                )
            ),
            RecordingNewsDataSource(NewsSourceType.HTML_CRAWLING, emptyList(), success = false)
        )

        val result = repository.searchNews(setOf(NewsPress.YTN), emptyList())

        assertTrue(result.articles.isEmpty())
        assertEquals(listOf(NewsPress.YTN), result.failedPresses)
        assertFalse(result.usedMockFallback)
        assertTrue(result.sourceStatuses.any { status -> status.sourceType == NewsSourceType.NOT_FOUND })
    }

    @Test
    fun pressCollectionRunsInParallelButPressInternalSourcesStaySequential() = runTest {
        val dataSource = DelayedNewsDataSource(::article)
        val repository = repositoryWith(dataSource)

        repository.searchNews(
            selectedPresses = setOf(NewsPress.MBC, NewsPress.SBS),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

        assertTrue(dataSource.maxActive.get() > 1)
    }

    @Test
    fun pressExceptionDoesNotCancelOtherPressCollection() = runTest {
        val dataSource = ThrowingForPressNewsDataSource(
            throwingPress = NewsPress.MBC,
            articleFactory = ::article
        )
        val repository = repositoryWith(dataSource)

        val result = repository.searchNews(setOf(NewsPress.MBC, NewsPress.SBS), emptyList())

        assertEquals(setOf(NewsPress.SBS), result.articles.map { article -> article.press }.toSet())
        assertEquals(listOf(NewsPress.MBC), result.failedPresses)
    }

    @Test
    fun pressTimeoutDoesNotCancelOtherPressCollection() = runTest {
        val dataSource = SlowForPressNewsDataSource(
            slowPress = NewsPress.KBS,
            articleFactory = ::article
        )
        val repository = MultiSourceNewsRepository(
            dataSources = listOf(dataSource),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.searchNews(setOf(NewsPress.KBS, NewsPress.MBC), emptyList())

        assertEquals(setOf(NewsPress.MBC), result.articles.map { article -> article.press }.toSet())
        assertEquals(listOf(NewsPress.KBS), result.failedPresses)
        assertTrue(result.sourceStatuses.any { status ->
            status.press == NewsPress.KBS && status.message.orEmpty().contains("timeout")
        })
    }

    @Test
    fun incrementalCompletedArticleIsCachedBeforeFinalResult() = runTest {
        val dataSource = SlowForPressNewsDataSource(
            slowPress = NewsPress.KBS,
            articleFactory = ::article
        )
        val repository = MultiSourceNewsRepository(
            dataSources = listOf(dataSource),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val event = repository.collectNewsByPressFlow(
            selectedPresses = setOf(NewsPress.SBS, NewsPress.KBS),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        ).first { collectionEvent ->
            collectionEvent is NewsCollectionEvent.PressCollectionCompleted &&
                collectionEvent.press == NewsPress.SBS
        } as NewsCollectionEvent.PressCollectionCompleted

        val article = event.articles.single()

        assertEquals(article.id, repository.getNewsArticleById(article.id)?.id)
        assertEquals(article.originalUrl, repository.getNewsArticleById(article.id)?.originalUrl)
    }

    @Test
    fun collectionTimeoutBudgetLeavesRoomForPerPressFallback() {
        assertTrue(NewsFetchConfig.PRESS_COLLECTION_TIMEOUT_MS >= 35_000L)
        assertTrue(NewsFetchConfig.SOURCE_COLLECTION_TIMEOUT_MS < NewsFetchConfig.PRESS_COLLECTION_TIMEOUT_MS)
        assertTrue(NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_RSS_SOURCE_TIMEOUT_MS <= 5_000L)
        assertTrue(NewsFetchConfig.MAIN_DEFAULT_GOOGLE_NEWS_RSS_SOURCE_TIMEOUT_MS < NewsFetchConfig.MAIN_DEFAULT_RSS_SOURCE_TIMEOUT_MS)
        assertTrue(NewsFetchConfig.MAIN_COLLECTION_TIMEOUT_MS > NewsFetchConfig.PRESS_COLLECTION_TIMEOUT_MS)
        assertTrue(NewsFetchConfig.MAIN_VIEWMODEL_TIMEOUT_MS > NewsFetchConfig.MAIN_COLLECTION_TIMEOUT_MS)
    }

    @Test
    fun mainDefaultProblemPressesRunHtmlBeforeGoogleNewsRss() = runTest {
        listOf(NewsPress.MBC, NewsPress.KBS, NewsPress.YTN).forEach { press ->
            val calls = Collections.synchronizedList(mutableListOf<String>())
            val official = NamedRecordingNewsDataSource(
                sourceType = NewsSourceType.RSS,
                sourceName = "Official RSS",
                calls = calls,
                articles = emptyList(),
                success = false
            )
            val html = NamedRecordingNewsDataSource(
                sourceType = NewsSourceType.HTML_CRAWLING,
                sourceName = "HTML Crawling",
                calls = calls,
                articles = listOf(article(press, "html-$press"))
            )
            val google = NamedRecordingNewsDataSource(
                sourceType = NewsSourceType.RSS,
                sourceName = "Google News RSS",
                calls = calls,
                articles = listOf(article(press, "google-$press"))
            )
            val repository = MultiSourceNewsRepository(dataSources = listOf(official, html, google))

            val result = repository.searchNews(
                selectedPresses = setOf(press),
                keywords = emptyList(),
                collectionMode = CollectionMode.MAIN_DEFAULT
            )

            assertEquals("Official RSS:$press", calls[0])
            assertEquals("HTML Crawling:$press", calls[1])
            assertTrue(calls.indexOf("HTML Crawling:$press") < calls.indexOf("Google News RSS:$press"))
            assertTrue(result.articles.any { article -> article.originalUrl.contains("html-$press") })
        }
    }

    @Test
    fun mainDefaultSbsRssSuccessStillSkipsFallbackSources() = runTest {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val official = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Official RSS",
            calls = calls,
            articles = (1..NewsFilter.MAX_ARTICLES_PER_PRESS).map { index ->
                article(NewsPress.SBS, "official-sbs-$index")
            }
        )
        val html = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            sourceName = "HTML Crawling",
            calls = calls,
            articles = listOf(article(NewsPress.SBS, "html-sbs"))
        )
        val google = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Google News RSS",
            calls = calls,
            articles = listOf(article(NewsPress.SBS, "google-sbs"))
        )
        val repository = MultiSourceNewsRepository(dataSources = listOf(official, html, google))

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.SBS),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

        assertEquals(listOf("Official RSS:SBS"), calls.toList())
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertTrue(result.articles.all { article -> article.sourceType == NewsSourceType.RSS })
    }

    @Test
    fun searchUsesKeywordGoogleNewsRssBeforeLatestSources() = runTest {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val official = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Official RSS",
            calls = calls,
            articles = listOf(article(NewsPress.MBC, "official-latest", title = "latest article"))
        )
        val html = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            sourceName = "HTML Crawling",
            calls = calls,
            articles = listOf(article(NewsPress.MBC, "html-latest", title = "another latest article"))
        )
        val google = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Google News RSS",
            calls = calls,
            articles = (1..NewsFilter.MAX_ARTICLES_PER_PRESS).map { index ->
                article(
                    press = NewsPress.MBC,
                    suffix = "google-keyword-query-$index",
                    title = "Google News query result without literal keyword"
                )
            }
        )
        val repository = MultiSourceNewsRepository(dataSources = listOf(official, html, google))

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.MBC),
            keywords = listOf("rare-keyword"),
            collectionMode = CollectionMode.SEARCH
        )

        assertEquals(listOf("Google News RSS:MBC"), calls.toList())
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertTrue(result.articles.all { article -> article.originalUrl.contains("google-keyword-query") })
        assertTrue(result.articles.all { article -> article.keywords.any { keyword -> keyword == "rare-keyword" } })
        assertTrue(result.articles.all { article -> article.matchedKeywords == listOf("rare-keyword") })
    }

    @Test
    fun searchFinalLimitMeansDisplayLimitNotRawCandidateLimit() = runTest {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val google = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Google News RSS",
            calls = calls,
            articles = (1..20).map { index ->
                article(
                    press = NewsPress.KBS,
                    suffix = "google-raw-candidate-$index",
                    title = "Google News query result $index"
                )
            }
        )
        val repository = MultiSourceNewsRepository(dataSources = listOf(google))

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.KBS),
            keywords = listOf("rare-keyword"),
            collectionMode = CollectionMode.SEARCH
        )

        assertEquals(listOf("Google News RSS:KBS"), calls.toList())
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.articles.size)
        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.sourceStatuses.single().articleCount)
        assertTrue(result.articles.all { article -> article.keywords.any { keyword -> keyword == "rare-keyword" } })
        assertTrue(result.articles.all { article -> article.matchedKeywords == listOf("rare-keyword") })
    }

    @Test
    fun searchKeywordQueryKeepsArticleWhenOnlyBodyIsMissing() = runTest {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val google = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Google News RSS",
            calls = calls,
            articles = listOf(
                article(
                    press = NewsPress.KBS,
                    suffix = "google-bodyless-keyword",
                    title = "rare-keyword query result"
                ).copy(
                    summary = "",
                    content = "",
                    bodyParagraphs = emptyList(),
                    keywords = emptyList()
                )
            )
        )
        val repository = MultiSourceNewsRepository(dataSources = listOf(google))

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.KBS),
            keywords = listOf("rare-keyword"),
            collectionMode = CollectionMode.SEARCH
        )

        assertEquals(listOf("Google News RSS:KBS"), calls.toList())
        assertEquals(1, result.articles.size)
        assertEquals(listOf("rare-keyword"), result.articles.single().matchedKeywords)
    }

    @Test
    fun searchDoesNotStopWhenLatestArticlesDoNotMatchKeyword() = runTest {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val google = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Google News RSS",
            calls = calls,
            articles = emptyList(),
            success = false
        )
        val official = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Official RSS",
            calls = calls,
            articles = (1..NewsFilter.MAX_ARTICLES_PER_PRESS).map { index ->
                article(NewsPress.SBS, "official-latest-$index", title = "latest article $index")
            }
        )
        val html = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            sourceName = "HTML Crawling",
            calls = calls,
            articles = listOf(
                article(
                    press = NewsPress.SBS,
                    suffix = "html-keyword-match",
                    title = "rare-keyword article"
                )
            )
        )
        val repository = MultiSourceNewsRepository(dataSources = listOf(official, html, google))

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.SBS),
            keywords = listOf("rare-keyword"),
            collectionMode = CollectionMode.SEARCH
        )

        assertEquals(
            listOf("Google News RSS:SBS", "Official RSS:SBS", "HTML Crawling:SBS"),
            calls.toList()
        )
        assertTrue(result.articles.single().originalUrl.contains("html-keyword-match"))
        assertEquals(listOf("rare-keyword"), result.articles.single().matchedKeywords)
        assertEquals(1, result.sourceStatuses.last { status -> status.success }.articleCount)
    }

    @Test
    fun searchFinalCountMatchesDisplayedKeywordResults() = runTest {
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val google = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Google News RSS",
            calls = calls,
            articles = emptyList(),
            success = false
        )
        val official = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.RSS,
            sourceName = "Official RSS",
            calls = calls,
            articles = (1..NewsFilter.MAX_ARTICLES_PER_PRESS).map { index ->
                article(NewsPress.YONHAP, "official-latest-$index", title = "latest article $index")
            }
        )
        val html = NamedRecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            sourceName = "HTML Crawling",
            calls = calls,
            articles = listOf(
                article(NewsPress.YONHAP, "html-keyword-1", title = "rare-keyword first article"),
                article(NewsPress.YONHAP, "html-keyword-2", title = "rare-keyword second article")
            )
        )
        val repository = MultiSourceNewsRepository(dataSources = listOf(official, html, google))

        val event = repository.collectNewsByPressFlow(
            selectedPresses = setOf(NewsPress.YONHAP),
            keywords = listOf("rare-keyword"),
            collectionMode = CollectionMode.SEARCH
        ).first { collectionEvent -> collectionEvent is NewsCollectionEvent.AllPressCollectionFinished }
            as NewsCollectionEvent.AllPressCollectionFinished

        assertEquals(2, event.result.articles.size)
        assertEquals(2, event.result.sourceStatuses.last { status -> status.success }.articleCount)
    }

    @Test
    fun mainDefaultLimitsFinalPressResultToFiveArticlesAcrossSources() = runTest {
        val officialArticles = (1..4).map { index -> article(NewsPress.MBC, "official-$index") }
        val htmlArticles = (1..5).map { index -> article(NewsPress.MBC, "html-$index") }
        val repository = MultiSourceNewsRepository(
            dataSources = listOf(
                RecordingNewsDataSource(NewsSourceType.RSS, officialArticles),
                RecordingNewsDataSource(NewsSourceType.HTML_CRAWLING, htmlArticles)
            )
        )

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.MBC),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

        assertEquals(NewsFilter.MAX_ARTICLES_PER_PRESS, result.articles.size)
    }

    @Test
    fun htmlEntityOriginalUrlIsDecodedBeforeCachingAndLookup() = runTest {
        val rawUrl = "https://news.sbs.co.kr/news/endPage.do?news_id=N1008615495&amp;plink=RSSLINK&amp;cooper=RSSREADER"
        val decodedUrl = UrlNormalizer.decodeHtmlEntities(rawUrl)
        val repository = repositoryWith(
            RecordingNewsDataSource(
                sourceType = NewsSourceType.RSS,
                articles = listOf(article(NewsPress.SBS, "amp-url", originalUrl = rawUrl))
            )
        )

        val result = repository.searchNews(setOf(NewsPress.SBS), emptyList())
        val storedArticle = result.articles.single()

        assertEquals(decodedUrl, storedArticle.originalUrl)
        assertFalse(UrlNormalizer.normalize(storedArticle.originalUrl).contains("amp;plink"))
        assertFalse(UrlNormalizer.normalize(storedArticle.originalUrl).contains("amp;cooper"))
        assertEquals(
            ArticleIdentity.idFor(NewsPress.SBS, decodedUrl, storedArticle.title),
            storedArticle.id
        )
        assertEquals(storedArticle.id, repository.getNewsArticleById(rawUrl)?.id)
        assertEquals(storedArticle.id, repository.getNewsArticleById(decodedUrl)?.id)
    }

    @Test
    fun sourceTimeoutFallsBackAndReturnsPressResult() = runTest {
        val slowRss = SlowForPressNewsDataSource(
            slowPress = NewsPress.MBC,
            articleFactory = ::article
        )
        val htmlFallback = RecordingNewsDataSource(
            sourceType = NewsSourceType.HTML_CRAWLING,
            articles = listOf(article(NewsPress.MBC, "html-after-timeout")),
            successfulPresses = setOf(NewsPress.MBC)
        )
        val repository = MultiSourceNewsRepository(
            dataSources = listOf(slowRss, htmlFallback),
            ioDispatcher = StandardTestDispatcher(testScheduler)
        )

        val result = repository.searchNews(
            selectedPresses = setOf(NewsPress.MBC),
            keywords = emptyList(),
            collectionMode = CollectionMode.MAIN_DEFAULT
        )

        assertEquals(1, result.articles.size)
        assertTrue(result.articles.single().originalUrl.contains("html-after-timeout"))
        assertTrue(result.sourceStatuses.any { status ->
            status.sourceType == NewsSourceType.RSS &&
                status.message.orEmpty().contains("timeoutStage=source")
        })
    }

    private suspend fun collectSequentialReference(
        dataSources: List<NewsDataSource>,
        selectedPresses: Set<NewsPress>,
        keywords: List<String>,
        collectionMode: CollectionMode
    ): List<NewsArticle> =
        NewsPress.articlePresses()
            .filter { press -> press in selectedPresses }
            .flatMap { press ->
                val collectedArticles = mutableListOf<NewsArticle>()
                dataSources.firstNotNullOfOrNull { dataSource ->
                    val result = dataSource.fetch(press, keywords)
                    val displayable = result.articles
                        .filter { article -> article.press == press }
                        .map(ArticleQualityValidator::cleanAndValidate)
                        .mapNotNull { check -> check.article.takeIf { check.result.isValid } }
                    if (!result.success || displayable.isEmpty()) {
                        null
                    } else if (collectionMode == CollectionMode.MAIN_DEFAULT) {
                        val mergedArticles = NewsDeduplicator.deduplicate(collectedArticles + displayable)
                        collectedArticles.clear()
                        collectedArticles += mergedArticles
                        collectedArticles.takeIf { it.size >= NewsFilter.MAX_ARTICLES_PER_PRESS }
                    } else {
                        displayable
                    }
                } ?: collectedArticles
            }

    private fun repositoryWith(vararg dataSources: NewsDataSource): MultiSourceNewsRepository =
        MultiSourceNewsRepository(dataSources = dataSources.toList())

    private fun article(
        press: NewsPress,
        suffix: String,
        publishedAt: Long = 100L,
        title: String = "[Mock] ${press.displayName} article $suffix",
        originalUrl: String = originalUrlFor(press, suffix)
    ): NewsArticle =
        MockNewsRepository.mockArticles
            .first { article -> article.press == press }
            .copy(
                id = "${press.name.lowercase()}-$suffix",
                title = title,
                publishedAt = publishedAt,
                originalUrl = originalUrl,
                sourceType = NewsSourceType.RSS,
                sourceLabel = NewsSourceType.RSS.displayName
            )

    private fun originalUrlFor(
        press: NewsPress,
        suffix: String
    ): String =
        when (press) {
            NewsPress.YONHAP -> "https://www.yna.co.kr/view/$suffix"
            NewsPress.MBC -> "https://imnews.imbc.com/news/2026/mock/$suffix.html"
            NewsPress.SBS -> "https://news.sbs.co.kr/news/endPage.do?news_id=$suffix"
            NewsPress.KBS -> "https://news.kbs.co.kr/news/pc/view/view.do?ncd=$suffix"
            NewsPress.YTN -> "https://www.ytn.co.kr/_ln/0101_$suffix"
            NewsPress.ALL -> "https://example.com/$suffix"
        }

    private class RecordingNewsDataSource(
        override val sourceType: NewsSourceType,
        private val articles: List<NewsArticle>,
        private val success: Boolean = articles.isNotEmpty(),
        private val successfulPresses: Set<NewsPress>? = null
    ) : NewsDataSource {
        override val sourceName: String = sourceType.displayName
        var callCount: Int = 0

        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult {
            callCount += 1
            val articlesForPress = if (successfulPresses == null || press in successfulPresses) {
                articles.filter { article -> article.press == press }
            } else {
                emptyList()
            }

            return NewsDataSourceResult(
                articles = articlesForPress.map { article ->
                    article.copy(
                        sourceType = sourceType,
                        sourceLabel = sourceType.displayName
                    )
                },
                success = success && articlesForPress.isNotEmpty(),
                message = sourceType.displayName
            )
        }
    }

    private class NamedRecordingNewsDataSource(
        override val sourceType: NewsSourceType,
        override val sourceName: String,
        private val calls: MutableList<String>,
        private val articles: List<NewsArticle>,
        private val success: Boolean = articles.isNotEmpty()
    ) : NewsDataSource {
        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult {
            calls += "$sourceName:${press.name}"
            val articlesForPress = articles.filter { article -> article.press == press }
            return NewsDataSourceResult(
                articles = articlesForPress.map { article ->
                    article.copy(
                        sourceType = sourceType,
                        sourceLabel = sourceName
                    )
                },
                success = success && articlesForPress.isNotEmpty(),
                message = sourceName
            )
        }
    }

    private class PressRecordingDataSource(
        private val articleFactory: (NewsPress, String) -> NewsArticle
    ) : NewsDataSource {
        override val sourceType: NewsSourceType = NewsSourceType.RSS
        override val sourceName: String = sourceType.displayName
        val presses: MutableList<NewsPress> = Collections.synchronizedList(mutableListOf())

        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult {
            presses += press
            return NewsDataSourceResult(
                articles = listOf(articleFactory(press, "record-${press.name}")),
                success = true
            )
        }
    }

    private class DelayedNewsDataSource(
        private val articleFactory: (NewsPress, String) -> NewsArticle
    ) : NewsDataSource {
        override val sourceType: NewsSourceType = NewsSourceType.RSS
        override val sourceName: String = sourceType.displayName
        private val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)

        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult {
            val activeCount = active.incrementAndGet()
            maxActive.updateAndGet { current -> maxOf(current, activeCount) }
            try {
                delay(50)
                return NewsDataSourceResult(
                    articles = listOf(articleFactory(press, "parallel-${press.name}")),
                    success = true
                )
            } finally {
                active.decrementAndGet()
            }
        }
    }

    private class ThrowingForPressNewsDataSource(
        private val throwingPress: NewsPress,
        private val articleFactory: (NewsPress, String) -> NewsArticle
    ) : NewsDataSource {
        override val sourceType: NewsSourceType = NewsSourceType.RSS
        override val sourceName: String = sourceType.displayName

        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult {
            if (press == throwingPress) error("boom")
            return NewsDataSourceResult(
                articles = listOf(articleFactory(press, "safe-${press.name}")),
                success = true
            )
        }
    }

    private class SlowForPressNewsDataSource(
        private val slowPress: NewsPress,
        private val articleFactory: (NewsPress, String) -> NewsArticle
    ) : NewsDataSource {
        override val sourceType: NewsSourceType = NewsSourceType.RSS
        override val sourceName: String = sourceType.displayName

        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult {
            if (press == slowPress) {
                delay(NewsFetchConfig.PRESS_COLLECTION_TIMEOUT_MS + 1_000)
            }
            return NewsDataSourceResult(
                articles = listOf(articleFactory(press, "timeout-${press.name}")),
                success = true
            )
        }
    }
}
