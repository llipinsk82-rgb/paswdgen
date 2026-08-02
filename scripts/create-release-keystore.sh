#!/usr/bin/env bash
set -euo pipefail
umask 077

OUTPUT_DIRECTORY="${1:-${HOME}/PasswdGen-Signing}"
ALIAS="${PASSWDGEN_KEY_ALIAS:-passwdgen-release}"
DISTINGUISHED_NAME="${PASSWDGEN_DNAME:-CN=PasswdGen Release, O=BlackServ, C=PL}"

command -v keytool >/dev/null 2>&1 || {
  echo "ERROR: keytool not found. Install JDK 17 and ensure keytool is in PATH." >&2
  exit 1
}

mkdir -p "${OUTPUT_DIRECTORY}"
chmod 700 "${OUTPUT_DIRECTORY}"

KEYSTORE_PATH="${OUTPUT_DIRECTORY}/passwdgen-release.jks"
BASE64_PATH="${OUTPUT_DIRECTORY}/passwdgen-release.jks.base64.txt"
CERTIFICATE_PATH="${OUTPUT_DIRECTORY}/passwdgen-release-cert.der"
METADATA_PATH="${OUTPUT_DIRECTORY}/release-signing-info.txt"

for path in "${KEYSTORE_PATH}" "${BASE64_PATH}" "${CERTIFICATE_PATH}" "${METADATA_PATH}"; do
  if [[ -e "${path}" ]]; then
    echo "ERROR: file already exists: ${path}. Refusing to overwrite signing identity." >&2
    exit 1
  fi
done

read_confirmed_password() {
  local first second
  while true; do
    read -r -s -p "Strong signing password (minimum 16 characters): " first
    printf '\n'
    read -r -s -p "Repeat password: " second
    printf '\n'

    if (( ${#first} < 16 )); then
      echo "Password must contain at least 16 characters." >&2
      continue
    fi
    if [[ "${first}" != "${second}" ]]; then
      echo "Passwords do not match." >&2
      continue
    fi

    PASSWDGEN_SIGNING_PASSWORD="${first}"
    export PASSWDGEN_SIGNING_PASSWORD
    unset first second
    return 0
  done
}

cleanup() {
  unset PASSWDGEN_SIGNING_PASSWORD || true
}
trap cleanup EXIT HUP INT TERM

read_confirmed_password

keytool \
  -genkeypair \
  -v \
  -keystore "${KEYSTORE_PATH}" \
  -storetype JKS \
  -alias "${ALIAS}" \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 36500 \
  -dname "${DISTINGUISHED_NAME}" \
  -storepass:env PASSWDGEN_SIGNING_PASSWORD \
  -keypass:env PASSWDGEN_SIGNING_PASSWORD

keytool \
  -exportcert \
  -keystore "${KEYSTORE_PATH}" \
  -storetype JKS \
  -alias "${ALIAS}" \
  -storepass:env PASSWDGEN_SIGNING_PASSWORD \
  -file "${CERTIFICATE_PATH}"

KEYTOOL_INFO="$(keytool \
  -list \
  -v \
  -keystore "${KEYSTORE_PATH}" \
  -storetype JKS \
  -alias "${ALIAS}" \
  -storepass:env PASSWDGEN_SIGNING_PASSWORD 2>&1)"

base64 < "${KEYSTORE_PATH}" | tr -d '\r\n' > "${BASE64_PATH}"

if command -v sha256sum >/dev/null 2>&1; then
  CERTIFICATE_SHA256="$(sha256sum "${CERTIFICATE_PATH}" | awk '{print $1}')"
  KEYSTORE_SHA256="$(sha256sum "${KEYSTORE_PATH}" | awk '{print $1}')"
elif command -v shasum >/dev/null 2>&1; then
  CERTIFICATE_SHA256="$(shasum -a 256 "${CERTIFICATE_PATH}" | awk '{print $1}')"
  KEYSTORE_SHA256="$(shasum -a 256 "${KEYSTORE_PATH}" | awk '{print $1}')"
else
  echo "ERROR: sha256sum or shasum is required." >&2
  exit 1
fi

GENERATED_UTC="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
cat > "${METADATA_PATH}" <<EOF
PasswdGen release signing identity
Generated UTC: ${GENERATED_UTC}
Application ID: com.blackserv.passwdgen
Alias: ${ALIAS}
Certificate SHA-256: ${CERTIFICATE_SHA256}
Keystore SHA-256: ${KEYSTORE_SHA256}
Validity days: 36500

${KEYTOOL_INFO}
EOF

chmod 600 "${KEYSTORE_PATH}" "${BASE64_PATH}" "${CERTIFICATE_PATH}" "${METADATA_PATH}"

cat <<EOF

Created the PasswdGen release signing identity.
Directory: ${OUTPUT_DIRECTORY}
Certificate SHA-256: ${CERTIFICATE_SHA256}

Add these GitHub Actions repository secrets:
ANDROID_KEYSTORE_BASE64 = entire contents of ${BASE64_PATH}
ANDROID_KEYSTORE_PASSWORD = password entered above
ANDROID_KEY_ALIAS = ${ALIAS}
ANDROID_KEY_PASSWORD = the same password

Do not send the JKS file or password through chat and never commit them to the repository.
EOF
