#!/bin/sh
set -eu
apk="${GITHUB_WORKSPACE:?}/artifacts/telebots-dev-universal.apk"
if ! test -s "$apk"; then
  echo "::error::APK candidate missing or empty: $apk" >&2
  exit 1
fi
adb install "$apk"
adb shell pm path org.comunidadtelebots.dev | grep -Fq 'package:'
for profile in phone tablet; do
  if [ "$profile" = phone ]; then
    adb shell wm size 1080x2340
    adb shell wm density 420
  else
    adb shell wm size 1600x2560
    adb shell wm density 240
  fi
  adb shell am force-stop org.comunidadtelebots.dev || true
  adb logcat -b crash -b events -c
  adb shell monkey -p org.comunidadtelebots.dev -c android.intent.category.LAUNCHER 1
  sleep 8
  adb shell pidof org.comunidadtelebots.dev
  # AndroidRuntime writes FATAL EXCEPTION and Process on separate lines.
  # The events buffer also records crashes and ANRs with the package name.
  crash_pattern='Process: org\.comunidadtelebots\.dev(:[^ ,]+)?, PID:|am_(crash|anr).*[,[]org\.comunidadtelebots\.dev(:[^,]+)?,'
  qa_log="$(mktemp)"
  adb logcat -b crash -b events -d -v brief > "$qa_log"
  if grep -Eq "$crash_pattern" "$qa_log"; then
    rm -f "$qa_log"
    echo "::error::Application crash or ANR on $profile"
    exit 1
  fi
  rm -f "$qa_log"
  echo "PASS installation and startup: $profile"
done
adb shell wm size reset
adb shell wm density reset
