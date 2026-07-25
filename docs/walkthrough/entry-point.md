# Entry Point & Key Patterns

## 8. `Application.groovy` — Entry Point

```groovy
package com.learning.bookstore

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
        // GrailsApp.run() is the Grails equivalent of SpringApplication.run()
        // It bootstraps the Spring application context, wires GORM, and starts Tomcat
    }
}
```

This is the smallest possible entry point. Grails' convention-over-configuration does the rest: it scans for domain classes, services, and controllers automatically based on their location in `grails-app/`.

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
| Elvis Operator | Throughout | `value ?: default` for null-safe defaults |
| Safe Navigation | Throughout | `object?.field` returns null instead of NullPointerException |
| Spread Operator | `errors.allErrors*.defaultMessage` | Applies method to every element in a collection |
| Implicit Return | All service/domain methods | Last expression is returned without `return` keyword |
