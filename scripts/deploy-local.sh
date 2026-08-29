#!/usr/bin/env bash
#
# Deploy a packaged Grails WAR onto this machine as a Tomcat container.
#
# Releases are kept side by side under $DEPLOY_ROOT/releases and selected with a
# `current` symlink, so a failed health check can put the previous WAR back
# without rebuilding it.
#
#   WAR_PATH=target/grails-bookstore.war ./scripts/deploy-local.sh
#
set -euo pipefail

APP_NAME="${APP_NAME:-grails-bookstore}"
APP_PORT="${APP_PORT:-8080}"
TOMCAT_IMAGE="${TOMCAT_IMAGE:-tomcat:8.5-jre8}"
DEPLOY_ROOT="${DEPLOY_ROOT:-$HOME/deploys/$APP_NAME}"
RELEASE_ID="${RELEASE_ID:-$(date +%Y%m%d-%H%M%S)}"
KEEP_RELEASES="${KEEP_RELEASES:-5}"
HEALTH_PATH="${HEALTH_PATH:-/health}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-150}"
JAVA_OPTS="${JAVA_OPTS:--Xms128m -Xmx768m -Dgrails.env=production -Dfile.encoding=UTF-8}"

# The `developer` MariaDB account is granted on 'localhost' only, so the
# container shares the host network stack and reaches the database as a local
# client. Set USE_HOST_NETWORK=0 to publish a port instead -- that needs a
# grant for the Docker bridge subnet and a DB_URL pointing at the gateway.
USE_HOST_NETWORK="${USE_HOST_NETWORK:-1}"

DB_URL="${DB_URL:-jdbc:mariadb://127.0.0.1:3306/bookstore_db?useSSL=false}"
DB_USER="${DB_USER:-developer}"
DB_PASSWORD="${DB_PASSWORD:-}"

log() { printf '==> %s\n' "$*"; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

[ -n "${WAR_PATH:-}" ] || die "WAR_PATH is not set"
[ -f "$WAR_PATH" ] || die "no WAR at $WAR_PATH"
command -v docker >/dev/null || die "docker is not on PATH"
docker info >/dev/null 2>&1 || die "cannot talk to the Docker daemon"

if [ -z "$DB_PASSWORD" ]; then
  # dbCreate is 'none' in production, so the app comes up but every query fails.
  # Better to stop here than to swap a working release for a broken one.
  die "DB_PASSWORD is empty -- set the repository secret (see DEPLOY-LOCAL.md)"
fi

releases="$DEPLOY_ROOT/releases"
# Tomcat writes its logs here. Mounted into the container because `docker rm -f`
# -- which every deploy does -- discards a container's own logs with it.
logs_dir="$DEPLOY_ROOT/logs"
release_dir="$releases/$RELEASE_ID"
current_link="$DEPLOY_ROOT/current"

previous_release=""
if [ -L "$current_link" ]; then
  previous_release="$(readlink -f "$current_link" || true)"
fi

log "staging release $RELEASE_ID"
mkdir -p "$release_dir" "$logs_dir"
install -m 0644 "$WAR_PATH" "$release_dir/ROOT.war"

# Provenance for the WAR sitting in this directory, so a running release can be
# identified from the filesystem alone.
cat > "$release_dir/RELEASE" <<META
release=$RELEASE_ID
commit=${RELEASE_COMMIT:-unknown}
run=${RELEASE_RUN_URL:-local}
war_sha256=$(sha256sum "$release_dir/ROOT.war" | cut -d' ' -f1)
deployed_at=$(date -Is)
deployed_by=$(id -un)@$(hostname)
META

ln -sfn "$release_dir" "$current_link"

start_container() {
  local war="$1"
  local -a net_args

  if [ "$USE_HOST_NETWORK" = "1" ]; then
    net_args=(--network host)
  else
    net_args=(-p "${APP_PORT}:8080" --add-host "host.docker.internal:host-gateway")
  fi

  docker rm -f "$APP_NAME" >/dev/null 2>&1 || true
  docker run --detach \
    --name "$APP_NAME" \
    --restart unless-stopped \
    "${net_args[@]}" \
    --env "JAVA_OPTS=$JAVA_OPTS" \
    --env "DB_URL=$DB_URL" \
    --env "DB_USER=$DB_USER" \
    --env "DB_PASSWORD=$DB_PASSWORD" \
    --volume "$war:/usr/local/tomcat/webapps/ROOT.war:ro" \
    --volume "$logs_dir:/usr/local/tomcat/logs" \
    "$TOMCAT_IMAGE" >/dev/null
}

wait_for_health() {
  local url="http://127.0.0.1:${APP_PORT}${HEALTH_PATH}"
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  local code=""

  log "waiting for $url (up to ${HEALTH_TIMEOUT}s)"
  while [ "$SECONDS" -lt "$deadline" ]; do
    if ! docker ps --filter "name=^/${APP_NAME}$" --filter status=running --quiet | grep -q .; then
      log "container exited before it became healthy"
      return 1
    fi
    code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$url" || true)"
    if [ "$code" = "200" ]; then
      log "healthy (HTTP 200)"
      verify_commit "$url"
      return 0
    fi
    sleep 3
  done

  log "health check never passed (last status: ${code:-none})"
  return 1
}

# A 200 only proves *something* healthy is listening. Comparing the commit the
# probe reports against the one being deployed catches the case where the new
# container failed to replace the old one.
verify_commit() {
  local url="$1" reported
  [ -n "${RELEASE_COMMIT:-}" ] || return 0
  reported="$(curl -s --max-time 5 "$url" | sed -n 's/.*"commit":"\([^"]*\)".*/\1/p')"
  if [ -n "$reported" ] && [ "$reported" != "$RELEASE_COMMIT" ]; then
    log "warning: /health reports commit $reported, expected $RELEASE_COMMIT"
  fi
}

log "starting $APP_NAME from $TOMCAT_IMAGE"
docker image inspect "$TOMCAT_IMAGE" >/dev/null 2>&1 || docker pull "$TOMCAT_IMAGE"
start_container "$release_dir/ROOT.war"

if ! wait_for_health; then
  echo "--- last 100 log lines ---" >&2
  docker logs --tail 100 "$APP_NAME" >&2 || true

  if [ -n "$previous_release" ] && [ -f "$previous_release/ROOT.war" ]; then
    log "rolling back to $(basename "$previous_release")"
    ln -sfn "$previous_release" "$current_link"
    start_container "$previous_release/ROOT.war"
    if wait_for_health; then
      die "deploy failed; rolled back to $(basename "$previous_release")"
    fi
    die "deploy failed and the rollback did not come up either"
  fi

  die "deploy failed and there is no previous release to roll back to"
fi

# Keep a few releases for rollback, drop the rest.
if [ "$KEEP_RELEASES" -gt 0 ]; then
  # shellcheck disable=SC2012  # release ids are our own, no odd filenames
  ls -1dt "$releases"/*/ 2>/dev/null | tail -n "+$((KEEP_RELEASES + 1))" | while read -r old; do
    old="${old%/}"
    [ "$old" = "$release_dir" ] && continue
    log "pruning $(basename "$old")"
    rm -rf "$old"
  done
fi

printf '%s\t%s\t%s\t%s\n' "$(date -Is)" "$RELEASE_ID" "${RELEASE_COMMIT:-unknown}" "${RELEASE_RUN_URL:-local}" \
  >> "$DEPLOY_ROOT/deployments.log"

log "deployed $RELEASE_ID -> http://localhost:${APP_PORT}${HEALTH_PATH}"
docker ps --filter "name=^/${APP_NAME}$" --format 'table {{.Names}}\t{{.Status}}\t{{.Image}}'
