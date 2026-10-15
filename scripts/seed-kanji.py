#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Import seed kanji (F-B, Feature Pack 2) vào MongoDB.

Đọc:  data/seed/kanji-n5.json, data/seed/kanji-n4.json
      ({"kanji": [...]}, mỗi chữ đã curate:
      character, onYomi[], kunYomi[], meaningVi, strokes, jlpt, lessonNos[])
Ghi:  kanji  — upsert theo _id = character (string) nên chạy lại an toàn
      (idempotent): chạy N lần cũng không tạo trùng, chỉ cập nhật field.

Index theo backend — PHẢI dùng đúng tên như @CompoundIndex trong
domain/Kanji.java (idx_kanji_lesson, idx_kanji_jlpt). Spring auto-index đã
tạo chúng khi backend khởi động; tạo lại cùng tên+keys+options là no-op.
Tên khác mà keys giống → Atlas báo IndexOptionsConflict (code 85).

ENV:
  MONGODB_URI       (default: mongodb://localhost:27017)
  MONGODB_DATABASE  (default: master_jp)

Yêu cầu: pip install pymongo

Ví dụ (PowerShell, chạy từ thư mục master-jp):
  $env:MONGODB_URI="mongodb+srv://<user>:<pass>@<cluster>/?retryWrites=true&w=majority"
  $env:MONGODB_DATABASE="masterjp"
  python scripts/seed-kanji.py
"""
import argparse
import json
import os
import sys

try:
    from pymongo import MongoClient, UpdateOne
except ImportError:
    sys.exit('LỖI: chưa cài pymongo. Chạy: pip install pymongo')

MONGODB_URI = os.environ.get('MONGODB_URI', 'mongodb://localhost:27017')
MONGODB_DATABASE = os.environ.get('MONGODB_DATABASE', 'master_jp')

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KANJI_FILES = [
    os.path.join(ROOT, 'data', 'seed', 'kanji-n5.json'),
    os.path.join(ROOT, 'data', 'seed', 'kanji-n4.json'),
]

BATCH = 500


def parse_args():
    p = argparse.ArgumentParser(
        description='Import seed kanji (F-B) vao MongoDB: upsert theo _id = character, idempotent.')
    return p.parse_args()


def load_kanji():
    # Data chia 2 file theo JLPT (kanji-n5.json, kanji-n4.json) để dễ quản lý.
    items = []
    for path in KANJI_FILES:
        if not os.path.exists(path):
            sys.exit(f'LỖI: không tìm thấy {path}. Kiểm tra data/seed/.')
        with open(path, encoding='utf-8') as f:
            d = json.load(f)
        items.extend(d.get('kanji', []))
    if not items:
        sys.exit('LỖI: data/seed/kanji-*.json không có chữ nào.')
    return items


def main():
    parse_args()
    items = load_kanji()
    print(f'kanji: đã load {len(items)} chữ từ kanji-n5.json + kanji-n4.json')

    client = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=5000)
    try:
        client.admin.command('ping')
    except Exception as e:
        sys.exit(f'LỖI: không kết nối được MongoDB ({MONGODB_URI}): {e}')

    db = client[MONGODB_DATABASE]
    kanji = db['kanji']

    # Index đúng tên như backend domain/Kanji.java
    kanji.create_index([('lessonNos', 1)], name='idx_kanji_lesson')
    kanji.create_index([('jlpt', 1)], name='idx_kanji_jlpt')

    print('kanji: đang import ...', flush=True)
    ops = []
    for k in items:
        ch = k['character']
        doc = {
            'character': ch,
            'onYomi': k.get('onYomi') or [],
            'kunYomi': k.get('kunYomi') or [],
            'meaningVi': k.get('meaningVi'),
            'strokes': k.get('strokes', 0),
            'jlpt': k.get('jlpt'),
            'lessonNos': k.get('lessonNos') or [],
        }
        # _id = character (string): upsert idempotent, đúng với Kanji.id của backend
        ops.append(UpdateOne({'_id': ch}, {'$set': doc}, upsert=True))

    inserted = updated = 0
    for i in range(0, len(ops), BATCH):
        batch = ops[i:i + BATCH]
        res = kanji.bulk_write(batch, ordered=False)
        inserted += res.upserted_count
        updated += res.modified_count
        print(f'  ... {min(i + BATCH, len(ops))}/{len(ops)} chữ', flush=True)
    print(f'kanji: {inserted} thêm mới, {updated} cập nhật, '
          f'tổng {kanji.count_documents({})} documents trong DB "{MONGODB_DATABASE}"')


if __name__ == '__main__':
    main()
