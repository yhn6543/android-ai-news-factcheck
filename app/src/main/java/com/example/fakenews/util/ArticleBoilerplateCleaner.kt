package com.example.fakenews.util

import org.jsoup.nodes.Element

object ArticleBoilerplateCleaner {
    fun removeBoilerplateElements(element: Element) {
        element.select(boilerplateSelectors.joinToString(",")).remove()
    }

    fun cleanParagraph(text: String): String =
        text
            .replace("\u00a0", " ")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    fun isBoilerplateParagraph(text: String): Boolean {
        val paragraph = cleanParagraph(text)
        if (paragraph.isBlank()) return true

        val lower = paragraph.lowercase()
        if (alwaysRemoveKeywords.any { keyword -> lower.contains(keyword.lowercase()) }) return true

        val compact = paragraph.replace(" ", "")
        if (
            shortUiKeywords.any { keyword ->
                val compactKeyword = keyword.replace(" ", "")
                compact == compactKeyword || compact.startsWith(compactKeyword)
            } && paragraph.length <= 40
        ) {
            return true
        }

        if (paragraph.length <= 60 && reporterPatterns.any { pattern -> pattern.containsMatchIn(paragraph) }) {
            return true
        }

        if (paragraph.length <= 30 && paragraph.contains("광고")) return true

        return false
    }

    fun boilerplateKeywordCount(text: String): Int {
        val lower = text.lowercase()
        return (alwaysRemoveKeywords + shortUiKeywords).count { keyword ->
            lower.contains(keyword.lowercase())
        }
    }

    private val boilerplateSelectors = listOf(
        "script",
        "style",
        "iframe",
        "noscript",
        "header",
        "footer",
        "nav",
        "aside",
        "form",
        "button",
        "input",
        "select",
        "textarea",
        ".share",
        ".sns",
        ".social",
        ".recommend",
        ".related",
        ".popular",
        ".ranking",
        ".notice",
        ".comment",
        ".reply",
        ".reaction",
        ".sympathy",
        ".empathy",
        ".like",
        ".dislike",
        ".rating",
        ".vote",
        ".poll",
        ".advertisement",
        ".advert",
        ".ad",
        ".banner",
        ".copyright",
        ".reporter",
        ".journalist",
        ".reporter_profile",
        ".journalist_profile",
        ".news_img",
        ".caption",
        ".image_caption",
        ".photo_caption",
        ".photo_info",
        "figcaption",
        ".tag",
        ".keyword",
        ".vod",
        "[class*=share]",
        "[class*=sns]",
        "[class*=recommend]",
        "[class*=related]",
        "[class*=popular]",
        "[class*=ranking]",
        "[class*=notice]",
        "[class*=comment]",
        "[class*=reply]",
        "[class*=reaction]",
        "[class*=sympathy]",
        "[class*=empathy]",
        "[class*=like]",
        "[class*=dislike]",
        "[class*=rating]",
        "[class*=vote]",
        "[class*=poll]",
        "[class*=advert]",
        "[id*=share]",
        "[id*=sns]",
        "[id*=recommend]",
        "[id*=related]",
        "[id*=popular]",
        "[id*=ranking]",
        "[id*=notice]",
        "[id*=comment]",
        "[id*=reply]",
        "[id*=reaction]",
        "[id*=sympathy]",
        "[id*=empathy]",
        "[id*=like]",
        "[id*=dislike]",
        "[id*=rating]",
        "[id*=vote]",
        "[id*=poll]",
        "[id*=advert]"
    )

    private val alwaysRemoveKeywords = listOf(
        "무단 전재",
        "재배포 금지",
        "all rights reserved",
        "copyright"
    )

    private val shortUiKeywords = listOf(
        "자료사진",
        "공유하기",
        "기사공유",
        "페이스북",
        "트위터",
        "카카오톡",
        "URL복사",
        "URL 복사",
        "SNS공유",
        "SNS 공유",
        "추천뉴스",
        "관련기사",
        "관련뉴스",
        "인기뉴스",
        "인기 뉴스",
        "랭킹뉴스",
        "많이본뉴스",
        "많이 본 뉴스",
        "공지사항",
        "댓글",
        "공감",
        "평가",
        "좋아요",
        "훈훈해요",
        "슬퍼요",
        "화나요",
        "후속요청",
        "싫어요",
        "광고",
        "평가하기",
        "사용자평가",
        "사용자 평가",
        "이기사를추천합니다",
        "이 기사를 추천합니다",
        "제보하기",
        "구독하기",
        "알림받기",
        "좋아요",
        "이시각주요뉴스",
        "SBS뉴스 앱 다운로드",
        "SBS 뉴스 앱 다운로드",
        "스브스프리미엄 앱 다운로드",
        "뉴스에 지식을 담다",
        "영상 시청"
    )

    private val reporterPatterns = listOf(
        Regex("^[가-힣]{2,5}\\s*기자\\s*$"),
        Regex("^.+@.+\\..+$"),
        Regex("^(입력|수정)\\s*[:：]?.{0,40}$"),
        Regex("^앵커\\s*[:：]?.{0,40}$")
    )
}
