"""Focused local regressions; generated classes stay in the caller-selected temporary workspace."""
import os
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'TMessagesProj/src/main/AndroidManifest.xml').getroot()
receivers = [r for r in manifest.findall('./application/receiver') if r.get(android + 'name') == '.SmsReceiver']
assert len(receivers) == 1
assert receivers[0].get(android + 'exported') == 'true'
assert receivers[0].get(android + 'permission') == 'com.google.android.gms.auth.api.phone.permission.SEND'
assert any(a.get(android + 'name') == 'com.google.android.gms.auth.api.phone.SMS_RETRIEVED'
           for a in receivers[0].findall('./intent-filter/action'))
print('PASS: SMS Retriever remains exported for Play Services, with an enforced sender permission', flush=True)

with tempfile.TemporaryDirectory(dir=os.environ.get('CODEX_SECURITY_TEST_WORK_DIR')) as directory:
    base = root / 'TMessagesProj/src/main/java/org/telegram/messenger'
    sources = [base / 'TelebotsCacheCipher.java', base / 'TelebotsPeerConnections.java',
               root / 'Tools/tests/TelebotsCacheCipherTest.java', root / 'Tools/tests/TelebotsPeerConnectionsTest.java']
    subprocess.run(['javac', '-encoding', 'UTF-8', '-d', directory, *map(str, sources)], check=True)
    for name in ['TelebotsCacheCipherTest', 'org.telegram.messenger.TelebotsPeerConnectionsTest']:
        subprocess.run(['java', '-cp', directory, name], check=True, timeout=15)
