#!/usr/bin/env bash
# escalate.sh — "what do I need to be sure about before this ships?"
#
# Policy lives in .claude/escalation.conf. Edit that, not this.
#
#   escalate.sh <path>                 which rules care about this file
#   escalate.sh <path> --stdin         also scan the changed content on stdin
#   escalate.sh <path> --quiet         print nothing when no rule matches (used by validate.sh)
#   escalate.sh --list                 print the whole policy
#   escalate.sh --role DBA             what counts as a schema decision
#   escalate.sh --unsure               you are not sure. Read this.
#
# Exit 0 always. This advises; it never blocks. validate.sh does the blocking.
# bash 3.2 compatible: no mapfile, no readarray, no declare -A, no ${var,,}.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CONF="${ESCALATION_CONF:-$REPO_ROOT/.claude/escalation.conf}"

if [ ! -r "$CONF" ]; then
  echo "escalate.sh: cannot read $CONF — the escalation policy is missing." >&2
  exit 0
fi

lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }
trim()  { printf '%s' "$1" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'; }

contact_for() { # contact_for <ROLE_KEY>
  local want="$1" k c d
  while IFS='|' read -r tag k c d; do
    [ "$(trim "$tag")" = "ROLE" ] || continue
    if [ "$(trim "$k")" = "$want" ]; then
      printf '%s — %s' "$(trim "$c")" "$(trim "$d")"
      return 0
    fi
  done < "$CONF"
  printf '%s' "$want"
}

# ------------------------------------------------------------------ --unsure
if [ "${1:-}" = "--unsure" ]; then
  cat <<'UNSURE'

You are not sure about a change. That is a useful signal, not a failure.

This repository has one maintainer, which means nobody else will ask you the obvious question.
The five minutes below are the substitute for that reviewer:

  1. What are you actually changing, in one sentence?
  2. What made it feel wrong? Name the specific thing, even vaguely.
     "I don't understand why this lock is here" is a good answer.
  3. What did you already check? Which .claude/context/ file, which existing code, which test.
  4. What is the blast radius if you are wrong — one endpoint, the deployed app, or the
     database that has no backups?
  5. Would you be able to reconstruct this reasoning in six months from the PR description
     alone? If not, that is the thing to fix before merging.

Then run:   .claude/hooks/escalate.sh <the file you are changing>

If nothing matches, the change is probably routine. If you still feel uneasy after that,
write the doubt into the PR description rather than dropping it — it is the only place a
future reader will find it.

UNSURE
  exit 0
fi

# ------------------------------------------------------------------ --list
if [ "${1:-}" = "--list" ]; then
  printf '\nEscalation policy — %s\n\n' "$CONF"
  while IFS='|' read -r tag k c d; do
    [ "$(trim "$tag")" = "ROLE" ] || continue
    printf '  %-9s %-18s %s\n' "$(trim "$k")" "$(trim "$c")" "$(trim "$d")"
  done < "$CONF"
  printf '\n'
  while IFS='|' read -r tag kind pat role level reason; do
    [ "$(trim "$tag")" = "RULE" ] || continue
    printf '  %-5s %-9s %-5s %s\n' "$(trim "$level")" "$(trim "$role")" "$(trim "$kind")" "$(trim "$pat")"
  done < "$CONF"
  printf '\n'
  exit 0
fi

# ------------------------------------------------------------------ --role
if [ "${1:-}" = "--role" ]; then
  WANT="$(printf '%s' "${2:-}" | tr '[:lower:]' '[:upper:]')"
  printf '\n%s: %s\n\n' "$WANT" "$(contact_for "$WANT")"
  while IFS='|' read -r tag kind pat role level reason; do
    [ "$(trim "$tag")" = "RULE" ] || continue
    [ "$(trim "$role")" = "$WANT" ] || continue
    printf '  [%s] %s %s\n      %s\n\n' "$(trim "$level")" "$(trim "$kind")" "$(trim "$pat")" "$(trim "$reason")"
  done < "$CONF"
  exit 0
fi

# ------------------------------------------------------------------ match a path (+ optional content)
TARGET="${1:-}"
[ -n "$TARGET" ] || { echo "usage: escalate.sh <path> | --list | --role <KEY> | --unsure" >&2; exit 0; }

REL="${TARGET#$REPO_ROOT/}"
REL_L="$(lower "$REL")"

QUIET=0
for a in "$@"; do [ "$a" = "--quiet" ] && QUIET=1; done

CONTENT=""
if [ "${2:-}" = "--stdin" ] && [ ! -t 0 ]; then
  CONTENT="$(cat 2>/dev/null || true)"
elif [ -f "$TARGET" ]; then
  CONTENT="$(head -c 200000 "$TARGET" 2>/dev/null || true)"
fi
CONTENT_L="$(lower "$CONTENT")"

HITS=0
OUT=""
while IFS='|' read -r tag kind pat role level reason; do
  [ "$(trim "$tag")" = "RULE" ] || continue
  kind="$(trim "$kind")"; pat="$(trim "$pat")"
  role="$(trim "$role")"; level="$(trim "$level")"; reason="$(trim "$reason")"
  matched=0

  case "$kind" in
    path)
      # shellcheck disable=SC2254
      case "$REL_L" in $(lower "$pat")) matched=1 ;; esac
      ;;
    text)
      [ -n "$CONTENT_L" ] || continue
      printf '%s' "$CONTENT_L" | grep -qE "$(lower "$pat")" && matched=1
      ;;
  esac

  [ "$matched" -eq 1 ] || continue
  HITS=$((HITS + 1))
  OUT="${OUT}
  [${level}] ${role} — $(contact_for "$role")
      ${reason}
"
done < "$CONF"

if [ "$HITS" -eq 0 ]; then
  [ "$QUIET" -eq 1 ] && exit 0
  printf '\nescalate.sh: no escalation rule matches %s\n' "$REL"
  printf '  Routine change. Still uneasy? run: .claude/hooks/escalate.sh --unsure\n\n'
  exit 0
fi

printf '\n──────────────────────────────────────────────────────────────────────\n'
printf ' ESCALATE — %s\n' "$REL"
printf '──────────────────────────────────────────────────────────────────────%s' "$OUT"
printf '  STOP = do not merge without deliberately signing this off. Say so in the PR.\n'
printf '  ASK  = get a second opinion, or sleep on it.\n'
printf '  TELL = proceed, but record it.\n'
printf '\n  Policy: .claude/escalation.conf — edit it if this is wrong.\n\n'
exit 0
