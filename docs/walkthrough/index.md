# Code Walkthrough — Grails Bookstore

This document walks through every source file in the project in reading order — from the bottom of the stack (domain) upward to the top (controllers). For each file, the full code is explained line by line where it matters.

---

## Reading Order

```
1. application.properties     ← Grails version and app identity
2. BuildConfig.groovy         ← Dependencies, plugins, JVM targets
3. DataSource.groovy          ← Per-environment database configuration
4. Config.groovy              ← App config, external config, log4j
5. UrlMappings.groovy         ← All REST routes in one place
6. Domain classes             ← Data model and rules
   6a. OrderStatus.groovy     ← State machine enum
   6b. CustomerStatus.groovy
   6c. Category.groovy
   6d. Author.groovy
   6e. Customer.groovy
   6f. Book.groovy
   6g. OrderItem.groovy
   6h. BookOrder.groovy
7. Services                   ← Business logic
   7a. ApiResponseService.groovy
   7b. AuthorService.groovy
   7c. BookService.groovy
   7d. OrderService.groovy
8. Controllers                ← HTTP layer
   8a. AuthorController.groovy
   8b. CategoryController.groovy
   8c. BookController.groovy
   8d. OrderController.groovy
   8e. HealthController.groovy
9. BootStrap.groovy           ← Lifecycle hooks (there is no Application.groovy)
```

---

## Pages in This Walkthrough

The reading order above is split across five pages, in the same sequence:

| # | Page | Covers |
|---|---|---|
| 1 | [Configuration & Build](configuration.md) | `application.properties`, `BuildConfig.groovy`, `DataSource.groovy`, `Config.groovy`, `UrlMappings.groovy` |
| 2 | [Domain Layer](domain.md) | All eight domain classes and enums (6a–6h) |
| 3 | [Service Layer](services.md) | `ApiResponseService`, `AuthorService`, `BookService`, `OrderService` (7a–7d) |
| 4 | [Controller Layer](controllers.md) | `AuthorController`, `CategoryController`, `BookController`, `OrderController` (8a–8d) |
| 5 | [Entry Point & Key Patterns](entry-point.md) | `BootStrap.groovy`, `HealthController`, and the patterns summary |
