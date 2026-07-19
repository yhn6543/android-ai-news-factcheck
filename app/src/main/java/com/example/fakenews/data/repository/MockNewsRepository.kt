package com.example.fakenews.data.repository

import com.example.fakenews.data.model.NewsArticle
import com.example.fakenews.data.model.NewsFetchResult
import com.example.fakenews.data.model.NewsPress
import com.example.fakenews.data.model.NewsSourceStatus
import com.example.fakenews.data.model.NewsSourceType
import com.example.fakenews.data.model.ArticleType
import com.example.fakenews.util.ArticleIdentity
import com.example.fakenews.util.ArticleTypeClassifier
import com.example.fakenews.util.NewsFilter
import java.util.Locale

class MockNewsRepository(
    private val articles: List<NewsArticle> = mockArticles
) : NewsRepository {
    override suspend fun getLatestNews(): NewsFetchResult =
        searchNews(
            selectedPresses = NewsPress.articlePresses().toSet(),
            keywords = emptyList()
        )

    override suspend fun searchNews(
        selectedPresses: Set<NewsPress>,
        keywords: List<String>
    ): NewsFetchResult {
        val filteredArticles = NewsFilter.filter(
            articles = articles,
            selectedPresses = selectedPresses,
            keywords = keywords
        )
        val statuses = NewsFilter.normalizeSelectedPresses(selectedPresses)
            .map { press ->
                NewsSourceStatus(
                    press = press,
                    sourceType = NewsSourceType.MOCK,
                    sourceName = "MockNewsRepository",
                    success = filteredArticles.any { article -> article.press == press },
                    articleCount = filteredArticles.count { article -> article.press == press },
                    message = "테스트 데이터 사용"
                )
            }

        return NewsFetchResult(
            articles = filteredArticles,
            sourceStatuses = statuses,
            usedMockFallback = filteredArticles.isNotEmpty(),
            fallbackPresses = statuses
                .filter { status -> status.success }
                .map { status -> status.press },
            message = if (filteredArticles.isEmpty()) null else "테스트 데이터를 표시합니다."
        )
    }

    override suspend fun getNewsArticleById(articleId: String): NewsArticle? =
        articles.firstOrNull { article -> article.id == articleId }

    companion object {
        private const val BASE_PUBLISHED_AT = 1_735_689_600_000L
        private const val HOUR_MILLIS = 3_600_000L

        val mockArticles: List<NewsArticle> = listOf(
            article(
                NewsPress.YONHAP,
                1,
                0,
                "영상 재생 샘플 경제 점검 제목검색토큰",
                "시장 흐름",
                "경제 지표",
                "경제",
                true,
                "https://storage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            ),
            article(NewsPress.YONHAP, 2, 6, "선거 제도", "투표 절차", "시민 참여", "선거", false),
            article(NewsPress.YONHAP, 3, 12, "기후 자료", "기후 변화 요약검색토큰", "환경 분석", "기후", true),
            article(NewsPress.YONHAP, 4, 18, "데이터 정책", "공개 데이터", "본문검색토큰 행정 데이터", "데이터", false),
            article(NewsPress.YONHAP, 5, 24, "AI 활용", "기술 실험", "인공지능 도구", "키워드검색토큰", true),
            article(NewsPress.YONHAP, 6, 30, "보건 안내", "건강 정보", "예방 수칙", "보건", false),
            article(NewsPress.YONHAP, 7, 36, "문화 행사", "전시 일정", "지역 문화", "문화", true),

            article(NewsPress.MBC, 1, 1, "사회 이슈", "공공 서비스", "생활 변화", "사회", true),
            article(NewsPress.MBC, 2, 7, "교육 현장", "학교 일정", "학습 지원", "교육", false),
            article(NewsPress.MBC, 3, 13, "주거 정책", "임대 정보", "주택 제도", "주거", true),
            article(NewsPress.MBC, 4, 19, "교통 개선", "대중교통", "도로 안전", "교통", false),
            article(NewsPress.MBC, 5, 25, "복지 안내", "지원 대상", "신청 절차", "복지", true),
            article(NewsPress.MBC, 6, 31, "의료 체계", "진료 정보", "건강 관리", "의료", false),
            article(NewsPress.MBC, 7, 37, "날씨 변화", "기온 흐름", "생활 날씨", "날씨", true),

            article(
                NewsPress.SBS,
                1,
                2,
                "영상 재생 샘플 기술 트렌드",
                "서비스 실험",
                "디지털 전환",
                "기술",
                true,
                "https://storage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
            ),
            article(NewsPress.SBS, 2, 8, "금융 생활", "가계 흐름", "소비 계획", "금융", false),
            article(NewsPress.SBS, 3, 14, "스포츠 일정", "경기 준비", "팀 소식", "스포츠", true),
            article(NewsPress.SBS, 4, 20, "환경 보호", "재활용 정책", "친환경 생활", "환경", false),
            article(NewsPress.SBS, 5, 26, "스타트업 동향", "창업 지원", "사업 모델", "스타트업", true),
            article(NewsPress.SBS, 6, 32, "소비자 정보", "상품 비교", "가격 동향", "소비자", false),
            article(NewsPress.SBS, 7, 38, "안전 점검", "시설 관리", "예방 훈련", "안전", true),

            article(NewsPress.KBS, 1, 3, "국제 정세", "외교 일정", "세계 동향", "국제", true),
            article(NewsPress.KBS, 2, 9, "농업 기술", "작황 정보", "농가 지원", "농업", false),
            article(NewsPress.KBS, 3, 15, "과학 실험", "연구 과정", "실험 데이터", "과학", true),
            article(NewsPress.KBS, 4, 21, "지역 소식", "지자체 계획", "생활권 변화", "지역", false),
            article(NewsPress.KBS, 5, 27, "예산 안내", "재정 계획", "사업 편성", "예산", true),
            article(NewsPress.KBS, 6, 33, "디지털 행정", "온라인 민원", "공공 플랫폼", "디지털", false),
            article(NewsPress.KBS, 7, 39, "관광 자원", "여행 코스", "지역 명소", "관광", true),

            article(
                NewsPress.YTN,
                1,
                4,
                "정치 현장 점검",
                "국회 일정",
                "정책 논의",
                "정치",
                true,
                "https://storage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"
            ),
            article(NewsPress.YTN, 2, 10, "사회 안전 점검", "현장 대응", "안전 대책", "사회", false),
            article(NewsPress.YTN, 3, 16, "경제 지표 분석", "시장 반응", "경제 흐름", "경제", true),
            article(NewsPress.YTN, 4, 22, "국제 회의 소식", "외교 현안", "국제 관계", "국제", false),
            article(NewsPress.YTN, 5, 28, "과학 기술 동향", "연구 성과", "기술 변화", "과학", true),
            article(NewsPress.YTN, 6, 34, "문화 행사 안내", "공연 일정", "문화 현장", "문화", false),
            article(NewsPress.YTN, 7, 40, "날씨 변화 전망", "기온 흐름", "생활 기상", "날씨", true)
        )

        private fun article(
            press: NewsPress,
            number: Int,
            offsetHours: Long,
            titleTopic: String,
            summaryTopic: String,
            contentTopic: String,
            keyword: String,
            hasImage: Boolean,
            videoUrl: String? = null
        ): NewsArticle {
            val pressId = press.name.lowercase(Locale.ROOT)
            val title = "[목업] ${press.displayName} 테스트 기사 $number - $titleTopic"
            val summary = "실제 뉴스가 아닌 테스트용 요약입니다. $summaryTopic 항목을 확인합니다."
            val bodyParagraphs = listOf(
                "이 본문은 앱 개발 검증을 위한 mock 데이터입니다. $contentTopic 내용을 테스트합니다.",
                "카드와 상세 화면에서 문단 간격, 긴 본문 표시, 영상 기사 본문 노출을 확인하기 위한 추가 문단입니다."
            )
            val originalUrl = mockOriginalUrl(press, pressId, number)
            val articleType = ArticleTypeClassifier.classify(
                title = title,
                originalUrl = originalUrl,
                bodyText = bodyParagraphs.joinToString(" "),
                metaText = summary,
                videoUrl = videoUrl
            )
            return NewsArticle(
                id = ArticleIdentity.idFor(press, originalUrl, title),
                title = title,
                press = press,
                publishedAt = BASE_PUBLISHED_AT - (offsetHours * HOUR_MILLIS),
                summary = summary,
                content = bodyParagraphs.joinToString("\n\n"),
                bodyParagraphs = bodyParagraphs,
                imageUrl = if (hasImage) "https://example.com/mock-news/$pressId/$number.jpg" else null,
                videoUrl = videoUrl,
                originalUrl = originalUrl,
                keywords = listOf(keyword, press.displayName, "목업", "테스트"),
                sourceType = NewsSourceType.MOCK,
                sourceLabel = NewsSourceType.MOCK.displayName,
                articleType = articleType,
                isVideoNews = videoUrl != null || articleType == ArticleType.VIDEO_NEWS_ARTICLE
            )
        }

        private fun mockOriginalUrl(
            press: NewsPress,
            pressId: String,
            number: Int
        ): String =
            when (press) {
                NewsPress.YONHAP -> "https://www.yna.co.kr/view/MOCK$number"
                NewsPress.MBC -> "https://imnews.imbc.com/news/2026/mock/article$number.html"
                NewsPress.SBS -> "https://news.sbs.co.kr/news/endPage.do?news_id=mock$number"
                NewsPress.KBS -> "https://news.kbs.co.kr/news/pc/view/view.do?ncd=mock$number"
                NewsPress.YTN -> "https://www.ytn.co.kr/_ln/0101_mock$number"
                NewsPress.ALL -> "https://example.com/mock-news/$pressId/$number"
            }
    }
}
