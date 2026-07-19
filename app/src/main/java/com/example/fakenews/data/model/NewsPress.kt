package com.example.fakenews.data.model

enum class NewsPress(
    val displayName: String,
    val pastelColor: String,
    val buttonColor: String = pastelColor,
    val cardColor: String = pastelColor
) {
    ALL("전체", "#C5CAE9"),
    YONHAP("연합뉴스", "#BBDEFB"),
    MBC("MBC", "#F8BBD0"),
    SBS("SBS", "#FFE0B2"),
    KBS("KBS", "#E5EBFF", buttonColor = "#6082E2", cardColor = "#E5EBFF"),
    YTN("YTN", "#FFE7EE", buttonColor = "#DD7791", cardColor = "#FFE7EE");

    companion object {
        fun orderedValues(): List<NewsPress> = listOf(
            ALL,
            YONHAP,
            MBC,
            SBS,
            KBS,
            YTN
        )

        fun selectablePresses(): List<NewsPress> = orderedValues()

        fun articlePresses(): List<NewsPress> = orderedValues().filterNot { it == ALL }
    }
}
