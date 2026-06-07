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
