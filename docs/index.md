# Grails Bookstore

A RESTful back-end API for a bookstore, built with Grails 2.5.6 and Groovy 2.4, persisting through GORM/Hibernate to MariaDB (H2 in tests). There is no front-end — every endpoint under `/api/v1/` speaks JSON, wrapped in a consistent `success` / `message` / `data` / `timestamp` envelope. It manages books, authors, categories, customers, and orders, with real business rules behind them: pessimistic locking on stock decrement, an order state machine that rejects invalid transitions, price snapshots on order lines, and referential-integrity guards in the service layer.

## Running the App

Grails 2.5.6 is the build tool — there is no Gradle wrapper. It needs **JDK 8**;
Groovy 2.4 cannot run on a newer JVM.

```bash
export JAVA_HOME=~/.sdkman/candidates/java/current   # a JDK 8
export PATH="$JAVA_HOME/bin:$HOME/.sdkman/candidates/grails/current/bin:$PATH"

cd grails-bookstore
grails run-app
```

The API comes up on `http://localhost:8080/grails-bookstore/`, and the health
probe at `/grails-bookstore/health` reports whether the database is reachable.
The `development` environment expects MariaDB at `localhost:3306/bookstore_db_dev`
— a different database from production — with the password supplied outside the
repository. See [Configuration Environments](HLD.md#9-configuration-environments)
for the full matrix.

The deployed WAR is packaged as `ROOT.war`, so in production the same routes sit
at the server root: `http://localhost:8080/health`.

## The Documentation

Three documents describe this codebase at three different altitudes. **Read them in this order** — each assumes the one before it.

<div class="grid cards" markdown>

- :material-map-outline: **[High-Level Design](HLD.md)** — *Start here.*

    The 10,000-foot view: technology stack, system context, the layered
    architecture Grails prescribes, the entity-relationship overview, the full
    API surface, the seven key business rules, and the environment matrix.
    Read this first — it is short, and it gives you the vocabulary the other
    two documents use.

- :material-file-tree: **[Low-Level Design](LLD.md)** — *Then this.*

    The specification level: every domain class field-by-field with its GORM
    constraints, the generated database schema, method signatures for every
    service, action specifications for every controller, the URL routing table,
    three critical flows traced end to end, and the exact JSON response shapes
    and HTTP status codes.

- :material-code-braces: **[Code Walkthrough](walkthrough/index.md)** — *Then this, as needed.*

    The line-by-line reading of every source file, from the bottom of the stack
    upward: build config, then domain classes, then services, then controllers.
    Split across five pages that follow the walkthrough's own reading order.
    Use it as a reference while working in the code rather than as a
    cover-to-cover read.

</div>

!!! tip "If you only have ten minutes"

    Read the [High-Level Design](HLD.md) end to end, then jump to
    [Critical Flows](LLD.md#6-critical-flows-detailed) in the LLD. Between
    them, those two cover what the application does and the three places where
    the interesting logic lives.
