import json
import re
import unittest
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[2]
DATA = json.loads((ROOT / "TMessagesProj/src/main/assets/client_design_history.json").read_text(encoding="utf-8"))
REPOS = {
    "android": "DrKLO/Telegram", "ios": "TelegramMessenger/Telegram-iOS",
    "telegramx": "TGX-Android/Telegram-X", "unigram": "UnigramDev/Unigram",
    "macos": "overtake/TelegramSwift", "web": "Ajaxy/telegram-tt", "webk": "morethanwords/tweb",
}

class DesignHistoryTest(unittest.TestCase):
    def test_each_requested_client_has_distinct_milestones(self):
        entries = DATA["milestones"]
        self.assertEqual(len(entries), len({x["id"] for x in entries}))
        self.assertEqual(set(REPOS), {x["family"] for x in entries})
        for family in REPOS:
            self.assertGreaterEqual(len({x["commit"] for x in entries if x["family"] == family}), 2)

    def test_sources_are_pinned_and_belong_to_the_client(self):
        for item in DATA["milestones"]:
            with self.subTest(id=item["id"]):
                self.assertRegex(item["commit"], r"^[0-9a-f]{40}$")
                repo = "https://github.com/" + REPOS[item["family"]]
                self.assertEqual(item["repository"], repo)
                self.assertEqual(item["sourceUrl"], repo + "/commit/" + item["commit"])
                self.assertRegex(item["compareUrl"], re.escape(repo) + r"/compare/[0-9a-f]{40}\.\.\." + item["commit"] + r"$")
                if item["tag"]:
                    self.assertEqual(item["releaseUrl"], repo + "/releases/tag/" + item["tag"])
                else:
                    self.assertEqual(item["releaseUrl"], "")

    def test_unimplemented_history_is_not_misrepresented(self):
        for item in DATA["milestones"]:
            self.assertEqual(item["implementation"], "reference-only")
            self.assertIn(item["verifiedBy"], ["source-diff", "release-notes"])
            self.assertRegex(item["date"], r"^\d{4}-\d{2}-\d{2}$")
            for field in ["title", "changes"]:
                for language in ["es", "en"]:
                    self.assertTrue(item[field][language].strip())

if __name__ == "__main__":
    unittest.main()
