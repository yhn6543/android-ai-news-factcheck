# Raw News Inspector

This is an isolated helper for manually checking RSS and HTML candidates before
changing the Android app's news parsers.

It is intentionally not part of the Gradle project:

- no files under `app/src/main`
- no imports from the Android app
- Python standard library only
- output goes to `reports/raw-news-inspector/`

## Included Presses

- Yonhap
- MBC
- SBS
- KBS
- YTN

## Usage

From the repository root:

```powershell
python tools\raw-news-inspector\raw_news_inspector.py --all
```

Inspect one or more presses:

```powershell
python tools\raw-news-inspector\raw_news_inspector.py --press mbc --press ytn
```

Each run creates a timestamped directory under:

```text
reports/raw-news-inspector/
```

The run directory contains raw `.xml` / `.html` responses, `summary.json`, and
`summary.md`.

## Policy

This tool is for inspection only. It does not change the app display flow. The
app currently displays news with this order:

1. RSS, including Google News RSS for configured presses
2. limited HTML crawling only if RSS fails
3. not found state if both fail

Search RSS and mock fallback are not used for app display data.
