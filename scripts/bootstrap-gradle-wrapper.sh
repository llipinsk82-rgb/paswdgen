#!/usr/bin/env bash
set -euo pipefail

# Securely generates the Gradle Wrapper used by this repository.
# Run from the repository root on a trusted Linux/macOS machine with:
#   bash scripts/bootstrap-gradle-wrapper.sh

readonly GRADLE_VERSION="8.13"
readonly DISTRIBUTION_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"
readonly WRAPPER_JAR_SHA256="81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f"
readonly DISTRIBUTION_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

for command in curl unzip sha256sum java; do
    if ! command -v "${command}" >/dev/null 2>&1; then
        echo "ERROR: required command not found: ${command}" >&2
        exit 1
    fi
done

if [[ ! -f settings.gradle.kts || ! -f build.gradle.kts ]]; then
    echo "ERROR: run this script from the PasswdGen repository root." >&2
    exit 1
fi

work_dir="$(mktemp -d)"
cleanup() {
    rm -rf "${work_dir}"
}
trap cleanup EXIT

archive="${work_dir}/gradle-${GRADLE_VERSION}-bin.zip"

echo "Downloading Gradle ${GRADLE_VERSION} from the official distribution service..."
curl --fail --location --proto '=https' --tlsv1.2 \
    --retry 3 --retry-delay 2 \
    --output "${archive}" \
    "${DISTRIBUTION_URL}"

echo "${DISTRIBUTION_SHA256}  ${archive}" | sha256sum --check --strict
unzip -q "${archive}" -d "${work_dir}"

"${work_dir}/gradle-${GRADLE_VERSION}/bin/gradle" wrapper \
    --gradle-version "${GRADLE_VERSION}" \
    --distribution-type bin

properties_file="gradle/wrapper/gradle-wrapper.properties"
if grep -q '^distributionSha256Sum=' "${properties_file}"; then
    sed -i.bak "s/^distributionSha256Sum=.*/distributionSha256Sum=${DISTRIBUTION_SHA256}/" "${properties_file}"
    rm -f "${properties_file}.bak"
else
    printf '\ndistributionSha256Sum=%s\n' "${DISTRIBUTION_SHA256}" >> "${properties_file}"
fi

actual_wrapper_sha="$(sha256sum gradle/wrapper/gradle-wrapper.jar | awk '{print $1}')"
if [[ "${actual_wrapper_sha}" != "${WRAPPER_JAR_SHA256}" ]]; then
    echo "ERROR: unexpected Gradle Wrapper JAR checksum." >&2
    echo "Expected: ${WRAPPER_JAR_SHA256}" >&2
    echo "Actual:   ${actual_wrapper_sha}" >&2
    rm -f gradle/wrapper/gradle-wrapper.jar gradlew gradlew.bat
    exit 1
fi

chmod +x gradlew

echo "Gradle Wrapper ${GRADLE_VERSION} generated and verified."
echo "Distribution SHA-256: ${DISTRIBUTION_SHA256}"
echo "Wrapper JAR SHA-256:  ${WRAPPER_JAR_SHA256}"
echo
echo "Next commands:"
echo "  ./gradlew --version"
echo "  ./gradlew test lintDebug assembleDebug"
