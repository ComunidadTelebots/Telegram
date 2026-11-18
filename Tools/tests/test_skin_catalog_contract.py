"""Static regression contract against Canva reference DAHaJ0oNnF4 (2026-11-06).

These tests verify source constants, not screenshots or device fidelity.
No Telegram credentials, Android SDK, third-party images, or network needed.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKIN_ROOT = ROOT / "TMessagesProj/src/main/java/org/telegram/ui/skins/skins"

# ID, Java class, bubble radius dp, tail, avatar factor, chat dividers.
# Frozen 16-style values from the existing Canva reference, NOT new designs.
SKINS = [
    ("android:current", "AndroidSkin", 17, True, 1.0, True),
    ("android:original", "AndroidOriginalSkin", 4, True, 1.0, True),
    ("android:holo", "AndroidHoloSkin", 5, True, 1.0, True),
    ("android:material", "AndroidMaterialSkin", 8, True, 1.0, True),
    ("android:classic", "AndroidClassicSkin", 12, True, 1.0, True),
    ("android:redesign", "AndroidRedesignSkin", 16, True, 1.0, False),
    ("android:glass", "AndroidGlassSkin", 18, False, 0.25, False),
    ("web", "WebSkin", 18, False, 1.0, False),
    ("webk", "WebKSkin", 12, True, 1.0, True),
    ("webogram", "WebogramSkin", 6, True, 1.0, True),
    ("ios", "IosSkin", 18, True, 1.0, True),
    ("macos", "MacosSkin", 10, False, 1.0, False),
    ("tdesktop", "TdesktopSkin", 6, True, 1.0, True),
    ("unigram", "UnigramSkin", 4, False, 1.0, True),
    ("telegramx", "TelegramXSkin", 14, True, 1.0, False),
    ("aurora", "AuroraSkin", 18, False, 0.3, False),
]


def java_return(source, method):
    pattern = r"\b" + re.escape(method) + r"\s*\(\s*\)\s*\{\s*return\s+([^;]+);"
    result = re.search(pattern, source)
    if result is None:
        raise AssertionError("Missing literal return for " + method)
    return result.group(1).strip()


class SkinCatalogContractTests(unittest.TestCase):
    def test_catalog_has_sixteen_unique_identifiers(self):
        self.assertEqual(16, len(SKINS))
        self.assertEqual(16, len({item[0] for item in SKINS}))

    def test_registered_order_and_identity(self):
        manager = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/skins/SkinManager.java").read_text(encoding="utf-8")
        registered = re.findall(r"skins\.add\(new\s+(\w+)\(\)\);", manager)
        self.assertEqual([entry[1] for entry in SKINS], registered)

    def test_each_profile_matches_frozen_geometry_and_dividers(self):
        for skin_id, class_name, radius, tail, avatar, dividers in SKINS:
            with self.subTest(skin_id=skin_id):
                source = (SKIN_ROOT / (class_name + ".java")).read_text(encoding="utf-8")
                self.assertEqual(skin_id, java_return(source, "getId").strip('"'))
                self.assertEqual(radius, int(java_return(source, "getBubbleRadiusDP")))
                self.assertEqual(tail, java_return(source, "hasBubbleTail") == "true")
                self.assertAlmostEqual(avatar, float(java_return(source, "getAvatarRadiusFactor").rstrip("fF")))
                self.assertEqual(dividers, java_return(source, "showChatDividers") == "true")

    def test_motion_values_remain_independent_from_skins(self):
        source = (ROOT / "TMessagesProj/src/main/java/org/telegram/ui/skins/MotionStyle.java").read_text(encoding="utf-8")
        expected = [
            ("ORIGINAL", "original", 150, 48),
            ("SMOOTH", "smooth", 240, 32),
            ("QUICK", "quick", 120, 24),
            ("FADE", "fade", 180, 0),
            ("NONE", "none", 0, 0),
        ]
        for name, persisted_id, duration, distance in expected:
            with self.subTest(motion=name):
                pattern = re.escape(name) + r'\s*\(\s*"' + re.escape(persisted_id) + r'"\s*,\s*' + str(duration) + r'\s*,\s*' + str(distance) + r'\s*\)'
                self.assertRegex(source, pattern)


if __name__ == "__main__":
    unittest.main()
