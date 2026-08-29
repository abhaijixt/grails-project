#!/usr/bin/env bash
#
# Best-effort local notification when a deploy fails.
#
# The runner is a systemd service with no desktop session of its own, so
# notify-send has to be pointed at the login session's D-Bus. When that is not
# available (headless, logged out) the log line is still written, which is the
# part that matters.
#
set -uo pipefail

DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploys/grails-bookstore}"
log_file="$DEPLOY_ROOT/deploy-failures.log"
url="${RUN_URL:-no run url}"

mkdir -p "$DEPLOY_ROOT"
printf '%s\tdeploy failed\t%s\n' "$(date -Is)" "$url" >> "$log_file"
echo "recorded failure in $log_file"

uid="$(id -u)"
bus="/run/user/${uid}/bus"
if command -v notify-send >/dev/null 2>&1 && [ -S "$bus" ]; then
  DBUS_SESSION_BUS_ADDRESS="unix:path=${bus}" \
    notify-send -u critical "grails-bookstore deploy failed" "$url" 2>/dev/null \
    && echo "desktop notification sent" \
    || echo "desktop notification unavailable"
else
  echo "no desktop session to notify; see $log_file"
fi

# Never mask the real failure with a notification error.
exit 0
