# Grails version compatibility — what 2.5.6 does NOT have

This file answers one question: **what will a developer or a model, reasoning from modern
Grails and Groovy knowledge, write into this repo that will not compile or will not run?**

Grails 2.5.6 is EOL. Every model's training data is dominated by Grails 3/4/5/6/7 and
Groovy 3/4. The default failure here is not a crash — it is confidently-written, idiomatic,
**uncompilable** code that looks correct in review.

This repository was **downgraded from Grails 6.1.2 to 2.5.6** in Aug 2026 (`DOWNGRADE.md`),
so the temptation to write modern Grails is unusually strong: the git history contains it.

---

## 1 · The pinned ceiling

| Thing | Pinned at | Evidence |
|---|---|---|
| Grails | **2.5.6** | `application.properties:3` |
| Java source + target level | **1.7** | `BuildConfig.groovy:9-10` |
| JDK used to build and run | 1.8 | `BuildConfig.groovy:7-8` comment; `.github/workflows/ci.yml` pins `java-version: 8`; `deploy-local.yml` asserts `"1.8` and fails otherwise |
| Servlet | 3.0 | `BuildConfig.groovy:1` |
| Hibernate plugin | 3.6.10.19 | `BuildConfig.groovy` plugins block |
| Tomcat build plugin | 8.0.50 | `BuildConfig.groovy` plugins block |
| MariaDB JDBC client | 1.5.9 | `BuildConfig.groovy` dependencies |
| Groovy | 2.4.x — whatever Grails 2.5.6 bundles | **UNVERIFIED.** Nothing in this repo pins it. Confirm from `~/.grails/ivy-cache` on a machine that has built. |
| Spock | bundled with Grails 2.5.6 | **UNVERIFIED.** No explicit pin; the specs compile, so it is present. |

Two independent sources agree on 2.5.6: `application.properties:3` and the deliberate
`grails.project.target.level = 1.7` comment at `BuildConfig.groovy:7-10`, which explains
*why* the ceiling exists rather than merely setting it.

> **Red herring.** The git history contains a complete Gradle build — `build.gradle`,
> `gradle.properties`, a wrapper, `application.yml`, `Application.groovy`, Hibernate 5.6,
> Spring Boot 2.7. All of it was deleted by the downgrade (`git show 42961d5`). Finding those
> files in an old commit, or in `docs/CODE_WALKTHROUGH.md` (which is excluded from the docs
> build and still describes them), is not evidence about the current build.

---

## 2 · Where to actually look things up

Grails moved to the Apache Software Foundation, and the docs moved with it.

| Source | Use it? |
|---|---|
| `https://grails.github.io/grails2-doc/2.5.6/guide/single.html` | ✅ the 2.5.6 guide |
| `https://grails.github.io/grails2-doc/2.5.6/api/index.html` | ✅ the 2.5.6 API |
| `https://docs.grails.org/latest/...` | ❌ **never** — serves Grails 7 |
| `https://grails.org/documentation.html` | ❌ current versions only |

A `/latest/` URL will confidently answer a Grails 2 question with a Grails 7 answer. The two
frameworks share a name and very little else.

---

## 3 · Banned idioms

| If you write | It fails with | Write instead |
|---|---|---|
| `import grails.gorm.transactions.Transactional` | class not found | `import grails.transaction.Transactional` |
| `@Autowired` field injection in a service | works, but is not the convention here | declare the service by name: `BookService bookService` |
| `Application.groovy` / `GrailsApp.run()` | there is no such class | there is no entry point — the container starts the WAR (`web-app/WEB-INF/`) |
| `application.yml` | ignored entirely | `Config.groovy` + `DataSource.groovy` |
| `logback.groovy` | ignored entirely | the `log4j` DSL inside `Config.groovy:64-100` |
| `build.gradle` | there is no Gradle | `BuildConfig.groovy` |
| `implementation "..."` dependency syntax | not a Grails 2 keyword | `compile` / `runtime` / `build` inside `dependency.resolution` |
| Java 8 lambdas in `src/java` | source level is 1.7 | anonymous inner classes — but note this repo has no `src/java` yet |
| `params.bigDecimal('x')` | no such converter in 2.5 | parse by hand; `BookService.toBigDecimal` is the precedent |
| GORM `where` detached criteria on a `@Mock`ed domain | unreliable in unit tests | `createCriteria()` or a dynamic finder |
| `@Rollback`, `@Integration` | Grails 3+ test annotations | `@TestFor` / `@Mock` mixins |

## 4 · Things that look wrong and are not

- **`maxPerm: 256` in `BuildConfig.groovy:12-17`.** PermGen was removed in Java 8; the setting
  is inert. Removing it is harmless, keeping it is harmless. It is not evidence of a Java 7 JVM.
- **MariaDB driver 1.5.9.** The 1.x line is the last that targets the JDBC 4.1 surface
  Hibernate 3.6 expects. 2.x implements JDBC 4.2 and breaks at runtime, not at compile time.
- **`MySQL5InnoDBDialect` against MariaDB** (`DataSource.groovy:6-7`). Hibernate 3.6.10 ships
  no MariaDB dialect; this is the supported way.
- **`grails.project.target.level = 1.7` while building on JDK 8.** Deliberate: Groovy 2.4
  cannot emit above 1.8, and 1.7 is what the downgrade settled on.

## 5 · The version gate

Before writing any Grails artefact, ask: *does this API exist in 2.5.6?* If you are reasoning
from memory of Grails 3+, the answer is often no. Check the 2.5.6 guide URL above, or find an
existing example in this repo — with 4 services and 5 controllers, there is almost always one.
