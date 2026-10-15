#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Import seed lessons 1-50 (Master JP) vào MongoDB.

Đọc:  data/seed/seed-lessons-1-3.json
      data/seed/seed-lessons-4-50.json
      data/seed/clusters.json        (F-A: gán cụm chủ đề cho từ)
Ghi:  lessons      — upsert theo (level, lessonNo)
      vocab_words  — upsert theo (lessonId, kana, section)

Chạy lại an toàn (idempotent): chạy N lần cũng không tạo trùng.
Chạy lại trên DB cũ sẽ $set thêm field cluster (không cần --reset-words).

ENV:
  MONGODB_URI       (default: mongodb://localhost:27017)
  MONGODB_DATABASE  (default: master_jp)

Yêu cầu: pip install pymongo
"""
import json
import os
import sys
import argparse
import unicodedata

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
CLUSTERS_FILE = os.path.join(ROOT, 'data', 'seed', 'clusters.json')


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


def load_clusters():
    """F-A: đọc data/seed/clusters.json một lần -> map 'kana|lessonNo' -> mã cụm.
    Từ nào không có trong map -> 'khac' (backend cũng normalize null -> 'khac')."""
    if not os.path.exists(CLUSTERS_FILE):
        print(f'CẢNH BÁO: không tìm thấy {CLUSTERS_FILE} — mọi từ sẽ vào cụm "khac".')
        return {}
    with open(CLUSTERS_FILE, encoding='utf-8') as f:
        d = json.load(f)
    return d.get('assignments', {})


def parse_args():
    p = argparse.ArgumentParser(description='Import seed lessons 1-50 vao MongoDB')
    p.add_argument('--reset-words', action='store_true',
                   help='Xoa sach collection vocab_words truoc khi import. '
                        'DUNG khi du lieu cu sai format (vd. lessonId dang ObjectId '
                        'thay vi string, thieu active/searchNorm) de tranh trùng lặp.')
    return p.parse_args()


def normalize_vi(s):
    """Port cua VietnameseUtils.normalize (backend): bo dau tieng Viet, đ->d,
    lowercase, gon khoang trang. Dung de build searchNorm."""
    if not s:
        return ''
    n = unicodedata.normalize('NFD', s)
    n = ''.join(c for c in n if not unicodedata.combining(c))
    n = n.replace('đ', 'd').replace('Đ', 'd')
    return ' '.join(n.lower().strip().split())


def build_search_norm(w):
    """Port cua VietnameseUtils.buildSearchNorm: kana + kanji + romaji +
    nghia Viet (bo dau) + loai tu."""
    return normalize_vi(' '.join([
        w.get('kana') or '',
        w.get('kanji') or '',
        w.get('romaji') or '',
        w.get('meaningVi') or '',
        w.get('pos') or '',
    ]))


def main():
    args = parse_args()
    data = load_seeds()
    cluster_map = load_clusters()
    if cluster_map:
        print(f'clusters: đã load {len(cluster_map)} gán cụm từ clusters.json')

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
        # LUU Y: lessonId trong VocabWord cua backend la String (hex), KHONG
        # phai ObjectId — Spring query bang string nen phai luu string.
        lesson_ids[L['lessonNo']] = str(doc['_id'])
    print(f'lessons: upsert {len(lesson_ids)} bài vào DB "{MONGODB_DATABASE}"')

    if args.reset_words:
        n = words.delete_many({}).deleted_count
        print(f'vocab_words: da xoa {n} documents cu (do --reset-words)')

    # 2) Upsert vocab_words — dung bulk_write theo lo (nhanh gap ~100 lan
    # so voi tung update_one: 2692 round-trip -> vai batch).
    print('vocab_words: dang import ...', flush=True)
    ops = []
    for w in data['words']:
        lid = lesson_ids[w['lessonNo']]
        doc = {
            'lessonId': lid,  # string hex — khop String lessonId cua backend
            'active': True,   # backend query ...AndActiveTrue... (FR-031)
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
            'searchNorm': build_search_norm(w),   # tim kiem khong dau (F-012)
            # F-A: cụm chủ đề (key '<kana>|<lessonNo>' đã verify unique 2692/2692)
            'cluster': cluster_map.get(f"{w['kana']}|{w['lessonNo']}", 'khac'),
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
