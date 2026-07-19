#!/usr/bin/env python3
"""Fetch raw news RSS/HTML candidates for manual inspection.

This tool is intentionally isolated from the Android app. It uses only Python
standard-library modules and writes raw responses plus summaries under
reports/raw-news-inspector/.
"""

from __future__ import annotations

import argparse
import datetime as dt
import html.parser
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any
from xml.etree import ElementTree as ET


PRESS_URLS: dict[str, dict[str, list[str]]] = {
    "yonhap": {
        "rss": ["https://www.yna.co.kr/rss/news.xml"],
        "html": ["https://www.yna.co.kr/news"],
    },
    "mbc": {
        "rss": [
            "https://news.google.com/rss/search?q=site:imnews.imbc.com%20MBC&hl=ko&gl=KR&ceid=KR:ko",
        ],
        "html": ["https://imnews.imbc.com/news/2026/society/"],
    },
    "sbs": {
        "rss": [
            "https://news.sbs.co.kr/news/newsflashRssFeed.do?plink=RSSREADER",
            "https://news.sbs.co.kr/news/headlineRssFeed.do?plink=RSSREADER",
        ],
        "html": ["https://news.sbs.co.kr/news/newsflash.do"],
    },
    "kbs": {
        "rss": [
            "https://news.google.com/rss/search?q=site:news.kbs.co.kr%20KBS&hl=ko&gl=KR&ceid=KR:ko"
        ],
        "html": ["https://news.kbs.co.kr/news/pc/main/main.html"],
    },
    "ytn": {
        "rss": [
            "https://news.google.com/rss/search?q=site:ytn.co.kr%20YTN&hl=ko&gl=KR&ceid=KR:ko"
        ],
        "html": ["https://www.ytn.co.kr/news/list.php"],
    },
}

USER_AGENT = "FakeNewsRawInspector/1.0 (+manual educational inspection)"
TIMEOUT_SECONDS = 15


class HtmlSummaryParser(html.parser.HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self._in_title = False
        self._title_parts: list[str] = []
        self.meta: dict[str, str] = {}
        self.links: list[str] = []
        self.images: list[str] = []

    @property
    def title(self) -> str:
        return normalize_space(" ".join(self._title_parts))

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attrs_dict = {key.lower(): value or "" for key, value in attrs}
        if tag.lower() == "title":
            self._in_title = True
        if tag.lower() == "meta":
            key = attrs_dict.get("property") or attrs_dict.get("name")
            content = attrs_dict.get("content")
            if key and content:
                self.meta[key.lower()] = content
        if tag.lower() == "a":
            href = attrs_dict.get("href")
            if href:
                self.links.append(href)
        if tag.lower() == "img":
            src = attrs_dict.get("src")
            if src:
                self.images.append(src)

    def handle_endtag(self, tag: str) -> None:
        if tag.lower() == "title":
            self._in_title = False

    def handle_data(self, data: str) -> None:
        if self._in_title:
            self._title_parts.append(data)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Fetch raw RSS/HTML news candidates.")
    parser.add_argument("--all", action="store_true", help="Inspect every configured press.")
    parser.add_argument(
        "--press",
        action="append",
        choices=sorted(PRESS_URLS.keys()),
        help="Inspect one press. Can be passed multiple times.",
    )
    args = parser.parse_args(argv)

    selected_presses = sorted(PRESS_URLS.keys()) if args.all else args.press
    if not selected_presses:
        parser.error("Use --all or --press.")

    run_dir = report_root() / dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    run_dir.mkdir(parents=True, exist_ok=True)

    records: list[dict[str, Any]] = []
    for press in selected_presses:
        for kind, urls in PRESS_URLS[press].items():
            for index, url in enumerate(urls, start=1):
                record = inspect_url(
                    press=press,
                    kind=kind,
                    index=index,
                    url=url,
                    run_dir=run_dir,
                )
                records.append(record)
                print(f"{press} {kind} {record['status'] or 'ERR'} {url}")

    write_summary(run_dir, records)
    print(f"Saved raw inspection files to {run_dir}")
    return 0


def inspect_url(
    press: str,
    kind: str,
    index: int,
    url: str,
    run_dir: Path,
) -> dict[str, Any]:
    response = fetch_url(url)
    suffix = "xml" if kind == "rss" else "html"
    status_for_name = response["status"] if response["status"] is not None else "error"
    raw_path = run_dir / f"{press}_{kind}_{index:02d}_{status_for_name}.{suffix}"

    body = response["body"]
    if body:
        raw_path.write_bytes(body)

    text = decode_body(body, response.get("content_type", ""))
    parsed = parse_rss(text) if kind == "rss" else parse_html(text)

    return {
        "press": press,
        "kind": kind,
        "index": index,
        "url": url,
        "status": response["status"],
        "content_type": response.get("content_type", ""),
        "byte_count": len(body),
        "raw_path": str(raw_path.relative_to(repo_root())) if body else None,
        "error": response.get("error"),
        "parsed": parsed,
    }


def fetch_url(url: str) -> dict[str, Any]:
    request = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "application/rss+xml, application/xml, text/xml, text/html, */*",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            return {
                "status": getattr(response, "status", None),
                "content_type": response.headers.get("content-type", ""),
                "body": response.read(),
                "error": None,
            }
    except urllib.error.HTTPError as error:
        return {
            "status": error.code,
            "content_type": error.headers.get("content-type", "") if error.headers else "",
            "body": error.read(),
            "error": str(error),
        }
    except urllib.error.URLError as error:
        return {
            "status": None,
            "content_type": "",
            "body": b"",
            "error": str(error.reason),
        }


def parse_rss(text: str) -> dict[str, Any]:
    if not text.strip():
        return {"item_count": 0, "items": [], "error": "empty response"}

    try:
        root = ET.fromstring(text)
    except ET.ParseError as error:
        return {"item_count": 0, "items": [], "error": str(error)}

    items = [element for element in root.iter() if local_name(element.tag) in {"item", "entry"}]
    summaries = []
    for item in items[:5]:
        description = child_text(item, "description", "summary", "content", "encoded")
        summaries.append(
            {
                "title": child_text(item, "title"),
                "link": child_link(item),
                "pubDate": child_text(item, "pubDate", "published", "updated"),
                "descriptionPreview": normalize_space(strip_tags(description))[:180],
                "imageUrl": item_image_url(item, description),
            }
        )

    return {"item_count": len(items), "items": summaries, "error": None}


def parse_html(text: str) -> dict[str, Any]:
    if not text.strip():
        return {"title": "", "link_count": 0, "image_count": 0, "error": "empty response"}

    parser = HtmlSummaryParser()
    try:
        parser.feed(text)
    except Exception as error:  # HTMLParser is forgiving, but keep the tool resilient.
        return {"title": "", "link_count": 0, "image_count": 0, "error": str(error)}

    return {
        "title": parser.title,
        "ogTitle": parser.meta.get("og:title", ""),
        "ogUrl": parser.meta.get("og:url", ""),
        "link_count": len(parser.links),
        "sample_links": parser.links[:10],
        "image_count": len(parser.images),
        "sample_images": parser.images[:5],
        "error": None,
    }


def write_summary(run_dir: Path, records: list[dict[str, Any]]) -> None:
    summary_json = run_dir / "summary.json"
    summary_json.write_text(
        json.dumps(records, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    lines = [
        "# Raw News Inspector Summary",
        "",
        f"- Created: {dt.datetime.now().isoformat(timespec='seconds')}",
        f"- Records: {len(records)}",
        "",
    ]
    for record in records:
        parsed = record["parsed"]
        lines += [
            f"## {record['press']} / {record['kind']} / {record['status'] or 'ERR'}",
            "",
            f"- URL: {record['url']}",
            f"- Bytes: {record['byte_count']}",
            f"- Raw: {record['raw_path'] or 'not saved'}",
            f"- Error: {record['error'] or parsed.get('error') or 'none'}",
        ]
        if record["kind"] == "rss":
            lines.append(f"- Parsed items: {parsed.get('item_count', 0)}")
            for item in parsed.get("items", [])[:3]:
                lines.append(f"  - {item.get('title', '')} | {item.get('link', '')}")
        else:
            lines.append(f"- HTML title: {parsed.get('title', '')}")
            lines.append(f"- Links/images: {parsed.get('link_count', 0)} / {parsed.get('image_count', 0)}")
        lines.append("")

    (run_dir / "summary.md").write_text("\n".join(lines), encoding="utf-8")


def child_text(element: ET.Element, *names: str) -> str:
    wanted = set(names)
    for child in list(element):
        if local_name(child.tag) in wanted:
            return normalize_space("".join(child.itertext()))
    return ""


def child_link(element: ET.Element) -> str:
    text_link = child_text(element, "link")
    if text_link:
        return text_link
    for child in list(element):
        if local_name(child.tag) == "link":
            return child.attrib.get("href", "")
    return ""


def item_image_url(element: ET.Element, description: str) -> str:
    for child in list(element):
        name = local_name(child.tag)
        if name in {"enclosure", "content", "thumbnail"}:
            url = child.attrib.get("url") or child.attrib.get("href")
            if url:
                return url
    match = re.search(r"<img[^>]+src=['\"]([^'\"]+)['\"]", description, re.IGNORECASE)
    return match.group(1) if match else ""


def local_name(tag: str) -> str:
    return tag.rsplit("}", 1)[-1].split(":", 1)[-1]


def decode_body(body: bytes, content_type: str) -> str:
    match = re.search(r"charset=([^;\s]+)", content_type, re.IGNORECASE)
    encodings = [match.group(1)] if match else []
    encodings += ["utf-8", "euc-kr", "cp949"]
    for encoding in encodings:
        try:
            return body.decode(encoding)
        except (LookupError, UnicodeDecodeError):
            continue
    return body.decode("utf-8", errors="replace")


def strip_tags(text: str) -> str:
    return re.sub(r"<[^>]+>", " ", text)


def normalize_space(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def report_root() -> Path:
    return repo_root() / "reports" / "raw-news-inspector"


if __name__ == "__main__":
    sys.exit(main())
