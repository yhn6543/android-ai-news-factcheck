package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsDataSourceResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.remote.NewsDataSource
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoMockFallbackInAppFlowTest {
    @Test
    fun allSourceFailureReturnsNoMockArticles() = runTest {
        val repository = MultiSourceNewsRepository(
            dataSources = listOf(
                FailingSource(NewsSourceType.RSS),
                FailingSource(NewsSourceType.HTML_CRAWLING)
            )
        )

        val result = repository.searchNews(setOf(NewsPress.KBS), emptyList())

        assertTrue(result.articles.isEmpty())
        assertTrue(result.fallbackPresses.isEmpty())
        assertFalse(result.usedMockFallback)
        assertTrue(result.failedPresses.contains(NewsPress.KBS))
    }

    @Test
    fun appContainerAndMainFlowDoNotReferenceMockNewsRepositoryFallback() {
        val root = repoRoot()
        val appFlowFiles = listOf(
            "app/src/main/java/com/example/fakenews/AppContainer.kt",
            "app/src/main/java/com/example/fakenews/data/repository/DefaultNewsRepository.kt",
            "app/src/main/java/com/example/fakenews/data/repository/MultiSourceNewsRepository.kt",
            "app/src/main/java/com/example/fakenews/ui/main/MainViewModel.kt"
        )

        appFlowFiles.forEach { relativePath ->
            val text = File(root, relativePath).readText()
            assertFalse("$relativePath must not use MockNewsRepository as display fallback", text.contains("MockNewsRepository"))
        }
    }

    private class FailingSource(
        override val sourceType: NewsSourceType
    ) : NewsDataSource {
        override val sourceName: String = sourceType.displayName

        override suspend fun fetch(
            press: NewsPress,
            keywords: List<String>
        ): NewsDataSourceResult =
            NewsDataSourceResult(
                articles = emptyList(),
                success = false,
                message = "failed"
            )
    }

    private fun repoRoot(): File {
        var current = File(System.getProperty("user.dir")).canonicalFile
        repeat(8) {
            if (File(current, "settings.gradle.kts").exists()) {
                return current
            }
            current = current.parentFile ?: current
        }
        error("Could not locate repository root")
    }
}
