# Transactional Analyzer

A Java AST-based tool that finds every `@Repository`-annotated class/interface in the
codebase, builds the **upward caller tree** for each of their methods, annotates each node
with its `@Transactional` status, and produces a **self-contained HTML report**.

## What it detects

| Node colour | Meaning |
|---|---|
| 🟢 Green | Method carries Spring `@Transactional` (method or class level) |
| 🟡 Amber | Method carries Jakarta `@Transactional` (deprecated) |
| 🔴 Red | No `@Transactional` — potential missing transaction boundary |
| 🟣 Purple | `@Repository` method (tree root) |
| ⬛ Grey | External method (Spring Data inherited, no source found) |

## Build

Requires Maven ≥ 3.6 and Java 21.

```bash
cd scripts/transactional-analyzer
mvn package
```

This produces `target/transactional-analyzer-1.0.0.jar` (fat JAR, ~15 MB).

## Run

```bash
# Using the fat JAR
java -jar target/transactional-analyzer-1.0.0.jar <repo-root> <output.html>

# Example (from repo root)
java -jar scripts/transactional-analyzer/target/transactional-analyzer-1.0.0.jar \
  . \
  tx-report.html

# Or via Maven exec plugin (skips JAR build)
cd scripts/transactional-analyzer
mvn exec:java -Dexec.args="../../ tx-report.html"
```

Then open `tx-report.html` in any browser — the file is fully self-contained (no CDN, no
external resources).

## Report structure

```
tx-report.html
├── Stats banner  — repos, methods, missing-@Tx count
├── Legend        — colour key
├── Sidebar       — repository list with anchor links + live search (press /)
├── Per-repository sections (one per @Repository type)
│   ├── Merged caller tree   — all methods of this repo, their callers merged
│   └── Individual method trees — one expandable <details> tree per declared method
└── Global section — all repositories nested under a single expandable root
```

Each tree node shows: `ClassName.methodName()` + annotation badges + source file:line.
Nodes carrying **@Transactional** are collapsed by default; click to expand the caller
chain. Use "Expand all / Collapse all" buttons per section.

## How it works

1. **Source root detection** — finds every `src/main/java` directory under the repo root.
2. **JavaParser indexing** (pass 1) — parses all `.java` files; records every method's
   annotations (including class-level `@Transactional`) and field types.
3. **Symbol-solver-assisted call graph** (pass 2) — for each method call expression, tries
   to resolve the receiver type via the JavaParser symbol solver; falls back to field-type
   lookup for the common `private final XxxRepository xxxRepository` pattern used with
   Lombok `@RequiredArgsConstructor`.
4. **Caller tree construction** — BFS upward from each `@Repository` method, with cycle
   detection and a configurable depth limit (default: 12).
5. **HTML generation** — self-contained file with inline CSS (colour-coded tree) and
   minimal JavaScript (expand/collapse, live search).

## Limitations

* Overloaded methods share the same tree node (resolution ignores parameter types).
* Spring Data JPA inherited methods (`findAll`, `save`, …) are tracked when called on a
  `@Repository`-typed variable, but are not present in the "declared methods" section if
  the repository interface does not redeclare them.
* Resolution accuracy depends on how many types the JavaParser symbol solver can resolve.
  Calls through complex generic chains or dynamic proxies may be missed.
