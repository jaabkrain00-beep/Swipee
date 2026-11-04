#!/usr/bin/env bash
# คัดลอกฟอนต์ Mali (ไทย+ละติน) จาก node_modules เข้า www/fonts เพื่อฝังใน APK (ใช้ออฟไลน์ได้)
# รันหลัง `npm install`
set -euo pipefail
mkdir -p www/fonts
for w in 500 700; do
  for s in thai latin; do
    cp "node_modules/@fontsource/mali/files/mali-${s}-${w}-normal.woff2" www/fonts/
  done
done
echo "fonts OK"
