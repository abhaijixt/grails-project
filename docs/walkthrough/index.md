# Code Walkthrough — Grails Bookstore

This document walks through every source file in the project in reading order — from the bottom of the stack (domain) upward to the top (controllers). For each file, the full code is explained line by line where it matters.

---

## Reading Order

```
1. gradle.properties          ← JVM and Grails version pins
2. build.gradle               ← Dependencies and plugins
3. application.yml            ← Database configuration
4. UrlMappings.groovy         ← All REST routes in one place
5. Domain classes             ← Data model and rules
   5a. OrderStatus.groovy     ← State machine enum
   5b. CustomerStatus.groovy
   5c. Category.groovy
   5d. Author.groovy
   5e. Customer.groovy
   5f. Book.groovy
   5g. OrderItem.groovy
   5h. BookOrder.groovy
6. Services                   ← Business logic
   6a. ApiResponseService.groovy
   6b. AuthorService.groovy
   6c. BookService.groovy
   6d. OrderService.groovy
7. Controllers                ← HTTP layer
   7a. AuthorController.groovy
   7b. CategoryController.groovy
   7c. BookController.groovy
   7d. OrderController.groovy
8. Application.groovy         ← Entry point
```

---

## Pages in This Walkthrough

The reading order above is split across five pages, in the same sequence:

| # | Page | Covers |
|---|---|---|
| 1 | [Configuration & Build](configuration.md) | `gradle.properties`, `build.gradle`, `application.yml`, `UrlMappings.groovy` |
| 2 | [Domain Layer](domain.md) | All eight domain classes and enums (5a–5h) |
| 3 | [Service Layer](services.md) | `ApiResponseService`, `AuthorService`, `BookService`, `OrderService` (6a–6d) |
| 4 | [Controller Layer](controllers.md) | `AuthorController`, `CategoryController`, `BookController`, `OrderController` (7a–7d) |
| 5 | [Entry Point & Key Patterns](entry-point.md) | `Application.groovy` and the patterns summary |
