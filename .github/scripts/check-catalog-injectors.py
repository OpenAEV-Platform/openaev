#!/usr/bin/env python3
"""
Check that every injector of OpenAEV-Platform/injectors has its settings in the catalog.

The platform builds the keys an operator may set on a catalog-deployed injector from the
``config_schema.properties`` of its entry in ``catalog-integrators.json``
(``CatalogConnectorIngestionService.buildConnectorConfigurations``); any other key is
refused with "not a valid key for this integration". An injector setting the catalog
entry does not list is therefore unreachable on every catalog deployment, while every
injector test stays green (#9889 for nuclei, #9974 for nmap).

For each injector directory with a ``manifest-metadata.json``:

1. its ``slug`` must have an entry in the catalog (unless listed in ``NOT_IN_CATALOG``);
2. every setting the injector declares must be a property of that entry. A setting is
   named as the injector dumps it (``--dump-config-schema``, pyoaev
   ``ConnectorConfigSchemaGenerator``): ``<SECTION>_<FIELD>`` for each field of each
   settings model held by a field of the injector's ``SettingsLoader`` subclass, base
   classes defined in the injector included. Fields wrapped in ``SkipJsonSchema`` are not
   dumped and are skipped, as are the base settings pyoaev defines for every injector
   (``BASE_SETTINGS``), which the platform sets itself.

The sources are read statically (``ast``): no injector code or dependency is imported, so
the check needs neither poetry nor the injectors' dependencies.

Usage: check-catalog-injectors.py --injectors DIR [--catalog PATH]
"""

import argparse
import ast
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path

CATALOG_FILE = "openaev-api/src/main/resources/catalog/catalog-integrators.json"

# Settings that pyoaev's base models (ConfigLoaderOAEV, ConfigLoaderCollector) define for
# every injector. The platform sets the identity ones itself on a catalog deployment, and
# the others are optional overrides an injector does not need to expose in the catalog.
BASE_SETTINGS = {
    "OPENAEV_URL",
    "OPENAEV_TOKEN",
    "OPENAEV_TENANT_ID",
    "INJECTOR_ID",
    "INJECTOR_NAME",
    "INJECTOR_LOG_LEVEL",
    "INJECTOR_PERIOD",
    "INJECTOR_ICON_FILEPATH",
    "INJECTOR_AUTHOR",
    "INJECTOR_PLATFORM_DESCRIPTION",
    "INJECTOR_PLATFORM_TAGS",
}

# Manifest slugs not published in the catalog, each with the reason. Every other injector
# must have its entry, so a new injector cannot ship without the entry the platform
# deploys it from.
NOT_IN_CATALOG: dict[str, str] = {}


@dataclass
class _Field:
    name: str
    annotation_names: set[str]
    skipped: bool


@dataclass
class _Class:
    bases: set[str]
    fields: list[_Field] = field(default_factory=list)


def _names(node: ast.expr | None) -> set[str]:
    if node is None:
        return set()
    found = set()
    for item in ast.walk(node):
        if isinstance(item, ast.Name):
            found.add(item.id)
        elif isinstance(item, ast.Attribute):
            found.add(item.attr)
    return found


def _base_names(node: ast.ClassDef) -> set[str]:
    return {
        base.id if isinstance(base, ast.Name) else base.attr
        for base in node.bases
        if isinstance(base, (ast.Name, ast.Attribute))
    }


def _python_files(injector: Path) -> list[Path]:
    """The injector's runtime sources, tests excluded."""
    return [
        path
        for path in sorted(injector.rglob("*.py"))
        if not {"test", "tests", ".venv", "venv"}
        & set(path.relative_to(injector).parts)
    ]


def read_classes(injector: Path) -> dict[str, _Class]:
    classes: dict[str, _Class] = {}
    for path in _python_files(injector):
        tree = ast.parse(path.read_text(encoding="utf-8"), filename=str(path))
        for node in ast.walk(tree):
            if not isinstance(node, ast.ClassDef):
                continue
            cls = classes.setdefault(node.name, _Class(bases=set()))
            cls.bases |= _base_names(node)
            for statement in node.body:
                if not (
                    isinstance(statement, ast.AnnAssign)
                    and isinstance(statement.target, ast.Name)
                ):
                    continue
                if statement.target.id == "model_config":
                    continue
                annotation = _names(statement.annotation)
                cls.fields.append(
                    _Field(
                        name=statement.target.id,
                        annotation_names=annotation,
                        skipped="SkipJsonSchema" in annotation,
                    )
                )
    return classes


def _all_fields(name: str, classes: dict[str, _Class], seen: set[str]) -> list[_Field]:
    """Fields of a class and of its bases defined in the injector (subclass wins)."""
    if name in seen or name not in classes:
        return []
    seen.add(name)
    cls = classes[name]
    fields = {
        item.name: item
        for base in cls.bases
        for item in _all_fields(base, classes, seen)
    }
    fields.update({item.name: item for item in cls.fields})
    return list(fields.values())


def declared_settings(injector: Path) -> set[str]:
    """``<SECTION>_<FIELD>`` names the injector dumps in its config schema."""
    classes = read_classes(injector)
    loaders = [name for name, cls in classes.items() if "SettingsLoader" in cls.bases]
    settings: set[str] = set()
    for loader in loaders:
        for section in _all_fields(loader, classes, set()):
            if section.skipped:
                continue
            for section_type in section.annotation_names & classes.keys():
                for sub_field in _all_fields(section_type, classes, set()):
                    if not sub_field.skipped:
                        settings.add(f"{section.name}_{sub_field.name}".upper())
    return settings


def check_injector(injector: Path, contracts: dict[str, dict]) -> list[str]:
    manifest = json.loads((injector / "manifest-metadata.json").read_text("utf-8"))
    slug = manifest.get("slug")
    if slug in NOT_IN_CATALOG:
        return []
    contract = contracts.get(slug)
    if contract is None:
        return [
            f"{injector.name}: no catalog entry for slug '{slug}': add it to "
            f"{CATALOG_FILE} (built from manifest-metadata.json)"
        ]
    properties = contract.get("config_schema", {}).get("properties", {})
    missing = sorted(declared_settings(injector) - BASE_SETTINGS - set(properties))
    return [
        f"{injector.name}: setting '{key}' is declared by the injector but missing "
        f"from the '{slug}' entry of {CATALOG_FILE}: add it with the injector's "
        "description, type, default and bounds"
        for key in missing
    ]


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[1])
    parser.add_argument("--injectors", required=True, type=Path)
    parser.add_argument("--catalog", default=CATALOG_FILE, type=Path)
    args = parser.parse_args(argv)

    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))
    contracts = {item.get("slug"): item for item in catalog.get("contracts", [])}
    injectors = sorted(
        path.parent for path in args.injectors.glob("*/manifest-metadata.json")
    )
    if not injectors:
        print(f"No injector found under {args.injectors}", file=sys.stderr)
        return 2

    errors = [
        error
        for injector in injectors
        for error in check_injector(injector, contracts)
    ]
    for error in errors:
        print(f"::error::{error}")
    print(
        f"{len(injectors)} injector(s) checked against {args.catalog}: "
        f"{len(errors)} drift(s)."
    )
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
