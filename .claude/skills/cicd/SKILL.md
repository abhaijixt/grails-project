---
name: cicd
description: Use when changing the build, test or deploy pipeline in grails-bookstore, or when a run has failed. Trigger on "the deploy failed", "CI is red", "add a workflow", "change the pipeline", "why didn't it deploy", "roll back", "the runner", "branch protection", or on any edit to .github/workflows/ or scripts/. Not for writing application tests (use the testing skill).
---

# Pipeline work

Read `.claude/context/cicd.md` for the shape and `.claude/context/security.md` for why the
runner boundary matters. This is the procedure.

## The one rule that is not negotiable

The repository is **public** and the deploy runner is the **maintainer's laptop**. Therefore:

- `deploy-local.yml` is the only workflow that may target `self-hosted`.
- It triggers on `push: main` and `workflow_dispatch`. **Never `pull_request`.**
- Anything that runs pull-request code — CI above all — stays on `ubuntu-latest`.

`validate.sh` blocks both violations. If you find yourself wanting to relax that, the answer is
almost certainly a hosted runner, not a wider rule.

## Adding or changing a workflow

1. **Pin every action to a commit SHA**, version in a trailing comment:
   `uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1`.
   `sha_pinning_required` is on, so a tag reference is rejected when the run starts — the PR
   itself looks fine. Resolve a SHA with:
   `gh api repos/actions/checkout/commits/v7.0.1 --jq .sha`
2. **Only GitHub-owned actions are allowed** (`patterns_allowed: []`). A third-party action
   needs the repository policy changed first — that is a decision, not a step.
3. **Declare `permissions:` explicitly.** CI and deploy both use `contents: read`.
4. **Do not rename the CI job.** `Compile, test, package` is the required status check on
   `main`. Renaming it blocks every PR until branch protection is updated to match, and the
   error message does not say so.
5. Validate the YAML before pushing — a trailing colon is the classic trap here:
   `run: grails --non-interactive test-app unit:` parses as a mapping and must be quoted.

## Debugging a failed run

```bash
gh run list --repo abhaijixt/grails-project --branch main --limit 5
gh run view <id> --repo abhaijixt/grails-project --json status,conclusion,jobs \
  --jq '{status,conclusion,steps:[.jobs[].steps[]|{name,conclusion}]}'
```

Common causes, in the order they actually occur:

| Symptom | Cause |
|---|---|
| Deploy job stays `queued` forever | no self-hosted runner registered, or the laptop is asleep. `gh api repos/<repo>/actions/runners --jq .total_count` |
| "Grails 2.5.6 needs JDK 8, found …" | the toolchain step did its job; `JAVA8_HOME` moved |
| Health check never passes | the app started but cannot reach MariaDB, or `/health` itself broke. The script dumps the last 100 container log lines |
| Run rejected before starting | an action referenced by tag |
| Every PR blocked on a missing check | the CI job was renamed |

## Deploy failure and rollback

`scripts/deploy-local.sh` rolls back automatically: on a failed health check it re-points
`current` at the previous release, restarts it, and exits non-zero. So a bad build leaves the
last good WAR serving.

To confirm what is actually live — and note the health probe reports the commit, so this is how
you catch a container that failed to be replaced:

```bash
curl -s http://localhost:8080/health
cat ~/deploys/grails-bookstore/current/RELEASE
cat ~/deploys/grails-bookstore/deployments.log
```

To roll back by hand, re-point the symlink and re-run the script with that WAR. The passwords
are in `~/.config/bookstore/` and `.claude/settings.json` denies reading them — that is a
command for the maintainer to run, not an agent.

## Changing scripts/deploy-local.sh

It removes and recreates the running container on every deploy and is the only rollback path.
Before changing it, know which of these you are touching:

- the release staging and `current` symlink (rollback depends on it)
- `start_container` and its `--network host` (the DB account is granted on `localhost` only)
- `wait_for_health` and `verify_commit` (the gate, and the replaced-container check)
- the prune step (`KEEP_RELEASES=5` is the rollback depth)

Test it against a real WAR before merging. `--dry-run` does not exist here; the honest test is
a deploy.

## Things that look like pipeline bugs and are not

- **`git branch -d` refusing a merged branch.** PRs are squash-merged to keep history linear,
  so the branch tip is not an ancestor of `main`. Diff it against `main` to confirm the content
  landed, then `-D`.
- **`application.properties` dirty after a local build.** `stamp-build-metadata.sh` writes the
  commit stamp there. CI checkouts are clean; locally, `git checkout -- application.properties`.
- **The docs workflow running on a code-only push.** It is path-filtered; check the filter
  before assuming it misfired.
