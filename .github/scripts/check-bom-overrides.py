#!/usr/bin/env python3
"""Check the Spring Boot BOM overrides declared in the root pom.xml.

The root pom redefines properties that the spring-boot-dependencies BOM also defines. Each one
changes the version of every artifact the BOM manages through that property, which is how Tomcat,
Logback and Jackson are held ahead of the Spring Boot line. Two things can go wrong with no build
error and no failing test:

  1. The override has no Renovate tracking path, so nothing ever proposes an update for it and
     nothing reports it going stale. That happened to kotlin.version, added on 2026-08-19 in
     0d0e99ead and unnoticed until 2026-09-08.
  2. The override stops taking effect and Maven resolves the Spring Boot version instead, which
     silently reverts whatever the override was for.

Nothing here is hardcoded. The override list is the intersection of the pom properties with the BOM
properties. The property to artifact mapping comes from the BOM's own dependencyManagement, followed
one level into imported BOMs such as jackson-bom and netty-bom. Expected versions come from those
BOMs rather than being assumed equal to the property, because a BOM member can carry its own version:
jackson-bom 2.22.2 ships jackson-annotations 2.22, and netty-bom 4.2.17.Final ships
netty-tcnative-boringssl-static 2.0.81.Final.

Usage:
    mvn -q -pl openaev-api -am dependency:list -DincludeScope=runtime \
        -DoutputFile="$PWD/target/deplist.txt" -DappendOutput=true
    python3 .github/scripts/check-bom-overrides.py --dependency-list target/deplist.txt

Exit 0 when clean, 1 when something is found. Whether that blocks a merge is decided by the
Pipeline Gate's advisory list, not here, so there is deliberately no soft-fail flag.
"""

from __future__ import annotations

import argparse
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

MAVEN_CENTRAL = "https://repo1.maven.org/maven2"
NS = "{http://maven.apache.org/POM/4.0.0}"
ANSI_RE = re.compile(r"\x1b\[[0-9;]*m")
PLACEHOLDER_RE = re.compile(r"\$\{([^}]+)\}")

# dependency:list prints group:artifact:type:version:scope, or with a classifier
# group:artifact:type:classifier:version:scope
DEP_5 = re.compile(r"^\s+([\w.\-]+):([\w.\-]+):([\w.\-]+):([\w.\-]+):([\w.\-]+)\b")
DEP_6 = re.compile(r"^\s+([\w.\-]+):([\w.\-]+):([\w.\-]+):([\w.\-]+):([\w.\-]+):([\w.\-]+)\b")
SCOPES = {"compile", "runtime", "provided", "test", "system", "import"}


def die(message: str) -> None:
    print(f"ERROR  {message}", file=sys.stderr)
    sys.exit(2)


def parse(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def text(node: ET.Element | None, tag: str, default: str = "") -> str:
    if node is None:
        return default
    return (node.findtext(f"{NS}{tag}") or default).strip()


def boot_version(pom: Path) -> str:
    root = parse(pom)
    parent = root.find(f"{NS}parent")
    if parent is None:
        die(f"{pom} has no <parent>, cannot resolve the Spring Boot version")
    group, artifact = text(parent, "groupId"), text(parent, "artifactId")
    if (group, artifact) != ("org.springframework.boot", "spring-boot-starter-parent"):
        die(f"{pom} parent is {group}:{artifact}, this check assumes spring-boot-starter-parent")
    return text(parent, "version")


def download(url: str, cache: Path) -> Path | None:
    if cache.exists() and cache.stat().st_size > 0:
        return cache
    try:
        with urllib.request.urlopen(url, timeout=60) as response:
            body = response.read()
    except Exception as err:  # noqa: BLE001 - the message is the useful part
        print(f"NOTE   could not download {url}: {err}")
        return None
    cache.parent.mkdir(parents=True, exist_ok=True)
    cache.write_bytes(body)
    return cache


def fetch_pom(group: str, artifact: str, version: str, cache_dir: Path) -> Path | None:
    url = f"{MAVEN_CENTRAL}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.pom"
    return download(url, cache_dir / f"{group}--{artifact}--{version}.pom")


def version_properties(root: ET.Element) -> dict[str, str]:
    node = root.find(f"{NS}properties")
    if node is None:
        return {}
    out = {}
    for child in node:
        tag = child.tag.replace(NS, "")
        if tag.endswith(".version"):
            out[tag] = (child.text or "").strip()
    return out


def all_properties(root: ET.Element) -> dict[str, str]:
    """Every property, plus the project coordinates that BOMs reference as ${project.*}."""
    out: dict[str, str] = {}
    node = root.find(f"{NS}properties")
    if node is not None:
        for child in node:
            out[child.tag.replace(NS, "")] = (child.text or "").strip()
    group = text(root, "groupId") or text(root.find(f"{NS}parent"), "groupId")
    version = text(root, "version") or text(root.find(f"{NS}parent"), "version")
    out.setdefault("project.groupId", group)
    out.setdefault("project.version", version)
    out.setdefault("pom.groupId", group)
    out.setdefault("pom.version", version)
    return out


def expand(value: str, properties: dict[str, str], depth: int = 4) -> str:
    """Substitute ${...} placeholders, a few levels deep, leaving unknown ones untouched."""
    for _ in range(depth):
        if "${" not in value:
            break
        value = PLACEHOLDER_RE.sub(lambda m: properties.get(m.group(1), m.group(0)), value)
    return value


def managed_by_property(bom: Path) -> dict[str, list[tuple[str, str, bool]]]:
    """property -> [(group, artifact, is_imported_bom)] that the BOM versions through ${property}."""
    root = parse(bom)
    properties = all_properties(root)
    out: dict[str, list[tuple[str, str, bool]]] = {}
    for dep in root.iter(f"{NS}dependency"):
        raw = text(dep, "version")
        match = PLACEHOLDER_RE.fullmatch(raw)
        if not match:
            continue
        group = expand(text(dep, "groupId"), properties)
        artifact = expand(text(dep, "artifactId"), properties)
        is_import = text(dep, "type") == "pom" and text(dep, "scope") == "import"
        if group and artifact:
            out.setdefault(match.group(1), []).append((group, artifact, is_import))
    return out


def declared_versions(bom: Path) -> dict[str, str]:
    """group:artifact -> version, with the BOM's own properties resolved."""
    root = parse(bom)
    properties = all_properties(root)
    out: dict[str, str] = {}
    for dep in root.iter(f"{NS}dependency"):
        group = expand(text(dep, "groupId"), properties)
        artifact = expand(text(dep, "artifactId"), properties)
        version = expand(text(dep, "version"), properties)
        if group and artifact and version and "${" not in version:
            out.setdefault(f"{group}:{artifact}", version)
    return out


def property_references(poms: list[Path]) -> dict[str, int]:
    """How many times each ${property} is used as a <version> in the repository's poms."""
    counts: dict[str, int] = {}
    for path in poms:
        for element in parse(path).iter():
            if element.tag != f"{NS}version":
                continue
            match = PLACEHOLDER_RE.fullmatch((element.text or "").strip())
            if match:
                counts[match.group(1)] = counts.get(match.group(1), 0) + 1
    return counts


def custom_manager_properties(renovate: Path) -> set[str]:
    """Properties captured by a renovate.json5 customManagers regex."""
    if not renovate.exists():
        return set()
    body = renovate.read_text(encoding="utf-8")
    found = set()
    for raw in re.findall(r"matchStrings:\s*\[([^\]]*)\]", body, re.S):
        # the dot may or may not be backslash-escaped in the regex, both are valid
        for name in re.findall(r"<([A-Za-z0-9][A-Za-z0-9._\\-]*\\?\.version)>", raw):
            found.add(name.replace("\\", ""))
    return found


def resolved_versions(dep_list: Path) -> dict[str, set[str]]:
    """group:artifact -> every resolved version seen, from a dependency:list output file.

    A set rather than a single version on purpose. dependency:list is run with appendOutput, so the
    file holds one section per module and the same artifact can appear at two versions. Keeping only
    the first would hide the drift this check exists to find.
    """
    out: dict[str, set[str]] = {}
    for line in dep_list.read_text(encoding="utf-8", errors="replace").splitlines():
        line = ANSI_RE.sub("", line).split(" -- ")[0].rstrip()
        six = DEP_6.match(line)
        if six and six.group(6) in SCOPES:
            group, artifact, version = six.group(1), six.group(2), six.group(5)
        else:
            five = DEP_5.match(line)
            if not (five and five.group(5) in SCOPES):
                continue
            group, artifact, version = five.group(1), five.group(2), five.group(4)
        out.setdefault(f"{group}:{artifact}", set()).add(version)
    return out


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pom", default="pom.xml")
    parser.add_argument("--renovate", default="renovate.json5")
    parser.add_argument("--dependency-list", default="target/deplist.txt")
    parser.add_argument("--cache-dir", default="target/bom-cache")
    args = parser.parse_args()

    root_pom = Path(args.pom)
    if not root_pom.exists():
        die(f"{root_pom} not found, run this from the repository root")

    cache = Path(args.cache_dir)
    version = boot_version(root_pom)
    bom = fetch_pom("org.springframework.boot", "spring-boot-dependencies", version, cache)
    if bom is None:
        die(f"spring-boot-dependencies {version} is required and could not be downloaded")

    pom_properties = version_properties(parse(root_pom))
    bom_properties = version_properties(parse(bom))
    overrides = {k: v for k, v in pom_properties.items() if k in bom_properties}

    poms = sorted(Path(".").glob("pom.xml")) + sorted(Path(".").glob("*/pom.xml"))
    references = property_references(poms)
    managers = custom_manager_properties(Path(args.renovate))
    managed = managed_by_property(bom)

    dep_list = Path(args.dependency_list)
    resolved = resolved_versions(dep_list) if dep_list.exists() else {}

    print(f"Spring Boot parent      : {version}")
    print(f"poms scanned            : {len(poms)}")
    print(f"pom.xml .version props  : {len(pom_properties)}")
    print(f"of which BOM overrides  : {len(overrides)}")
    print(f"artifacts on classpath  : {len(resolved)}")
    if not resolved:
        print(f"NOTE   {dep_list} is missing, the resolved-version half of the check is skipped")
    print()

    # ── part 1: is every override tracked by something ──────────────────────────
    untracked: list[str] = []
    print("Renovate tracking")
    print("-" * 104)
    print(f"{'property':32s} {'pom':22s} {'spring boot':18s} tracked by")
    for name in sorted(overrides):
        ours, theirs = overrides[name], bom_properties[name]
        alias = PLACEHOLDER_RE.fullmatch(ours)
        target = alias.group(1) if alias else name
        if references.get(target):
            how = f"maven manager, {references[target]} declaration(s)"
        elif target in managers:
            how = "renovate customManager"
        else:
            how = "NOTHING"
            untracked.append(name)
        if alias:
            how = f"alias of ${{{target}}}: {how}"
        print(f"{name:32s} {ours:22s} {theirs:18s} {how}")
    print()

    # ── part 2: does every override still take effect ───────────────────────────
    drifted: list[str] = []
    foreign: list[str] = []
    absent: list[str] = []
    if resolved:
        print("Resolved versions of the artifacts each override governs")
        print("-" * 104)
        for name in sorted(overrides):
            ours = overrides[name]
            if PLACEHOLDER_RE.fullmatch(ours):
                print(f"SKIP   {name} is an alias, checked through its target")
                continue
            theirs = bom_properties[name]
            entries = managed.get(name, [])
            if not entries:
                print(f"SKIP   {name} versions no dependency in the BOM directly")
                continue

            expected_ours: dict[str, str] = {}
            expected_boot: dict[str, str] = {}
            for group, artifact, is_import in entries:
                coordinate = f"{group}:{artifact}"
                if not is_import:
                    expected_ours[coordinate] = ours
                    expected_boot[coordinate] = theirs
                    continue
                nested_ours = fetch_pom(group, artifact, ours, cache)
                nested_boot = fetch_pom(group, artifact, theirs, cache)
                if nested_ours is None:
                    print(f"NOTE   {name}: {coordinate}:{ours} unavailable, cannot expand it")
                    continue
                expected_ours.update(declared_versions(nested_ours))
                if nested_boot is not None:
                    expected_boot.update(declared_versions(nested_boot))

            present = sorted(set(expected_ours) & set(resolved))
            if not present:
                absent.append(name)
                print(f"ABSENT {name}: none of the {len(expected_ours)} artifact(s) it governs are on "
                      f"the runtime classpath")
                continue
            for coordinate in present:
                seen = resolved[coordinate]
                want = expected_ours[coordinate]
                if len(seen) > 1:
                    versions = ", ".join(sorted(seen))
                    drifted.append(f"{coordinate} resolves to more than one version across the "
                                   f"modules: {versions}, while ${{{name}}} gives {want}")
                    print(f"SPLIT  {coordinate} = {{{versions}}}, more than one version on the "
                          f"classpath. ${{{name}}} gives {want}")
                    continue
                got = next(iter(seen))
                if got == want:
                    print(f"OK     {coordinate} = {got}")
                elif expected_boot.get(coordinate) == got:
                    drifted.append(f"{coordinate} resolves to {got}, the Spring Boot {version} "
                                   f"version, instead of {want} from ${{{name}}}")
                    print(f"DRIFT  {coordinate} = {got}, which is the Spring Boot version. "
                          f"${{{name}}} should give {want}")
                else:
                    foreign.append(f"{coordinate} resolves to {got}, neither {want} from "
                                   f"${{{name}}} nor the Spring Boot version "
                                   f"{expected_boot.get(coordinate, 'unknown')}")
                    print(f"NOTE   {coordinate} = {got}, expected {want}; something other than "
                          f"the BOM is deciding this one")
        print()

    # ── report ──────────────────────────────────────────────────────────────────
    if untracked:
        print(f"::warning title=BOM override with no Renovate tracking::"
              f"{', '.join(untracked)}. Nothing will ever propose an update, and nothing will report "
              f"it going stale. Fix by adding a customManagers entry in renovate.json5, or by "
              f"declaring the artifact so the maven manager sees the property.")
    for message in drifted:
        print(f"::warning title=BOM override not taking effect::{message}")
    for message in foreign:
        print(f"::notice title=Version decided outside the BOM::{message}")
    if absent:
        print(f"::notice title=Override governs nothing on the runtime classpath::"
              f"{', '.join(absent)}. Either the property is dead, or the artifact is test-only, or "
              f"it moved.")

    problems = len(untracked) + len(drifted)
    if problems == 0:
        print(f"OK: all {len(overrides)} Spring Boot BOM overrides are tracked and take effect "
              f"({len(foreign)} notice(s), {len(absent)} not on the runtime classpath).")
        return 0

    print(f"FOUND {problems} problem(s): {len(untracked)} untracked, {len(drifted)} not taking effect.")
    return 1


if __name__ == "__main__":
    sys.exit(main())
