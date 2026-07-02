# Grails/Groovy Equivalent - Bookstore CRUD

This directory contains a Grails 6 + Groovy implementation equivalent to the existing Spring Boot CRUD API.

## Implemented API Surface

- `GET/POST/PUT/DELETE /api/v1/books`
- `GET /api/v1/books/search`
- `GET /api/v1/books/isbn/{isbn}`
- `GET /api/v1/books/low-stock`
- `GET/POST /api/v1/authors`
- `GET/POST/PUT/DELETE /api/v1/categories`
- `POST/GET/DELETE /api/v1/orders`
- `GET /api/v1/orders/customer/{customerId}`
- `GET /api/v1/orders/status/{status}`
- `PATCH /api/v1/orders/{id}/status?newStatus=...`

## Key Structure

- `grails-app/domain/com/learning/bookstore/`  
  GORM entities (`Book`, `Author`, `Category`, `Customer`, `BookOrder`, `OrderItem`) and enums.
- `grails-app/services/com/learning/bookstore/`  
  Business logic (`BookService`, `OrderService`) and response wrapper helper (`ApiResponseService`).
- `grails-app/controllers/com/learning/bookstore/`  
  REST controllers wired to `/api/v1/...` endpoints.
- `grails-app/conf/UrlMappings.groovy`  
  Route mapping for custom actions (`search`, `lowStock`, `byCustomer`, `updateStatus`, etc.).

## Run (when Grails/Gradle is available)

```bash
cd grails-bookstore
./gradlew bootRun
```

# grails-project
