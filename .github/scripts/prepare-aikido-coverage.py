#!/usr/bin/env python3
"""Prepare the coverage reports of the three test suites for the Aikido upload.

Aikido reads LCOV and Cobertura reports, and works out which repository file each entry is about
from its path, which must be relative to the repository root. The suites write other things:

- the backend writes JaCoCo XML, which is converted to Cobertura, one class per source file, with
  the path of the file in the repository and, for lines with branches, the branches covered;
- the frontend unit tests and the E2E tests write LCOV with paths relative to `openaev-front`
  (`src/app.tsx`), which get the `openaev-front/` prefix. Entries that are not files of the
  repository (`localhost-8080`, `www.youtube.com`, ...) are left out.

Aikido detects the format from the file name: `*cobertura*.xml` and `*lcov*.info`.

    python3 .github/scripts/prepare-aikido-coverage.py --out coverage/upload \\
        --jacoco coverage/backend/jacoco.xml \\
        --lcov frontend=coverage/frontend/lcov.info --lcov e2e-arsenals=coverage/e2e/a/lcov.info
"""

from __future__ import annotations

import argparse
import re
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path


def rate(covered: int, valid: int) -> str:
    return f"{covered / valid:.4f}" if valid else "0"


def jacoco_to_cobertura(xml: bytes, src_root: str) -> bytes:
    """Cobertura XML with the lines and branches of a JaCoCo XML report."""
    report = ET.fromstring(xml)
    coverage = ET.Element("coverage")
    ET.SubElement(ET.SubElement(coverage, "sources"), "source").text = "."
    packages = ET.SubElement(coverage, "packages")
    overall = [0, 0, 0, 0]  # lines covered, lines valid, branches covered, branches valid

    for package in report.iter("package"):
        folder = package.get("name", "")
        package_element = ET.SubElement(
            packages, "package", {"name": folder.replace("/", "."), "complexity": "0"}
        )
        classes = ET.SubElement(package_element, "classes")
        in_package = [0, 0, 0, 0]
        for source in package.findall("sourcefile"):
            name = source.get("name")
            path = "/".join(part for part in (src_root, folder, name) if part)
            package_name = folder.replace("/", ".")
            class_name = ".".join(part for part in (package_name, Path(name).stem) if part)
            class_element = ET.SubElement(
                classes, "class", {"name": class_name, "filename": path, "complexity": "0"}
            )
            ET.SubElement(class_element, "methods")
            lines = ET.SubElement(class_element, "lines")
            in_class = [0, 0, 0, 0]
            for line in source.findall("line"):
                number = int(line.get("nr"))
                if number <= 0:
                    continue
                hits = int(line.get("ci"))
                missed, covered = int(line.get("mb")), int(line.get("cb"))
                attributes = {"number": str(number), "hits": str(hits), "branch": "false"}
                if missed + covered:
                    attributes["branch"] = "true"
                    percent = 100 * covered // (missed + covered)
                    attributes["condition-coverage"] = (
                        f"{percent}% ({covered}/{missed + covered})"
                    )
                ET.SubElement(lines, "line", attributes)
                in_class[0] += int(hits > 0)
                in_class[1] += 1
                in_class[2] += covered
                in_class[3] += missed + covered
            class_element.set("line-rate", rate(in_class[0], in_class[1]))
            class_element.set("branch-rate", rate(in_class[2], in_class[3]))
            in_package = [a + b for a, b in zip(in_package, in_class, strict=True)]
        package_element.set("line-rate", rate(in_package[0], in_package[1]))
        package_element.set("branch-rate", rate(in_package[2], in_package[3]))
        overall = [a + b for a, b in zip(overall, in_package, strict=True)]

    coverage.attrib.update(
        {
            "line-rate": rate(overall[0], overall[1]),
            "branch-rate": rate(overall[2], overall[3]),
            "lines-covered": str(overall[0]),
            "lines-valid": str(overall[1]),
            "branches-covered": str(overall[2]),
            "branches-valid": str(overall[3]),
            "complexity": "0",
            "version": "1",
            "timestamp": str(int(time.time())),
        }
    )
    return ET.tostring(coverage, encoding="utf-8", xml_declaration=True)


def prefix_lcov(text: str, prefix: str) -> str:
    """LCOV records of the repository's files, with their path made relative to the root."""
    kept = []
    for record in text.split("end_of_record"):
        record = record.strip("\n")
        source = re.search(r"^SF:(.*)$", record, re.MULTILINE)
        if source and source.group(1).startswith("src/"):
            kept.append(re.sub(r"^SF:", f"SF:{prefix}", record, count=1, flags=re.MULTILINE))
    return "".join(f"{record}\nend_of_record\n" for record in kept)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--out", required=True, help="folder the prepared reports are written to")
    parser.add_argument("--jacoco", help="JaCoCo XML report of the backend")
    parser.add_argument("--src-root", default="openaev-api/src/main/java")
    parser.add_argument(
        "--lcov", action="append", default=[], metavar="NAME=PATH", help="LCOV report, any number"
    )
    parser.add_argument("--lcov-prefix", default="openaev-front/")
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    written = []
    if args.jacoco:
        target = out / "backend-cobertura.xml"
        target.write_bytes(jacoco_to_cobertura(Path(args.jacoco).read_bytes(), args.src_root))
        written.append(target)
    for entry in args.lcov:
        name, _, path = entry.partition("=")
        target = out / f"{name}-lcov.info"
        target.write_text(
            prefix_lcov(Path(path).read_text(encoding="utf-8"), args.lcov_prefix), encoding="utf-8"
        )
        written.append(target)
    if not written:
        print("No coverage report to prepare.", file=sys.stderr)
        return 1
    for target in written:
        print(f"{target} ({target.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
