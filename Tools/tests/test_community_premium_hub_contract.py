"""Static integration checks for the Premium Comunidad Telebots settings hub.

These tests assert source wiring only. They are not Android instrumentation tests.
"""
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "TMessagesProj/src/main/java/org/telegram/ui"


class CommunityPremiumHubContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.settings = (UI / "SettingsActivity.java").read_text(encoding="utf-8")
        cls.hub = (UI / "CommunityPremiumHubActivity.java").read_text(encoding="utf-8")
        cls.previous = (UI / "CommunitySettingsActivity.java").read_text(encoding="utf-8")

    def test_existing_settings_entry_routes_to_hub(self):
        self.assertRegex(self.settings, r"SettingCell.Factory.of\(101,[^;]*Bots, comunidad, novedades y recursos")
        self.assertRegex(self.settings, r"case 101:\s*presentSettingFragment\(new CommunityPremiumHubActivity\(\)\)")

    def test_old_settings_remain_accessible(self):
        self.assertIn("new CommunitySettingsActivity()", self.hub)
        self.assertIn("new SkinSelectorCell", self.previous)

    def test_navigation_and_nonfictional_empty_states(self):
        for label in ("Comunidad", "Explorador de bots", "Novedades", "Categorías",
                      "Recursos destacados", "Ajustes específicos"):
            with self.subTest(label=label):
                self.assertIn('"' + label + '"', self.hub)
        self.assertIn("new AppUpdatesActivity()", self.hub)
        self.assertIn("finishFragment()", self.hub)
        self.assertIn("No hay enlaces de grupos o canales verificados", self.hub)
        self.assertIn("Todavía no hay bots verificados", self.hub)

    def test_does_not_add_unverified_external_links_or_notification_switches(self):
        self.assertNotIn("openByUserName", self.hub)
        self.assertNotIn("https://t.me/", self.hub)
        self.assertNotIn("TextCheckCell", self.hub)
        self.assertNotIn("SharedPreferences", self.hub)


if __name__ == "__main__":
    unittest.main()
