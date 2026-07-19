package com.example.fakenews.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsPressTest {
    @Test
    fun selectablePressesUseYtnAndRemovedPressesAreGone() {
        val selectablePresses = NewsPress.selectablePresses()

        assertEquals(listOf(NewsPress.ALL, NewsPress.YONHAP, NewsPress.MBC, NewsPress.SBS, NewsPress.KBS, NewsPress.YTN), selectablePresses)
        removedPressNames().forEach { removedName ->
            assertFalse(selectablePresses.any { press -> press.name == removedName })
        }
    }

    @Test
    fun articlePressesContainFiveRealPressesWithoutAll() {
        val articlePresses = NewsPress.articlePresses()

        assertEquals(5, articlePresses.size)
        assertFalse(NewsPress.ALL in articlePresses)
        assertTrue(NewsPress.YTN in articlePresses)
    }

    @Test
    fun kbsAndYtnUseRequestedButtonAndCardColors() {
        assertEquals("#E5EBFF", NewsPress.KBS.pastelColor)
        assertEquals("#E5EBFF", NewsPress.KBS.cardColor)
        assertEquals("#6082E2", NewsPress.KBS.buttonColor)

        assertEquals("#FFE7EE", NewsPress.YTN.pastelColor)
        assertEquals("#FFE7EE", NewsPress.YTN.cardColor)
        assertEquals("#DD7791", NewsPress.YTN.buttonColor)
    }

    private fun removedPressNames(): List<String> =
        listOf("KBS" + "_WORLD", "J" + "TBC")
}
