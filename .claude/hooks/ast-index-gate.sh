#!/usr/bin/env bash
# PreToolUse gate (Grep, Bash, PowerShell): send code-symbol searches to ast-index first.
#
# The ast-index plugin's own Grep hook only *suggests* ast-index, after the fact, and only for a
# bare identifier typed into the Grep tool. Regex searches, `grep -r`/`rg` in a shell and searches
# made by subagents all slipped past it, so the rule in .claude/rules/ast-index.md was routinely
# ignored. This gate denies the FIRST such search and names the ast-index command to use instead.
#
# Deny-once: the identical call, repeated in the same session, goes through. That is the escape
# hatch the rule allows — ast-index came back empty, or the search really is for text (a string
# literal, a comment, a regex). Anything this script cannot parse is allowed silently: a broken
# gate must never block work.
#
# No jq on this machine, so the JSON is read with sed. Values keep their JSON escapes; nothing
# here needs them decoded exactly.

set -u
input="$(cat)"

# Raw (still JSON-escaped) string value of key $1, or empty.
json_str() {
    printf '%s' "$input" \
        | sed -nE 's/.*"'"$1"'"[[:space:]]*:[[:space:]]*"(([^"\\]|\\.)*)".*/\1/p' \
        | head -n 1
}

# Denies unless this exact call ($1) was already denied in this session.
deny_once() {
    local key="$1" reason="$2"
    local session state_dir state_file hash
    session="$(json_str session_id | tr -cd 'A-Za-z0-9-')"
    state_dir="${TMPDIR:-/tmp}/claude-ast-index-gate"
    mkdir -p "$state_dir" 2>/dev/null || exit 0
    state_file="$state_dir/${session:-unknown}"
    hash="$(printf '%s' "$key" | cksum | cut -d ' ' -f 1)"
    if grep -qx "$hash" "$state_file" 2>/dev/null; then
        exit 0
    fi
    printf '%s\n' "$hash" >> "$state_file" 2>/dev/null || exit 0
    # `reason` never contains a double quote or a backslash, so it embeds as-is.
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$reason"
    exit 0
}

COMMANDS="ast-index usages X (who uses it), refs X (definition + imports + usages), class X / symbol X (definition), search X (anything), callers X, implementations X, hierarchy X, outline <path/to/File.kt> (members of a file; takes a path, not a bare file name)"
ESCAPE="If ast-index came back empty, or this really is a text search (string literal, comment, regex), repeat this exact call and it will run. See .claude/rules/ast-index.md."

tool="$(json_str tool_name)"

case "$tool" in
Grep)
    pattern="$(json_str pattern)"
    [ -z "$pattern" ] && exit 0

    # Searches aimed at non-code are what Grep is for.
    type="$(json_str type)"
    case "$type" in
        "" | kotlin | java) ;;
        *) exit 0 ;;
    esac
    glob="$(json_str glob)"
    if [ -n "$glob" ] && ! printf '%s' "$glob" | grep -qE 'kt|java'; then
        exit 0
    fi
    path="$(json_str path | sed 's#\\\\#/#g')"
    case "$path" in
        # One file is cheap to grep; resources, docs and build output are not code.
        *.kt | *.kts | *.java | */res | */res/* | */docs | */docs/* | */build/* | *.xml | *.md | *.json | *.toml | *.properties | *.yml | *.log | *.txt)
            exit 0 ;;
    esac

    # `\bFoo\b`, `Foo\.bar`, `(Foo|Bar)`, `^Foo` all name symbols; anything else is a real regex.
    core="$(printf '%s' "$pattern" \
        | sed -e 's/\\\\/\\/g' \
        | sed -E -e 's/\\b//g' -e 's/\\\././g' -e 's/\?://g' -e 's/[()^$]//g')"
    printf '%s' "$core" | grep -qE '^[A-Za-z_][A-Za-z0-9_]*([.|:]+[A-Za-z_][A-Za-z0-9_]*)*$' || exit 0
    [ "${#core}" -ge 3 ] || exit 0

    deny_once "Grep:$pattern:$path:$glob:$type" \
        "ast-index gate: '$core' looks like a code symbol. Look it up with ast-index before grepping: $COMMANDS. $ESCAPE"
    ;;

Bash | PowerShell)
    cmd="$(json_str command)"
    [ -z "$cmd" ] && exit 0
    # Already went through ast-index, or a fallback written next to it.
    printf '%s' "$cmd" | grep -q 'ast-index' && exit 0

    recursive=false
    # grep with -r/-R (also inside a cluster such as -rn) among the flags right after it.
    printf '%s' "$cmd" | grep -qE '(^|[^A-Za-z0-9_.-])grep([[:space:]]+-[^[:space:]]+)*[[:space:]]+(-[A-Za-z]*[rR][A-Za-z]*|--recursive)([[:space:]]|$)' && recursive=true
    printf '%s' "$cmd" | grep -qE '(^|[^A-Za-z0-9_./-])rg[[:space:]]' && recursive=true
    printf '%s' "$cmd" | grep -qE 'git[[:space:]]+grep[[:space:]]' && recursive=true
    printf '%s' "$cmd" | grep -q 'Select-String' && printf '%s' "$cmd" | grep -q -- '-Recurse' && recursive=true
    [ "$recursive" = true ] || exit 0

    # Aimed at resources, docs, logs, build output or config: not a symbol search.
    printf '%s' "$cmd" | grep -qE '\.(xml|md|toml|properties|json|ya?ml|gradle|txt|log|keep|pro)\b|(^|[/[:space:]])(res|docs|build|\.github|\.claude)/' && exit 0

    deny_once "$tool:$cmd" \
        "ast-index gate: recursive grep over source. For classes, functions, properties or usages use ast-index first: $COMMANDS. $ESCAPE"
    ;;
esac

exit 0
