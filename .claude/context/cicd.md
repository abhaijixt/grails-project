# CI/CD

Answers: how does a change reach the running app, what gates it, and what breaks the pipeline.
The full write-up is at `CICD.md` and `DEPLOY-LOCAL.md` in the repo root; this is the operating
summary.

## The path a change takes

```
PR → CI (ubuntu-latest) → required status check → squash-merge to main
                                                        ↓
                              Deploy (self-hosted runner on the laptop)
                              tests → stamp commit → war → artifact
                                    → release dir → tomcat container → /health 200
                                                        ↓ fail
                                              roll back to previous release + notify
```

| Workflow | Trigger | Runner |
|---|---|---|
| `ci.yml` | every PR, every push to `main`, manual | `ubuntu-latest` |
| `deploy-local.yml` | push to `main` (non-docs), manual | `[self-hosted, linux, grails-laptop]` |
| `deploy-docs.yml` | push to `main` touching `docs/**` | `ubuntu-latest` |

**Only `deploy-local.yml` may touch the self-hosted runner, and it must never gain a
`pull_request` trigger.** This is the sharpest rule in the repository — see
`.claude/context/security.md`.

## The scripts

| Script | Does |
|---|---|
| `scripts/deploy-local.sh` | stages the WAR as a numbered release, recreates the container, health-gates it, rolls back on failure, prunes to 5 releases |
| `scripts/stamp-build-metadata.sh` | writes `app.commit` / `app.build` / `app.builtAt` into `application.properties` before packaging |
| `scripts/notify-failure.sh` | appends to `deploy-failures.log`, best-effort desktop notification |
| `scripts/setup-self-hosted-runner.sh` | one-time runner install and systemd registration |

## On disk, on the laptop

```
~/deploys/grails-bookstore/
├── current -> releases/r1-f1d7975…     symlink to the live release
├── deployments.log                     append-only: when, which release, which commit
├── logs/                               mounted into the container; survives redeploys
└── releases/<id>/{ROOT.war,RELEASE}    RELEASE holds commit, run URL, WAR sha256
```

## The health gate

`/health` must return **200** within 150s or the deploy rolls back. After it passes, the script
compares the commit `/health` reports against the one being deployed — a container that failed
to be replaced answers 200 with the *old* commit, and that mismatch is what catches it.

This is why `HealthController` is load-bearing infrastructure, not an ordinary endpoint. Break
it and every deploy fails its gate.

## Things that break the pipeline

- **Renaming the CI job.** `Compile, test, package` is the required status check on `main`.
  Rename it and every PR blocks until branch protection is updated to match.
- **Referencing an action by tag.** `sha_pinning_required` is on; GitHub rejects it at run time.
- **Stamping and then committing.** `stamp-build-metadata.sh` leaves `application.properties`
  dirty. CI checkouts are clean so it never matters there; locally,
  `git checkout -- application.properties`.
- **Force-pushing or pushing straight to `main`.** Branch protection rejects both.
- **Expecting `git branch -d` to work after a merge.** PRs are squash-merged to keep history
  linear, so the branch tip is not an ancestor of `main`. Diff the branch against `main` to
  confirm the content landed, then `-D`.
- **The laptop being asleep.** The self-hosted runner only picks up jobs while it runs. A push
  made while it is off is executed when it wakes; GitHub cancels jobs queued over 24h.

## Deploying by hand

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/grails/current/bin:$PATH"
./scripts/stamp-build-metadata.sh
grails --non-interactive prod war target/grails-bookstore.war
WAR_PATH=$PWD/target/grails-bookstore.war \
  DB_PASSWORD=$(cat ~/.config/bookstore/prod-db-password) ./scripts/deploy-local.sh
```

Note `.claude/settings.json` denies reading the password file, so that last line is a thing the
maintainer runs, not something an agent should be doing on its own.
