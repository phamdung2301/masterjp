#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sinh file audio MP3 cho seed lessons 1-3 bằng edge-tts.

  pip install edge-tts
  python3 scripts/generate-audio.py            # sinh toàn bộ 3 bài
  python3 scripts/generate-audio.py --lesson 1  # chỉ 1 bài (khuyến nghị chạy thử bài 1 trước)

- Voice: ja-JP-NanamiNeural
- Text đọc: kana của từ; cụm từ (PHRASE) đọc cả cụm.
- Làm sạch text trước khi đọc: bỏ phương án đọc trong ngoặc （…）,
  bỏ dấu ［］, bỏ ký tự giữ chỗ ～.
- Output: backend/src/main/resources/static/audio/{audioFile} (vd. b01-001.mp3)
- Chạy lại an toàn: bỏ qua file đã tồn tại (resume được).

LƯU Ý: phase 1 CHƯA cần chạy full. Hãy chạy thử 1 bài trên máy có mạng
bình thường trước (sandbox chặn TLS tới speech.platform.bing.com nên
không chạy được ở đó).
"""
import argparse
import asyncio
import json
import os
import re
import sys
from typing import Optional

VOICE = 'ja-JP-NanamiNeural'

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SEED_FILE = os.path.join(ROOT, 'data', 'seed', 'seed-lessons-1-3.json')
AUDIO_DIR = os.path.join(ROOT, 'backend', 'src', 'main', 'resources', 'static', 'audio')


def tts_text(kana: str) -> str:
    """Text đưa cho edge-tts: kana đã làm sạch."""
    t = kana or ''
    t = re.sub(r'（[^）]*）', '', t)   # bỏ phương án đọc thay thế trong ngoặc
    t = t.replace('［', '').replace('］', '')
    t = t.replace('～', '').replace('~', '')  # ký tự giữ chỗ (～さん -> さん)
    t = re.sub(r'\s+', ' ', t).strip()
    return t


async def synth(text: str, out_path: str):
    import edge_tts
    await edge_tts.Communicate(text, VOICE).save(out_path)


async def main_async(only_lesson: Optional[int]):
    try:
        import edge_tts  # noqa: F401
    except ImportError:
        sys.exit('LỖI: chưa cài edge-tts. Chạy: pip install edge-tts')

    with open(SEED_FILE, encoding='utf-8') as f:
        data = json.load(f)

    words = [w for w in data['words']
             if only_lesson is None or w['lessonNo'] == only_lesson]
    if not words:
        sys.exit(f'LỖI: không có từ nào cho bài {only_lesson}')

    os.makedirs(AUDIO_DIR, exist_ok=True)

    done = skipped = failed = 0
    cur_lesson = None
    for w in words:
        if w['lessonNo'] != cur_lesson:
            cur_lesson = w['lessonNo']
            print(f'--- Bài {cur_lesson} ---')
        out = os.path.join(AUDIO_DIR, w['audioFile'])
        if os.path.exists(out) and os.path.getsize(out) > 0:
            skipped += 1
            continue
        text = tts_text(w['kana'])
        if not text:
            print(f"  BỎ QUA {w['audioFile']}: kana rỗng sau khi làm sạch")
            failed += 1
            continue
        try:
            await synth(text, out)
            done += 1
            print(f'  OK {w["audioFile"]}  ({text})')
        except Exception as e:  # lỗi mạng / voice -> báo và đi tiếp
            failed += 1
            print(f'  LỖI {w["audioFile"]}: {e}')

    print(f'\nXong: {done} file mới, {skipped} đã có (bỏ qua), {failed} lỗi, '
          f'tổng {len(words)} mục.')


def main():
    ap = argparse.ArgumentParser(description='Sinh audio MP3 cho seed lessons 1-3 (edge-tts).')
    ap.add_argument('--lesson', type=int, default=None,
                    help='chỉ sinh cho 1 bài (1, 2 hoặc 3); mặc định sinh cả 3 bài')
    args = ap.parse_args()
    asyncio.run(main_async(args.lesson))


if __name__ == '__main__':
    main()
