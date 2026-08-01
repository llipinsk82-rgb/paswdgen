#!/usr/bin/env bash
set -Eeuo pipefail

ADB="${ADB:-adb}"
APP_ID="${APP_ID:-com.blackserv.passwdgen.preview2}"
ACTIVITY="${ACTIVITY:-com.blackserv.passwdgen.MainActivity}"
APK_PATH="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
ARTIFACT_DIR="${ARTIFACT_DIR:-app/build/emulator-smoke}"
EXPECTED_API="${EXPECTED_API:-}"

mkdir -p "${ARTIFACT_DIR}"

fail() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

collect_diagnostics() {
    set +e
    "${ADB}" shell getprop > "${ARTIFACT_DIR}/device-properties.txt" 2>&1
    "${ADB}" shell dumpsys activity activities > "${ARTIFACT_DIR}/activities.txt" 2>&1
    "${ADB}" shell dumpsys package "${APP_ID}" > "${ARTIFACT_DIR}/package.txt" 2>&1
    "${ADB}" logcat -d -v threadtime > "${ARTIFACT_DIR}/logcat-full.txt" 2>&1
    "${ADB}" exec-out screencap -p > "${ARTIFACT_DIR}/launch.png" 2>/dev/null
}
trap collect_diagnostics EXIT

[[ -f "${APK_PATH}" ]] || fail "Debug APK not found: ${APK_PATH}"

"${ADB}" wait-for-device
api_level="$("${ADB}" shell getprop ro.build.version.sdk | tr -d '\r')"
[[ -n "${api_level}" ]] || fail "Could not read emulator API level."
if [[ -n "${EXPECTED_API}" && "${api_level}" != "${EXPECTED_API}" ]]; then
    fail "Expected API ${EXPECTED_API}, got API ${api_level}."
fi

"${ADB}" shell settings put global window_animation_scale 0
"${ADB}" shell settings put global transition_animation_scale 0
"${ADB}" shell settings put global animator_duration_scale 0

"${ADB}" install -r -t "${APK_PATH}" | tee "${ARTIFACT_DIR}/install.txt"
"${ADB}" shell pm path "${APP_ID}" | tee "${ARTIFACT_DIR}/package-path.txt" | grep -Fq 'package:' \
    || fail "Installed package was not found."

"${ADB}" shell am force-stop "${APP_ID}"
"${ADB}" logcat -c
"${ADB}" shell am start -W -n "${APP_ID}/${ACTIVITY}" \
    | tee "${ARTIFACT_DIR}/activity-start.txt"
grep -Fq 'Status: ok' "${ARTIFACT_DIR}/activity-start.txt" \
    || fail "Android did not report a successful activity launch."

sleep 8
pid="$("${ADB}" shell pidof "${APP_ID}" | tr -d '\r')"
[[ -n "${pid}" ]] || fail "Application process is not running after launch."
printf '%s\n' "${pid}" > "${ARTIFACT_DIR}/pid.txt"

"${ADB}" shell dumpsys activity top > "${ARTIFACT_DIR}/activity-top.txt"
grep -Fq "${APP_ID}" "${ARTIFACT_DIR}/activity-top.txt" \
    || fail "Application is not present in the foreground activity state."

"${ADB}" logcat --pid="${pid}" -d -v threadtime > "${ARTIFACT_DIR}/logcat-app.txt" 2>&1 || true
if grep -Eq 'FATAL EXCEPTION|ANR in com\.blackserv\.passwdgen|Process: com\.blackserv\.passwdgen' \
    "${ARTIFACT_DIR}/logcat-app.txt"; then
    fail "Crash or ANR signature detected in application logcat."
fi

cat > "${ARTIFACT_DIR}/summary.txt" <<EOF
api_level=${api_level}
app_id=${APP_ID}
activity=${ACTIVITY}
pid=${pid}
launch=success
crash_signature=not_detected
EOF

trap - EXIT
collect_diagnostics
printf 'PasswdGen emulator smoke test passed on API %s.\n' "${api_level}"
