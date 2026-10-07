"""Check the AAB signer and the release manifest emitted by AGP."""
import base64
import hashlib
from pathlib import Path
import re
import xml.etree.ElementTree as ET

certificate = Path('/tmp/bundle-certificate').read_text()
match = re.search(r'-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----', certificate, re.S)
if not match:
    raise SystemExit('Bundle certificate missing')
digest = hashlib.sha256(base64.b64decode(match[1])).hexdigest()
expected = re.sub(r'[\s:]', '', Path('/signing/certificate.sha256').read_text()).lower()
if digest != expected:
    raise SystemExit('Unexpected bundle signing certificate')

manifests = []
root = Path('TMessagesProj_App/build/intermediates')
for directory in ('merged_manifests', 'merged_manifest', 'packaged_manifests'):
    manifests.extend((root / directory / 'bundleAfatRelease').glob('**/AndroidManifest.xml'))
if not manifests:
    raise SystemExit('Release merged manifest missing')
for path in manifests:
    manifest = ET.parse(path).getroot()
    if manifest.get('package') != 'org.comunidadtelebots.stable':
        raise SystemExit('Unexpected Google Play package')
    application = manifest.find('application')
    if application is None or application.get('{http://schemas.android.com/apk/res/android}debuggable', 'false') != 'false':
        raise SystemExit('Debuggable bundle rejected')
print('Stable bundle manifest and signing certificate verified.')
