"""Offline unit tests: do not call Telegram networks."""
import importlib.util
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "telegram_probe", Path(__file__).resolve().parent / "check_telegram_datacenters.py"
)
probe = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(probe)


class TelegramDatacenterProbeTest(unittest.TestCase):
    def test_credential_validation_does_not_expose_values(self):
        secret = "a" * 32
        status = probe.credentials_status({"TELEGRAM_API_ID": "12345",
                                           "TELEGRAM_API_HASH": secret})
        self.assertTrue(all(status.values()))
        self.assertNotIn(secret, str(status))

    def test_invalid_credentials(self):
        result = probe.credentials_status({"TELEGRAM_API_ID": "0",
                                           "TELEGRAM_API_HASH": "invalid"})
        self.assertFalse(any(result.values()))

    @patch.object(probe, "check_tcp", return_value=(True, None))
    def test_both_datacenters_are_checked_without_auth(self, connect):
        with tempfile.TemporaryDirectory() as folder, patch.dict(
            os.environ, {"TELEGRAM_API_ID": "12345", "TELEGRAM_API_HASH": "a"*32}
        ):
            report = Path(folder) / "report.json"
            self.assertEqual(probe.main(["--output", str(report)]), 0)
            contents = report.read_text()
            self.assertIn('"production"', contents)
            self.assertIn('"test"', contents)
            self.assertIn('"mtproto_authenticated": false', contents)
            self.assertNotIn("a" * 32, contents)
            self.assertEqual(connect.call_count, 2)

    @patch.object(probe, "check_tcp", return_value=(False, "tcp_connection_failed"))
    def test_strict_mode_rejects_unreachable(self, connect):
        self.assertEqual(probe.main(["--environment", "test", "--strict-network"]), 3)


if __name__ == "__main__":
    unittest.main()
