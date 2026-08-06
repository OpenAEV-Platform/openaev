# AI Agent Instructions — OpenAEV Documentation

You are working on the **OpenAEV Documentation** repository, a MkDocs Material site for the OpenAEV Adversary Exposure
Validation Platform.

## Project stack

- **Static site generator:** MkDocs with Material for MkDocs
- **Content format:** Markdown (`.md`) files in `docs/docs/`
- **Config:** `docs/mkdocs.yml`
- **Deployment:** Mike for versioning, GitHub Pages
- **Language:** English only

## Repository structure

```
docs/docs/            → Markdown source files (the documentation)
docs/overrides/       → MkDocs Material template overrides
docs/site/            → Generated output (do NOT edit)
docs/mkdocs.yml       → MkDocs configuration and nav tree
docs/requirements.txt → Python dependencies
```

## Writing style rules

Follow these rules strictly when creating or editing documentation:

### Voice and tone

- Use **active voice** and **present tense**: "Run the command" ✅, not "The command should be run" ❌.
- Be clear, concise, and pedagogical. Avoid unnecessary jargon.
- Explain acronyms on first use **per page**: e.g., **IOC (Indicator of Compromise)**. Common technical acronyms that do not need expansion: API, CLI, CSS, DNS, HTML, HTTP, HTTPS, JSON, REST, SQL, SSH, SSL, TLS, UI, URL, UUID, YAML.

### Capitalization

Capitalize the following **domain-specific proper nouns** when they refer to an OpenAEV entity or concept (not when used in a generic sense):

- **Platform concepts:** OpenAEV, XTM Hub, Enterprise Edition
- **Campaign entities:** Scenario, Simulation, Inject, Atomic Test
- **Resources:** Asset, Team, Player, Payload, Finding, Dashboard
- **Infrastructure:** Executor, Collector, Injector, Tenant
- **External frameworks:** MITRE ATT&CK, REST API, RBAC

Use lowercase when the word is used generically (e.g., "run a simulation of the attack" vs. "create a new Simulation in OpenAEV").

### Headings

Use **sentence case** for all headings: capitalize the first word and proper nouns only.

- "How to configure login messages" ✅
- "How to Configure Login Messages" ❌

### UI paths

When referencing navigation paths in the interface, use `>` as the separator, with the full path in bold:

- **Settings > Security > Policies** ✅
- **Settings → Security → Policies** ❌
- Settings / Security / Policies ❌

### Page types and structure

Not all pages serve the same purpose. Apply the structure that fits the page type.

#### Feature pages (e.g., multi-tenancy, scenarios, injects)

These pages explain a concept and how to use it. Follow this structure:

1. **What is this?** -- Define the concept in the introduction.
2. **Why use it?** -- Explain the value and context.
3. **How do I do it?** -- Provide clear, numbered steps.
4. **Example** -- Add a realistic case (command, screenshot, workflow).
5. **What's next?** -- Suggest related pages or next steps.

Always start with usage and benefits, then show the execution.

#### Reference pages (e.g., parameters, filters, error codes)

These pages document settings, fields, or options. Follow this structure:

1. **Introduction** -- Explain what is documented and where to find it in the UI.
2. **Tables** -- Use tables for settings, fields, and options with descriptions and defaults.
3. **What's next?** -- Link to related pages.

Numbered steps are only needed if the page includes a procedure (e.g., "How to change a setting").

#### Index pages (e.g., administration introduction)

These pages orient the reader toward sub-pages. Follow this structure:

1. **Introduction** -- Explain what the section covers.
2. **Sub-sections** -- One `##` per sub-page with a short description and a link.
3. **What's next?** -- Link list to all sub-pages.

### Markdown conventions

- Start each page with a short introduction explaining what the page covers.
- Use `##` for sections, `###` for subsections -- keep headings consistent.
- Use **numbered lists** for steps.
- Use **tables** for parameters, config options, and field descriptions.
- Use **code blocks** with syntax highlighting for commands and configs. Use the appropriate language tag: `bash` for shell commands, `json` for JSON, `http` for HTTP requests, `properties` for config files, `yaml` for YAML, `java` for Java code, `typescript` for TypeScript.
- Use **admonitions** for emphasis:
    - `!!! warning` for warnings and cautions
    - `!!! danger` for destructive or irreversible actions
    - `!!! note` for supplementary information
    - `!!! tip` for best practices and recommendations
    - `!!! tip "Enterprise Edition"` for features that require an EE license
    - `!!! example` for worked examples
- Never use raw emoji paragraphs for warnings (e.g. a paragraph starting with a warning emoji) -- use an admonition.
- Never add an in-page table of contents -- the theme renders the page TOC on the right automatically.
- Never use `---` horizontal rules as section separators -- headings provide the structure.
- Never ship internal editorial comments (`<!-- to be completed -->`, screenshot placeholders) -- track them in issues instead. Note: `<!-- filigran-*:start/end -->` markers are **managed sync blocks** maintained by an automated tool and must not be edited or removed manually.

### "What's next?" format

End pages with a `## What's next?` section containing a bullet list of links. Use an em dash to separate the link from its description:

```markdown
## What's next?

- [Page title](page.md) -- Short description of what the reader will find
- [Other page](other.md) -- Short description
```

### Filenames and URIs

- Use **hyphens** (`-`) in filenames: `scenarios-and-simulations.md` ✅
- **Never** use underscores (`_`): `scenarios_and_simulations.md` ❌

### Images

- Store images in the `assets/` subdirectory next to the page that references them (e.g., `docs/administration/assets/`, `docs/deployment/ecosystem/integration-manager/assets/`).
- Use descriptive filenames: `scenario-import-global.png`.
- Optimize for web (compressed, < 1 MB).

## When adding a new page

1. Create the `.md` file in the appropriate `docs/` subdirectory.
2. Add the page to the `nav` section in `mkdocs.yml`.
3. Add cross-links from related pages.
4. Follow the page structure matching the page type (feature, reference, or index).


<!-- filigran-conventions:start -->
## Commit, PR & issue conventions

All commits, pull requests and issues in this repository follow the
[Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/)
specification with a GitHub issue reference:

```
type(scope?)!?: description (#issue)
```

- Types: `feat`, `fix`, `chore`, `docs`, `style`, `refactor`, `perf`, `test`,
  `build`, `ci`, `revert`.
- The description starts with a lowercase letter and has no trailing period;
  preserve acronyms and proper nouns.
- The old `[backend]` / `[frontend]` bracket prefixes are discontinued — use a
  Conventional Commits scope instead.
- Pull request titles **must** end with the related issue reference, e.g.
  `(#1234)`, and every pull request must be linked to an issue.
- Sign your commits.

When generating commit messages, PR titles or issue titles, always follow this
convention. See [`.github/LABELS.md`](.github/LABELS.md) for the full title and
label taxonomy.
<!-- filigran-conventions:end -->


<!-- filigran-model-policy:start -->
## GitHub Copilot model usage

To keep token consumption under control, pick the model that matches the task:

- **Opus 4.6** — reserve for complex work: deep reasoning, large refactors,
  architecture design, tricky debugging. It is significantly more
  token-expensive, so it is not the daily driver.
- **Sonnet / Gemini / GPT** — default for everyday tasks: autocomplete, small
  fixes, quick questions, code explanations.

We have a limited token budget — being mindful of the model you pick makes a
real difference at scale. Think of Opus as a specialist you call in when you
really need it.
<!-- filigran-model-policy:end -->
