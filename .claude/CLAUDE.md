# grails-bookstore

A RESTful bookstore back-end: books, authors, categories, customers and orders, with real
business rules behind them (stock decrement under a pessimistic lock, an order state machine,
price snapshots on order lines). There is no front-end — every endpoint under `/api/v1/`
speaks JSON in a `success` / `message` / `data` / `timestamp` envelope.

It is a **learning project with a production-shaped pipeline**: single maintainer, public
repository, deployed by GitHub Actions onto the maintainer's own laptop. Read that sentence
twice — most of the sharp edges below come from the gap between "learning project" and
"public repo with a self-hosted runner".

It was **downgraded from Grails 6.1.2 to Grails 2.5.6** in Aug 2026 (`DOWNGRADE.md`). Much of
what looks like an odd choice is a consequence of that.

## Tech stack (exact, from `application.properties` + `grails-app/conf/BuildConfig.groovy`)

| Thing | Version | Evidence |
|---|---|---|
| Grails | **2.5.6** | `application.properties:3` |
| Groovy | 2.4.x — whatever Grails 2.5.6 bundles | **UNVERIFIED**: nothing in the repo pins it |
| JDK to build and run | **1.8** | `BuildConfig.groovy:7-10` comment; every workflow asserts it |
| Bytecode source/target level | 1.7 | `BuildConfig.groovy:9-10` |
| Servlet | 3.0 | `BuildConfig.groovy:1` |
| ORM | GORM 3.x / Hibernate **3.6.10.19** | `BuildConfig.groovy` plugins block |
| Database (dev, prod) | MariaDB 10.x | `DataSource.groovy:30,51` |
| Database (test) | H2 in-memory 1.3.176 | `DataSource.groovy:38`, `BuildConfig.groovy` |
| JDBC driver | MariaDB Java client **1.5.9** | `BuildConfig.groovy` — pinned, see below |
| Build tool | the `grails` CLI + Ivy | there is no Gradle, no wrapper |
| Test framework | Spock (bundled with Grails 2.5.6) | `test/unit/**` — 8 specs, 70 tests |
| Logging | log4j 1.x via the `Config.groovy` DSL | `Config.groovy:64-100` |
| Runtime | Tomcat 8.5 / JRE 8, in a container | `scripts/deploy-local.sh` |
| Auth | **none** — every endpoint is public | no filters, no plugin, nothing |

The deployed WAR is `ROOT.war`, so production routes sit at the server root (`/health`).
Under `grails run-app` the app is served from `/grails-bookstore/`.

## Directory map

```
grails-app/conf/        BuildConfig.groovy (deps, JVM levels), DataSource.groovy (no credentials),
                        Config.groovy (external config + log4j), UrlMappings.groovy, BootStrap.groovy
grails-app/domain/      6 GORM classes + 2 enums, com/learning/bookstore/
grails-app/services/    4 services — all the business logic
grails-app/controllers/ 5 thin REST controllers
test/unit/              8 Spock specs, 70 tests — the whole suite
web-app/WEB-INF/        applicationContext.xml, sitemesh.xml (there is no Application.groovy)
scripts/                deploy-local.sh, stamp-build-metadata.sh, notify-failure.sh,
                        setup-self-hosted-runner.sh — the deploy pipeline's moving parts
.github/workflows/      ci.yml (hosted), deploy-local.yml (self-hosted), deploy-docs.yml
docs/                   MkDocs sources, published to GitHub Pages
```

~1,800 lines of Groovy. This is a small codebase; read the file rather than guessing.

## Commands developers actually run

```bash
# JDK 8 is mandatory. The system JDK is 26 and Groovy 2.4 will not start on it.
export JAVA_HOME=~/.sdkman/candidates/java/current
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/grails/current/bin:$PATH"

grails --non-interactive compile                            # compile only
grails --non-interactive test-app unit:                     # 8 specs, 70 tests, ~19s — this is what CI runs
grails --non-interactive prod war target/grails-bookstore.war
grails run-app                                              # dev, on bookstore_db_dev, at /grails-bookstore/
grails --non-interactive clean                              # when compilation goes strange

.claude/hooks/test.sh --dry-run <path>   # which specs a change implies
.claude/hooks/escalate.sh <path>         # who/what needs to sign this off
```

There is no wrapper to commit and no daemon. `grails` resolves everything through Ivy into
`~/.grails`.

## Non-negotiable conventions

1. **JDK 8, always.** `BuildConfig.groovy:9-10` pins source/target to 1.7 and Groovy 2.4 cannot
   run on a newer JVM. Every build path asserts the version before invoking `grails` — do not
   remove those assertions.
2. **No credentials in the repository.** `DataSource.groovy` reads every password from the
   environment or from `~/.grails/bookstore-local.groovy` (wired at `Config.groovy:4-6`).
   The file is outside the tree so a password cannot be committed by accident.
3. **Development and production are different databases.** `bookstore_db_dev` vs `bookstore_db`
   (`DataSource.groovy:30,51`). They shared one until 2026-08-29, which meant dev's
   `dbCreate = "update"` could rewrite production's schema.
4. **Production is `dbCreate = "none"`** (`DataSource.groovy:50`) *and* the `bookstore_app`
   account has DML rights only. There is **no migration tooling** — a domain change reaches the
   code but never the schema.
5. **Controllers stay thin.** Parse `params`/`request.JSON`, call one service, render through
   `apiResponseService`. See `BookController.groovy`. No GORM queries in a controller.
6. **Business logic lives in services**, and money is `BigDecimal` (`Book.price` is
   `decimal(6,2)`, constrained `0.01 .. 9999.99`).
7. **Order status changes go through `OrderService.updateStatus`**, which consults
   `OrderStatus.canTransitionTo`. Never assign `status` and save.
8. **Stock decrement holds a pessimistic lock** — `Book.lock(id)` in `OrderService.place`.
   A write path that loads a book any other way can oversell it.
9. **Write Grails 2.5.6, not modern Grails.** Before using any Grails/GORM/Spock API, check
   `.claude/context/grails-version-compat.md`. Docs live at `grails.apache.org/docs/2.5.6/`
   — never a `/latest/` URL.
10. **Only `deploy-local.yml` may target the self-hosted runner**, and it must never gain a
    `pull_request` trigger. The repo is public; that combination is remote code execution on
    the maintainer's laptop. See `.claude/context/security.md`.

## Where to read more

| Topic | File |
|---|---|
| Layers, request flow, the API surface, where logic lives | `.claude/context/architecture.md` |
| The 6 domain classes, relationships, GORM quirks, the enums | `.claude/context/domain-model.md` |
| Both schemas, the accounts, the orphaned legacy tables, migrations | `.claude/context/database.md` |
| Every build/test command, what CI runs, what it does not | `.claude/context/build-and-test.md` |
| **What Grails 2.5.6 does NOT have** — banned idioms, the docs URL | `.claude/context/grails-version-compat.md` |
| No auth, the public-repo/self-hosted-runner risk, secrets, accounts | `.claude/context/security.md` |
| CI, deploy, rollback, branch protection, what breaks the pipeline | `.claude/context/cicd.md` |

## Top 5 gotchas

1. **The app serves an empty dataset and looks healthy.** `bookstore_db` holds two parallel
   schemas: 62 rows in a legacy `books` table, 0 in the Grails-mapped `book`. The legacy tables
   predate the app and were never mapped by it. `/api/v1/books` returning `totalElements: 0` is
   correct behaviour, not a bug. See `.claude/context/database.md`.

2. **There is no schema migration path.** Production is `dbCreate = "none"` with a DML-only
   account. Add a field to a domain class and production will throw on the missing column at
   runtime, not at deploy. Any domain change needs a hand-applied DDL step first.

3. **`main` is protected and requires a green CI check.** Direct pushes are rejected, history
   must stay linear, so PRs are squash-merged — which means `git branch -d` will refuse the
   merged branch afterwards (its commit is not an ancestor of `main`). Diff the branch against
   `main` before reaching for `-D`.

4. **Actions must be SHA-pinned.** The repository has `sha_pinning_required: true`; a workflow
   using `actions/checkout@v4` will be rejected. Pin the commit SHA with the version in a
   trailing comment.

5. **The MariaDB driver is pinned at 1.5.9 deliberately.** 2.x implements JDBC 4.2, which
   Hibernate 3.6 does not understand. Upgrading it breaks the app at runtime, not at compile
   time.
