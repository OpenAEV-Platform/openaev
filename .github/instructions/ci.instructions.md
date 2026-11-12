---
applyTo: ".github/workflows/**,.github/actions/**"
description: "CI conventions: GitHub Actions workflows and composite actions, template injection"
---

# CI Conventions (GitHub Actions)

## No `${{ }}` inside scripts

GitHub expands `${{ }}` into the script text before the shell runs it, so a value holding quotes,
`$(...)` or a newline becomes code. Pull request titles, bodies and branch names are attacker-controlled,
and inputs or step outputs that are safe today can be fed one tomorrow by a new caller.
Security scanners (Aikido, zizmor) report every such expansion as template injection.

In a `run:` script, or the `script:` of `actions/github-script`, never write `${{ ... }}`. Pass the value
through the step `env:` and read it as a quoted shell variable:

```yaml
# Wrong
- run: |
    gzip ${{ steps.names.outputs.artifact_name }}.tar
    echo "Building ${{ inputs.platform }}"

# Right
- env:
    ARTIFACT: ${{ steps.names.outputs.artifact_name }}
    PLATFORM: ${{ inputs.platform }}
  run: |
    gzip "${ARTIFACT}.tar"
    echo "Building ${PLATFORM}"
```

- This applies to every context: `inputs`, `steps.*.outputs`, `matrix`, `needs`, `github.event`,
  `github.head_ref`, and secrets.
- In `github-script`, read the value with `process.env.NAME`.
- `${{ }}` is fine outside scripts: in `if:`, `with:`, `env:`, `name:` and `working-directory:`.
- Quote every variable: `"$VAR"`, not `$VAR`.
- Pass secrets through `env:` too, never in the script text.

## Arguments built in one step, used in another

Do not pass a ready-made argument string with shell quotes inside it (`args=-Dfoo='$LIST'`) from one step
to another: it only works when pasted into the script by `${{ }}`. Output each value on its own, and build
the arguments as a bash array in the step that runs the command:

```bash
ARGS=()
if [ -n "$GATE_MODE" ]; then
  ARGS+=("-Dopenaev.tenant.active-tables=${ACTIVE_TABLES}")
fi
mvn ... "${ARGS[@]}"
```

## Check before opening a pull request

```bash
uvx zizmor --persona=regular .github
```

No new `template-injection` finding should appear in the files you changed.
