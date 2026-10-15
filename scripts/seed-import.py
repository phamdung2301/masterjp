#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Import seed lessons 1-50 (Master JP) vào MongoDB.

Đọc:  data/seed/seed-lessons-1-3.json
      data/seed/seed-lessons-4-50.json
Ghi:  lessons      — upsert theo (level, lessonNo)
      vocab_words  — upsert theo (lessonId, kana, section)

Chạy lại an toàn (idempotent): chạy N lần cũng không tạo trùng.

ENV:
  MONGODB_URI       (default: mongodb://localhost:27017)
  MONGODB_DATABASE  (default: master_jp)

Yêu cầu: pip install pymongo
"""
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
SEED_FILES = [
    os.path.join(ROOT, 'data', 'seed', 'seed-lessons-1-3.json'),
    os.path.join(ROOT, 'data', 'seed', 'seed-lessons-4-50.json'),
]


def load_seeds():
    data = {'lessons': [], 'words': []}
    for path in SEED_FILES:
        if not os.path.exists(path):
            print(f'CẢNH BÁO: không tìm thấy {path}, bỏ qua.')
            continue
        with open(path, encoding='utf-8') as f:
            d = json.load(f)
        data['lessons'].extend(d['lessons'])
        data['words'].extend(d['words'])
    if not data['lessons']:
        sys.exit('LỖI: không có file seed nào. Kiểm tra data/seed/.')
    return data


def main():
    data = load_seeds()

    client = MongoClient(MONGODB_URI, serverSelectionTimeoutMS=5000)
    try:
        client.admin.command('ping')
    except Exception as e:
        sys.exit(f'LỖI: không kết nối được MongoDB ({MONGODB_URI}): {e}')

    db = client[MONGODB_DATABASE]
    lessons = db['lessons']
    words = db['vocab_words']

    # Index theo ERD — PHẢI dùng đúng tên như @CompoundIndex trong backend
    # (Lesson.uq_level_lessonNo, VocabWord.idx_lesson_order,
    # VocabWord.idx_lesson_section_kana). Spring auto-index đã tạo chúng khi
    # backend khởi động; tạo lại cùng tên+keys+options là no-op (idempotent).
    # Tên khác mà keys giống → Atlas báo IndexOptionsConflict (code 85).
    lessons.create_index([('level', 1), ('lessonNo', 1)],
                         name='uq_level_lessonNo', unique=True)
    words.create_index([('lessonId', 1), ('section', 1), ('kana', 1)],
                       name='idx_lesson_section_kana')
    words.create_index([('lessonId', 1), ('orderInLesson', 1)],
                       name='idx_lesson_order')

    # 1) Upsert lessons
    lesson_ids = {}
    for L in data['lessons']:
        lessons.update_one(
            {'level': L['level'], 'lessonNo': L['lessonNo']},
            {'$set': {
                'level': L['level'],
                'lessonNo': L['lessonNo'],
                'titleJa': L['titleJa'],
                'titleVi': L['titleVi'],
                'order': L['lessonNo'],
            }},
            upsert=True,
        )
        doc = lessons.find_one({'level': L['level'], 'lessonNo': L['lessonNo']},
                               projection={'_id': 1})
        lesson_ids[L['lessonNo']] = doc['_id']
    print(f'lessons: upsert {len(lesson_ids)} bài vào DB "{MONGODB_DATABASE}"')

    # 2) Upsert vocab_words — dung bulk_write theo lo (nhanh gap ~100 lan
    # so voi tung update_one: 2692 round-trip -> vai batch).
    print('vocab_words: dang import ...', flush=True)
    ops = []
    for w in data['words']:
        lid = lesson_ids[w['lessonNo']]
        doc = {
            'lessonId': lid,
            'section': w['section'],
            'kana': w['kana'],
            'kanji': w['kanji'] or None,          # "" -> null (theo ERD)
            'romaji': w['romaji'],
            'meaningVi': w['meaningVi'],
            'pos': w['pos'],
            'exampleJa': w.get('exampleJa'),
            'exampleVi': w.get('exampleVi'),
            'audioUrl': '/audio/' + w['audioFile'],
            'orderInLesson': w['orderInLesson'],
        }
        ops.append(UpdateOne(
            {'lessonId': lid, 'kana': w['kana'], 'section': w['section']},
            {'$set': doc},
            upsert=True,
        ))
    inserted = updated = 0
    BATCH = 500
    for i in range(0, len(ops), BATCH):
        batch = ops[i:i + BATCH]
        res = words.bulk_write(batch, ordered=False)
        inserted += res.upserted_count
        updated += res.modified_count
        print(f'  ... {min(i + BATCH, len(ops))}/{len(ops)} từ', flush=True)
    print(f'vocab_words: {inserted} thêm mới, {updated} cập nhật, '
          f'tổng {words.count_documents({})} documents')


if __name__ == '__main__':
    main()
