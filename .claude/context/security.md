# Security

Answers: what protects this application, and what does not. The short version is that the
application has no security at all, and the interesting risk is not in the application.

## There is no authentication

No Spring Security plugin, no filters, no `@Secured`, nothing:

```
grep -rln "spring-security\|Filters\|@Secured\|authenticate" grails-app/ src/   # no matches
```

Every route in `UrlMappings.groovy` is reachable by anyone who can reach the port, including
the mutating ones — `POST /api/v1/books`, `POST /api/v1/orders`,
`POST /api/v1/orders/$id/cancel`. The deployed container binds `0.0.0.0:8080` on the
maintainer's laptop (`--network host` in `scripts/deploy-local.sh`), so it is reachable from
the local network, not just localhost.

This is acceptable for a learning project on a laptop. It is worth being explicit about because
**adding a route to `UrlMappings.groovy` is a security decision** — there is no layer beneath it
that will catch you.

## The real risk: a public repository with a self-hosted runner

This repository is **public**, and `deploy-local.yml` runs on a **self-hosted runner installed
on the maintainer's laptop**. A workflow that runs repository code on that runner is arbitrary
code execution on the machine.

Making the repository private was considered and rejected: the MkDocs site is published through
GitHub Pages, and Pages on a private repository needs a paid plan. So the mitigation is a hard
split, enforced in four places:

| Layer | Control |
|---|---|
| Workflow design | `deploy-local.yml` is the **only** workflow targeting `self-hosted`, and it has **no `pull_request` trigger**. CI, which runs PR code including from forks, is on `ubuntu-latest`. |
| Repository policy | Fork-PR approval is `all_external_contributors` — every outside contributor's run needs manual approval, not just their first. |
| Environment | The `production` environment restricts deployments to `main` and scopes `DB_PASSWORD` to it. |
| Hook | `validate.sh` blocks any workflow file that is not `deploy-local.yml` and mentions `self-hosted`, and blocks a `pull_request:` trigger being added to `deploy-local.yml`. |

If this repository ever accepts outside contributions, revisit all four. The current posture is
safe for a solo public repo; it is not a substitute for deploying somewhere that is not a
personal machine.

## Secrets

| Secret | Where it lives | Notes |
|---|---|---|
| production DB password | GitHub `production` environment secret; `~/.config/bookstore/prod-db-password` (0600) | injected into the container as `DB_PASSWORD` |
| dev DB password | `~/.grails/bookstore-local.groovy` (0600) | outside the repo, loaded via `Config.groovy:4-6` |

`DataSource.groovy` contains **no credentials** — every password comes from
`System.getenv(...)` or from external config, and neither has a default. An unset password
fails loudly rather than silently connecting as somebody else.

## Known gap: a credential in git history

The old `developer` account password is in this public repository's history. It has been
**rotated** (2026-08-29) and the old value is now inert — verified: the old password is
rejected with `ERROR 1045`. Rewriting history to remove it would need a force push, which
branch protection blocks.

Note what made it worse than it looked: `developer` was a MariaDB superuser
(`ALL PRIVILEGES ON *.* WITH GRANT OPTION`). Exposure was bounded only because every grant was
`@localhost`. The application no longer uses that account at all — see
`.claude/context/database.md`.

## Actions hardening

- Actions restricted to **GitHub-owned only** (`patterns_allowed: []`).
- **SHA pinning required** repo-wide. An action referenced by tag is rejected at run time;
  `validate.sh` warns about it at edit time.
- `permissions: contents: read` declared explicitly in CI and deploy.
- `main` is protected: PR required, CI check required, linear history, no force pushes.

## What a change here should not do

- Add a route without asking who can reach it. There is no auth layer.
- Log a request body. There is no redaction anywhere.
- Put a hostname, token or password in `Config.groovy` or `DataSource.groovy` — `validate.sh`
  blocks the obvious shapes, but it only catches literals.
- Widen `.claude/settings.json` `allow` without reading the `deny` list. `deny` beats `allow`,
  and it is the real boundary.
