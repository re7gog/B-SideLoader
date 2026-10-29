# ast-index rules — Claude Code enforcement

The rules themselves (what counts as grep, when grep is right, the command table) live in
**AGENTS.md**, section *Code search — ast-index first*, which `CLAUDE.md` imports into every
Claude Code session. This file documents only the Claude-Code-specific mechanics.

## Enforcement

`.claude/hooks/ast-index-gate.sh` (PreToolUse on Grep, Bash and PowerShell) denies the first
symbol-like code search of a session and names the ast-index command to use. Repeating the
identical call goes through — that is the escape hatch AGENTS.md rule 3 allows: ast-index came
back empty, or the search really is for text (a string literal, a comment, a regex). Do not work
around the gate by rewording the search; either use ast-index or repeat the call because rule 3
applies. Anything the script cannot parse is allowed silently — a broken gate must never block
work.

## Subagents

Subagents (Explore, general-purpose, ...) sent to "find where X is used" do not automatically
read AGENTS.md. When you delegate a code search, tell the subagent in the prompt to use
`ast-index` first.

## Index freshness

The ast-index plugin refreshes the index at session start and after every Edit/Write, so results
match the working tree. After a `git pull`, merge or branch switch made outside Claude Code, run
`ast-index update`; after a clone, `ast-index rebuild`.
