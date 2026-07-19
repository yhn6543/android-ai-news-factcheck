package com.example.fakenews.util

import com.example.fakenews.data.model.FactCheckVerdict

object FactCheckResultColorPolicy {
    const val TRUE_BACKGROUND = "#E8F5E9"
    const val FALSE_BACKGROUND = "#FFEBEE"
    const val UNCERTAIN_BACKGROUND = "#FFF3E0"

    fun backgroundColorHex(verdict: FactCheckVerdict): String =
        when (verdict) {
            FactCheckVerdict.TRUE -> TRUE_BACKGROUND
            FactCheckVerdict.FALSE -> FALSE_BACKGROUND
            FactCheckVerdict.MISLEADING,
            FactCheckVerdict.PARTLY_TRUE,
            FactCheckVerdict.UNVERIFIABLE,
            FactCheckVerdict.NEEDS_MORE_CONTEXT -> UNCERTAIN_BACKGROUND
        }
}
