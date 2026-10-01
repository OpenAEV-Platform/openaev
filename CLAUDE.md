@AGENTS.md
@.github/instructions/code-review.instructions.md

# Claude Code specifics

`AGENTS.md` (imported above) is the single source of truth, shared with GitHub Copilot. The import
covers Claude Code versions and sessions that do not read `AGENTS.md` natively.

- **Path-scoped instructions**: each folder covered by a `.github/instructions/` file has a
  `CLAUDE.md` that imports it, so Claude Code loads it when it reads a file there (see the
  maintenance rule in `AGENTS.md`).
- **Skills** live in `.claude/skills/`, read by both Claude Code and Copilot.
- **Specialized agents** in `.github/agents/*.agent.md` use Copilot frontmatter. To run one, spawn a
  subagent with the `Agent` tool and tell it to read `AGENTS.md`, then that `.agent.md` file, and
  follow it. For a full review, run the Code Reviewer first, then the specialists it lists, in
  parallel.
