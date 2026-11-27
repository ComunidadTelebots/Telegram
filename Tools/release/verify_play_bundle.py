"""Fail closed on Play bundle identity, signing and 16 KiB native alignment.

Uses Google's bundletool and the JDK; never reads private signing keys.
"""
import argparse
import base64
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

UPSTREAM_TEST_CERT = 'a08d7dc323ddf71ef3201944397e0d3cce7d40847263e11f328b68bbe19229ab'
ANDROID = '{http://schemas.android.com/apk/res/android}'


def elf_load_alignments(data):
    if data[:4] != b'\x7fELF' or len(data) < 64:
        raise ValueError('Invalid ELF')
    if data[4] != 2:
        return []  # 16 KiB requirement tested for 64-bit libraries.
    endian = '<' if data[5] == 1 else '>' if data[5] == 2 else None
    if endian is None:
        raise ValueError('Invalid ELF byte order')
    offset = struct.unpack_from(endian + 'Q', data, 32)[0]
    size, count = struct.unpack_from(endian + 'HH', data, 54)
    if size < 56 or count == 0 or offset + size * count > len(data):
        raise ValueError('Truncated ELF program headers')
    result = []
    for index in range(count):
        typ, flags, off, address, physical, filesz, memsz, alignment = struct.unpack_from(endian + 'IIQQQQQQ', data, offset + size * index)
        if typ == 1:
            if alignment < 16384 or alignment & (alignment - 1) or (address - off) % 16384:
                raise ValueError('PT_LOAD not aligned to 16 KiB')
            result.append(alignment)
    if not result:
        raise ValueError('No ELF load segments')
    return result


def validate_manifest(xml, package, previous_code):
    root = ET.fromstring(xml)
    if root.get('package') != package:
        raise ValueError('Unexpected application ID')
    app = root.find('application')
    if app is None or app.get(ANDROID + 'debuggable', 'false') != 'false' or app.get(ANDROID + 'testOnly', 'false') != 'false':
        raise ValueError('Debug/test-only bundle rejected')
    code = int(root.get(ANDROID + 'versionCode', '0'))
    if not previous_code < code <= 2100000000:
        raise ValueError('Version code must exceed the latest Play version')
    sdk = root.find('uses-sdk')
    if sdk is None or int(sdk.get(ANDROID + 'targetSdkVersion', '0')) < 36:
        raise ValueError('Expected target API 36 or higher')
    return {'package': package, 'version_code': code, 'version_name': root.get(ANDROID + 'versionName'),
            'min_sdk': int(sdk.get(ANDROID + 'minSdkVersion', '0')), 'target_sdk': int(sdk.get(ANDROID + 'targetSdkVersion'))}


def validate_native_archive(archive):
    native = {}
    abis = set()
    core_abis = set()
    for name in archive.namelist():
        match = re.search(r'(?:^|/)lib/([^/]+)/([^/]+\.so)$', name)
        if not match:
            continue
        abis.add(match[1])
        if match[2] == 'libtmessages.49.so':
            core_abis.add(match[1])
        try:
            native[name] = elf_load_alignments(archive.read(name))
        except ValueError as error:
            raise ValueError(f'{name}: {error}') from error
    if abis - core_abis:
        raise ValueError('Missing Telegram native core for: ' + ', '.join(sorted(abis - core_abis)))
    if not abis:
        raise ValueError('Native libraries absent')
    return native


def run(command):
    proc = subprocess.run([str(x) for x in command], capture_output=True, text=True, encoding='utf-8', errors='replace')
    if proc.returncode:
        raise ValueError(f'{Path(str(command[0])).name} failed: ' + (proc.stderr or proc.stdout)[-1500:])
    return proc.stdout


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('bundle', type=Path)
    parser.add_argument('--bundletool', required=True, type=Path)
    parser.add_argument('--jdk', required=True, type=Path)
    parser.add_argument('--expected-certificate', required=True, help='SHA-256 copied from Play Console upload certificate')
    parser.add_argument('--package', default='org.comunidadtelebots.stable')
    parser.add_argument('--previous-version-code', required=True, type=int)
    parser.add_argument('--report', required=True, type=Path)
    args = parser.parse_args()
    suffix = '.exe' if sys.platform == 'win32' else ''
    java = args.jdk / 'bin' / ('java' + suffix)
    bundletool = [java, '-jar', args.bundletool]
    report = {'bundle': args.bundle.name, 'sha256': hashlib.sha256(args.bundle.read_bytes()).hexdigest(), 'ready_for_upload': False, 'errors': []}
    try:
        run(bundletool + ['validate', '--bundle=' + str(args.bundle)])
        xml = run(bundletool + ['dump', 'manifest', '--bundle=' + str(args.bundle), '--module=base'])
        report.update(validate_manifest(xml, args.package, args.previous_version_code))
        with zipfile.ZipFile(args.bundle) as archive:
            native = validate_native_archive(archive)
        if not any('/arm64-v8a/' in name for name in native):
            raise ValueError('ARM64 native libraries absent')
        report['native_libraries'] = native
        verification = run([args.jdk / 'bin' / ('jarsigner' + suffix), '-J-Duser.language=en', '-J-Duser.country=US', '-verify', args.bundle])
        if 'jar verified.' not in verification or 'unsigned entries' in verification:
            raise ValueError('Bundle signature verification failed')
        cert = run([args.jdk / 'bin' / ('keytool' + suffix), '-printcert', '-rfc', '-jarfile', args.bundle])
        pem = re.findall(r'-----BEGIN CERTIFICATE-----\s*(.*?)\s*-----END CERTIFICATE-----', cert, re.S)
        if len(pem) != 1:
            raise ValueError('Expected exactly one signing certificate')
        actual = hashlib.sha256(base64.b64decode(pem[0])).hexdigest()
        report['certificate_sha256'] = actual
        if actual == UPSTREAM_TEST_CERT:
            raise ValueError('Bundled public Telegram test key: NOT suitable for Play upload')
        expected = re.sub(r'[:\s]', '', args.expected_certificate).lower()
        if not re.fullmatch('[0-9a-f]{64}', expected) or actual != expected:
            raise ValueError('Signer does not match the Play Console upload certificate')
        report['ready_for_upload'] = True
    except (ValueError, OSError, zipfile.BadZipFile, ET.ParseError) as error:
        report['errors'].append(str(error))
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({k: v for k, v in report.items() if k != 'native_libraries'}, indent=2))
    return 0 if report['ready_for_upload'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
