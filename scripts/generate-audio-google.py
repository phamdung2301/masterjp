#!/usr/bin/env python3
"""Sinh audio MP3 cho toàn bộ từ vựng Master JP bằng Google TTS (miễn phí).

Đọc audioFile + kana từ 2 file seed, tải MP3 vào
backend/src/main/resources/static/audio/{audioFile}.
Idempotent: bỏ qua file đã tồn tại. Chạy lại thoải mái khi bị ngắt.
"""
import json, os, sys, time, urllib.parse, urllib.request

BASE = os.path.expanduser("~/workspace/master-jp")
SEEDS = [f"{BASE}/data/seed/seed-lessons-1-3.json",
         f"{BASE}/data/seed/seed-lessons-4-50.json"]
OUT = f"{BASE}/backend/src/main/resources/static/audio"
os.makedirs(OUT, exist_ok=True)

UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"}

def fetch_mp3(text, retries=4):
    q = urllib.parse.quote(text)
    url = (f"https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob"
           f"&tl=ja&q={q}")
    last = None
    for i in range(retries):
        try:
            req = urllib.request.Request(url, headers=UA)
            with urllib.request.urlopen(req, timeout=25) as r:
                data = r.read()
                if r.status == 200 and len(data) > 1000:
                    return data
                last = f"http={r.status} size={len(data)}"
        except Exception as e:
            last = repr(e)[:120]
        time.sleep(2 * (i + 1))
    raise RuntimeError(f"FAILED after {retries}: {last}")

def main():
    words = []
    for s in SEEDS:
        d = json.load(open(s, encoding="utf-8"))
        words += d["words"]
    total = len(words)
    done = skipped = failed = 0
    fails = []
    for w in words:
        dest = os.path.join(OUT, w["audioFile"])
        if os.path.exists(dest) and os.path.getsize(dest) > 1000:
            skipped += 1
            continue
        try:
            data = fetch_mp3(w["kana"])
            with open(dest, "wb") as f:
                f.write(data)
            done += 1
        except Exception as e:
            failed += 1
            fails.append((w["audioFile"], w["kana"], str(e)[:80]))
        if (done + skipped) % 100 == 0:
            print(f"[{done+skipped}/{total}] done={done} skipped={skipped} failed={failed}", flush=True)
        time.sleep(0.4)
    print(f"XONG: total={total} done={done} skipped={skipped} failed={failed}")
    for a, k, e in fails[:20]:
        print("FAIL:", a, k, e)

if __name__ == "__main__":
    main()
