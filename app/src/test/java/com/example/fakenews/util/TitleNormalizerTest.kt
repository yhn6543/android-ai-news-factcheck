package com.example.fakenews.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleNormalizerTest {
    @Test
    fun trimsWhitespaceAndCollapsesConsecutiveSpaces() {
        val result = TitleNormalizer.normalize("  경제   뉴스   점검  ")

        assertEquals("경제 뉴스 점검", result)
    }

    @Test
    fun ignoresCaseForEnglishTitles() {
        val result = TitleNormalizer.normalize("GLOBAL Economy UPDATE")

        assertEquals("global economy update", result)
    }

    @Test
    fun removesBreakingExclusiveAndComprehensivePrefixes() {
        val breaking = TitleNormalizer.normalize("[속보] 경제 뉴스 점검")
        val exclusive = TitleNormalizer.normalize("단독: 경제 뉴스 점검")
        val comprehensive = TitleNormalizer.normalize("종합 - 경제 뉴스 점검")

        assertEquals("경제 뉴스 점검", breaking)
        assertEquals("경제 뉴스 점검", exclusive)
        assertEquals("경제 뉴스 점검", comprehensive)
    }

    @Test
    fun removesPressNameNoiseAtEdges() {
        val result = TitleNormalizer.normalize("연합뉴스 경제 뉴스 점검")

        assertEquals("경제 뉴스 점검", result)
    }
}
