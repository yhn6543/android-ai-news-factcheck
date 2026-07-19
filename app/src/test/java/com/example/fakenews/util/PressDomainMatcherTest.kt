package com.example.fakenews.util

import com.example.fakenews.data.model.NewsPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PressDomainMatcherTest {
    @Test
    fun sbsNewsDomainIsRecognizedAsSbs() {
        assertEquals(
            NewsPress.SBS,
            PressDomainMatcher.detectPressFromUrl("https://news.sbs.co.kr/news/endPage.do")
        )
    }

    @Test
    fun mbcNewsDomainIsRecognizedAsMbc() {
        assertEquals(
            NewsPress.MBC,
            PressDomainMatcher.detectPressFromUrl("https://imnews.imbc.com/replay/2026/nwdesk/article.html")
        )
    }

    @Test
    fun titleContainingMbcDoesNotMakeSbsUrlMbc() {
        val url = "https://news.sbs.co.kr/news/endPage.do?title=MBC"

        assertFalse(PressDomainMatcher.isUrlAllowedForPress(url, NewsPress.MBC))
        assertTrue(PressDomainMatcher.isUrlAllowedForPress(url, NewsPress.SBS))
    }

    @Test
    fun bodyKeywordCannotAffectUrlOnlyDetection() {
        val url = "https://www.ytn.co.kr/_ln/0101_202606110001?body=KBS"

        assertFalse(PressDomainMatcher.isUrlAllowedForPress(url, NewsPress.KBS))
        assertTrue(PressDomainMatcher.isUrlAllowedForPress(url, NewsPress.YTN))
    }

    @Test
    fun unknownDomainReturnsNull() {
        assertNull(PressDomainMatcher.detectPressFromUrl("https://example.com/news/1"))
    }

    @Test
    fun regularKbsNewsDomainIsRecognizedAsKbs() {
        assertEquals(
            NewsPress.KBS,
            PressDomainMatcher.detectPressFromUrl("https://news.kbs.co.kr/news/pc/view/view.do?ncd=1")
        )
        assertEquals(
            NewsPress.KBS,
            PressDomainMatcher.detectPressFromUrl("https://www.news.kbs.co.kr/news/view.do?ncd=1")
        )
    }

    @Test
    fun ytnDomainsAreRecognizedAsYtn() {
        assertEquals(NewsPress.YTN, PressDomainMatcher.detectPressFromUrl("https://ytn.co.kr/_ln/0101_1"))
        assertEquals(NewsPress.YTN, PressDomainMatcher.detectPressFromUrl("https://www.ytn.co.kr/_ln/0101_1"))
        assertEquals(NewsPress.YTN, PressDomainMatcher.detectPressFromUrl("https://m.ytn.co.kr/news_view.php"))
    }

    @Test
    fun ytnUnlistedSubdomainIsRejected() {
        assertFalse(
            PressDomainMatcher.isUrlAllowedForPress(
                url = "https://news.ytn.co.kr/_ln/0101_1",
                press = NewsPress.YTN
            )
        )
    }

    @Test
    fun allowedSubdomainMatchesParentAllowedDomain() {
        assertTrue(
            PressDomainMatcher.isUrlAllowedForPress(
                url = "https://m.news.sbs.co.kr/news/endPage.do",
                press = NewsPress.SBS
            )
        )
    }
}
