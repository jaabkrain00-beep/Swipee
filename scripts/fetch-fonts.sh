#!/usr/bin/env bash
# คัดลอกฟอนต์ Mali จาก node_modules เข้า www/fonts (ใช้ออฟไลน์ใน APK)
# ถ้าไฟล์ไหนหาไม่เจอ จะข้าม ไม่ทำให้ build ล้ม (แอปจะใช้ฟอนต์สำรองแทน)
set -uo pipefail
mkdir -p www/fonts
for w in 500 700; do
  for s in thai latin; do
    f="node_modules/@fontsource/mali/files/mali-${s}-${w}-normal.woff2"
    if [ -f "$f" ]; then cp "$f" www/fonts/; else echo "WARN: missing $f"; fi
  done
done
echo "fonts done"
exit 0
