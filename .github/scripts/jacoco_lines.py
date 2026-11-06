"""Line coverage of a JaCoCo XML report, counted the way Codecov counts it.

Codecov reads each `<line>` of the report as:

- a line with branches: a hit when every branch ran, a miss when none ran, partial in between;
- a line without branches: a hit when its instructions ran, a miss otherwise.

Partial lines are not hits. JaCoCo's own LINE counter, and diff-cover, call them covered, so they
read higher. Counting like Codecov keeps the CI check and the Codecov comment on the same numbers.

The base report is downloaded from a workflow artifact, so the parser refuses entity declarations:
no entity expansion, internal or external. JaCoCo reports declare none.
"""

from __future__ import annotations

import xml.etree.ElementTree as ET
from xml.parsers import expat

HIT, PARTIAL, MISS = "hit", "partial", "miss"


def classify(attributes: dict[str, str]) -> str:
    """State of one `<line>`: instructions (mi, ci) and branches (mb, cb) missed and covered."""
    missed_branches = int(attributes["mb"])
    covered_branches = int(attributes["cb"])
    if missed_branches:
        return PARTIAL if covered_branches else MISS
    if covered_branches:
        return HIT
    return HIT if int(attributes["ci"]) > 0 else MISS


def parse(xml: bytes) -> ET.Element:
    """Root of a JaCoCo XML report, refusing entity declarations."""

    def refuse(*_: object) -> None:
        raise ValueError("entity declarations are not allowed in a JaCoCo report")

    builder = ET.TreeBuilder()
    parser = expat.ParserCreate()
    parser.StartElementHandler = builder.start
    parser.EndElementHandler = builder.end
    parser.EntityDeclHandler = refuse
    parser.UnparsedEntityDeclHandler = refuse
    parser.Parse(xml, True)
    return builder.close()


def report_lines(xml: bytes, src_root: str) -> dict[str, dict[int, str]]:
    """State of every line of the report, by repository path then line number."""
    files: dict[str, dict[int, str]] = {}
    for package in parse(xml).iter("package"):
        for source in package.findall("sourcefile"):
            path = "/".join(
                part for part in (src_root, package.get("name"), source.get("name")) if part
            )
            files[path] = {
                int(line.get("nr")): classify(line.attrib)
                for line in source.findall("line")
                if int(line.get("nr")) > 0
            }
    return files


def line_totals(files: dict[str, dict[int, str]]) -> tuple[int, int, int]:
    """Hit, partial and missed lines of a whole report."""
    states = [state for lines in files.values() for state in lines.values()]
    return states.count(HIT), states.count(PARTIAL), states.count(MISS)
