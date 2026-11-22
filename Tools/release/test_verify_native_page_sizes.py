"""Regression tests for Android native ELF page-size validation."""
import importlib.util
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("verify_native_page_sizes", Path(__file__).with_name("verify_native_page_sizes.py"))
validator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(validator)


def elf(bits, alignment):
    # Small synthetic ELF with one PT_LOAD program header.
    data = bytearray(128)
    data[:4] = b"\x7fELF"
    data[4] = 2 if bits == 64 else 1
    data[5] = 1
    if bits == 64:
        struct.pack_into("<Q", data, 32, 64)
        struct.pack_into("<HH", data, 54, 56, 1)
        struct.pack_into("<IIQQQQQQ", data, 64, 1, 0, 0, 0, 0, 0, 0, alignment)
    else:
        struct.pack_into("<I", data, 28, 52)
        struct.pack_into("<HH", data, 42, 32, 1)
        struct.pack_into("<IIIIIIII", data, 52, 1, 0, 0, 0, 0, 0, 0, alignment)
    return bytes(data)


class PageSizeTest(unittest.TestCase):
    def inspect(self, entries):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "test.apk"
            with zipfile.ZipFile(path, "w") as archive:
                for filename, content in entries.items():
                    archive.writestr(filename, content)
            return validator.inspect(path)

    def test_arm64_valid_and_legacy_32bit_allowed(self):
        count, abis, failures, legacy = self.inspect({
            "lib/arm64-v8a/libcore.so": elf(64, 16384),
            "lib/armeabi-v7a/libtor.so": elf(32, 4096),
            "lib/x86/libtor.so": elf(32, 4096),
            "lib/x86_64/libcore.so": elf(64, 16384),
        })
        self.assertEqual(count, 4)
        self.assertFalse(failures)
        self.assertEqual(len(legacy), 2)

    def test_64bit_4k_is_blocking(self):
        _, _, failures, _ = self.inspect({
            "lib/arm64-v8a/libbad.so": elf(64, 4096),
            "lib/x86_64/libbad.so": elf(64, 4096),
        })
        self.assertEqual(len(failures), 2)

    def test_corrupted_32bit_elf_is_blocking(self):
        _, _, failures, _ = self.inspect({
            "lib/arm64-v8a/libgood.so": elf(64, 16384),
            "lib/armeabi-v7a/libbad.so": b"no-elf",
        })
        self.assertEqual(len(failures), 1)

    def test_arm64_required(self):
        with self.assertRaisesRegex(ValueError, "ARM64"):
            self.inspect({"lib/x86/libnative.so": elf(32, 4096)})


if __name__ == "__main__":
    unittest.main()
