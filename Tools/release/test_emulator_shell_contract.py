"""Regression gate for the emulator QA script on hosted runners.

The android-emulator-runner action invokes the script using /bin/sh, not bash.
This test must run before the expensive Android build.
"""
from pathlib import Path
import re
import subprocess
import unittest


WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/samsung-device-dev-apk.yml"


def emulator_script(text):
    match = re.search(r"(?m)^          script: \\|\\n((?:            [^\\n]*\\n|\\n)+)", text)
    if match is None:
        raise AssertionError("Missing emulator runner script")
    return "\n".join(line[12:] if line.startswith("            ") else line
                     for line in match.group(1).splitlines())


class EmulatorShellContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.script = emulator_script(cls.workflow)

    def test_shell_options_are_posix(self):
        commands = [line.strip() for line in self.script.splitlines()
                    if line.strip() and not line.strip().startswith("#")]
        self.assertEqual("set -eu", commands[0])
        self.assertNotIn("pipefail", self.script)
        self.assertNotIn("[[", self.script)

    def test_shell_parses_entire_emulator_script(self):
        result = subprocess.run(["/bin/sh", "-n"], input=self.script,
                                text=True, capture_output=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_workflow_checks_before_build(self):
        self.assertLess(
            self.workflow.index("test_emulator_shell_contract.py"),
            self.workflow.index(":TMessagesProj_App:assembleAfatRelease"),
        )
        self.assertIn("adb install \"$apk\"", self.script)
        self.assertIn("adb shell pidof org.comunidadtelebots.dev", self.script)


if __name__ == "__main__":
    unittest.main()
