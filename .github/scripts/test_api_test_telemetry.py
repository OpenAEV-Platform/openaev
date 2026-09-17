"""Focused checks for CI diagnostics without starting backing services."""

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

SCRIPT = Path(__file__).with_name("api-test-telemetry.py")
SPEC = importlib.util.spec_from_file_location("api_test_telemetry", SCRIPT)
telemetry = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(telemetry)


class ApiTestTelemetryTest(unittest.TestCase):
    def test_maven_phase_markers(self):
        cases = [
            ("[INFO] --- compiler:3.16.0:compile (default-compile) @ openaev-api ---", "compile-main"),
            ("[INFO] --- compiler:3.16.0:testCompile (default-testCompile) @ openaev-api ---", "compile-tests"),
            ("[INFO] --- surefire:3.5.6:test (default-test) @ openaev-api ---", "surefire-startup"),
            ("\x1b[32m[INFO] Running Suite with display name\x1b[0m", "test-execution"),
            ("[INFO] Results:\n", "maven-finalize"),
            ("[INFO] Tests run: 5, Time elapsed: 20 s -- in Nested suite", None),
        ]
        for line, expected in cases:
            with self.subTest(line=line):
                self.assertEqual(telemetry.maven_phase(line, False), expected)
        self.assertIsNone(telemetry.maven_phase("[INFO] Running Another suite", True))

    def test_wrapper_preserves_arguments_exit_code_and_java_options(self):
        with tempfile.TemporaryDirectory() as temporary:
            environment = {**os.environ, "RUNNER_TEMP": temporary,
                           "JAVA_TOOL_OPTIONS": "-Dexisting=true"}
            child = (
                "import os,sys; "
                "print(os.environ['JAVA_TOOL_OPTIONS']); "
                "print(repr(sys.argv[1:])); "
                "print('[INFO] Running Display name'); "
                "print('[INFO] Results:'); "
                "sys.exit(7)"
            )
            result = subprocess.run(
                [sys.executable, str(SCRIPT), "run", sys.executable, "-c", child,
                 "-Dactive-tables=*", "argument with spaces"],
                env=environment, text=True, capture_output=True, timeout=15
            )
            self.assertEqual(result.returncode, 7, result.stderr)
            self.assertIn("-Dexisting=true -Xlog:gc*,safepoint:", result.stdout)
            self.assertIn("['-Dactive-tables=*', 'argument with spaces']", result.stdout)
            directory = Path(temporary) / "api-test-telemetry"
            events = [json.loads(line) for line in (directory / "phases.jsonl").read_text().splitlines()]
            self.assertEqual([event["phase"] for event in events],
                             ["maven-startup", "test-execution", "maven-finalize", "post-tests"])
            self.assertEqual(json.loads((directory / "result.json").read_text())["exit_code"], 7)

    def test_unwritable_telemetry_does_not_prevent_command(self):
        with tempfile.TemporaryDirectory() as temporary:
            blocked = Path(temporary) / "file"
            blocked.touch()
            result = telemetry.run_command(blocked, [sys.executable, "-c", "raise SystemExit(4)"])
            self.assertEqual(result, 4)

    def test_summary_uses_elapsed_phases_not_nested_suite_durations(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            with patch.object(telemetry.time, "monotonic", side_effect=[10, 30, 35]), \
                    patch.dict(os.environ, {"GITHUB_STEP_SUMMARY": str(directory / "job.md")}):
                telemetry.record_phase(directory, "artifact-wait")
                telemetry.record_phase(directory, "artifact-downloads")
                telemetry.record_phase(directory, "complete")
                telemetry.write_summary(directory)
            rows = json.loads((directory / "timings.json").read_text())
            self.assertEqual([row["seconds"] for row in rows], [20, 5])
            self.assertIn("| artifact-wait | 20.0 |", (directory / "job.md").read_text())

    def test_missing_and_slow_tools_are_nonfatal(self):
        for error in (FileNotFoundError("docker unavailable"), subprocess.TimeoutExpired("docker", 4)):
            with self.subTest(error=error), patch.object(telemetry.subprocess, "run", side_effect=error):
                self.assertIn("error", telemetry.capture(["docker", "stats"]))

    def test_samples_are_timestamped_and_do_not_collect_sql_text(self):
        with patch.object(telemetry, "capture", return_value={"exit_code": 0}) as capture, \
                patch.object(telemetry, "read_kernel_files", return_value={"/proc/stat": "cpu 1 2 3"}):
            sample = telemetry.sample_resources()
        self.assertIn("timestamp", sample)
        self.assertEqual(sample["kernel"]["/proc/stat"], "cpu 1 2 3")
        self.assertEqual(capture.call_count, 2)
        self.assertIn("statement_timeout = '2s'", telemetry.POSTGRES_WAITS)
        self.assertIn("pid <> pg_backend_pid()", telemetry.POSTGRES_WAITS)
        self.assertNotIn("query", telemetry.POSTGRES_WAITS)

    def test_monitor_stops_child_samplers(self):
        with tempfile.TemporaryDirectory() as temporary:
            stopped = threading.Event()
            sampler = Mock()
            sampler.poll.return_value = None

            def sample_once():
                stopped.set()
                return {"timestamp": "sample"}

            with patch.object(telemetry.signal, "signal"), \
                    patch.object(telemetry.shutil, "which", return_value="available"), \
                    patch.object(telemetry.subprocess, "Popen", return_value=sampler), \
                    patch.object(telemetry, "sample_resources", side_effect=sample_once):
                telemetry.monitor(Path(temporary), stopped)
            self.assertEqual(sampler.terminate.call_count, 2)
            self.assertEqual(sampler.wait.call_count, 2)
            rows = (Path(temporary) / "resources.jsonl").read_text().splitlines()
            self.assertEqual(len(rows), 1)

    def test_stop_does_not_signal_unrelated_process(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "collector.pid").write_text("123")
            with patch.object(telemetry.Path, "read_bytes", return_value=b"other-command\0"):
                telemetry.stop(directory)
            self.assertTrue((directory / "summary.md").is_file())

    @unittest.skipUnless(sys.platform == "linux", "Requires Linux process groups and /proc")
    def test_linux_collector_lifecycle(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary) / "api-test-telemetry"
            tools = Path(temporary) / "bin"
            tools.mkdir()
            docker = tools / "docker"
            docker.write_text("#!/bin/sh\nprintf '%s\\n' '[]'\n", encoding="utf-8")
            docker.chmod(0o755)
            processes = []
            original_popen = subprocess.Popen

            def launch(*arguments, **options):
                process = original_popen(*arguments, **options)
                processes.append(process)
                return process

            environment = {"RUNNER_TEMP": temporary, "PATH": f'{tools}:{os.environ["PATH"]}',
                           "GITHUB_STEP_SUMMARY": str(Path(temporary) / "job.md")}
            with patch.dict(os.environ, environment), patch.object(telemetry.subprocess, "Popen", side_effect=launch):
                telemetry.start(directory)
                collector_pid = int((directory / "collector.pid").read_text())
                process = next(process for process in processes if process.pid == collector_pid)
                try:
                    deadline = telemetry.time.monotonic() + 10
                    resources = directory / "resources.jsonl"
                    while not resources.exists() or not resources.stat().st_size:
                        self.assertIsNone(process.poll(), (directory / "collector.log").read_text())
                        self.assertLess(telemetry.time.monotonic(), deadline, "Collector did not sample")
                        threading.Event().wait(0.05)
                    sample = json.loads(resources.read_text().splitlines()[0])
                    self.assertIn("/proc/stat", sample["kernel"])
                    self.assertEqual(sample["docker"]["exit_code"], 0)
                    self.assertEqual(sample["postgres"]["exit_code"], 0)
                    children = Path(f"/proc/{process.pid}/task/{process.pid}/children").read_text().split()
                    telemetry.stop(directory)
                    self.assertEqual(process.poll(), 0, "Stop must finish before artifact upload")
                    for child in children:
                        self.assertFalse(Path(f"/proc/{child}").exists(), f"Sampler {child} survived cleanup")
                    self.assertTrue((directory / "timings.json").is_file())
                finally:
                    if process.poll() is None:
                        os.killpg(process.pid, telemetry.signal.SIGKILL)
                        process.wait()


if __name__ == "__main__":
    unittest.main()