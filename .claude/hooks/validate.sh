#!/usr/bin/env bash
# validate.sh — fast pre-flight guard for grails-bookstore.
#
# Wired as a PreToolUse hook on Edit / Write / Bash (see .claude/settings.json).
# Reads the hook payload as JSON on stdin; also usable manually:  validate.sh <path>
#
# Exit codes:
#   0  allow
#   2  BLOCK, with the reason on stderr
#
# Deliberately fast: no compilation. A cold `grails compile` here is ~40s and needs JDK 8 on
# PATH plus a warm Ivy cache — far too slow for a hook that runs on every edit. Set
# GBOOK_HOOK_COMPILE=1 to opt in.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT" || exit 0

block() { echo "validate.sh: BLOCKED — $*" >&2; exit 2; }
warn()  { echo "validate.sh: warning — $*" >&2; }

# ---------------------------------------------------------------- read payload
# Defensively: skip when a path was passed explicitly, and never block forever on a stdin that
# is open but silent (an interactive shell, or a caller that never writes).
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

json_get() { # json_get <jq-path> — empty string when jq is absent or the path is missing
  local path="$1"
  [[ -n "$PAYLOAD" ]] || return 0
  command -v jq >/dev/null 2>&1 || return 0
  printf '%s' "$PAYLOAD" | jq -r "$path // empty" 2>/dev/null || true
}

TOOL_NAME="$(json_get '.tool_name')"
FILE_PATH="$(json_get '.tool_input.file_path')"
NEW_TEXT="$(json_get '.tool_input.new_string // .tool_input.content')"
COMMAND="$(json_get '.tool_input.command')"

# manual invocation
if [[ -z "$FILE_PATH" && -n "${1:-}" ]]; then
  FILE_PATH="$1"
  [[ -f "$FILE_PATH" ]] && NEW_TEXT="$(cat "$FILE_PATH")"
fi

REL_PATH="${FILE_PATH#$REPO_ROOT/}"

# ---------------------------------------------------------------- 1. dangerous shell commands
if [[ -n "$COMMAND" ]]; then
  # Both SQL guards require an actual client invocation as well as the statement. Matching the
  # bare words blocked heredocs that merely *documented* the rule — including the skill file
  # that explains it. A guard that blocks writing its own documentation is a guard people
  # switch off. The clients are separately denied in settings.json; this is the backstop.
  if printf '%s' "$COMMAND" | grep -qiE '\b(mysql|mariadb|mysqldump|sqlplus)\b' \
     && printf '%s' "$COMMAND" | grep -qiE '\b(drop|truncate)[[:space:]]+(table|database|schema)\b'; then
    block "the command drops or truncates a table. This database has no backups and no migration ledger — see .claude/context/database.md and .claude/skills/database/SKILL.md."
  fi
  if printf '%s' "$COMMAND" | grep -qiE '\b(mysql|mariadb)\b' \
     && printf '%s' "$COMMAND" | grep -qiE '\b(delete|update)[[:space:]]+(from[[:space:]]+)?(book|author|category|customer|book_order|order_item)\b' \
     && ! printf '%s' "$COMMAND" | grep -qiE '\bwhere\b'; then
    block "unbounded DELETE/UPDATE against an application table (no WHERE clause)."
  fi
  if printf '%s' "$COMMAND" | grep -qE 'rm[[:space:]]+-[a-zA-Z]*r[a-zA-Z]*f?[[:space:]]+/($|[^t])'; then
    block "recursive delete outside the repository."
  fi
  if printf '%s' "$COMMAND" | grep -qE '\bgit[[:space:]]+push\b.*(--force|-f)\b'; then
    block "force push. main is protected and requires linear history; a force push is rejected server-side anyway."
  fi
  exit 0
fi

[[ -n "$REL_PATH" ]] || exit 0

# ---------------------------------------------------------------- 2. protected paths
case "$REL_PATH" in
  target/*|site/*|.venv-docs/*)
    block "'$REL_PATH' is build output, not source." ;;
  web-app/WEB-INF/tld/c.tld|web-app/WEB-INF/tld/fmt.tld)
    block "'$REL_PATH' is copied in by the Grails build (selected by grails.servlet.version) and is gitignored." ;;
esac

# ---------------------------------------------------------------- 3. the self-hosted runner boundary
# The repository is public. A workflow that runs repository code on the self-hosted runner runs
# it on the maintainer's laptop. Exactly one workflow may do that, and only from push: main.
case "$REL_PATH" in
  .github/workflows/*)
    if [[ -n "$NEW_TEXT" ]]; then
      if printf '%s' "$NEW_TEXT" | grep -qE 'self-hosted' && [[ "$REL_PATH" != ".github/workflows/deploy-local.yml" ]]; then
        block "'$REL_PATH' targets the self-hosted runner. Only deploy-local.yml may do that: this repository is public, so any other workflow reaching that runner is arbitrary code execution on the laptop (.claude/context/security.md)."
      fi
      if [[ "$REL_PATH" == ".github/workflows/deploy-local.yml" ]] && printf '%s' "$NEW_TEXT" | grep -qE '^[[:space:]]*pull_request:'; then
        block "deploy-local.yml runs on the self-hosted runner; a pull_request trigger would let a fork PR execute code on the laptop. CI already covers pull requests on hosted runners."
      fi
      if printf '%s' "$NEW_TEXT" | grep -qE 'uses:[[:space:]]*[^ ]+@v[0-9]'; then
        warn "'$REL_PATH' references an action by tag. The repository sets sha_pinning_required, so GitHub rejects this at run time — pin the commit SHA with the version in a trailing comment."
      fi
    fi ;;
esac

# ---------------------------------------------------------------- 4. obvious secrets
if [[ -n "$NEW_TEXT" ]]; then
  if printf '%s' "$NEW_TEXT" | grep -qE '(password|passwd|secret|apiKey|api_key|accessToken|access_token|privateKey)[[:space:]]*[:=][[:space:]]*["'"'"'][^"'"'"'$#{]{6,}'; then
    block "a hardcoded credential appears in the change. This repository is public and has leaked one before (see CICD.md). Passwords come from the environment, from ~/.grails/bookstore-local.groovy, or from a GitHub environment secret."
  fi
  if printf '%s' "$NEW_TEXT" | grep -qE '[-]{5}BEGIN [A-Z ]*PRIVATE KEY[-]{5}'; then
    block "a private key appears in the change."
  fi
  if printf '%s' "$NEW_TEXT" | grep -qE '\bAKIA[0-9A-Z]{16}\b'; then
    block "an AWS access key id appears in the change."
  fi
fi

# ---------------------------------------------------------------- 5. repo conventions (advisory)
case "$REL_PATH" in
  grails-app/domain/*)
    warn "domain change: production is dbCreate 'none' with a DML-only account, so a new or renamed field never reaches the production schema. Apply the DDL by hand before deploying (.claude/context/database.md)." ;;
  grails-app/controllers/*)
    if [[ -n "$NEW_TEXT" ]] && printf '%s' "$NEW_TEXT" | grep -qE 'new Sql\(|\.createCriteria\(\)|\.findAllBy'; then
      warn "database access in a controller. Controllers stay thin — persistence and business logic belong in a service (.claude/CLAUDE.md)."
    fi ;;
  grails-app/conf/BuildConfig.groovy)
    if [[ -n "$NEW_TEXT" ]] && printf '%s' "$NEW_TEXT" | grep -qE 'source\.level[[:space:]]*=[[:space:]]*1\.[89]|target\.level[[:space:]]*=[[:space:]]*1\.[89]'; then
      warn "raising source/target level above 1.7. Groovy 2.4 cannot emit higher bytecode — the build will fail (.claude/context/grails-version-compat.md)."
    fi ;;
  test/unit/*)
    if [[ -n "$NEW_TEXT" ]] && printf '%s' "$NEW_TEXT" | grep -qE 'save\(failOnError' \
       && ! printf '%s' "$NEW_TEXT" | grep -qE 'save\(flush:[[:space:]]*true'; then
      warn "a spec saves without flush. The unit-test datastore only answers queries and unique constraints against flushed state — save(flush: true, failOnError: true) (.claude/skills/testing/SKILL.md)."
    fi ;;
esac

# ---------------------------------------------------------------- 6. optional compile check
if [[ "${GBOOK_HOOK_COMPILE:-0}" == "1" ]]; then
  case "$REL_PATH" in
    *.groovy)
      if ! grails --non-interactive compile >/tmp/gbook-compile.log 2>&1; then
        echo "validate.sh: compile failed — see /tmp/gbook-compile.log" >&2
        tail -20 /tmp/gbook-compile.log >&2
        exit 2
      fi ;;
  esac
fi

# ---------------------------------------------------------------- 7. escalation advice
# Advisory only — never blocks. Policy lives in .claude/escalation.conf; edit that, not this.
# A blocked edit has already exited above, so this runs on changes that are allowed but that
# still deserve a second look.
ESCALATE="$REPO_ROOT/.claude/hooks/escalate.sh"
if [[ -x "$ESCALATE" && -n "$REL_PATH" ]]; then
  if [[ -n "$NEW_TEXT" ]]; then
    printf '%s' "$NEW_TEXT" | "$ESCALATE" "$REL_PATH" --stdin --quiet >&2 2>/dev/null || true
  else
    "$ESCALATE" "$REL_PATH" --quiet >&2 2>/dev/null || true
  fi
fi

exit 0
