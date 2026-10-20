#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Import seed ngữ pháp (grammar) Master JP vào MongoDB.

Đọc:  data/seed/grammar-n5-1-10.json, grammar-n5-11-25.json, grammar-n4-26-38.json, grammar-n4-39-50.json
      ({"grammar": [...]}, mỗi điểm đã curate:
      _id (vd. "g01-01"), lessonNo, level ("N5"/"N4"), orderInLesson,
      title, titleVi, pattern, meaningVi, explanation, formation,
      examples[] ({ja, kana, vi}), notes, compareWith[] (list id),
      tags[], active)
Ghi:  grammar — upsert theo _id = điểm _id (string) nên chạy lại an toàn
      (idempotent): chạy N lần cũng không tạo trùng, chỉ cập nhật field.
      Mỗi doc ghi đủ mọi field gốc + `lessonId` (string hex, tra từ
      collection lessons) + `searchNorm` (chuỗi tìm kiếm đã bỏ dấu).

lessonId: resolve từ collection `lessons` bằng query
          {'level': level, 'lessonNo': lessonNo} (level là "N5"/"N4").
          LƯU Ý: nếu thiếu lesson → IN LỖI liệt kê rõ (level, lessonNo) và
          sys.exit, KHÔNG ghi lessonId null, KHÔNG bỏ qua lặng lẽ.

Index theo backend — PHẢI dùng đúng tên như @CompoundIndex trong
domain/Grammar.java (idx_grammar_lesson). Spring auto-index đã tạo
chúng khi backend khởi động; tạo lại cùng tên+keys+options là no-op.
Tên khác mà keys giống → Atlas báo IndexOptionsConflict (code 85).

ENV:
  MONGODB_URI       (default: mongodb://localhost:27017)
  MONGODB_DATABASE  (BẮT BUỘC — fail-fast nếu thiếu, không default ngầm)

Cờ:
  --dry-run   Chỉ kết nối DB + resolve lessonId + in thống kê
              (số điểm theo level/bài, điểm nào thiếu lesson),
              KHÔNG ghi gì vào collection grammar.

Yêu cầu: pip install pymongo

Ví dụ (PowerShell, chạy từ thư mục master-jp):
  $env:MONGODB_URI="mongodb+srv://<user>:<pass>@<cluster>/?retryWrites=true&w=majority"
  $env:MONGODB_DATABASE="masterjp"
  python scripts/seed-grammar.py --dry-run   # kiểm tra trước
  python scripts/seed-grammar.py              # import thật
"""
import argparse
import json
import os
import sys
import unicodedata

try:
    from pymongo import MongoClient, UpdateOne
except ImportError:
    sys.exit('LỖI: chưa cài pymongo. Chạy: pip install pymongo')

MONGODB_URI = os.environ.get('MONGODB_URI', 'mongodb://localhost:27017')
# Fail-fast: KHÔNG default ngầm (từng ghi nhầm DB do silent default —
# xem AGENTS.md bài học MongoDB 2026-10-15).
MONGODB_DATABASE = os.environ.get('MONGODB_DATABASE')
if not MONGODB_DATABASE:
    sys.exit('LỖI: thiếu env MONGODB_DATABASE. Ví dụ PowerShell:\n'
             '    $env:MONGODB_DATABASE="masterjp"\n'
             '    python scripts/seed-grammar.py --dry-run\n'
             '    python scripts/seed-grammar.py')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GRAMMAR_FILES = [
    os.path.join(ROOT, 'data', 'seed', 'grammar-n5-1-10.json'),
    os.path.join(ROOT, 'data', 'seed', 'grammar-n5-11-25.json'),
    os.path.join(ROOT, 'data', 'seed', 'grammar-n4-26-38.json'),
    os.path.join(ROOT, 'data', 'seed', 'grammar-n4-39-50.json'),
]

BATCH = 500


def parse_args():
    p = argparse.ArgumentParser(
        description='Import seed ngữ pháp (grammar) vao MongoDB: upsert theo _id, idempotent.')
    p.add_argument('--dry-run', action='store_true',
                   help='Chỉ kiểm tra (kết nối DB, resolve lessonId, in thống kê), KHÔNG ghi dữ liệu.')
    return p.parse_args()


def normalize_vi(s):
    """Port của VietnameseUtils.normalize (backend) — copy từ seed-import.py:
    bỏ dấu tiếng Việt, đ->d, lowercase, gọn khoảng trắng. Ký tự Nhật (hiragana,
    katakana, kanji) không có dấu tổ hợp nên được giữ nguyên sau NFD."""
    if not s:
        return ''
    n = unicodedata.normalize('NFD', s)
    n = ''.join(c for c in n if not unicodedata.combining(c))
    n = n.replace('đ', 'd').replace('Đ', 'd')
    return ' '.join(n.lower().strip().split())


def build_search_norm(g):
    """Chuỗi tìm kiếm của 1 điểm ngữ pháp: title + titleVi + meaningVi + pattern,
    bỏ dấu tiếng Việt (giữ nguyên ký tự Nhật), phục vụ tìm kiếm 'gõ không dấu vẫn ra'."""
    return normalize_vi(' '.join([
        g.get('title') or '',
        g.get('titleVi') or '',
        g.get('meaningVi') or '',
        g.get('pattern') or '',
    ]))


def load_grammar():
    items = []
    for path in GRAMMAR_FILES:
        if not os.path.exists(path):
            sys.exit(f'LỖI: không tìm thấy {path}. Kiểm tra data/seed/.')
        with open(path, encoding='utf-8') as f:
            d = json.load(f)
        pts = d.get('grammar') or []
        if not pts:
            sys.exit(f'LỖI: {path} không có điểm ngữ pháp nào (key "grammar" rỗng).')
        # Kiểm tra field bắt buộc trước khi import — fail sớm còn hơn ghi thiếu.
        for g in pts:
            for f_ in ('_id', 'lessonNo', 'level', 'orderInLesson', 'title', 'titleVi',
                       'pattern', 'meaningVi'):
                if g.get(f_) in (None, ''):
                    sys.exit(f"LỖI: điểm {g.get('_id', '?')} thiếu field bắt buộc '{f_}'.")
        items.extend(pts)
    return items


def main():
    args = parse_args()
    items = load_grammar()
    print(f'grammar: đã load {len(items)} điểm từ 4 file grammar-*.json')

    client = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=5000)
    try:
        client.admin.command('ping')
    except Exception as e:
        sys.exit(f'LỖI: không kết nối được MongoDB ({MONGODB_URI}): {e}')

    db = client[MONGODB_DATABASE]
    lessons = db['lessons']
    grammar = db['grammar']

    # --- Resolve lessonId từ collection lessons ---
    # Cache (level, lessonNo) -> lessonId (string hex), giống vocab_words.
    lesson_ids = {}
    missing = []  # các (level, lessonNo) không tìm thấy lesson
    for g in items:
        key = (g['level'], g['lessonNo'])
        if key in lesson_ids:
            continue
        doc = lessons.find_one({'level': key[0], 'lessonNo': key[1]},
                               projection={'_id': 1})
        if doc is None:
            missing.append(key)
        else:
            # LƯU Ý: lessonId trong Grammar của backend là String (hex),
            # KHÔNG phải ObjectId — phải lưu string để Spring query được.
            lesson_ids[key] = str(doc['_id'])

    if missing:
        # Fail cứng: liệt kê rõ các bài thiếu, không ghi lessonId null,
        # không bỏ qua lặng lẽ (dữ liệu mồ côi sẽ làm vỡ trang /ngu-phap).
        uniq = sorted(set(missing))
        lines = '\n'.join(f'  - level="{lv}", lessonNo={no}' for lv, no in uniq)
        sys.exit('LỖI: không tìm thấy các bài sau trong collection "lessons" '
                 f'của DB "{MONGODB_DATABASE}":\n{lines}\n'
                 'Hãy chạy scripts/seed-import.py để import lessons 1-50 trước.')

    # --- Thống kê (dùng cho --dry-run và log thật) ---
    from collections import Counter
    by_level = Counter(g['level'] for g in items)
    by_lesson = Counter((g['level'], g['lessonNo']) for g in items)
    print('grammar: thống kê theo level:', dict(by_level))
    print(f'grammar: {len(lesson_ids)} bài đã resolve lessonId OK '
          f'({sum(by_lesson.values())} điểm)')

    if args.dry_run:
        # Không ghi gì, không tạo index — chỉ kiểm tra.
        print('grammar: DRY-RUN — không ghi dữ liệu.')
        print('grammar: phân bố theo bài (level, lessonNo: số điểm):')
        for (lv, no), n in sorted(by_lesson.items()):
            print(f'  - {lv} bài {no}: {n} điểm')
        return

    # Index đúng tên như backend domain/Grammar.java
    # (Spring auto-index tạo khi backend khởi động; tạo lại là no-op.)
    grammar.create_index([('lessonNo', 1), ('orderInLesson', 1)],
                         name='idx_grammar_lesson')

    print('grammar: đang import ...', flush=True)
    ops = []
    for g in items:
        gid = g['_id']
        doc = {
            'lessonId': lesson_ids[(g['level'], g['lessonNo'])],  # string hex
            'lessonNo': g['lessonNo'],
            'level': g['level'],
            'orderInLesson': g['orderInLesson'],
            'title': g['title'],
            'titleVi': g['titleVi'],
            'pattern': g['pattern'],
            'meaningVi': g['meaningVi'],
            'explanation': g.get('explanation'),
            'formation': g.get('formation'),
            'examples': g.get('examples') or [],      # list {ja, kana, vi}
            'notes': g.get('notes'),
            'compareWith': g.get('compareWith') or [],  # list id
            'tags': g.get('tags') or [],
            'active': bool(g.get('active', True)),
            'searchNorm': build_search_norm(g),       # tìm kiếm không dấu
        }
        # _id = id điểm ngữ pháp (string): upsert idempotent, đúng với
        # Grammar.id của backend
        ops.append(UpdateOne({'_id': gid}, {'$set': doc}, upsert=True))

    inserted = updated = 0
    for i in range(0, len(ops), BATCH):
        batch = ops[i:i + BATCH]
        res = grammar.bulk_write(batch, ordered=False)
        inserted += res.upserted_count
        updated += res.modified_count
        print(f'  ... {min(i + BATCH, len(ops))}/{len(ops)} điểm', flush=True)
    print(f'grammar: {inserted} thêm mới, {updated} cập nhật, '
          f'tổng {grammar.count_documents({})} documents trong DB "{MONGODB_DATABASE}"')


if __name__ == '__main__':
    main()
