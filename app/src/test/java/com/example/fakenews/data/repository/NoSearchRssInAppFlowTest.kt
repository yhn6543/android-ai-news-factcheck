package com.example.fakenews.data.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoSearchRssInAppFlowTest {
    @Test
    fun appDisplayRepositoryDoesNotReferenceSearchRssDataSource() {
        val root = repoRoot()
        val appFlowFiles = listOf(
            "app/src/main/java/com/example/fakenews/AppContainer.kt",
            "app/src/main/java/com/example/fakenews/data/repository/DefaultNewsRepository.kt",
            "app/src/main/java/com/example/fakenews/data/repository/MultiSourceNewsRepository.kt",
            "app/src/main/java/com/example/fakenews/ui/main/MainViewModel.kt"
        )

        appFlowFiles.forEach { relativePath ->
            val file = File(root, relativePath)
            assertTrue("$relativePath should exist", file.exists())
            val text = file.readText()
            assertFalse("$relativePath must not wire Search RSS into app display flow", text.contains("SearchRssNewsDataSource"))
        }
    }

    @Test
    fun searchRssDataSourceCanRemainForIsolatedTestsOnly() {
        val root = repoRoot()
        val searchSource = File(
            root,
            "app/src/main/java/com/example/fakenews/data/remote/SearchRssNewsDataSource.kt"
        )

        assertTrue(searchSource.exists())
        assertTrue(searchSource.readText().contains("isolated experiments/tests only"))
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
