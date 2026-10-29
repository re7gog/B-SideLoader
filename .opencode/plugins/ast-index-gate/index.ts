import { Plugin } from "@opencode/plugin"

/**
 * ast-index gate for OpenCode — the portable mirror of
 * `.claude/hooks/ast-index-gate.sh` (see AGENTS.md, section "Code search").
 *
 * Denies the first symbol-like code search of a session via the permission hook
 * (`grep` and `shell` actions) and names the ast-index command to use instead.
 * Deny-once: repeating the identical call goes through — the escape hatch
 * AGENTS.md rule 3 allows (ast-index came back empty, or the search really is
 * for text). Anything this gate cannot classify is allowed: a broken gate must
 * never block work.
 *
 * The OpenCode grep permission carries the pattern only, not the search path,
 * so instead of the Claude hook's path/type exemptions this gate classifies the
 * pattern itself: CamelCase names, `a.b` / `a::b` compounds and `\bword\b`
 * anchored words are treated as symbols; plain lowercase words as text.
 */

const COMMANDS =
  "ast-index usages X (who uses it), refs X (definition + imports + usages), class X / symbol X (definition), search X (anything), callers X, implementations X, hierarchy X, outline <path/to/File.kt> (members of a file; takes a path, not a bare file name)"
const ESCAPE =
  "If ast-index came back empty, or this really is a text search (string literal, comment, regex, non-code file), repeat this exact call and it will run. See AGENTS.md (Code search)."

// Same core grammar as the Claude hook: after stripping regex decoration, a
// bare identifier or an `a.b`, `a|b`, `a::b` compound of identifiers.
const SYMBOL = /^[A-Za-z_][A-Za-z0-9_]*([.|:]+[A-Za-z_][A-Za-z0-9_]*)*$/

const RECURSIVE_GREPS = [
  // grep with -r/-R (also inside a cluster such as -rn) among the flags right after it.
  /(^|[^A-Za-z0-9_.-])grep(?:\s+-\S+)*\s+(?:-[A-Za-z]*[rR][A-Za-z]*|--recursive)(\s|$)/,
  /(^|[^A-Za-z0-9_./-])rg\s/,
  /\bgit\s+grep\s/,
]

// Aimed at resources, docs, logs, build output or config: not a symbol search.
const NON_CODE =
  /\.[^\s]*\.(xml|md|toml|properties|json|ya?ml|gradle|txt|log|keep|pro)\b|(^|[/\\\s])(res|docs|build|\.github|\.claude|\.opencode)[/\\]/

function hash(s: string): string {
  let h = 5381
  for (let i = 0; i < s.length; i++) h = (((h << 5) + h) ^ s.charCodeAt(i)) >>> 0
  return h.toString(36)
}

export default Plugin.define({
  id: "ast-index-gate",
  async setup(ctx) {
    // sessionID -> hashes of already-denied calls (deny-once state, in-memory;
    // a server restart starts a fresh session anyway).
    const denied = new Map<string, Set<string>>()

    function firstTime(sessionID: string, key: string): boolean {
      let seen = denied.get(sessionID)
      if (!seen) {
        seen = new Set()
        denied.set(sessionID, seen)
      }
      const h = hash(key)
      if (seen.has(h)) return false
      seen.add(h)
      return true
    }

    await ctx.permission.hook("evaluate", (event) => {
      try {
        if (event.action === "grep") {
          const pattern = event.resources[0] ?? ""
          if (!pattern) return
          const core = pattern
            .replace(/\\b/g, "")
            .replace(/\\\./g, ".")
            .replace(/\?:/g, "")
            .replace(/[(^$)]/g, "")
          if (core.length < 3 || !SYMBOL.test(core)) return
          // Text-search safety net: only CamelCase, compound (`a.b`, `a::b`) or
          // word-boundary-anchored (`\bfoo\b`) patterns count as symbol lookups.
          const symbolLike =
            /[A-Z]/.test(core) || core.includes(".") || core.includes("::") || /\\b/.test(pattern)
          if (!symbolLike) return
          if (!firstTime(event.sessionID, `grep:${pattern}`)) return
          event.effect = "deny"
          event.message =
            `ast-index gate: '${core}' looks like a code symbol. Look it up with ast-index before grepping: ${COMMANDS}. ${ESCAPE}`
          return
        }

        if (event.action === "shell") {
          // Compound commands produce several resources; judge the joined command.
          const command = event.resources.join(" ; ")
          if (!command || command.includes("ast-index")) return
          const recursive =
            RECURSIVE_GREPS.some((re) => re.test(command)) ||
            (/Select-String/.test(command) && /-Recurse/.test(command))
          if (!recursive) return
          if (NON_CODE.test(command)) return
          if (!firstTime(event.sessionID, `shell:${command}`)) return
          event.effect = "deny"
          event.message = `ast-index gate: recursive grep over source. For classes, functions, properties or usages use ast-index first: ${COMMANDS}. ${ESCAPE}`
        }
      } catch {
        // Never block work over a classification failure.
      }
    })
  },
})
