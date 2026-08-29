# Configuration & Build

Grails 2.5.6 predates the Gradle-based Grails build entirely. There is no
`build.gradle`, no `gradle.properties`, and no wrapper — the `grails` command is
the build tool, and configuration lives in four Groovy files under
`grails-app/conf/` plus `application.properties` at the project root.

---

## 1. `application.properties`

```properties
#Grails Metadata file
app.grails.version=2.5.6
app.name=grails-bookstore
app.version=1.0.0
```

This is the project's identity file. Grails reads it at build time to pick the
framework version, and at runtime into `grails.util.Metadata`, which is how
`HealthController` reports what is running.

The deploy pipeline appends three more keys before packaging (see
[CI/CD](https://github.com/abhaijixt/grails-project/blob/main/CICD.md)):

```properties
app.commit=9f3c1ab…      # the commit the WAR was built from
app.build=42             # the GitHub Actions run number
app.builtAt=2026-08-29T04:44:34Z
```

They are absent from a plain local build, and `/health` reports `unknown` for
them — which is the correct answer for a WAR nobody can trace to a commit.

---

## 2. `BuildConfig.groovy`

The Grails 2 equivalent of `build.gradle`: JVM targets, dependency resolution,
and plugins.

```groovy
// Grails 2.5.6 runs on JDK 7/8 only. Groovy 2.4.x cannot emit or run
// bytecode above 1.8, so these levels must not be raised.
grails.project.target.level = 1.7
grails.project.source.level = 1.7
```

**Why this matters more than it looks.** The system JDK on the build machine is
Java 26. Groovy 2.4 cannot run on it at all — not "with warnings", but a hard
failure. Every build path (local, CI, deploy) pins `JAVA_HOME` to a JDK 8 and
asserts the version before invoking `grails`.

```groovy
grails.project.fork = [
    test   : [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, daemon: true],
    run    : [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve: false],
    war    : [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve: false],
    console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256]
]
```

Grails 2.3+ runs each command in a forked JVM so the build's classpath cannot
leak into the application's. `maxPerm` is a PermGen setting — meaningless on
Java 8, harmless to leave.

```groovy
grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {
    repositories {
        grailsCentral()
        mavenLocal()
        mavenCentral()
        mavenRepo "https://repo.grails.org/grails/core"
        mavenRepo "https://repo.grails.org/grails/plugins"
    }

    dependencies {
        // Legacy JDBC drivers: the 1.x MariaDB client is the last line that
        // targets the JDBC 4.1 surface Hibernate 3.6 expects.
        runtime "org.mariadb.jdbc:mariadb-java-client:1.5.9"
        runtime "com.h2database:h2:1.3.176"
    }

    plugins {
        build ":tomcat:8.0.50"
        compile ":cache:1.1.8"
        // Hibernate 3.6.10 via the Grails 2 hibernate plugin (GORM 3.x).
        runtime ":hibernate:3.6.10.19"
    }
}
```

Two things are worth pausing on:

- **Plugins are dependencies here.** In Grails 3+ a plugin is an ordinary Maven
  artifact in `build.gradle`. In Grails 2 they are declared in their own
  `plugins` block and resolved from the Grails plugin repository.
- **The driver version is deliberate, not stale.** MariaDB client 2.x and later
  implement JDBC 4.2, which Hibernate 3.6 does not understand. Upgrading it
  breaks the application at runtime, not at compile time.

---

## 3. `DataSource.groovy`

Database configuration per environment. **No credentials appear in this file.**

```groovy
dataSource {
    pooled = true
    jmxExport = true
    driverClassName = "org.mariadb.jdbc.Driver"
    // Hibernate 3.6.10 ships no MariaDB dialect; MySQL5InnoDBDialect is the
    // supported way to talk to MariaDB from this Hibernate line.
    dialect = "org.hibernate.dialect.MySQL5InnoDBDialect"
    properties {
        jmxEnabled = true
        initialSize = 5
        maxActive = 50
    }
}
```

```groovy
environments {
    development {
        dataSource {
            dbCreate = "update"
            url = System.getenv("DEV_DB_URL") ?:
                "jdbc:mariadb://localhost:3306/bookstore_db_dev?createDatabaseIfNotExist=true&useSSL=false"
            username = System.getenv("DEV_DB_USER") ?: "bookstore_dev"
            password = System.getenv("DEV_DB_PASSWORD")
        }
    }
    test {
        dataSource {
            dbCreate = "create-drop"
            url = "jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
            driverClassName = "org.h2.Driver"
            dialect = "org.hibernate.dialect.H2Dialect"
            username = "sa"
            password = ""
        }
    }
    production {
        dataSource {
            dbCreate = "none"
            url = System.getenv("DB_URL") ?: "jdbc:mariadb://localhost:3306/bookstore_db?useSSL=false"
            username = System.getenv("DB_USER") ?: "bookstore_app"
            password = System.getenv("DB_PASSWORD")
        }
    }
}
```

**Development and production use different databases.** `bookstore_db_dev` is
owned by `bookstore_dev`; `bookstore_db` is reachable only by `bookstore_app`.
They shared one database until 2026-08-29, which meant development's
`dbCreate = "update"` was free to rewrite production's schema whenever a domain
class changed.

**`dbCreate` is defence in depth, not the only defence.** Production is `none`
*and* the `bookstore_app` account holds `SELECT, INSERT, UPDATE, DELETE` with no
DDL rights, so Hibernate could not alter the schema even if the setting were
changed by accident.

**Where the passwords come from.** Development reads `DEV_DB_PASSWORD`, or the
external config below. Production reads `DB_PASSWORD`, injected into the
container by the deploy pipeline from a GitHub environment secret. Neither has a
default: an unset password fails loudly rather than silently connecting as
somebody else.

---

## 4. `Config.groovy`

Application configuration: content negotiation, GORM behaviour, and logging.

```groovy
// Optional per-machine overrides (dev database password, local ports). Grails
// logs a warning and carries on when the file is absent, which is the normal
// case in CI and in the production container.
grails.config.locations = [
    "file:${System.getProperty('user.home')}/.grails/bookstore-local.groovy"
]
```

External config is merged *over* everything in `grails-app/conf/`, and it is
parsed per-environment, so a developer's local file can set
`environments { development { dataSource.password = "…" } }` without touching
the test or production settings. The file is outside the repository, so a
password can never be committed by accident.

```groovy
// GORM: fail loudly on a save() of an invalid object
grails.gorm.failOnError = true
```

Without this, `book.save()` returns `null` on a validation failure and execution
carries on as though the record were written.

```groovy
log4j.main = {
    appenders {
        console name: 'stdout',
                layout: pattern(conversionPattern: '%d{ISO8601} %-5p %c{2} - %m%n')

        // Docker discards a container's stdout when the container is removed,
        // and every deploy removes it. This file lives on a mounted volume so
        // the logs outlive the release that wrote them.
        rollingFile name: 'appLog',
                    file: "${logDirectory}/grails-bookstore.log",
                    maxFileSize: '10MB',
                    maxBackupIndex: 10,
                    layout: pattern(conversionPattern: '%d{ISO8601} %-5p %c{2} - %m%n')
    }

    root { warn 'stdout', 'appLog' }

    info 'com.learning.bookstore'
    debug 'grails.app'
}
```

`logDirectory` resolves to `$catalina.base/logs` inside Tomcat and to `target/`
outside it, so `grails run-app` and the test suite never write outside the
project.

Grails 2 uses **log4j 1.x with a Groovy DSL**, not Logback — the appender syntax
here has no equivalent in a Grails 3+ `logback.groovy`.

---

## 5. `UrlMappings.groovy`

```groovy
class UrlMappings {
    static mappings = {
        // Probed by the deploy pipeline and by anything watching the app.
        "/health"(controller: "health", action: "index", method: "GET")
        "/api/v1/health"(controller: "health", action: "index", method: "GET")

        // resources: 'book' generates 7 routes in total:
        //   GET    /api/v1/books           → index
        //   POST   /api/v1/books           → save
        //   GET    /api/v1/books/{id}      → show
        //   PUT    /api/v1/books/{id}      → update  (PATCH also maps here)
        //   DELETE /api/v1/books/{id}      → delete
        //   GET    /api/v1/books/create    → create  (browser-form scaffold, unused here)
        //   GET    /api/v1/books/{id}/edit → edit    (browser-form scaffold, unused here)
        "/api/v1/books"(resources: 'book')

        // Custom routes that the 'resources' shorthand doesn't cover
        "/api/v1/books/search"(controller: "book", action: "search", method: "GET")
        "/api/v1/books/isbn/$isbn"(controller: "book", action: "findByIsbn", method: "GET")
        "/api/v1/books/low-stock"(controller: "book", action: "lowStock", method: "GET")

        "/api/v1/authors"(resources: 'author')      // full CRUD
        "/api/v1/categories"(resources: 'category') // full CRUD

        // Orders: excludes: ['delete'] means DELETE /api/v1/orders/{id} is not routed.
        // Cancellation is an explicit business action, not a generic HTTP DELETE.
        "/api/v1/orders"(resources: 'order', excludes: ['delete'])
        "/api/v1/orders/$id/cancel"(controller: "order", action: "cancel", method: "POST")
        "/api/v1/orders/customer/$customerId"(controller: "order", action: "byCustomer", method: "GET")
        "/api/v1/orders/status/$status"(controller: "order", action: "byStatus", method: "GET")
        "/api/v1/orders/$id/status"(controller: "order", action: "updateStatus", method: "PATCH")

        "500"(view: '/error')     // Grails renders error.gsp for 500s
        "404"(view: '/notFound')  // Grails renders notFound.gsp for 404s
    }
}
```

**Why centralised routing matters:** In Spring Boot, every controller method has
its own `@GetMapping`/`@PostMapping` annotation. In Grails, all routes are in
this one file. This makes it trivial to audit every endpoint the application
exposes.

**Note on the health path.** The deployed WAR is `ROOT.war`, so the probe is at
`http://localhost:8080/health`. Under `grails run-app` the application is served
from a context path named after the project, so the same route is at
`http://localhost:8080/grails-bookstore/health`.

---

## 6. Building and running

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current        # JDK 8
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/grails/current/bin:$PATH"

grails --non-interactive compile           # compile only
grails --non-interactive test-app unit:    # 70 unit tests
grails --non-interactive prod war target/grails-bookstore.war
grails run-app                             # development, on the dev database
```

There is no wrapper to commit and no daemon to warm up; the `grails` command
resolves everything through Ivy into `~/.grails`.
