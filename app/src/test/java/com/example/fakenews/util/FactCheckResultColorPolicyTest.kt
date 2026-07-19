package com.example.fakenews.util

import com.example.fakenews.data.model.FactCheckVerdict
import org.junit.Assert.assertEquals
import org.junit.Test

class FactCheckResultColorPolicyTest {
    @Test
    fun trueVerdictUsesGreenBackground() {
        assertEquals(
            FactCheckResultColorPolicy.TRUE_BACKGROUND,
            FactCheckResultColorPolicy.backgroundColorHex(FactCheckVerdict.TRUE)
        )
    }

    @Test
    fun falseVerdictUsesRedBackground() {
        assertEquals(
            FactCheckResultColorPolicy.FALSE_BACKGROUND,
            FactCheckResultColorPolicy.backgroundColorHex(FactCheckVerdict.FALSE)
        )
    }

    @Test
    fun uncertainVerdictsUseOrangeBackground() {
        listOf(
            FactCheckVerdict.MISLEADING,
            FactCheckVerdict.PARTLY_TRUE,
            FactCheckVerdict.UNVERIFIABLE,
            FactCheckVerdict.NEEDS_MORE_CONTEXT
        ).forEach { verdict ->
            assertEquals(
                "$verdict should use uncertain background",
                FactCheckResultColorPolicy.UNCERTAIN_BACKGROUND,
                FactCheckResultColorPolicy.backgroundColorHex(verdict)
            )
        }
    }
}
