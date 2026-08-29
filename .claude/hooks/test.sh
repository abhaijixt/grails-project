#!/usr/bin/env bash
# test.sh — run the unit suite for grails-bookstore.
#
# Usage:  test.sh                     full suite (the default, and usually the right answer)
#         test.sh <path>...           narrow to the specs implied by those source paths
#         test.sh --dry-run [path]... print the chosen scope and exit (no JDK needed)
#
# NOT wired to a hook. Measured on this machine:
#
#     full suite (8 specs, 70 tests) ....... 19s wall clock
#     one spec ............................. 13s wall clock
#
# Grails 2.5.6 boot is ~12s of that, so narrowing the scope saves about six seconds. That is
# why this script defaults to the whole suite and keeps the mapping logic deliberately simple —
# elaborate spec selection would be machinery that does not earn its keep here. It is still too
# slow to run on every edit, which is why it is not a PostToolUse hook; run it before you
# commit, or delegate to the `testing` subagent (.claude/agents/testing.md).
#
# Exit code mirrors the test run (0 = pass). Exits 0 with a clear message when the toolchain is
# unavailable, so it can never wedge a session.
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT" || exit 0

DRY_RUN=0
if [[ "${1:-}" == "--dry-run" ]]; then DRY_RUN=1; shift; fi

# ------------------------------------------------------------------ preconditions
if [[ "$DRY_RUN" -eq 0 ]]; then
  if ! command -v grails >/dev/null 2>&1; then
    echo "test.sh: no grails on PATH. See .claude/context/build-and-test.md for the two exports."
    exit 0
  fi
  JAVA_BIN="${JAVA_HOME:+$JAVA_HOME/bin/java}"
  [[ -x "${JAVA_BIN:-}" ]] || JAVA_BIN="$(command -v java 2>/dev/null || true)"
  if [[ -z "$JAVA_BIN" ]]; then
    echo "test.sh: no java. Grails 2.5.6 requires JDK 8."
    exit 0
  fi
  # Groovy 2.4 will not start on a newer JVM, and the system default here is JDK 26.
  JAVA_VER="$("$JAVA_BIN" -version 2>&1 | head -1)"
  case "$JAVA_VER" in
    *'"1.8'*) ;;
    *) echo "test.sh: JAVA_HOME points at $JAVA_VER; this project needs JDK 8. Skipping."
       echo "         export JAVA_HOME=~/.sdkman/candidates/java/current"
       exit 0 ;;
  esac
fi

# ------------------------------------------------------------------ scope
SPECS=()
spec_for_path() { # spec_for_path <repo-relative source path> -> fully qualified spec name, or nothing
  local f="$1" base
  case "$f" in
    test/unit/*.groovy)
      base="${f#test/unit/}"; base="${base%.groovy}"; printf '%s' "${base//\//.}"; return ;;
    grails-app/domain/*.groovy|grails-app/services/*.groovy|grails-app/controllers/*.groovy)
      base="$(basename "$f" .groovy) " ;;
    *) return ;;
  esac
  base="$(basename "$f" .groovy)"
  local found
  found="$(find test/unit -name "${base}Spec.groovy" 2>/dev/null | head -1)"
  [[ -n "$found" ]] || return
  found="${found#test/unit/}"; found="${found%.groovy}"
  printf '%s' "${found//\//.}"
}

for f in "$@"; do
  [[ -n "$f" ]] || continue
  s="$(spec_for_path "${f#$REPO_ROOT/}")"
  if [[ -n "$s" ]]; then
    SPECS+=("$s")
  else
    echo "test.sh: no spec maps to $f — falling back to the full suite."
    SPECS=()
    break
  fi
done

if [[ "$DRY_RUN" -eq 1 ]]; then
  if [[ ${#SPECS[@]} -eq 0 ]]; then
    echo "test.sh (dry-run): scope = FULL SUITE (8 specs, 70 tests, ~19s)"
  else
    echo "test.sh (dry-run): scope = ${#SPECS[@]} spec(s): ${SPECS[*]}  (~13s; saves ~6s over the full suite)"
  fi
  exit 0
fi

# ------------------------------------------------------------------ run
STATUS=0
if [[ ${#SPECS[@]} -eq 0 ]]; then
  echo "test.sh: scope = FULL SUITE"
  grails --non-interactive test-app unit: || STATUS=$?
else
  echo "test.sh: scope = ${SPECS[*]}"
  grails --non-interactive test-app unit: "${SPECS[@]}" || STATUS=$?
fi

echo
if [[ "$STATUS" -eq 0 ]]; then
  echo "test.sh: RESULT = PASS"
else
  echo "test.sh: RESULT = FAIL (exit $STATUS). Reports: target/test-reports/"
fi
exit "$STATUS"
