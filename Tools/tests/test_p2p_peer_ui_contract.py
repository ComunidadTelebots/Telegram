"""Static P2P UI checks; no Android SDK or credentials required."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / "TMessagesProj/src/main/java/org/telegram/ui"
ENGINE = ROOT / "TMessagesProj/src/main/java/org/telegram/messenger/TelebotsP2pIndependentMessaging.java"


class P2pPeerUiContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.chat = (UI / "TelebotsP2pChatActivity.java").read_text(encoding="utf-8")
        cls.label = (UI / "TelebotsPeerStatus.java").read_text(encoding="utf-8")
        cls.engine = ENGINE.read_text(encoding="utf-8")

    def test_counter_never_claims_a_minimum_peer(self):
        self.assertNotIn("getActivePeers().size(), 1", self.chat)
        self.assertEqual(2, self.chat.count("TelebotsPeerStatus.label("))
        self.assertIn("Math.max(0, activePeers)", self.label)

    def test_refreshes_when_fragment_resumes(self):
        resume = self.chat.split("public void onResume()", 1)[1].split("public void onPause()", 1)[0]
        self.assertIn("updatePeersIndicator();", resume)
        discovered = self.chat.split("public void onPeerDiscovered(", 1)[1]
        self.assertIn("updatePeersIndicator();", discovered)

    def test_discloses_plaintext_limitation(self):
        self.assertIn("sin cifrado autenticado", self.chat)
        self.assertIn("No compartas información privada", self.chat)

    def test_existing_engine_owns_main_thread_dispatch(self):
        # The UI callback is already marshalled to main by the messaging engine.
        section = self.engine.split("private void notifyMessage(", 1)[1]
        self.assertIn("AndroidUtilities.runOnUIThread(", section)
        self.assertIn("l.onMessageReceived(msg)", section)


if __name__ == "__main__":
    unittest.main()
