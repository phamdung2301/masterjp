#!/usr/bin/env bash
# Import seed lessons 1-3 vào MongoDB (chạy trên máy có MongoDB).
#   bash scripts/import-seed.sh
# Tùy chọn ENV: MONGODB_URI, MONGODB_DATABASE (mặc định master_jp)
set -euo pipefail
cd "$(dirname "$0")/.."
: "${MONGODB_URI:=mongodb://localhost:27017}"
: "${MONGODB_DATABASE:=master_jp}"
export MONGODB_URI MONGODB_DATABASE
python3 scripts/seed-import.py
