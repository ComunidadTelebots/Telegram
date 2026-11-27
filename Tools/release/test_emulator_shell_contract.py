"""Regression for the emulator runner's independent /bin/sh command invocation."""
from pathlib import Path
import os
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
WORKFLOW = ROOT / ".github/workflows/samsung-device-dev-apk.yml"
SMOKE = ROOT / "Tools/release/emulator_smoke.sh"


def emulator_script(text):
    lines = text.splitlines()
    start = lines.index("          script: |")
    commands = []
    for line in lines[start + 1:]:
        if line and not line.startswith("            "):
            break
        commands.append(line[12:] if line.startswith("            ") else "")
    return "\n".join(commands)


class EmulatorShellContractTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = WORKFLOW.read_text(encoding="utf-8")
        cls.command = emulator_script(cls.workflow).strip()
        cls.script = SMOKE.read_text(encoding="utf-8")

    def test_runner_uses_one_process(self):
        self.assertEqual('sh "$GITHUB_WORKSPACE/Tools/release/emulator_smoke.sh"', self.command)

    def test_posix_script(self):
        self.assertIn("set -eu", self.script)
        result = subprocess.run(["/bin/sh", "-n", str(SMOKE)], capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_checks_run_before_build(self):
        self.assertLess(self.workflow.index("test_emulator_shell_contract.py"),
                        self.workflow.index(":TMessagesProj_App:assembleAfatRelease"))

    def simulate(self, has_apk, crash=False, pid_mode="normal", log_failure=False):
        with tempfile.TemporaryDirectory(prefix="telebots smoke ") as folder:
            root = Path(folder)
            (root / "Tools/release").mkdir(parents=True)
            (root / "Tools/release/emulator_smoke.sh").write_text(self.script, encoding="utf-8")
            (root / "artifacts").mkdir()
            if has_apk:
                (root / "artifacts/telebots-dev-universal.apk").write_bytes(b"synthetic APK")
            bin_dir = root / "bin"
            bin_dir.mkdir()
            adb = bin_dir / "adb"
            adb.write_text(
                '#!/bin/sh\n'
                'printf "%s\\n" "$*" >> "$ADB_TRACE"\n'
                'case "$*" in\n'
                '  "shell pm path "*) echo "package:/data/app/base.apk";;\n'
                '  "shell pidof "*)\n'
                '    if [ "$FAKE_PID_MODE" = missing ]; then exit 1; fi\n'
                '    if [ "$FAKE_PID_MODE" = delayed ] && [ "$(grep -c "^shell pidof " "$ADB_TRACE")" -eq 1 ]; then exit 1; fi\n'
                '    echo 42;;\n'
                '  "logcat -b crash -b events -d -v brief") '
                'if [ "$FAKE_LOG_FAILURE" = 1 ]; then exit 1; fi; '
                'if [ "$FAKE_CRASH" = 1 ]; then echo "Process: org.comunidadtelebots.dev, PID: 42"; fi;;\n'
                'esac\nexit 0\n', encoding="utf-8")
            adb.chmod(0o755)
            sleeper = bin_dir / "sleep"
            sleeper.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            sleeper.chmod(0o755)
            trace = root / "trace"
            env = dict(os.environ, GITHUB_WORKSPACE=str(root), ADB_TRACE=str(trace),
                       FAKE_CRASH="1" if crash else "0", FAKE_PID_MODE=pid_mode,
                       FAKE_LOG_FAILURE="1" if log_failure else "0",
                       PATH=str(bin_dir) + os.pathsep + os.environ["PATH"])
            # Model the action: execute each non-empty line in a separate shell,
            # from outside the repository, not sharing shell variables.
            result = None
            for line in self.command.splitlines():
                if line.strip():
                    result = subprocess.run(["/bin/sh", "-c", line], cwd="/",
                                            env=env, capture_output=True, text=True)
                    if result.returncode:
                        break
            return result, trace.read_text() if trace.exists() else ""

    def test_candidate_reaches_both_profiles(self):
        result, trace = self.simulate(True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("install ", trace)
        self.assertIn("PASS installation and startup: phone", result.stdout)
        self.assertIn("PASS installation and startup: tablet", result.stdout)

    def test_missing_candidate_fails_before_adb(self):
        result, trace = self.simulate(False)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("APK candidate missing or empty", result.stderr)
        self.assertEqual("", trace)

    def test_crash_fails_gate(self):
        result, trace = self.simulate(True, crash=True, pid_mode="missing")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Application crash or ANR on phone", result.stdout)
        self.assertNotIn("PASS installation and startup: tablet", result.stdout)

    def test_missing_pid_collects_logs_and_fails_explicitly(self):
        result, trace = self.simulate(True, pid_mode="missing")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Application process not found on phone", result.stdout)
        self.assertEqual(6, trace.count("logcat -b crash -b events -d -v brief"))
        self.assertNotIn("PASS installation and startup", result.stdout)

    def test_delayed_start_can_reach_both_profiles(self):
        result, trace = self.simulate(True, pid_mode="delayed")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("PASS installation and startup: tablet", result.stdout)
        self.assertEqual(3, trace.count("shell pidof "))

    def test_crash_buffer_failure_is_not_success(self):
        result, trace = self.simulate(True, log_failure=True)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Cannot read crash/event buffers on phone", result.stdout)
        self.assertNotIn("PASS installation and startup", result.stdout)


if __name__ == "__main__":
    unittest.main()
