#!/usr/bin/env bash
set -euo pipefail

fail() {
  printf '%s\n' "APK contract failed: $1" >&2
  exit 1
}

[ "$#" -eq 1 ] || fail "usage: verify-apk-contract.sh <apk>"
apk=$1

if command -v cygpath >/dev/null 2>&1; then
  apk=$(cygpath -u "$apk")
fi
[ -f "$apk" ] || fail "APK was not found"

sdk_dir=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
if [ -z "$sdk_dir" ] && [ -n "${LOCALAPPDATA:-}" ]; then
  sdk_dir="$LOCALAPPDATA/Android/Sdk"
fi
[ -n "$sdk_dir" ] || fail "Android SDK location is unavailable"
if command -v cygpath >/dev/null 2>&1; then
  sdk_dir=$(cygpath -u "$sdk_dir")
fi

aapt=${AAPT:-}
if [ -z "$aapt" ]; then
  aapt=$(find "$sdk_dir/build-tools" -type f \( -name aapt -o -name aapt.exe \) -print -quit 2>/dev/null || true)
fi
[ -n "$aapt" ] && [ -x "$aapt" ] || fail "Android SDK aapt is unavailable"

work_dir=$(mktemp -d)
trap 'rm -rf "$work_dir"' EXIT
badging="$work_dir/badging.txt"
manifest="$work_dir/manifest.txt"
"$aapt" dump badging "$apk" >"$badging" || fail "aapt could not inspect APK"
"$aapt" dump xmltree "$apk" AndroidManifest.xml >"$manifest" || fail "aapt could not inspect manifest"

grep -q "^package: name='com.luc.body'" "$badging" || fail "application ID mismatch"
grep -q "targetSdkVersion:'36'" "$badging" || fail "target SDK mismatch"
grep -q "application-label:'Luc'" "$badging" || fail "application label mismatch"

permission_count=0
while IFS= read -r permission; do
  permission=${permission#uses-permission: name=\'}
  permission=${permission%%\'*}
  case "$permission" in
    com.luc.body.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)
      # AndroidX adds this package-private permission during manifest merging.
      ;;
    android.permission.SYSTEM_ALERT_WINDOW|android.permission.FOREGROUND_SERVICE|android.permission.FOREGROUND_SERVICE_SPECIAL_USE|android.permission.POST_NOTIFICATIONS|android.permission.INTERNET)
      permission_count=$((permission_count + 1))
      ;;
    *) fail "permission contract mismatch" ;;
  esac
done < <(grep "^uses-permission:" "$badging")
[ "$permission_count" -eq 5 ] || fail "permission contract mismatch"

service_block=$(awk '
  /^      E: service / { if (seen) exit; seen = 1; print; next }
  seen { print }
  seen && /^      E: (activity|receiver|provider) / { exit }
' "$manifest")
printf '%s\n' "$service_block" | grep -q 'android:name.*OverlayService' || fail "OverlayService missing"
printf '%s\n' "$service_block" | grep -Eq 'android:exported.*0x0|android:exported.*false' || fail "OverlayService export mismatch"
printf '%s\n' "$service_block" | grep -Eq 'android:foregroundServiceType.*(specialUse|0x40000000)' || fail "OverlayService type mismatch"
printf '%s\n' "$service_block" | grep -q 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' || fail "OverlayService subtype missing"
grep -Eq 'android:usesCleartextTraffic.*0x0|android:usesCleartextTraffic.*false' "$manifest" || fail "cleartext traffic is enabled"

printf '%s\n' 'APK contract passed'
