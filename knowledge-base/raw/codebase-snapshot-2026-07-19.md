# Codebase snapshot — 2026-07-19

Verbatim captures from the repository at ingestion time. No interpretation.

## Source layout

```
grails-app/conf/application.yml
grails-app/conf/UrlMappings.groovy
grails-app/controllers/com/learning/bookstore/{Author,Book,Category,Order}Controller.groovy
grails-app/domain/com/learning/bookstore/{Author,Book,BookOrder,Category,Customer,CustomerStatus,OrderItem,OrderStatus}.groovy
grails-app/services/com/learning/bookstore/{ApiResponseService,AuthorService,BookService,OrderService}.groovy
grails-app/init/com/learning/bookstore/Application.groovy
docs/{HLD,LLD,CODE_WALKTHROUGH}.md
build.gradle, gradle.properties, gradlew, gradle/wrapper/
lib/groovy-5.0.0*.jar (many Groovy 5.0.0 jars + sources)
.claude/settings.local.json
```

## UrlMappings.groovy (full contents)

```groovy
class UrlMappings {
    static mappings = {
        "/api/v1/books"(resources: 'book')
        "/api/v1/books/search"(controller: "book", action: "search", method: "GET")
        "/api/v1/books/isbn/$isbn"(controller: "book", action: "findByIsbn", method: "GET")
        "/api/v1/books/low-stock"(controller: "book", action: "lowStock", method: "GET")

        "/api/v1/authors"(resources: 'author')

        "/api/v1/categories"(resources: 'category')

        // Order resources without delete (cancellation is an explicit business action)
        "/api/v1/orders"(resources: 'order', excludes: ['delete'])
        "/api/v1/orders/$id/cancel"(controller: "order", action: "cancel", method: "POST")
        "/api/v1/orders/customer/$customerId"(controller: "order", action: "byCustomer", method: "GET")
        "/api/v1/orders/status/$status"(controller: "order", action: "byStatus", method: "GET")
        "/api/v1/orders/$id/status"(controller: "order", action: "updateStatus", method: "PATCH")

        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
```

## application.yml — key facts (verbatim excerpts)

```yaml
grails:
  gorm:
    failOnError: true

environments:
  development:
    dataSource:
      dbCreate: update
      url: jdbc:mariadb://localhost:3306/bookstore_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true
      username: developer
      password: dev_password_123
  test:
    dataSource:
      dbCreate: create-drop
      url: jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE
  production:
    dataSource:
      dbCreate: none        # migrations run explicitly
      url: ${DB_URL:...}
      password: ${DB_PASSWORD}

dataSource:
  pooled: true
  driverClassName: org.mariadb.jdbc.Driver
  dialect: org.hibernate.dialect.MariaDB103Dialect
  properties: { initialSize: 5, maxActive: 50 }
```

## ApiResponseService.groovy (full contents)

```groovy
class ApiResponseService {
    JSON success(Object data, String message = "Success") {
        [success: true, message: message, data: data, timestamp: new Date()] as JSON
    }
    JSON error(String error, String message = "Error") {
        [success: false, message: message, error: error, timestamp: new Date()] as JSON
    }
}
```

## OrderService.groovy — captured behaviors (paraphrase-free signatures + key lines)

- Class annotated `@Transactional(readOnly = true)`; mutating methods annotated `@Transactional`.
- `place(Map payload)`: rejects missing customer and non-ACTIVE customer; per item does `Book.lock(item.bookId)` with comment "Pessimistic lock prevents concurrent orders from overselling the same book"; rejects `stockQuantity < qty`; decrements stock; records `priceAtPurchase: book.price`; calls `order.recalculateTotal()` then `save(flush: true, failOnError: true)`.
- `updateStatus(Long, OrderStatus)`: guards with `order.status.canTransitionTo(newStatus)`; sets `shippedAt` on SHIPPED, `deliveredAt` on DELIVERED.
- `cancel(Long, String reason)`: guards transition to CANCELLED; restocks every item (`stockQuantity + quantity`); appends cancellation reason to `notes`.
- Pagination in `customerOrders`/`byStatus`: `max = Math.min(size ?: 10, 100)`, sorted `dateCreated desc`, returns `[content, totalElements, page, size]`.
