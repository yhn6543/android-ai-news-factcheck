package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleUrlPolicyTest {
    @Test
    fun kbsNewsViewUrlIsIncluded() {
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://news.kbs.co.kr/news/pc/view/view.do?ncd=1234567"
            )
        )
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://news.kbs.co.kr/news/mobile/view/view.do?ncd=1234567"
            )
        )
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://news.kbs.co.kr/news/view.do?ncd=1234567"
            )
        )
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://news.kbs.co.kr/common/news_view.html?ncd=1234567"
            )
        )
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://www.news.kbs.co.kr/news/pc/view/view.do?ncd=1234567"
            )
        )
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://kbs.co.kr/news/view.do?ncd=1234567"
            )
        )
    }

    @Test
    fun kbsLiveOnairProgramReplayUrlsAreExcluded() {
        val urls = listOf(
            "https://news.kbs.co.kr/live",
            "https://news.kbs.co.kr/onair",
            "https://news.kbs.co.kr/program/news",
            "https://news.kbs.co.kr/replay/news"
        )

        urls.forEach { url ->
            assertFalse(url, ArticleUrlPolicy.isValidArticleUrl(NewsPress.KBS, url))
            assertTrue(url, ArticleUrlPolicy.isExcludedNonArticleUrl(NewsPress.KBS, url))
        }
    }

    @Test
    fun kbsDomainWithoutViewArticlePatternIsExcluded() {
        assertFalse(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://news.kbs.co.kr/news/list.do"
            )
        )
    }

    @Test
    fun kbsViewUrlWithoutNcdIsExcluded() {
        assertFalse(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://news.kbs.co.kr/news/view.do"
            )
        )
        assertFalse(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.KBS,
                "https://news.kbs.co.kr/common/news_view.html?category=politics"
            )
        )
    }

    @Test
    fun ytnLnUrlIsIncluded() {
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.YTN,
                "https://www.ytn.co.kr/_ln/0101_202606110001"
            )
        )
        assertTrue(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.YTN,
                "https://m.ytn.co.kr/_ln/0101_202606110001"
            )
        )
    }

    @Test
    fun ytnLivePhpAndUtilityUrlsAreExcluded() {
        val urls = listOf(
            "https://www.ytn.co.kr/live.php",
            "https://www.ytn.co.kr/search?q=news",
            "https://www.ytn.co.kr/ranking",
            "https://www.ytn.co.kr/program"
        )

        urls.forEach { url ->
            assertFalse(url, ArticleUrlPolicy.isValidArticleUrl(NewsPress.YTN, url))
            assertTrue(url, ArticleUrlPolicy.isExcludedNonArticleUrl(NewsPress.YTN, url))
        }
    }

    @Test
    fun ytnLegacyNewsViewPathIsExcluded() {
        assertFalse(
            ArticleUrlPolicy.isValidArticleUrl(
                NewsPress.YTN,
                "https://m.ytn.co.kr/news_view.php?s_mcd=0101&key=202606110001"
            )
        )
    }
}
