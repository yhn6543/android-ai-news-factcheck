# News Integrity Report

- Status: PASS
- Failures: 0
- Warnings: 0
- Mock fallback presses: none
- Unavailable presses: none
- originalUrl domain mismatch count: 0
- Non-article page visible count: 0
- Boilerplate paragraphs detected/removed: 0
- Duplicate normalizedOriginalUrl count: 0
- Card-detail-originalUrl mismatch count: 0
- Missing publishedAt count: 0
- publishedAt without source count: 0

## Collection Summary

| Press | RSS | Crawling | Unavailable |
|---|---|---|---:|
| 연합뉴스 | success (5) | not run | no |
| MBC | success (5): Google News RSS 5 articles collected | not run | no |
| SBS | success (5) | not run | no |
| KBS | success (5): RSS 5 articles collected | not run | no |
| YTN | success (5): Google News RSS 5 articles collected | not run | no |

## Press Summary

| Press | Status | Articles | Warnings | Fails | Boilerplate |
|---|---:|---:|---:|---:|---:|
| 연합뉴스 | PASS | 1 | 0 | 0 | 0 |
| MBC | PASS | 1 | 0 | 0 | 0 |
| SBS | PASS | 1 | 0 | 0 | 0 |
| KBS | PASS | 1 | 0 | 0 | 0 |
| YTN | PASS | 1 | 0 | 0 | 0 |

## Issues

No issues.


## Targeted Validation

- Removed press references in active NewsPress list: none
- KBS RSS order: official RSS -> Google News RSS fallback
- KBS official RSS URL: http://news.kbs.co.kr/rss/kbsrss_news.php?site=news
- KBS Google News RSS query count: 2
  - https://news.google.com/rss/search?q=site:news.kbs.co.kr%20KBS&hl=ko&gl=KR&ceid=KR:ko
  - https://news.google.com/rss/search?q=site:news.kbs.co.kr/news/mobile/view/view.do&hl=ko&gl=KR&ceid=KR:ko
- KBS excluded live/onair/program/replay count: 4
- KBS final valid article count: 1
- KBS original URL policy valid: yes
- YTN source: Google News RSS only
- YTN Google News RSS query: https://news.google.com/rss/search?q=site:ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko
- YTN Google redirect resolve success/failure: 4/1
- YTN final valid article count: 1
- YTN allowed domain valid: yes
- YTN original URL policy valid: yes
- YTN Google redirect stored as originalUrl: no
- All press invalid article count: 0
- Unavailable presses: none
- Original URL domain validation: yes
- Remaining WARNING/FAIL: warnings=0, failures=0

| YTN title | originalUrl | domain valid | card-detail id match | card-detail originalUrl match | open original URL match |
|---|---|---:|---:|---:|---:|
| YTN policy field report confirms response | https://www.ytn.co.kr/_ln/0101_202606110001 | yes | yes | yes | yes |

## YTN Debug Section

- YTN raw RSS item count: 5
- YTN resolve success count: 4
- YTN resolve failure count: 1
- YTN domain mismatch count: 1
- YTN articleUrlPolicy mismatch count: 1
- YTN title_not_article_like count: 1
- YTN title valid/invalid count: 4/1
- YTN RSS final display item count: 1
- YTN final display article count: 1
- YTN repository final article count: not recorded
- YTN UI state article count: not recorded

### YTN Feed URLs
- https://news.google.com/rss/search?q=site:ytn.co.kr/_ln/&hl=ko&gl=KR&ceid=KR:ko

### Runtime Stage Counts
- not recorded

### Excluded Items
- #2 reason=title_not_article_like rawTitle=YTN resolvedUrl=https://www.ytn.co.kr/_ln/0101_202606110002
  - rawLink=https://news.google.com/rss/articles/ytn-title-only host=www.ytn.co.kr path=/_ln/0101_202606110001 allowedDomain=true policy=true titleValid=false
- #3 reason=redirect_resolve_failed rawTitle=YTN unresolved redirect resolvedUrl=
  - rawLink=https://news.google.com/rss/articles/ytn-unresolved host=www.ytn.co.kr path=/_ln/0101_202606110001 allowedDomain=false policy=false titleValid=true
- #4 reason=domain_mismatch rawTitle=Other press item resolvedUrl=https://imnews.imbc.com/news/2026/society/article.html
  - rawLink=https://news.google.com/rss/articles/ytn-domain-mismatch host=imnews.imbc.com path=/news/2026/society/article.html allowedDomain=false policy=false titleValid=true
- #5 reason=ytn_missing_ln_path rawTitle=YTN list page item resolvedUrl=https://www.ytn.co.kr/news/list.php
  - rawLink=https://news.google.com/rss/articles/ytn-list host=www.ytn.co.kr path=/news/list.php allowedDomain=true policy=false titleValid=true

### Final Display Items
- id=article-ytn-50bc8120 title=YTN policy field report confirms response originalUrl=https://www.ytn.co.kr/_ln/0101_202606110001 publishedAt=1780000000005 bodyPreviewLength=88 cardDetailOriginalUrlMatch=true

### Raw Item Trace
| # | raw title | raw link | pubDate | description length | resolvedUrl | resolve | host | path | domain | policy | cleanedTitle | titleValid | detailFetch | extractedTitle | paragraphs | contentLength | boilerplateRemoved | final |
|---:|---|---|---|---:|---|---:|---|---|---:|---:|---|---:|---:|---|---:|---:|---:|---:|
| 1 | YTN policy field report confirms response | https://news.google.com/rss/articles/ytn-final | Fri, 12 Jun 2026 12:00:00 +0900 | 96 | https://www.ytn.co.kr/_ln/0101_202606110001 | yes | www.ytn.co.kr | /_ln/0101_202606110001 | yes | yes | YTN policy field report confirms response | yes | yes | YTN policy field report confirms response | 2 | 88 | 0 | yes |
| 2 | YTN | https://news.google.com/rss/articles/ytn-title-only | Fri, 12 Jun 2026 12:00:00 +0900 | 96 | https://www.ytn.co.kr/_ln/0101_202606110002 | yes | www.ytn.co.kr | /_ln/0101_202606110001 | yes | yes | YTN | no | no |  | 0 | 0 | 0 | no |
| 3 | YTN unresolved redirect | https://news.google.com/rss/articles/ytn-unresolved | Fri, 12 Jun 2026 12:00:00 +0900 | 96 |  | no | www.ytn.co.kr | /_ln/0101_202606110001 | no | no | YTN unresolved redirect | yes | no |  | 0 | 0 | 0 | no |
| 4 | Other press item | https://news.google.com/rss/articles/ytn-domain-mismatch | Fri, 12 Jun 2026 12:00:00 +0900 | 96 | https://imnews.imbc.com/news/2026/society/article.html | yes | imnews.imbc.com | /news/2026/society/article.html | no | no | Other press item | yes | no |  | 0 | 0 | 0 | no |
| 5 | YTN list page item | https://news.google.com/rss/articles/ytn-list | Fri, 12 Jun 2026 12:00:00 +0900 | 96 | https://www.ytn.co.kr/news/list.php | yes | www.ytn.co.kr | /news/list.php | yes | no | YTN list page item | yes | no |  | 0 | 0 | 0 | no |
