# Agent integration

How this repository makes **any** coding agent follow the same rules.

## Principle

`AGENTS.md` at the repository root is the **single source of truth**. It follows the open
[agents.md](https://agents.md) standard, which is read natively by most current agents. Every
agent-specific instruction file in this repo is a thin entry point that imports or points at
AGENTS.md instead of keeping its own copy — so guidance is written once and no agent can "miss"
a rule because it was only written in some other agent's config.

Enforcement hooks (the ast-index search gate) are **not** portable between harnesses: each
harness has its own hook mechanism, if it has one at all. So the rules themselves live in
AGENTS.md where every agent reads them, and gates are only a per-agent safety net.

## Entry points and enforcement in this repo

| Agent | Instruction file | Reads AGENTS.md? | ast-index gate |
|---|---|---|---|
| OpenCode | `AGENTS.md` (native; V2 does **not** fall back to CLAUDE.md) | yes | `.opencode/plugins/ast-index-gate/index.ts` — permission-evaluation hook on `grep` / `shell`, deny-once |
| Claude Code | `CLAUDE.md` (imports `@AGENTS.md`) | yes | `.claude/hooks/ast-index-gate.sh` — PreToolUse on Grep / Bash / PowerShell, deny-once; plugin config in `.claude/settings.json` |
| Codex | `AGENTS.md` (native) | yes | none — follow the rules |
| Cursor | `AGENTS.md` (native; `.cursor/rules` optional) | yes | none — follow the rules |
| GitHub Copilot (agent mode / coding agent / Copilot CLI) | `AGENTS.md` (native) | yes | none — follow the rules |
| GitHub Copilot (legacy custom instructions) | `.github/copilot-instructions.md` (pointer + key rules inline) | via pointer | none |
| Gemini CLI | `GEMINI.md` (pointer) or AGENTS.md natively in recent versions | via pointer | none — follow the rules |
| Devin, Jules, Amp, Factory, Zed, … | `AGENTS.md` (native in the agents.md-compatible tools) | yes | none — follow the rules |

The two gates implement the identical contract described in AGENTS.md § *Code search*:

- the **first** symbol-like code search of a session is denied, and the denial names the
  ast-index command to use instead;
- **repeating the identical call goes through** — the escape hatch for "ast-index returned
  nothing" or "this really is a text search";
- anything the gate cannot classify is allowed — a broken gate must never block work;
- rewording a search to slip past a gate is a rule violation, not a workaround.

## ast-index availability per agent

The gate is useless if the agent cannot actually run ast-index:

- **Claude Code** — plugin `ast-index@ast-index-marketplace`
  (`defendend/Claude-ast-index-search`), enabled in `.claude/settings.json`, plus
  `Bash(ast-index *)` permission. It also keeps the index fresh at session start and after every
  Edit/Write.
- **OpenCode** — the bundled `ast-index` skill discovers this project's code; the `ast-index`
  CLI itself must be installed (it is usually reached via `ya tool ast-index ...`). The plugin
  keeps no index of its own; run `ast-index update` after external pulls.
- **Any other agent** — expose the CLI however that agent extends itself: a shell-allowlist
  entry for `ast-index`, an MCP wrapper, or that agent's own skill/plugin format. Without
  access to the tool the agent cannot follow the rule, so installing it is part of the
  integration.

## Adding a new agent

1. Check whether the agent already reads `AGENTS.md` natively (most 2025+ tools do). If it does,
   there is nothing to add.
2. Otherwise create its native instruction file as a **pointer**, in that agent's syntax:
   - Cursor (older setups): `.cursor/rules/agents.mdc` with `alwaysApply: true` and a line to
     read AGENTS.md.
   - Windsurf: `.windsurf/rules/agents.md` pointer.
   - Cline: `.clinerules/agents.md` pointer. Roo Code: `.roo/rules-<mode>/AGENTS.md` pointer.
   - Aider: `CONVENTIONS.md` pointer, referenced from `.aider.conf.yml`
     (`read: CONVENTIONS.md`).
   - Gemini CLI (older versions): `GEMINI.md` — already present here; `@AGENTS.md` also works if
     you prefer the content inlined (memory imports support `@path` syntax).
   - Anything else: one short paragraph — "read AGENTS.md at the repository root in full and
     follow it" — plus the five headline rules, so they survive even a partial-context agent.
3. If the agent has hooks (PreToolUse-like), mirror the gate: deny-once on symbol-like searches,
   identical denial message and escape hatch as in AGENTS.md § *Enforcement*. Existing
   implementations to copy: the bash script (Claude) and the TypeScript plugin (OpenCode).
4. Allow-list the `ast-index` command in that agent's tool permissions.
5. Add a row to the table in AGENTS.md § *Agent entry files* and to this file.

## Maintenance rules

- Write new guidance **only** in `AGENTS.md`. Entry files (`CLAUDE.md`, `GEMINI.md`,
  `.github/copilot-instructions.md`, `.claude/rules/*`) stay thin; a rule copied into an entry
  file is a rule that will drift.
- A gate's denial message must always point at AGENTS.md § *Code search* — update both gates
  together when the contract changes.
- Nested `AGENTS.md` files override/sharpen the root one for their subtree (OpenCode, Claude
  Code and Codex all discover nested files). Prefer editing the root file unless the guidance
  truly belongs to one directory.
- `.claude/worktrees/` is Claude scratch space, not configuration; never put rules, hooks or
  settings there.
- Test a gate change against both paths of the contract: a first symbol search must be denied
  with an actionable message, and the identical repeated call must run.
