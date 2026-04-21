# AI Agent Instructions — OpenAEV Documentation

You are working on the **OpenAEV Documentation** repository, a MkDocs Material site for the OpenAEV Adversary Exposure
Validation Platform.

## Project stack

- **Static site generator:** MkDocs with Material for MkDocs (Insiders)
- **Content format:** Markdown (`.md`) files in `docs/`
- **Config:** `mkdocs.yml` at root
- **Deployment:** Mike for versioning, GitHub Pages
- **Language:** English only

## Repository structure

```
docs/           → Markdown source files (the documentation)
overrides/      → MkDocs Material template overrides
site/           → Generated output (do NOT edit)
mkdocs.yml      → MkDocs configuration and nav tree
requirements.txt → Python dependencies
```

## Writing style rules

Follow these rules strictly when creating or editing documentation:

### Voice and tone

- Use **active voice** and **present tense**: "Run the command" ✅, not "The command should be run" ❌.
- Be clear, concise, and pedagogical. Avoid unnecessary jargon.
- Capitalize proper nouns and frameworks: **OpenAEV**, **MITRE ATT&CK**, **REST API**, **Payload**, **Asset**, **Inject
  **, **Scenario**, **Simulation**.
- Explain acronyms on first use: e.g., **IOC (Indicator of Compromise)**.

### Page structure (usage-driven, NOT "click-click" docs)

Every section should follow this structure:

1. **What is this?** — Define the concept.
2. **Why use it?** — Explain the value and context.
3. **How do I do it?** — Provide clear, ordered steps.
4. **Example** — Add a realistic case (command, screenshot, workflow).
5. **What's next?** — Suggest related pages or next steps.

Always start with usage and benefits, then show the execution.

### Markdown conventions

- Start each page with a short introduction explaining what the page covers.
- Use `##` for sections, `###` for subsections — keep headings consistent.
- Use **numbered lists** for steps.
- Use **tables** for parameters, config options, and field descriptions.
- Use **code blocks** with syntax highlighting for commands and configs.
- Use **admonitions** for emphasis:
    - `!!! warning` for warnings
    - `!!! note` for tips/info
    - `!!! tip` for best practices

### Filenames and URIs

- Use **hyphens** (`-`) in filenames: `scenarios-and-simulations.md` ✅
- **Never** use underscores (`_`): `scenarios_and_simulations.md` ❌

### Images

- Store images in `docs/[SECTION]/assets/`.
- Use descriptive filenames: `scenario-import-global.png`.
- Optimize for web (compressed, < 1 MB).

## When adding a new page

1. Create the `.md` file in the appropriate `docs/` subdirectory.
2. Add the page to the `nav` section in `mkdocs.yml`.
3. Add cross-links from related pages.
4. Follow the usage-driven page structure above.