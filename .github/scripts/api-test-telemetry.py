#!/usr/bin/env python3
"""Best-effort diagnostics for the Linux API test jobs."""

import json
import os
import platform
import re
import select
import shutil
import signal
import subprocess
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path


SAMPLE_SECONDS = 10
POSTGRES_WAITS = """
SET statement_timeout = '2s';
SELECT coalesce(json_agg(wait_counts), '[]'::json)
FROM (
    SELECT state, wait_event_type, wait_event, count(*) AS connections,
           max(extract(epoch FROM clock_timestamp() - xact_start)) AS oldest_transaction_seconds
    FROM pg_stat_activity
    WHERE datname = current_database() AND pid <> pg_backend_pid()
    GROUP BY state, wait_event_type, wait_event
) AS wait_counts;
"""


def timestamp():
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def telemetry_dir():
    return Path(os.environ["RUNNER_TEMP"]) / "api-test-telemetry"


def record_phase(directory, phase):
    try:
        directory.mkdir(parents=True, exist_ok=True)
        with (directory / "phases.jsonl").open("a", encoding="utf-8") as output:
            output.write(json.dumps({
                "phase": phase, "timestamp": timestamp(), "monotonic": time.monotonic()
            }) + "\n")
    except OSError as error:
        print(f"Telemetry phase unavailable: {error}", file=sys.stderr)


def maven_phase(line, tests_started):
    line = re.sub(r"\x1b\[[0-9;]*m", "", line)
    if re.search(r"--- compiler:[^ ]+:compile ", line):
        return "compile-main"
    if re.search(r"--- compiler:[^ ]+:testCompile ", line):
        return "compile-tests"
    if re.search(r"--- surefire:[^ ]+:test ", line):
        return "surefire-startup"
    if not tests_started and line.startswith("[INFO] Running "):
        return "test-execution"
    if line.strip() == "[INFO] Results:":
        return "maven-finalize"
    return None


def run_command(directory, command):
    record_phase(directory, "maven-startup")
    environment = os.environ.copy()
    try:
        log = (directory / "maven.log").open("w", encoding="utf-8", buffering=1)
    except OSError as error:
        print(f"Telemetry disabled for command: {error}", file=sys.stderr)
        return subprocess.call(command)

    gc_option = (
        f'-Xlog:gc*,safepoint:file="{directory.as_posix()}/jvm-%p.log"'
        ':time,uptime,level,tags:filecount=2,filesize=8M'
    )
    environment["JAVA_TOOL_OPTIONS"] = (
        environment.get("JAVA_TOOL_OPTIONS", "") + " " + gc_option
    ).strip()
    if Path("/usr/bin/time").is_file():
        command = ["/usr/bin/time", "-v", "-o", str(directory / "process-time.txt"), *command]

    with log, subprocess.Popen(
        command, env=environment, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
        text=True, encoding="utf-8", errors="replace", bufsize=1
    ) as process:
        tests_started = False
        for line in process.stdout:
            print(line, end="", flush=True)
            try:
                log.write(f"{timestamp()} {line}")
            except OSError:
                pass
            phase = maven_phase(line, tests_started)
            if phase:
                record_phase(directory, phase)
                tests_started = tests_started or phase == "test-execution"
        result = process.wait()
    record_phase(directory, "post-tests")
    try:
        (directory / "result.json").write_text(
            json.dumps({"exit_code": result, "timestamp": timestamp()}), encoding="utf-8"
        )
    except OSError:
        pass
    return result if result >= 0 else 128 - result


def write_summary(directory):
    events = [json.loads(line) for line in (directory / "phases.jsonl").read_text(
        encoding="utf-8"
    ).splitlines()]
    rows = [
        {"phase": before["phase"], "start": before["timestamp"],
         "seconds": round(after["monotonic"] - before["monotonic"], 3)}
        for before, after in zip(events, events[1:])
    ]
    (directory / "timings.json").write_text(json.dumps(rows, indent=2), encoding="utf-8")
    summary = "## API phase timings\n\n| Phase | Seconds |\n| --- | ---: |\n"
    summary += "".join(f'| {row["phase"]} | {row["seconds"]:.1f} |\n' for row in rows)
    summary += (
        "\nTimes are elapsed wall time between observed phase markers. Test execution includes "
        "Spring startup and fixtures. Artifact waiting is not runner work.\n"
        "\nResource samples, GC logs, timestamped Maven output and Surefire reports are in the "
        "`api-telemetry-shard-*` artifact. Missing tools/services are recorded in collector.log "
        "or resources.jsonl.\n"
    )
    (directory / "summary.md").write_text(summary, encoding="utf-8")
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a", encoding="utf-8") as output:
            output.write(summary)


def read_kernel_files(paths):
    result = {}
    for path in paths:
        try:
            result[str(path)] = Path(path).read_text(encoding="utf-8")
        except OSError:
            pass
    return result


def capture(command):
    try:
        result = subprocess.run(
            command, capture_output=True, text=True, encoding="utf-8", errors="replace",
            timeout=4, env={**os.environ, "LC_ALL": "C"}
        )
        return {"exit_code": result.returncode, "stdout": result.stdout, "stderr": result.stderr}
    except (OSError, subprocess.TimeoutExpired) as error:
        return {"error": str(error)}


def sample_resources():
    return {
        "timestamp": timestamp(),
        "kernel": read_kernel_files([
            "/proc/stat", "/proc/meminfo", "/proc/vmstat", "/proc/diskstats",
            "/proc/loadavg", "/proc/pressure/cpu", "/proc/pressure/io", "/proc/pressure/memory",
            "/sys/fs/cgroup/cpu.stat", "/sys/fs/cgroup/cpu.max", "/sys/fs/cgroup/memory.events"
        ]),
        "docker": capture(["docker", "stats", "--no-stream", "--format", "{{json .}}"]),
        "postgres": capture([
            "docker", "exec", "pgsql", "psql", "-X", "-U", "openaev", "-d", "openaev",
            "-qAt", "-c", POSTGRES_WAITS
        ]),
    }


def monitor(directory, stopped=None):
    stopped = stopped or threading.Event()
    signal.signal(signal.SIGTERM, lambda *_: stopped.set())
    signal.signal(signal.SIGINT, lambda *_: stopped.set())
    samplers = []
    streams = []
    try:
        for name, arguments in (
            ("vmstat", ["-w", "-t", "5"]), ("iostat", ["-x", "-t", "-y", "5"])
        ):
            if not shutil.which(name):
                print(f"{timestamp()} {name} unavailable; raw /proc counters are still sampled", flush=True)
                continue
            stream = (directory / f"{name}.log").open("w", encoding="utf-8")
            streams.append(stream)
            try:
                samplers.append(subprocess.Popen(
                    [name, *arguments], stdout=stream, stderr=subprocess.STDOUT,
                    env={**os.environ, "LC_ALL": "C", "TZ": "UTC"}
                ))
            except OSError as error:
                print(f"{timestamp()} {name} unavailable: {error}", flush=True)
        with (directory / "resources.jsonl").open("a", encoding="utf-8", buffering=1) as output:
            while not stopped.is_set():
                started = time.monotonic()
                output.write(json.dumps(sample_resources()) + "\n")
                stopped.wait(max(0, SAMPLE_SECONDS - (time.monotonic() - started)))
    finally:
        for sampler in samplers:
            if sampler.poll() is None:
                sampler.terminate()
                try:
                    sampler.wait(timeout=2)
                except subprocess.TimeoutExpired:
                    sampler.kill()
                    sampler.wait()
        for stream in streams:
            stream.close()


def start(directory):
    record_phase(directory, "telemetry-startup")
    metadata = {
        "timestamp": timestamp(), "platform": platform.uname()._asdict(),
        "cpu_count": os.cpu_count(),
        "runner": {key: os.environ.get(key) for key in (
            "RUNNER_ARCH", "RUNNER_OS", "ImageOS", "ImageVersion", "GITHUB_SHA",
            "GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT"
        )},
        "hardware": read_kernel_files([
            Path("/proc/cpuinfo"),
            *Path("/sys/block").glob("*/queue/read_ahead_kb"),
            *Path("/sys/block").glob("*/queue/rotational"),
            *Path("/sys/block").glob("*/device/model"),
        ]),
    }
    (directory / "machine.json").write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    with (directory / "collector.log").open("a", encoding="utf-8") as output:
        process = subprocess.Popen(
            [sys.executable, str(Path(__file__).resolve()), "monitor"],
            stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT,
            start_new_session=True
        )
    (directory / "collector.pid").write_text(str(process.pid), encoding="utf-8")
    record_phase(directory, "service-containers")


def stop(directory):
    record_phase(directory, "complete")
    try:
        pid = int((directory / "collector.pid").read_text(encoding="utf-8"))
        command = Path(f"/proc/{pid}/cmdline").read_bytes().split(b"\0")
        if os.fsencode(Path(__file__).resolve()) in command and b"monitor" in command:
            descriptor = os.pidfd_open(pid)
            try:
                os.killpg(pid, signal.SIGTERM)
                if not select.select([descriptor], [], [], 10)[0]:
                    os.killpg(pid, signal.SIGKILL)
                    select.select([descriptor], [], [], 2)
            finally:
                os.close(descriptor)
    except (OSError, ValueError) as error:
        print(f"Telemetry collector already stopped or unavailable: {error}", file=sys.stderr)
    write_summary(directory)


def main():
    directory = telemetry_dir()
    operation, *arguments = sys.argv[1:]
    if operation == "run":
        return run_command(directory, arguments)
    try:
        if operation == "start":
            start(directory)
        elif operation == "mark":
            record_phase(directory, arguments[0])
        elif operation == "monitor":
            monitor(directory)
        elif operation == "stop":
            stop(directory)
        else:
            raise ValueError(f"Unknown operation: {operation}")
    except (OSError, ValueError) as error:
        print(f"Telemetry {operation} incomplete: {error}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())