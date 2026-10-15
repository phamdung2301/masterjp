#!/usr/bin/env python3
"""Trích xuất tất cả chữ kanji từ field `kanji` của seed lessons + suy lessonNos.

Chạy:  python3 scripts/extract-kanji.py

Đọc  :  data/seed/seed-lessons-1-3.json, data/seed/seed-lessons-4-50.json
Ghi  :  /tmp/kanji_extracted.json  (danh sách {character, firstLesson, lessonNos})

File data/seed/kanji.json (biên soạn thủ công, có on/kun kun, số nét, nghĩa...)
sẽ hợp nhất lessonNos từ kết quả này khi build (xem bên dưới).

Lưu ý:
- 1 từ có thể chứa nhiều chữ (vd. "あの 人（あの 方）" -> 人, 方);
  ký tự lặp trong ngoặc chỉ tính 1 lần mỗi chữ (dùng set).
- jlpt: lần đầu xuất hiện ở bài 1-25 -> N5, bài 26-50 -> N4 (quy ước Minna I/II).
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED_FILES = [
    ROOT / "data" / "seed" / "seed-lessons-1-3.json",
    ROOT / "data" / "seed" / "seed-lessons-4-50.json",
]

KANJI_RE = re.compile(r"[\u4e00-\u9fff]")  # CJK Unified Ideographs (không gồm 々 U+3005)


def extract() -> dict:
    """-> {character: set(lessonNo)}"""
    char_lessons: dict = {}
    for f in SEED_FILES:
        data = json.loads(f.read_text(encoding="utf-8"))
        for w in data["words"]:
            lesson_no = w["lessonNo"]
            for ch in set(KANJI_RE.findall(w.get("kanji") or "")):
                char_lessons.setdefault(ch, set()).add(lesson_no)
    return char_lessons


def main() -> None:
    char_lessons = extract()
    items = [
        {
            "character": ch,
            "firstLesson": min(lessons),
            "lessonNos": sorted(lessons),
        }
        for ch, lessons in sorted(char_lessons.items(), key=lambda kv: (min(kv[1]), kv[0]))
    ]
    out_path = Path("/tmp/kanji_extracted.json")
    out_path.write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding="utf-8")
    n5 = sum(1 for it in items if it["firstLesson"] <= 25)
    n4 = len(items) - n5
    print(f"extracted {len(items)} unique kanji chars -> {out_path}")
    print(f"N5 (first seen lesson 1-25): {n5}, N4 (first seen lesson 26-50): {n4}")


if __name__ == "__main__":
    main()
