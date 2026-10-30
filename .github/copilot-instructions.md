# Copilot instructions

This project's full guidance for **all** AI coding agents lives in one file:
[AGENTS.md](../AGENTS.md) at the repository root. Read it in full before working and follow
it. The rules that matter most, so you cannot miss them even if you never open the file:

1. **Code search goes through `ast-index` first** — symbol lookups (classes, functions, usages,
   implementations, callers, DI bindings) use `ast-index` commands, not Grep or `grep -r`/`rg`.
   Plain grep is only for text: string literals, comments, regexes, or non-code files (XML,
   Gradle, Markdown, resources). If you delegate a search to a sub-search, tell it to use
   ast-index too.
2. **Never run an emulator, never install or run the app on a device yourself.** Verify with
   `./gradlew assembleDebug`, `:app:testDebugUnitTest`, `assembleRelease` and `lint`; anything
   hardware-only is reported to the developer for manual checking.
3. **`./gradlew lint` must pass** — every check on, every warning fatal, no baseline; fix
   findings instead of suppressing them (suppressions must say why).
4. Dependencies are added or bumped in the version catalog `gradle/libs.versions.toml`.
5. Layering: `ui` -> `domain` <- `data`; the domain layer imports no Android/Room/Retrofit/TDLib.

Keep new guidance in AGENTS.md, not in this file.
