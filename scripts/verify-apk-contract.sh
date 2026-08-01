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

system_alert_count=0
foreground_service_count=0
special_use_count=0
notification_count=0
internet_count=0
dynamic_receiver_count=0
while IFS= read -r permission_line; do
  permission=${permission_line#*: name=\'}
  permission=${permission%%\'*}
  case "$permission" in
    com.luc.body.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION)
      # AndroidX adds this package-private permission during manifest merging.
      dynamic_receiver_count=$((dynamic_receiver_count + 1))
      ;;
    android.permission.SYSTEM_ALERT_WINDOW) system_alert_count=$((system_alert_count + 1)) ;;
    android.permission.FOREGROUND_SERVICE) foreground_service_count=$((foreground_service_count + 1)) ;;
    android.permission.FOREGROUND_SERVICE_SPECIAL_USE) special_use_count=$((special_use_count + 1)) ;;
    android.permission.POST_NOTIFICATIONS) notification_count=$((notification_count + 1)) ;;
    android.permission.INTERNET) internet_count=$((internet_count + 1)) ;;
    *) fail "permission contract mismatch" ;;
  esac
done < <(grep "^uses-permission" "$badging" || true)
[ "$system_alert_count" -eq 1 ] || fail "permission contract mismatch"
[ "$foreground_service_count" -eq 1 ] || fail "permission contract mismatch"
[ "$special_use_count" -eq 1 ] || fail "permission contract mismatch"
[ "$notification_count" -eq 1 ] || fail "permission contract mismatch"
[ "$internet_count" -eq 1 ] || fail "permission contract mismatch"
[ "$dynamic_receiver_count" -le 1 ] || fail "permission contract mismatch"

service_block=$(awk '
  function indentation(line) {
    match(line, /[^[:space:]]/)
    return RSTART - 1
  }
  /^[[:space:]]*E: / {
    current_indent = indentation($0)
    if (active && current_indent <= service_indent) {
      if (direct_name_match) {
        printf "%s", block
        found = 1
        exit
      }
      active = 0
      block = ""
    }
    if ($0 ~ /^[[:space:]]*E: service /) {
      active = 1
      service_indent = current_indent
      direct_name_match = 0
      block = $0 ORS
      next
    }
  }
  active {
    if (
      indentation($0) == service_indent + 2 &&
      $0 ~ /^[[:space:]]*A: android:name.*com\.luc\.body\.OverlayService/
    ) direct_name_match = 1
    block = block $0 ORS
  }
  END {
    if (!found && active && direct_name_match) printf "%s", block
  }
' "$manifest")
printf '%s\n' "$service_block" | grep -q 'android:name.*OverlayService' || fail "OverlayService missing"
printf '%s\n' "$service_block" | grep -Eq 'android:exported\([^)]*\)=\(type 0x12\)0x0[[:space:]]*$' || fail "OverlayService export mismatch"
printf '%s\n' "$service_block" | grep -Eq 'android:foregroundServiceType\([^)]*\)=\(type 0x11\)0x40000000[[:space:]]*$' || fail "OverlayService type mismatch"
printf '%s\n' "$service_block" | grep -q 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' || fail "OverlayService subtype missing"
grep -Eq 'android:usesCleartextTraffic.*0x0|android:usesCleartextTraffic.*false' "$manifest" || fail "cleartext traffic is enabled"

printf '%s\n' 'APK contract passed'
