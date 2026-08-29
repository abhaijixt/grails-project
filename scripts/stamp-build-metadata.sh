#!/usr/bin/env bash
#
# Stamp the commit, build number and build time into application.properties.
# Grails reads that file into grails.util.Metadata, so a running WAR can be
# traced back to the commit that produced it -- HealthController reports it.
#
# The stamped file is deliberately left dirty in the working tree; CI checkouts
# are clean, so it never gets committed.
#
set -euo pipefail

props="${1:-application.properties}"
[ -f "$props" ] || { echo "no $props here" >&2; exit 1; }

commit="${GITHUB_SHA:-$(git rev-parse HEAD 2>/dev/null || echo unknown)}"
build="${GITHUB_RUN_NUMBER:-local}"
built_at="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

sed -i '/^app\.commit=/d; /^app\.build=/d; /^app\.builtAt=/d' "$props"
printf 'app.commit=%s\napp.build=%s\napp.builtAt=%s\n' "$commit" "$build" "$built_at" >> "$props"

echo "stamped $props: commit=${commit:0:12} build=$build builtAt=$built_at"
