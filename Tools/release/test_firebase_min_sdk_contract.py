"""Check Firebase Android SDK compatibility without Android SDK or credentials.

The fork's library and universal DEV package support Android API 21.
Firebase BoM 35.0.0 adopted Analytics/Crashlytics 24/21, which require API 24.
See https://firebase.google.com/support/release-notes/android
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LIBRARY = ROOT / "TMessagesProj/build.gradle"
APPLICATION = ROOT / "TMessagesProj_App/build.gradle"
MANIFEST = ROOT / "TMessagesProj/src/main/AndroidManifest.xml"


def minimum_sdk(script):
    match = re.search(r"\\bminSdkVersion\\s+(\\d+)\\b", script)
    if match is None:
        raise AssertionError("minSdkVersion missing")
    return int(match.group(1))


class FirebaseCompatibilityTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.library = LIBRARY.read_text(encoding="utf-8")
        cls.app = APPLICATION.read_text(encoding="utf-8")

    def test_firebase_bom_not_newer_than_api21_compatible_line(self):
        match = re.search(r"firebase-bom:(\\d+)\\.(\\d+)\\.(\\d+)", self.library)
        self.assertIsNotNone(match, "Firebase BoM must be explicitly versioned")
        major, minor, patch = map(int, match.groups())
        self.assertLessEqual((major, minor, patch), (34, 19, 0))
        self.assertEqual((major, minor, patch), (34, 19, 0),
                         "Re-review SDK manifests before upgrading Firebase")

    def test_library_and_application_preserve_android_21(self):
        self.assertEqual(21, minimum_sdk(self.library))
        self.assertIn("defaultConfig", self.app)
        default = self.app.split("defaultConfig {", 1)[1]
        self.assertEqual(21, minimum_sdk(default))

    def test_universal_afat_flavor_does_not_raise_minimum(self):
        self.assertIn("afat {", self.app)
        flavor = self.app.split("afat {", 1)[1]
        self.assertEqual(21, minimum_sdk(flavor))

    def test_no_firebase_manifest_override(self):
        text = MANIFEST.read_text(encoding="utf-8")
        self.assertNotRegex(text, r'tools:overrideLibrary="[^"]*com\\.google\\.firebase')
        self.assertNotIn("firebase-analytics:24.0.0", self.library)


if __name__ == "__main__":
    unittest.main()
