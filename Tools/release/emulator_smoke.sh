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
  crash_pattern='Process: org\.comunidadtelebots\.dev(:[^ ,]+)?, PID:|am_(crash|anr).*[,[]org\.comunidadtelebots\.dev(:[^,]+)?,'
  # Check crash evidence even when pidof fails. A missing PID must not
  # abort under set -e before the Android crash/event buffers are read.
  sleep 8
  attempt=0
  while :; do
    qa_log="$(mktemp)"
    if ! adb logcat -b crash -b events -d -v brief > "$qa_log"; then
      rm -f "$qa_log"
      echo "::error::Cannot read crash/event buffers on $profile"
      exit 1
    fi
    if grep -Eq "$crash_pattern" "$qa_log"; then
      # Log only the exception class (not arbitrary message text or account data).
      sed -nE 's/^.*: ([A-Za-z_$][A-Za-z0-9_$.]*(Exception|Error))(:.*)?$/Exception class: \1/p' "$qa_log" | head -n 10
      rm -f "$qa_log"
      echo "::error::Application crash or ANR on $profile"
      exit 1
    fi
    rm -f "$qa_log"
    if pid="$(adb shell pidof org.comunidadtelebots.dev)" && [ -n "$pid" ]; then
      echo "Process running on $profile: $pid"
      break
    fi
    attempt=$((attempt + 1))
    if [ "$attempt" -ge 6 ]; then
      echo "::error::Application process not found on $profile after bounded startup wait; no matching crash/ANR recorded"
      exit 1
    fi
    sleep 5
  done
  echo "PASS installation and startup: $profile"
done
adb shell wm size reset
adb shell wm density reset
