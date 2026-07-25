# Architecture

Sources: `raw/hld.md`, `raw/codebase-snapshot-2026-07-19.md`.

## What this is

A RESTful back-end API for a bookstore. No front-end — every response is JSON. Clients manage books, authors, categories, customers, and orders over HTTP on port 8080.

## Stack

| Layer | Technology |
|-------|------------|
| Language | Apache Groovy (HLD states 4.x — but see [questions.md](questions.md) about the Groovy 5 jars in `lib/`) |
| Framework | Grails 6.1.2 on Spring Boot 2.7.18 |
| ORM | GORM + Hibernate |
| Database | MariaDB (dev/prod), H2 in-memory (test) |
| Build | Gradle 7.6.4 wrapper, JDK 17, WAR packaging |

## The layered pattern

```
Controller  →  Service  →  Domain (GORM)  →  MariaDB/H2
```

One responsibility per layer, and the code holds to it:

- **Controllers** (`grails-app/controllers/…`) parse HTTP, call a service, render JSON through `ApiResponseService`. No business logic.
- **Services** (`grails-app/services/…`) own all business rules and transactions — e.g. `OrderService` is `@Transactional(readOnly = true)` at class level with `@Transactional` only on mutating methods.
- **Domains** (`grails-app/domain/…`) define shape, constraints, relationships, and the order state machine.

## Where to look first

| Task | File |
|------|------|
| Add or change a route | `grails-app/conf/UrlMappings.groovy` |
| Change a business rule | The matching service in `grails-app/services/` |
| Change validation or a relationship | The domain class in `grails-app/domain/` |
| Change environments or DB | `grails-app/conf/application.yml` |

## Related

- [domain-model.md](domain-model.md), [api-endpoints.md](api-endpoints.md), [order-lifecycle.md](order-lifecycle.md)
