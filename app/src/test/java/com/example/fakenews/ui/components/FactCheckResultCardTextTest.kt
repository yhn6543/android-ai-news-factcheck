package com.example.fakenews.ui.components

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FactCheckResultCardTextTest {
    @Test
    fun confidenceLabelExplainsVerdictConfidenceNotTruthProbability() {
        val source = File(
            repoRoot(),
            "app/src/main/java/com/example/fakenews/ui/components/FactCheckResultCard.kt"
        ).readText()

        assertTrue(source.contains("AI 판정 신뢰도"))
        assertTrue(source.contains("신뢰도는 주장이 사실일 확률이 아니라, 위 판정 결과에 대한 AI의 확신도입니다."))
        assertTrue(source.contains("거짓 가능성 높음"))
        assertFalse(source.contains("사실 확률"))
    }

    private fun repoRoot(): File {
        var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile ?: error("Could not locate repository root")
        }
        error("Could not locate repository root")
    }
}
