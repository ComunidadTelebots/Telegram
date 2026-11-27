import importlib.util
from pathlib import Path
import struct
import io
import zipfile
import unittest

spec = importlib.util.spec_from_file_location('play_bundle', Path(__file__).parents[1] / 'release/verify_play_bundle.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class BundleChecks(unittest.TestCase):
    def elf(self, alignment, address=0):
        data = bytearray(120)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, 0, address, 0, 120, 120, alignment)
        return data

    def test_64_bit_library_alignment(self):
        self.assertEqual(module.elf_load_alignments(self.elf(16384)), [16384])
        for bad in [self.elf(4096), self.elf(16384, 4096), self.elf(16384)[:80]]:
            with self.assertRaises(ValueError): module.elf_load_alignments(bad)

    def test_manifest_identity_and_upgrade(self):
        xml = '<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="org.comunidadtelebots.stable" android:versionCode="72381"><uses-sdk android:minSdkVersion="23" android:targetSdkVersion="36"/><application android:debuggable="false"/></manifest>'
        self.assertEqual(module.validate_manifest(xml, 'org.comunidadtelebots.stable', 72379)['version_code'], 72381)
        for bad, previous in [(xml.replace('.stable', '.alfa'), 0), (xml.replace('="false"', '="true"'), 0), (xml, 72381), (xml.replace('Version="36"', 'Version="34"'), 0)]:
            with self.assertRaises(ValueError): module.validate_manifest(bad, 'org.comunidadtelebots.stable', previous)

    def test_rejects_partial_architecture_that_installs_but_cannot_start(self):
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w') as archive:
            archive.writestr('lib/arm64-v8a/libtmessages.49.so', self.elf(16384))
            archive.writestr('lib/x86_64/libthirdparty.so', self.elf(16384))
        with zipfile.ZipFile(data) as archive:
            with self.assertRaisesRegex(ValueError, 'Missing Telegram native core for: x86_64'):
                module.validate_native_archive(archive)


if __name__ == '__main__': unittest.main()
