#!/usr/bin/env bash
# รันหลัง `npx cap add android` : ใส่ plugin Java, สิทธิ์ใน manifest, minSdk 30
set -euo pipefail

APP=android/app/src/main
PKG=com/swipeclean/app

mkdir -p "$APP/java/$PKG"
cp native/PhotoManagerPlugin.java "$APP/java/$PKG/PhotoManagerPlugin.java"
cp native/MainActivity.java "$APP/java/$PKG/MainActivity.java"

python3 - <<'PY'
import re
p = "android/app/src/main/AndroidManifest.xml"
s = open(p, encoding="utf-8").read()
perms = """    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
"""
if "READ_MEDIA_IMAGES" not in s:
    s = s.replace("<application", perms + "\n    <application", 1)
open(p, "w", encoding="utf-8").write(s)
PY

# Android 11+ (ใช้ MediaStore.createDeleteRequest)
sed -i -E 's/minSdkVersion *= *[0-9]+/minSdkVersion = 30/' android/variables.gradle

echo "patched OK"
