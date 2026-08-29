#!/usr/bin/env bash
#
# One-time install of the GitHub Actions self-hosted runner on this laptop.
#
# Get a registration token from:
#   https://github.com/<owner>/<repo>/settings/actions/runners/new
# (it expires after an hour), then:
#
#   ./scripts/setup-self-hosted-runner.sh <registration-token>
#
# Installs into ~/actions-runner and registers it as a systemd service so it
# comes back after a reboot. Re-running with a fresh token re-registers.
#
set -euo pipefail

REPO="${REPO:-abhaijixt/grails-project}"
RUNNER_DIR="${RUNNER_DIR:-$HOME/actions-runner}"
RUNNER_NAME="${RUNNER_NAME:-$(hostname)}"
RUNNER_LABELS="${RUNNER_LABELS:-grails-laptop}"

TOKEN="${1:-${RUNNER_TOKEN:-}}"
if [ -z "$TOKEN" ]; then
  echo "usage: $0 <registration-token>" >&2
  echo "get one at https://github.com/$REPO/settings/actions/runners/new" >&2
  exit 1
fi

# Everything the deploy workflow assumes is present on this machine.
for tool in curl tar docker git; do
  command -v "$tool" >/dev/null || { echo "missing required tool: $tool" >&2; exit 1; }
done
docker info >/dev/null 2>&1 || {
  echo "cannot talk to the Docker daemon -- is your user in the docker group?" >&2
  exit 1
}
[ -x "$HOME/.sdkman/candidates/java/current/bin/java" ] || {
  echo "no SDKMAN java candidate -- the workflow needs JDK 8 there" >&2
  exit 1
}
[ -x "$HOME/.sdkman/candidates/grails/current/bin/grails" ] || {
  echo "no SDKMAN grails candidate -- the workflow needs Grails 2.5.6 there" >&2
  exit 1
}

version="${RUNNER_VERSION:-}"
if [ -z "$version" ]; then
  version="$(curl -fsSL https://api.github.com/repos/actions/runner/releases/latest \
    | sed -n 's/.*"tag_name": *"v\([^"]*\)".*/\1/p' | head -1)"
fi
[ -n "$version" ] || { echo "could not determine the runner version" >&2; exit 1; }

tarball="actions-runner-linux-x64-${version}.tar.gz"
mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"

if [ ! -x ./config.sh ]; then
  echo "==> downloading runner $version"
  curl -fsSL -o "$tarball" \
    "https://github.com/actions/runner/releases/download/v${version}/${tarball}"
  tar xzf "$tarball"
  rm -f "$tarball"
fi

# svc.sh refuses to reconfigure a running service.
if [ -f ./svc.sh ] && sudo ./svc.sh status >/dev/null 2>&1; then
  echo "==> stopping the existing runner service"
  sudo ./svc.sh stop || true
  sudo ./svc.sh uninstall || true
fi

echo "==> registering with $REPO"
./config.sh \
  --url "https://github.com/$REPO" \
  --token "$TOKEN" \
  --name "$RUNNER_NAME" \
  --labels "$RUNNER_LABELS" \
  --work _work \
  --unattended --replace

echo "==> installing the systemd service"
sudo ./svc.sh install "$USER"
sudo ./svc.sh start
sudo ./svc.sh status

cat <<EOF

Runner '$RUNNER_NAME' is up with labels: self-hosted, linux, x64, $RUNNER_LABELS
The deploy workflow targets [self-hosted, linux, grails-laptop].

Next: set the DB_PASSWORD secret, then run the workflow.
  gh secret set DB_PASSWORD --repo $REPO
EOF
