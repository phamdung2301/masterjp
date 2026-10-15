# Import seed lessons 1-3 vào MongoDB (chạy trên Windows PowerShell).
#   .\scripts\import-seed.ps1
# Tùy chọn ENV: $env:MONGODB_URI, $env:MONGODB_DATABASE (mặc định master_jp)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path (Split-Path $MyInvocation.MyCommand.Path -Parent) -Parent)
if (-not $env:MONGODB_URI)      { $env:MONGODB_URI = 'mongodb://localhost:27017' }
if (-not $env:MONGODB_DATABASE) { $env:MONGODB_DATABASE = 'master_jp' }
python scripts/seed-import.py
