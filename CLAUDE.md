# CLAUDE.md

Guidance for Claude Code (claude.ai/code) when working in this repository.

The whole project guide — build commands, architecture, layering rules, testing policy, the
device/emulator rules and the ast-index code-search rules and commands — lives in one place for
**every** agent: [AGENTS.md](AGENTS.md). It is imported below, in full. Keep any new guidance in
AGENTS.md, not in this file.

@AGENTS.md

## Claude Code specifics

Everything here is Claude-only machinery; it enforces or extends AGENTS.md, it never replaces it.

- **ast-index plugin**: `ast-index@ast-index-marketplace` (marketplace `defendend/Claude-ast-index-search`)
  is enabled in `.claude/settings.json`, together with `Bash(ast-index *)` permissions. The plugin
  refreshes the index at session start and after every Edit/Write.
- **PreToolUse gate**: `.claude/hooks/ast-index-gate.sh` runs on Grep, Bash and PowerShell and
  denies the first symbol-like code search of a session, naming the ast-index command to use
  instead. Repeating the identical call goes through — the escape hatch AGENTS.md rule 3 allows;
  do not work around the gate by rewording the search. Mechanics: `.claude/rules/ast-index.md`.
- **Rules file**: `.claude/rules/ast-index.md` documents this enforcement and the subagent caveat
  (subagents do not automatically read AGENTS.md — tell them to use ast-index first).
