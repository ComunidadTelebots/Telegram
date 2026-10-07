#!/usr/bin/env bash
set -euo pipefail
cd /src
# Validate before copying any signing material. A failed process substitution
# would not propagate through mapfile, so write only after a successful check.
python3 /opt/build_resources.py > /tmp/telebots-gradle-options
mapfile -t resource_args < /tmp/telebots-gradle-options
format=${BUILD_FORMAT:-apk}
case "$format" in
    apk) task=:TMessagesProj_App:assembleAfatRelease ;;
    aab)
        test "$CHANNEL" = stable
        test -z "${BUILD_ABI:-}"
        task=:TMessagesProj_App:bundleBundleAfatRelease
        ;;
    *) echo 'BUILD_FORMAT must be apk or aab'; exit 1 ;;
esac
test -s /signing/release.keystore
test -s /signing/gradle.properties
test -s /signing/BuildVars.java
test -s /signing/certificate.sha256
# Secrets are mounted only in this disposable build, never baked into an image.
trap 'rm -f TMessagesProj/config/release.keystore TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java /tmp/gradle-home/gradle.properties' EXIT
cp /signing/release.keystore TMessagesProj/config/release.keystore
cp /signing/BuildVars.java TMessagesProj/src/main/java/org/telegram/messenger/BuildVars.java
mkdir -p /tmp/gradle-home
cp /signing/gradle.properties /tmp/gradle-home/gradle.properties
export GRADLE_USER_HOME=/tmp/gradle-home
abi_args=()
if [ -n "${BUILD_ABI:-}" ]; then
    case "$BUILD_ABI" in arm64-v8a|armeabi-v7a|x86|x86_64) ;; *) exit 1 ;; esac
    abi_args=("-Pandroid.injected.build.abi=$BUILD_ABI")
fi
java -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain "$task" --no-daemon "${resource_args[@]}" --console=plain "-PAPP_CHANNEL=$CHANNEL" -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process "${abi_args[@]}"
if [ "$format" = aab ]; then
    mapfile -t bundles < <(find TMessagesProj_App/build/outputs/bundle/bundleAfatRelease -name '*.aab' -type f)
    test "${#bundles[@]}" -eq 1
    unzip -t "${bundles[0]}" >/dev/null
    jarsigner -J-Duser.language=en -J-Duser.country=US -verify "${bundles[0]}" > /tmp/bundle-verification
    grep -Fq 'jar verified.' /tmp/bundle-verification
    if grep -Fq 'unsigned entries' /tmp/bundle-verification; then exit 1; fi
    keytool -printcert -rfc -jarfile "${bundles[0]}" > /tmp/bundle-certificate
    python3 /opt/verify-bundle.py
    cp "${bundles[0]}" /out/application.aab
    exit 0
fi
apk=$(python3 /opt/select_apk.py --build-root TMessagesProj_App/build --channel "$CHANNEL")
apksigner verify --verbose --print-certs "$apk" > /tmp/certificate
zipalign -c -P 16 4 "$apk"
expected=$(tr -d '[:space:]:' < /signing/certificate.sha256 | tr '[:upper:]' '[:lower:]')
test "${#expected}" -eq 64
actual=$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' /tmp/certificate | tr '[:upper:]' '[:lower:]')
test "$actual" = "$expected"
aapt dump badging "$apk" > /tmp/badging
grep -Fq "package: name='org.comunidadtelebots.$CHANNEL'" /tmp/badging
if [ -n "${RELEASE_VERSION:-}" ]; then
    grep -Fq "versionName='$RELEASE_VERSION'" /tmp/badging
fi
if grep -q 'application-debuggable' /tmp/badging; then exit 1; fi
APK_METADATA_SOURCE="$apk" python3 - <<'PY'
import json, os, pathlib, re
badging = pathlib.Path('/tmp/badging').read_text()
line = next(line for line in badging.splitlines() if line.startswith('package: '))
fields = dict(re.findall(r"(name|versionCode|versionName)='([^']*)'", line))
assert fields['name'] == 'org.comunidadtelebots.' + os.environ['CHANNEL']
code = int(fields['versionCode'])
assert 0 < code <= 2100000000
assert fields['versionName'] and len(fields['versionName']) <= 100
sdk = re.search(r"^sdkVersion:'([0-9]+)'", badging, re.M)
assert sdk is not None
native = re.search(r"^native-code:(.*)$", badging, re.M)
abis = re.findall(r"'([^']+)'", native.group(1)) if native else []
certs = re.findall(r'^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$', pathlib.Path('/tmp/certificate').read_text(), re.M)
assert certs
metadata = {'package': fields['name'], 'version_code': code, 'version_name': fields['versionName'],
            'size_bytes': pathlib.Path(os.environ['APK_METADATA_SOURCE']).stat().st_size,
            'min_sdk': int(sdk.group(1)), 'abis': abis, 'signer_sha256': sorted({cert.lower() for cert in certs})}
notes = pathlib.Path('Tools/release/notes') / (fields['versionName'] + '.md')
if notes.is_file():
    text = notes.read_text(encoding='utf-8').strip()
    assert len(text) <= 2000 and len(text.encode('utf-8')) <= 4000
    metadata['release_notes'] = text
pathlib.Path('/out/application.metadata.json').write_text(json.dumps(metadata), encoding='utf-8')
PY
cp "$apk" /out/application.apk
