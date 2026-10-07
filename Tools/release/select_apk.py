"""Select only AGP-declared afatRelease APKs, never a guessed intermediate file."""
import argparse
import hashlib
import json
from pathlib import Path
import re


def select_apk(build_root, channel):
    if channel not in ('dev','alfa','beta','rc','stable'):
        raise ValueError('Invalid Android channel')
    candidates = []
    for directory in (Path(build_root) / 'outputs/apk', Path(build_root) / 'intermediates/apk'):
        for metadata in directory.glob('**/output-metadata.json'):
            info = json.loads(metadata.read_text(encoding='utf-8'))
            if info.get('variantName') != 'afatRelease' or info.get('artifactType', {}).get('type') != 'APK':
                continue
            if info.get('applicationId') != 'org.comunidadtelebots.' + channel:
                raise ValueError('Release metadata package does not match requested channel')
            elements = info.get('elements', [])
            if len(elements) != 1 or elements[0].get('type') not in ('SINGLE','UNIVERSAL') or elements[0].get('filters'):
                raise ValueError('Expected one unsplit release APK')
            filename = elements[0].get('outputFile', '')
            if not isinstance(filename, str) or not re.fullmatch(r'[A-Za-z0-9_.-]+\.apk', filename):
                raise ValueError('Unsafe release output filename')
            apk = (metadata.parent / filename).resolve()
            if apk.parent != metadata.parent.resolve() or not apk.is_file() or apk.stat().st_size == 0:
                raise ValueError('Missing or unsafe release APK')
            code, version = elements[0].get('versionCode'), elements[0].get('versionName')
            if type(code) is not int or not 0 < code <= 2100000000 or not isinstance(version, str) or not version:
                raise ValueError('Invalid release version metadata')
            with apk.open('rb') as stream:
                digest = hashlib.file_digest(stream, 'sha256').hexdigest()
            candidates.append((apk, (info['applicationId'], code, version, digest)))
    if not candidates:
        raise ValueError('No afatRelease APK declared by AGP metadata')
    # AGP can expose a copied output as well as its packaged intermediate.
    # Accept only byte-identical APKs with matching package/version metadata.
    # Iterating outputs first gives the public build output deterministic priority.
    if any(identity != candidates[0][1] for _, identity in candidates[1:]):
        raise ValueError('Ambiguous afatRelease APKs: bytes or version metadata differ')
    return candidates[0][0]


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--build-root', required=True)
    parser.add_argument('--channel', required=True)
    args = parser.parse_args()
    try:
        print(select_apk(args.build_root, args.channel))
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + '\n')
