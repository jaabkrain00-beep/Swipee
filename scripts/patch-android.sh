#!/usr/bin/env bash
# รันหลัง `npx cap add android` : ใส่ plugin Java, สิทธิ์ใน manifest, minSdk 30
set -euo pipefail

APP=android/app/src/main
PKG=com/swipeclean/app

mkdir -p "$APP/java/$PKG"
cp native/PhotoManagerPlugin.java "$APP/java/$PKG/PhotoManagerPlugin.java"
cp native/MainActivity.java "$APP/java/$PKG/MainActivity.java"

python3 - <<'PY'
p = "android/app/src/main/AndroidManifest.xml"
s = open(p, encoding="utf-8").read()
perms = """    <uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
    <uses-permission android:name="android.permission.READ_MEDIA_VISUAL_USER_SELECTED" />
    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
"""
if "READ_MEDIA_VISUAL_USER_SELECTED" not in s:
    # เอาบรรทัด permission เก่า (ถ้ามี) ออกก่อน แล้วใส่ชุดใหม่
    import re
    s = re.sub(r'\s*<uses-permission[^>]*(READ_MEDIA_IMAGES|READ_EXTERNAL_STORAGE)[^>]*/>', '', s)
    s = s.replace("<application", perms + "\n    <application", 1)
if "android.permission.INTERNET" not in s:
    s = s.replace("<application", '    <uses-permission android:name="android.permission.INTERNET" />\n    <application', 1)
open(p, "w", encoding="utf-8").write(s)
PY

# Android 11+ (ใช้ MediaStore.createDeleteRequest)
sed -i -E 's/minSdkVersion *= *[0-9]+/minSdkVersion = 30/' android/variables.gradle

# ตรวจว่า patch สำเร็จจริง ไม่งั้นให้ build ล้ม (ดีกว่าได้ APK ที่ไม่ขอสิทธิ์)
grep -q "READ_MEDIA_IMAGES" "$APP/AndroidManifest.xml" || { echo "ERROR: manifest patch failed"; exit 1; }
grep -q "READ_MEDIA_VISUAL_USER_SELECTED" "$APP/AndroidManifest.xml" || { echo "ERROR: manifest patch failed"; exit 1; }
grep -q "minSdkVersion = 30" android/variables.gradle || { echo "ERROR: minSdk patch failed"; exit 1; }
test -f "$APP/java/$PKG/PhotoManagerPlugin.java" || { echo "ERROR: plugin not copied"; exit 1; }
echo "----- AndroidManifest.xml -----"
cat "$APP/AndroidManifest.xml"
echo "patched OK"
