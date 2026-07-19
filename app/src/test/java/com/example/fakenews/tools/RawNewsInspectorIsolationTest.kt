package com.example.fakenews.tools

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawNewsInspectorIsolationTest {
    @Test
    fun rawNewsInspectorIsOutsideAndroidMainSources() {
        val root = repoRoot()
        val tool = File(root, "tools/raw-news-inspector/raw_news_inspector.py")

        assertTrue(tool.exists())
        assertFalse(tool.canonicalPath.contains("${File.separator}app${File.separator}src${File.separator}main"))
    }

    @Test
    fun appSourceDoesNotReferenceRawInspectorTool() {
        val root = repoRoot()
        val appMain = File(root, "app/src/main")
        val appFiles = appMain.walkTopDown()
            .filter { file -> file.isFile }
            .filter { file -> file.extension in setOf("kt", "kts", "xml") }
            .toList()

        assertTrue(appFiles.isNotEmpty())
        appFiles.forEach { file ->
            val text = file.readText()
            assertFalse("${file.path} must not reference raw inspector", text.contains("raw_news_inspector"))
            assertFalse("${file.path} must not reference raw inspector path", text.contains("raw-news-inspector"))
        }
    }

    @Test
    fun rawInspectorDoesNotImportAndroidAppPackages() {
        val root = repoRoot()
        val toolText = File(root, "tools/raw-news-inspector/raw_news_inspector.py").readText()

        assertFalse(toolText.contains("com.example.fakenews"))
        assertFalse(toolText.contains("app.src.main"))
        assertTrue(toolText.contains("PRESS_URLS"))
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
