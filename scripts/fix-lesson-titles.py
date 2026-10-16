#!/usr/bin/env python3
"""fix-lesson-titles.py — R1: strip prefix 'Bài N — ' khỏi titleVi trên DB live.

Chạy MỘT LẦN trên MongoDB live (collection `lessons`).

- FAIL-FAST: env MONGODB_DATABASE bắt buộc, KHÔNG có default ngầm
  (từng ghi nhầm DB do silent default — xem AGENTS.md 2026-10-15).
- Idempotent: chỉ strip khi titleVi bắt đầu ĐÚNG bằng prefix của chính
  lesson đó ('Bài {lessonNo} — '); chạy lại sau khi sửa → 0 thay đổi.
- Chỉ in tóm tắt, không dump toàn bộ tiêu đề.

Ví dụ PowerShell (copy-paste):
    $env:MONGODB_DATABASE="masterjp"
    python scripts/fix-lesson-titles.py
"""
import os
import sys

MONGODB_URI = os.environ.get("MONGODB_URI", "mongodb://localhost:27017")
MONGODB_DATABASE = os.environ.get("MONGODB_DATABASE")
if not MONGODB_DATABASE:
    sys.exit('LỖI: thiếu env MONGODB_DATABASE. Ví dụ PowerShell:\n'
             '    $env:MONGODB_DATABASE="masterjp"\n'
             '    python scripts/fix-lesson-titles.py')

try:
    from pymongo import MongoClient
except ImportError:
    sys.exit("LỖI: chưa cài pymongo. Chạy: pip install pymongo")

DRY_RUN = "--dry-run" in sys.argv


def main() -> None:
    client = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=8000)
    try:
        client.admin.command("ping")
    except Exception as e:
        sys.exit(f"LỖI: không kết nối được MongoDB ({MONGODB_URI}): {e}")

    db = client[MONGODB_DATABASE]
    lessons = db["lessons"]
    docs = list(lessons.find({}, {"lessonNo": 1, "titleVi": 1}))

    fixed = []
    for d in docs:
        t = d.get("titleVi")
        no = d.get("lessonNo")
        if not isinstance(t, str) or no is None:
            continue
        prefix = f"Bài {no} — "
        if t.startswith(prefix):
            new_title = t[len(prefix):]
            fixed.append((no, t, new_title))
            if not DRY_RUN:
                lessons.update_one({"_id": d["_id"]}, {"$set": {"titleVi": new_title}})

    mode = "DRY-RUN" if DRY_RUN else "LIVE"
    print(f"[{mode}] DB: {MONGODB_DATABASE} — quét {len(docs)} lesson, "
          f"đã sửa {len(fixed)} titleVi")
    for no, old, new in fixed[:20]:
        print(f"  Bài {no}: '{old}' -> '{new}'")
    if len(fixed) > 20:
        print(f"  ... và {len(fixed) - 20} bài nữa")
    if not fixed:
        print("Không có lesson nào còn prefix — DB đã sạch.")


if __name__ == "__main__":
    main()
