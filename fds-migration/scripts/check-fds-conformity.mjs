#!/usr/bin/env node
/**
 * fds-migration conformity check — GENERATED TEMPLATE, copied verbatim from
 * filigran-design-system/scripts/fds-migration-templates/check-fds-conformity.mjs
 * by scripts/generate-fds-migration.ts. Do not hand-edit here; fix the
 * template upstream and re-run `pnpm generate:fds-migration --product <name>`
 * (filigran-design-system repo) to refresh every product's copy.
 *
 * Zero dependencies (plain Node, .mjs so it's ESM regardless of this
 * product's own package.json "type") — runs with whatever toolchain the
 * product already has, no pnpm/tsx requirement.
 *
 * Verifies, driven entirely by migration-state.json (never hardcoded here):
 *   1. The generated bridge file(s) haven't been hand-edited (sha256 vs the
 *      sidecar .meta.json written at generation time).
 *   2. Best-effort freshness vs the design system's current theme.css, IF
 *      filigran-design-system is checked out as a sibling repo — skipped
 *      otherwise, so this still works standalone in the product's own CI.
 *   3. Every "wired" file still imports the generated bridge.
 *   4. No forbidden pattern (a hardcoded value reintroduced into a migrated
 *      zone) matches in a wired file.
 *   5. Every declared library-component usage still holds: the component is
 *      imported from the library (not from MUI), and none of its rendered
 *      instances re-hardcodes a value the component now owns as a prop.
 *      See "Library component usage" below for why this one is not a regex.
 *
 * The check LISTS every issue it finds (this file), it does not decide what
 * to do about them — that's the agent's job, per the reconciliation loop in
 * fds-migration/AGENTS.md.
 *
 * Usage: node fds-migration/scripts/check-fds-conformity.mjs [--warn]
 *   --warn: always exit 0 (report only) — for non-blocking product CI.
 */
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const FDS_MIGRATION_DIR = path.resolve(__dirname, "..");
const PRODUCT_ROOT = path.resolve(FDS_MIGRATION_DIR, "..");
const STATE_PATH = path.join(FDS_MIGRATION_DIR, "migration-state.json");
const REPORT_PATH = path.join(FDS_MIGRATION_DIR, "reports", "conformity-latest.json");

const warnMode = process.argv.includes("--warn");

function sha256(content) {
  return `sha256:${createHash("sha256").update(content).digest("hex")}`;
}

function loadJson(filePath) {
  return JSON.parse(readFileSync(filePath, "utf8"));
}

function checkBridgeFiles(state, results) {
  for (const relPath of state.generatedBridgeFiles ?? []) {
    const tsPath = path.join(PRODUCT_ROOT, state.frontDir ?? "", relPath);
    const metaPath = tsPath.replace(/\.ts$/, ".meta.json");

    if (!existsSync(tsPath) || !existsSync(metaPath)) {
      results.push({
        check: "bridge-integrity",
        file: relPath,
        status: "MISSING",
        detail: "generated file or sidecar .meta.json not found — run pnpm generate:mui-bridge",
      });
      continue;
    }

    const content = readFileSync(tsPath, "utf8");
    const meta = loadJson(metaPath);
    const actualHash = sha256(content);
    if (actualHash !== meta.tsFileSha256) {
      results.push({
        check: "bridge-integrity",
        file: relPath,
        status: "MISMATCH",
        detail:
          "file content doesn't match the sha256 recorded at generation time — was it " +
          "hand-edited? Regenerate instead: pnpm generate:mui-bridge --product " +
          `${state.product ?? "<product>"} --write-to-product`,
      });
    } else {
      results.push({ check: "bridge-integrity", file: relPath, status: "OK" });
    }

    const libThemeCss = path.join(
      PRODUCT_ROOT,
      "..",
      "filigran-design-system",
      "packages",
      "filigran-design-system",
      "src",
      "tokens",
      "theme.css",
    );
    if (existsSync(libThemeCss)) {
      const currentHash = sha256(readFileSync(libThemeCss));
      if (currentHash !== meta.themeCssHash) {
        results.push({
          check: "bridge-freshness",
          file: relPath,
          status: "STALE",
          detail:
            "theme.css changed since this bridge was generated — run " +
            `pnpm generate:mui-bridge --product ${state.product ?? "<product>"} ` +
            "--write-to-product again",
        });
      } else {
        results.push({ check: "bridge-freshness", file: relPath, status: "OK" });
      }
    } else {
      results.push({
        check: "bridge-freshness",
        file: relPath,
        status: "SKIPPED",
        detail:
          "filigran-design-system not checked out as a sibling repo — can't compare theme.css",
      });
    }
  }
}

function checkWiring(state, results) {
  for (const wired of state.wiredFiles ?? []) {
    const filePath = path.join(PRODUCT_ROOT, wired.file);
    if (!existsSync(filePath)) {
      results.push({
        check: "wiring",
        file: wired.file,
        status: "MISSING",
        detail: "file listed in migration-state.json no longer exists",
      });
      continue;
    }
    const content = readFileSync(filePath, "utf8");
    if (!content.includes(wired.mustImport)) {
      results.push({
        check: "wiring",
        file: wired.file,
        status: "DRIFT",
        detail: `expected to find "${wired.mustImport}" — the wiring to the generated bridge may have been reverted`,
      });
    } else {
      results.push({ check: "wiring", file: wired.file, status: "OK" });
    }
  }
}

function checkForbiddenPatterns(state, results) {
  for (const forbidden of state.forbiddenPatterns ?? []) {
    const filePath = path.join(PRODUCT_ROOT, forbidden.file);
    if (!existsSync(filePath)) continue;
    const content = readFileSync(filePath, "utf8");
    let regex;
    try {
      regex = new RegExp(forbidden.pattern);
    } catch (err) {
      results.push({
        check: "forbidden-pattern",
        file: forbidden.file,
        status: "INVALID",
        detail: `invalid regex "${forbidden.pattern}" in migration-state.json: ${err.message}`,
      });
      continue;
    }
    if (regex.test(content)) {
      results.push({
        check: "forbidden-pattern",
        file: forbidden.file,
        status: "FOUND",
        detail: forbidden.reason ?? `pattern /${forbidden.pattern}/ matched`,
      });
    } else {
      results.push({ check: "forbidden-pattern", file: forbidden.file, status: "OK" });
    }
  }
}

/* ─── Library component usage (check 5) ────────────────────────────────────
 *
 * WHY THIS IS NOT A `forbiddenPatterns` ENTRY. `forbiddenPatterns` runs a
 * product-authored regex over a whole FILE. That works for what it was built
 * for — "this old hex literal must not come back into a theme file" — and it
 * is the wrong tool for a question about JSX, for three reasons the product
 * pilot ran into directly:
 *
 *   - A component's props span lines. Any regex that reaches across them needs
 *     `[\s\S]*?`, which then happily matches from one element into the NEXT
 *     one's attributes, or out of a `<Paper>` into an unrelated `<Box>`.
 *   - A file-wide match cannot tell WHICH element it hit, so the report says
 *     "this file has a padding somewhere" — true of almost every file, and
 *     useless to act on.
 *   - Comments and strings are indistinguishable from code to a regex. This
 *     repository has already shipped a gate that passed because a rationale
 *     COMMENT contained the exact string it was searching for.
 *
 * So the product declares INTENT (which component, in which files, under
 * which named guards) and the library owns the DETECTION. The scan below is a
 * small lexer, not a pattern: it strips comments, then walks each `<Component`
 * opening tag to ITS OWN closing `>`, tracking quotes, template literals and
 * `{}` nesting. Every rule is then applied to a single element's bounded
 * attribute region — so multiline JSX is not a special case, it is the normal
 * case, and a finding always names one element and one line.
 *
 * Upgrading a guard is a library change that reaches every product on the next
 * `pnpm generate:fds-migration`; the product's own state file only ever names
 * guards, never regexes.
 */

/**
 * Removes `//` and block comments while leaving string and template literals
 * intact, so neither the tag scan nor the attribute rules can be satisfied —
 * or defeated — by commented-out code.
 */
function stripComments(source) {
  let out = "";
  let i = 0;
  let quote = null;
  while (i < source.length) {
    const ch = source[i];
    const next = source[i + 1];
    if (quote) {
      if (ch === "\\") {
        out += ch + (next ?? "");
        i += 2;
        continue;
      }
      if (ch === quote) quote = null;
      out += ch;
      i += 1;
      continue;
    }
    if (ch === '"' || ch === "'" || ch === "`") {
      quote = ch;
      out += ch;
      i += 1;
      continue;
    }
    if (ch === "/" && next === "/") {
      while (i < source.length && source[i] !== "\n") i += 1;
      continue;
    }
    if (ch === "/" && next === "*") {
      i += 2;
      while (i < source.length && !(source[i] === "*" && source[i + 1] === "/")) {
        // Newlines are preserved so reported line numbers stay truthful.
        if (source[i] === "\n") out += "\n";
        i += 1;
      }
      i += 2;
      continue;
    }
    out += ch;
    i += 1;
  }
  return out;
}

/**
 * Every `<Component …>` opening tag in `source`, as `{ attributes, line }`.
 * `attributes` is the raw text between the tag name and THIS tag's own closing
 * `>` — bounded structurally, never by a lookahead.
 */
function scanJsxOpenTags(source, componentName) {
  const tags = [];
  // `(?=[\s/>])` keeps `<Paper` from matching `<PaperHeader`.
  const opener = new RegExp(`<${componentName}(?=[\\s/>])`, "g");
  for (const match of source.matchAll(opener)) {
    const attributesStart = match.index + match[0].length;
    let i = attributesStart;
    let depth = 0;
    let quote = null;
    while (i < source.length) {
      const ch = source[i];
      if (quote) {
        if (ch === "\\") {
          i += 2;
          continue;
        }
        if (ch === quote) quote = null;
        i += 1;
        continue;
      }
      if (ch === '"' || ch === "'" || ch === "`") {
        quote = ch;
        i += 1;
        continue;
      }
      if (ch === "{") depth += 1;
      else if (ch === "}") depth -= 1;
      else if (ch === ">" && depth === 0) break;
      i += 1;
    }
    tags.push({
      attributes: source.slice(attributesStart, i),
      line: source.slice(0, match.index).split("\n").length,
    });
  }
  return tags;
}

/**
 * The bounded value of one JSX attribute — `"…"`, `'…'` or `{…}` — or null if
 * the attribute is absent. Same walk as above, so a nested object/call in a
 * braced value cannot end the region early.
 */
function attributeValue(attributes, name) {
  const declaration = new RegExp(`(?:^|\\s)${name}\\s*=\\s*`, "g");
  const match = declaration.exec(attributes);
  if (!match) return null;
  let i = match.index + match[0].length;
  const open = attributes[i];
  if (open === '"' || open === "'") {
    const end = attributes.indexOf(open, i + 1);
    return attributes.slice(i + 1, end === -1 ? attributes.length : end);
  }
  if (open !== "{") return null;
  let depth = 0;
  let quote = null;
  const start = i;
  while (i < attributes.length) {
    const ch = attributes[i];
    if (quote) {
      if (ch === "\\") {
        i += 2;
        continue;
      }
      if (ch === quote) quote = null;
      i += 1;
      continue;
    }
    if (ch === '"' || ch === "'" || ch === "`") {
      quote = ch;
      i += 1;
      continue;
    }
    if (ch === "{") depth += 1;
    else if (ch === "}") {
      depth -= 1;
      if (depth === 0) return attributes.slice(start + 1, i);
    }
    i += 1;
  }
  return attributes.slice(start + 1);
}

/** Tailwind padding utilities, with or without a variant prefix (`md:p-4`). */
const PADDING_CLASS_RE = /(?:^|[\s:])p[xytrbl]?-(?:\d+|px|\[)/;
/** MUI `sx` / inline `style` padding keys, including the `p`/`px` shorthands. */
const PADDING_STYLE_KEY_RE =
  /(?:^|[\s,{'"])(?:p|px|py|pt|pr|pb|pl|padding(?:Top|Right|Bottom|Left|X|Y|Inline|Block)?)\s*:/;

/**
 * The named guards a product may declare. The product's state file names
 * these; it never supplies a pattern. Each returns an array of human-readable
 * findings for ONE declared usage entry.
 */
const LIB_COMPONENT_GUARDS = {
  /**
   * The component still comes from the library, and no longer from MUI. A
   * half-reverted migration reads as migrated (the JSX is unchanged) while
   * rendering the MUI component again — invisible to any JSX-level rule.
   */
  "imported-from-library": ({ component, importFrom, source, file }) => {
    const findings = [];
    const importsLibrary = new RegExp(
      `import\\s*\\{[^}]*\\b${component}\\b[^}]*\\}\\s*from\\s*['"]${importFrom.replace(
        /[.*+?^${}()|[\]\\]/g,
        "\\$&",
      )}['"]`,
    ).test(source);
    if (!importsLibrary) {
      findings.push(
        `${file}: <${component}> is rendered but not imported from "${importFrom}" — the wiring ` +
          `to the library component may have been reverted`,
      );
    }
    const muiImport = new RegExp(
      `import\\s*\\{[^}]*\\b${component}\\b[^}]*\\}\\s*from\\s*['"]@mui/material`,
    ).test(source);
    if (muiImport) {
      findings.push(
        `${file}: <${component}> is imported from @mui/material — the migrated zone has fallen ` +
          `back to the MUI component`,
      );
    }
    return findings;
  },

  /**
   * No rendered instance re-hardcodes padding. Paper owns padding as a typed
   * prop (`padding={0|8|16|24|32}`), and a `className="p-4"` / `sx={{ p: 2 }}`
   * next to it forks the scale back open — which is the whole reason the prop
   * exists. Reported per element, with its line.
   */
  "no-hardcoded-padding": ({ component, source, file }) => {
    const findings = [];
    for (const tag of scanJsxOpenTags(source, component)) {
      const offenders = [];
      const className = attributeValue(tag.attributes, "className");
      if (className && PADDING_CLASS_RE.test(className)) offenders.push("className");
      for (const styleProp of ["sx", "style"]) {
        const value = attributeValue(tag.attributes, styleProp);
        if (value && PADDING_STYLE_KEY_RE.test(value)) offenders.push(styleProp);
      }
      if (offenders.length > 0) {
        findings.push(
          `${file}:${tag.line}: <${component}> sets padding through ${offenders.join(" and ")} — ` +
            `use the \`padding\` prop (0 | 8 | 16 | 24 | 32) so the scale stays one contract. ` +
            `An off-scale value is a deliberate exception: say so in migration-state.json ` +
            `rather than leaving it to read as an oversight.`,
        );
      }
    }
    return findings;
  },
};

function checkLibComponentUsage(state, results) {
  for (const usage of state.libComponentUsage ?? []) {
    const guards = usage.guards ?? [];
    const unknown = guards.filter((name) => !(name in LIB_COMPONENT_GUARDS));
    if (unknown.length > 0) {
      results.push({
        check: "lib-component-usage",
        file: usage.component ?? "<component>",
        status: "INVALID",
        detail:
          `unknown guard(s) ${unknown.join(", ")} in migration-state.json. Available: ` +
          `${Object.keys(LIB_COMPONENT_GUARDS).join(", ")}. Guards are owned by the design ` +
          `system — refresh this script with pnpm generate:fds-migration if you expected a newer one.`,
      });
      continue;
    }
    for (const file of usage.files ?? []) {
      const filePath = path.join(PRODUCT_ROOT, file);
      if (!existsSync(filePath)) {
        results.push({
          check: "lib-component-usage",
          file,
          status: "MISSING",
          detail: "file listed in migration-state.json no longer exists",
        });
        continue;
      }
      const source = stripComments(readFileSync(filePath, "utf8"));
      // A declared file that renders none of the component at all is drift,
      // not a pass: it means the adoption was undone, or the declaration was
      // never true. Reported separately from the guards so the two are not
      // confused.
      if (scanJsxOpenTags(source, usage.component).length === 0) {
        results.push({
          check: "lib-component-usage",
          file,
          status: "DRIFT",
          detail:
            `declared as a <${usage.component}> adoption site but renders no <${usage.component}> ` +
            `at all${usage.reason ? ` — ${usage.reason}` : ""}`,
        });
        continue;
      }
      const findings = guards.flatMap((name) =>
        LIB_COMPONENT_GUARDS[name]({
          component: usage.component,
          importFrom: usage.importFrom ?? "@filigran/design-system",
          source,
          file,
        }),
      );
      if (findings.length === 0) {
        results.push({ check: "lib-component-usage", file, status: "OK" });
      } else {
        for (const detail of findings) {
          results.push({ check: "lib-component-usage", file, status: "FOUND", detail });
        }
      }
    }
  }
}

function main() {
  if (!existsSync(STATE_PATH)) {
    console.error(
      `fds-conformity: missing ${path.relative(PRODUCT_ROOT, STATE_PATH)} — run ` +
        "pnpm generate:fds-migration first (filigran-design-system repo).",
    );
    process.exit(1);
  }

  const state = loadJson(STATE_PATH);
  const results = [];
  checkBridgeFiles(state, results);
  checkWiring(state, results);
  checkForbiddenPatterns(state, results);
  checkLibComponentUsage(state, results);

  const failing = results.filter((r) => !["OK", "SKIPPED"].includes(r.status));

  console.log(`fds-migration conformity — ${results.length} checks, ${failing.length} issue(s)`);
  for (const r of results) {
    const marker = r.status === "OK" ? "✅" : r.status === "SKIPPED" ? "⏭️ " : "❌";
    console.log(`${marker} [${r.check}] ${r.file}: ${r.status}${r.detail ? " — " + r.detail : ""}`);
  }

  mkdirSync(path.dirname(REPORT_PATH), { recursive: true });
  writeFileSync(
    REPORT_PATH,
    JSON.stringify({ generatedAt: new Date().toISOString(), results }, null, 2) + "\n",
  );
  console.log(`\nReport: ${path.relative(PRODUCT_ROOT, REPORT_PATH)}`);

  if (failing.length > 0 && !warnMode) process.exit(1);
  if (failing.length > 0 && warnMode) {
    console.log("(--warn mode: exiting 0 despite issues above — non-blocking CI use)");
  }
}

main();
