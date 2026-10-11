#!/usr/bin/env bash
# Lists the permissions an APK requests and fails if any break the privacy invariants (spec 2, 14.3).
# Usage: scripts/check-permissions.sh [path/to.apk]   (default: debug APK)
set -euo pipefail

apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
aapt2="$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)aapt2"

[[ -f "$apk" ]] || { echo "APK not found: $apk (build it first)" >&2; exit 2; }

perms="$("$aapt2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\(.*\)'.*/\1/p")"
echo "Permissions requested by $apk:"
echo "${perms:-  (none)}" | sed 's/^/  /'

forbidden='android\.permission\.(INTERNET|ACCESS_NETWORK_STATE|ACCESS_WIFI_STATE|CAMERA|READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE|READ_MEDIA_IMAGES|READ_MEDIA_VIDEO|READ_MEDIA_VISUAL_USER_SELECTED)$'
if bad="$(grep -E "$forbidden" <<<"$perms")"; then
    echo "FAIL: forbidden permissions present:" >&2
    echo "$bad" | sed 's/^/  /' >&2
    exit 1
fi
echo "OK: no network, camera or storage permissions."
