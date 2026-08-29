# Build and test

Answers: how do I get from a clean checkout to a running app and a green suite, and what does
CI actually check.

## Zero to running

Grails 2.5.6 and JDK 8 both come from SDKMAN on the maintainer's machine. **The system JDK is
26 and Groovy 2.4 will not start on it** — this is the single most common failure.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current              # a JDK 8
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/grails/current/bin:$PATH"
java -version    # must print 1.8.x
grails --version # must print 2.5.6
```

Note the ordering: `$JAVA_HOME/bin` must come **first**. Sourcing `sdkman-init.sh` is not
enough — `/usr/bin` already precedes the SDKMAN candidate directory on this machine's PATH, so
`java` still resolves to 26.

Development needs MariaDB running and `~/.grails/bookstore-local.groovy` present with the
`bookstore_dev` password. Without it the dev DataSource has a null password and fails at boot.

## Commands

```bash
grails --non-interactive compile                              # ~15s warm
grails --non-interactive test-app unit:                       # 8 specs, 70 tests
grails --non-interactive prod war target/grails-bookstore.war # what the deploy builds
grails run-app                                                # dev, at /grails-bookstore/
grails --non-interactive clean                                # when compilation goes strange
```

`grails run-app` serves under a **context path** named after the project. The deployed WAR is
`ROOT.war` and serves at the root. So the health probe is `/grails-bookstore/health` locally
and `/health` in production — the same route, two URLs.

## Timing (measured on this machine)

| Scope | Wall clock |
|---|---|
| full unit suite | **19s** |
| a single spec | **13s** |

Grails 2.5.6 boot is ~12s of both. Narrowing the scope saves about six seconds, which is why
`.claude/hooks/test.sh` defaults to the whole suite and why it is not wired to a hook — 19s on
every edit would be disruptive, and 13s would not be much better.

```bash
.claude/hooks/test.sh                  # full suite
.claude/hooks/test.sh --dry-run <path> # which specs a change implies
```

## The tests

8 Spock specs, 70 tests, all unit — there are no integration tests and no test database beyond
H2 in-memory (`DataSource.groovy:36-43`).

| Spec | Covers |
|---|---|
| `BookSpec`, `AuthorSpec`, `CustomerSpec` | domain constraints and boundaries |
| `BookServiceSpec` | create/update/delete paths, guards, paging cap, DTO shape |
| `OrderServiceSpec` | stock decrement, all 11 status transitions, cancel restores stock |
| `BookControllerSpec` | 200/201/400/404 with a mocked service |
| `ApiResponseServiceSpec` | the envelope |
| `HealthControllerSpec` | UP with a real H2 datasource, DOWN/503 on a failing one |

## What CI runs

`.github/workflows/ci.yml`, on every PR and every push to `main`, on `ubuntu-latest`:

1. JDK 8 via `setup-java`
2. Grails 2.5.6 downloaded from its GitHub release, cached with `~/.grails` and `~/.m2`
3. `grails --non-interactive compile`
4. `grails --non-interactive test-app unit:`
5. `./scripts/stamp-build-metadata.sh` then `grails --non-interactive prod war`

Packaging is in CI deliberately: `war` exercises GSP compilation and plugin packaging that
`compile` and `test-app` never touch.

CI is the **required status check** on `main` under the name **`Compile, test, package`**.
Renaming that job breaks branch protection until the required check is renamed to match.

## What CI does not run

- No integration tests — there are none.
- No static analysis. `BuildConfig.groovy` declares no CodeNarc and there is no formatter;
  `.claude/hooks/format.sh` explains what it does instead.
- No coverage gate.

So the unit suite and the deploy-time `/health` probe are the only two gates between a bad
commit and a live deploy.

## When compilation goes strange

Grails 2 caches aggressively and reports phantom "unable to resolve class" errors after a
dependency or config change. `grails --non-interactive clean` first, before believing the error.
