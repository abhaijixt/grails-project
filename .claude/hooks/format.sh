#!/usr/bin/env bash
# format.sh — lightweight formatting/lint pass for grails-bookstore.
#
# Usage:  format.sh <file>        format one file
#         format.sh               format staged files
#
# This repo has NO formatter in its toolchain: BuildConfig.groovy declares no CodeNarc, no
# Spotless, no Groovy formatting plugin, and there is no package.json. There is therefore
# nothing to invoke. Rather than pretend — or add a dependency to a 2017 EOL build that
# resolves through Ivy — this hook does the only safe, universally correct normalisations and
# reports anything it cannot fix.
#
# Exit code is ALWAYS 0 unless the script itself fails — formatting must never block work.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT" || exit 0

# When run as a PostToolUse hook the payload arrives as JSON on stdin, not as $1.
read_stdin_payload() {
  [[ $# -gt 0 && -n "${1:-}" ]] && return 0
  [[ -t 0 ]] && return 0
  if command -v timeout >/dev/null 2>&1; then
    timeout 2s cat 2>/dev/null || true
  else
    cat 2>/dev/null || true
  fi
}
PAYLOAD="$(read_stdin_payload "${1:-}")"

collect_files() {
  if [[ $# -gt 0 && -n "${1:-}" ]]; then
    printf '%s\n' "$1"
    return
  fi
  if [[ -n "$PAYLOAD" ]] && command -v jq >/dev/null 2>&1; then
    local fp
    fp="$(printf '%s' "$PAYLOAD" | jq -r '.tool_input.file_path // empty' 2>/dev/null || true)"
    if [[ -n "$fp" ]]; then
      printf '%s\n' "${fp#$REPO_ROOT/}"
      return
    fi
  fi
  git diff --cached --name-only --diff-filter=ACM 2>/dev/null || true
}

format_one() {
  local f="$1"
  [[ -f "$f" ]] || return 0

  case "$f" in
    *.groovy|*.gsp|*.xml|*.sql|*.md|*.yaml|*.yml|*.properties|*.json|*.sh) ;;
    *) return 0 ;;
  esac

  # Never touch generated, vendored or build content.
  case "$f" in
    target/*|site/*|.venv-docs/*|web-app/WEB-INF/tld/*) return 0 ;;
  esac

  local before after
  before="$(cat "$f")"
  after="$before"

  # 1. Strip trailing whitespace (not inside .md — two trailing spaces is a hard line break).
  case "$f" in
    *.md) ;;
    *) after="$(printf '%s' "$after" | sed 's/[[:space:]]*$//')" ;;
  esac

  # 2. Convert hard tabs to 4 spaces in Groovy (the codebase is space-indented throughout).
  case "$f" in
    *.groovy) after="$(printf '%s' "$after" | expand -t4)" ;;
  esac

  if [[ "$after" != "$before" ]]; then
    printf '%s\n' "$after" > "$f"
    echo "format.sh: normalised $f"
  fi

  # Advisory checks only — never rewrite code semantics.
  case "$f" in
    grails-app/domain/*.groovy)
      if grep -qn 'static mapping' "$f" && ! grep -qn 'table ' "$f"; then
        echo "format.sh: NOTE $f relies on the default table name. bookstore_db also holds legacy plural tables that the app does NOT map — see .claude/context/database.md" >&2
      fi
      ;;
    *.groovy)
      if grep -qn 'System\.out\.print' "$f"; then
        echo "format.sh: NOTE $f prints to stdout. Use the injected log — the deployed app logs to a mounted volume (.claude/context/cicd.md)" >&2
      fi
      ;;
    .github/workflows/*)
      if grep -qnE 'uses:[[:space:]]*[^ ]+@v[0-9]' "$f"; then
        echo "format.sh: NOTE $f references an action by tag; this repository requires SHA pinning" >&2
      fi
      ;;
    *.sh)
      if [[ ! -x "$f" ]]; then
        echo "format.sh: NOTE $f is not executable — chmod +x it or the hook will not run" >&2
      fi
      ;;
  esac
}

while IFS= read -r file; do
  [[ -n "$file" ]] && format_one "$file"
done < <(collect_files "${1:-}")

exit 0
