# Entry Point & Key Patterns

## 8. Entry point — `BootStrap.groovy` and `web.xml`

Grails 2 has no `Application.groovy`. There is no `main()` method and no Spring
Boot: the application is a **plain WAR** started by a servlet container, which
reads a `web.xml` that Grails generates at package time from the templates in
`web-app/WEB-INF/`.

```
web-app/WEB-INF/
├── applicationContext.xml   ← bootstraps the Grails Spring context
└── sitemesh.xml             ← layout decorator configuration
```

The lifecycle hook the application owns is `grails-app/conf/BootStrap.groovy`:

```groovy
class BootStrap {

    def init = { servletContext ->
    }

    def destroy = {
    }
}
```

`init` runs once after the Spring context is built and GORM is wired, `destroy`
on shutdown. Both are empty here — the application seeds no data and registers
no startup hooks.

**What replaced what.** If you have read the Grails 3+ documentation, the
mapping is:

| Grails 3+ | Grails 2.5.6 |
|---|---|
| `Application.groovy` with `GrailsApp.run()` | none — the container starts the app |
| Embedded Tomcat via Spring Boot | external Tomcat, or the `tomcat` build plugin for `run-app` |
| `application.yml` | `DataSource.groovy` + `Config.groovy` |
| `logback.groovy` | `log4j` DSL inside `Config.groovy` |
| `build.gradle` | `BuildConfig.groovy` |

Convention-over-configuration is otherwise unchanged: Grails still scans
`grails-app/` and wires domain classes, services and controllers by location,
with no component scanning to configure.

---

## 9. Key Patterns Used

| Pattern | Where | What It Does |
|---|---|---|
| Active Record | Domain classes | Each domain class has `save()`, `delete()`, `get()`, `findBy*()` built in — no separate repository |
| Service Layer | `*Service.groovy` | All business logic and transactions isolated from HTTP handling |
| DTO via Map | `toDto()` in services | Converts domain objects to plain Maps for JSON serialisation |
| State Machine | `OrderStatus.canTransitionTo()` | Enforces valid order status transitions at the enum level |
| Pessimistic Lock | `Book.lock(id)` in `OrderService.place()` | Prevents concurrent oversell of the same book |
| Price Snapshot | `OrderItem.priceAtPurchase` | Preserves order totals when book prices change later |
| Health Probe | `HealthController` | Reports process, database and build state for the deploy gate |
| Elvis Operator | Throughout | `value ?: default` for null-safe defaults |
| Safe Navigation | Throughout | `object?.field` returns null instead of NullPointerException |
| Spread Operator | `errors.allErrors*.defaultMessage` | Applies method to every element in a collection |
| Implicit Return | All service/domain methods | Last expression is returned without `return` keyword |

---

## 10. `HealthController` — the deploy gate

```groovy
class HealthController {

    def dataSource

    def index() {
        Map database = checkDatabase()
        boolean up = (database.status == 'UP')
        Metadata metadata = Metadata.current

        response.status = up ? 200 : 503
        render([
            status     : up ? 'UP' : 'DOWN',
            application: metadata.getApplicationName(),
            version    : metadata.getApplicationVersion(),
            commit     : metadata.getProperty('app.commit') ?: 'unknown',
            build      : metadata.getProperty('app.build') ?: 'unknown',
            environment: Environment.current.name,
            checks     : [database: database],
            timestamp  : new Date()
        ] as JSON)
    }
}
```

Two decisions worth naming:

- **It probes the database with `SELECT 1`, not with a domain query.** A
  business endpoint would conflate "the application is healthy" with "there is
  data in it" — an empty table would look like an outage, and a broken
  connection pool behind a cached result would not.
- **It reports the commit.** After a deploy passes the health check, the
  pipeline compares the commit `/health` returns against the one it just
  deployed. A container that failed to be replaced answers 200 with the *old*
  commit, and that mismatch is what catches it.
