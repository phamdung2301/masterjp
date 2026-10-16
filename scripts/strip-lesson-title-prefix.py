#!/usr/bin/env python3
"""Strip leading 'Bài N — ' prefix from lesson titleVi in seed JSON files.

Idempotent: only touches lines that START with '"titleVi": "Bài <digits> — '
— already-stripped files produce zero matches. Uses a raw-text regex so the
original JSON formatting (indents, trailing newlines) is preserved byte-for-byte.

Targets (all under this repo's data/seed/):
  - seed-lessons-1-3.json (lessons 1-3)
  - lessons/lesson-*.json   (lessons 4-50)
NOT touched: seed-lessons-4-50.json (kept as-is; seed-import.py strips at
import time and fix-lesson-titles.py fixes the live DB).
"""
import glob
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED_DIR = os.path.join(ROOT, "data", "seed")

# "titleVi": "Bài 12 — So sánh"  ->  "titleVi": "So sánh"
PAT = re.compile(r'^(\s*"titleVi":\s*)"Bài \d+ — (.*)"(,?)$', re.MULTILINE)


def main() -> None:
    files = [os.path.join(SEED_DIR, "seed-lessons-1-3.json")] + sorted(
        glob.glob(os.path.join(SEED_DIR, "lessons", "lesson-*.json"))
    )
    total_files = 0
    total_titles = 0
    for path in files:
        with open(path, encoding="utf-8") as f:
            src = f.read()
        new, n = PAT.subn(lambda m: f'{m.group(1)}"{m.group(2)}"{m.group(3)}', src)
        if n:
            with open(path, "w", encoding="utf-8") as f:
                f.write(new)
            total_files += 1
            total_titles += n
        print(f"{'STRIPPED' if n else 'ok      '} "
              f"{os.path.relpath(path, ROOT)} ({n})")
    print(f"\nTotal: {total_titles} lesson titles stripped in {total_files} files "
          f"(idempotent — re-run strips 0)")


if __name__ == "__main__":
    main()
