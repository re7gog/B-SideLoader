# ast-index Rules

The project is indexed by `ast-index` (Kotlin, Java, C++). It answers "where is X defined / who
uses X / what implements X" exactly, without the false hits a text grep gets from comments and
strings. Use it **first** for any search for a code symbol.

## What counts as "grep"

All of these are grep for the purpose of these rules — none of them is the first step of a symbol
search:

- the built-in **Grep** and **Glob** tools (the harness prefers them over shell commands, but that
  preference is about *which grep*, not about skipping ast-index);
- `grep -r`, `rg`, `git grep` in Bash, and `Get-ChildItem -Recurse | Select-String` in PowerShell;
- a subagent (Explore, general-purpose, ...) sent to "find where X is used". When you delegate a
  code search, tell the subagent to use `ast-index` first; it does not read this file.

## Rules

1. **ast-index first** for classes, interfaces, functions, properties, usages, implementations,
   callers, DI bindings and resource references.
2. **Its result is the answer.** Do not re-run the same search with grep "for completeness".
3. **Grep is right when:** ast-index returned nothing; you need a regex; you are looking for text
   inside string literals or comments; the files are not code (XML, Gradle, Markdown, logs, lint
   reports, build output). That includes Android resources: `ast-index resource-usages` finds no
   resource files in this project, so look up `@string/...` and `R.string...` with Grep.

## Enforcement

`.claude/hooks/ast-index-gate.sh` (PreToolUse on Grep, Bash and PowerShell) denies the first
symbol-like code search of a session and names the ast-index command to use. Repeating the
identical call goes through — that is the escape hatch for rule 3. Do not work around the gate by
rewording the search; either use ast-index or repeat the call because rule 3 applies.

## Commands

Arguments are symbol names, except `outline` and `imports`, which take a **path** (a bare
`File.kt` returns "File not found").

| Task | Command |
|------|---------|
| Anything (files + symbols) | `ast-index search "query"` |
| Definition of a class/interface | `ast-index class "ClassName"` |
| Definition of any symbol | `ast-index symbol "name"` |
| Usages | `ast-index usages "SymbolName"` |
| Definition + imports + usages | `ast-index refs "SymbolName"` |
| Source, neighbours and tests at once | `ast-index explore "SymbolName"` |
| Implementations / subclasses | `ast-index implementations "Interface"` |
| Class hierarchy | `ast-index hierarchy "ClassName"` |
| Callers / call tree | `ast-index callers "fn"`, `ast-index call-tree "fn" --depth 3` |
| Members of a file | `ast-index outline app/src/main/java/.../File.kt` |
| Imports of a file | `ast-index imports app/src/main/java/.../File.kt` |
| Find a file by name | `ast-index file "SelfAppSeed"` |
| Possibly unused symbols | `ast-index unused-symbols` |
| Hilt `@Provides`/`@Binds`, `@Inject` | `ast-index provides "Type"`, `ast-index inject "Type"` |
| Composables, previews | `ast-index composables`, `ast-index previews` |
| Suspend functions, flows | `ast-index suspend`, `ast-index flows` |
| Module dependencies | `ast-index deps "app"`, `ast-index dependents "tdlib"` |

## Index freshness

The ast-index plugin refreshes the index at session start and after every Edit/Write, so results
match the working tree. After a `git pull`, merge or branch switch made outside Claude Code, run
`ast-index update`; after a clone, `ast-index rebuild`.
