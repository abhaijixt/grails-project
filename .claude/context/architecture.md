# Architecture

Answers: what are the layers, where does a request go, and where does logic live.
A summary — the code is authoritative, and there is not much of it (~1,800 lines of Groovy).

## Shape

A conventional Grails 2 layered REST service. No front-end, no messaging, no caching layer,
no scheduled jobs.

```
HTTP → UrlMappings.groovy → *Controller (thin) → *Service (all logic) → GORM domain → MariaDB
                                      ↓
                            ApiResponseService  → the JSON envelope
```

| Layer | Count | Location |
|---|---|---|
| Controllers | 5 | `grails-app/controllers/com/learning/bookstore/` |
| Services | 4 | `grails-app/services/com/learning/bookstore/` |
| Domain classes | 6 + 2 enums | `grails-app/domain/com/learning/bookstore/` |
| Unit specs | 8 (70 tests) | `test/unit/com/learning/bookstore/` |

## The response envelope

Every endpoint renders through `ApiResponseService`, which wraps payloads as:

```json
{ "success": true, "message": "Books retrieved", "data": { ... }, "timestamp": "..." }
```

Errors use the same shape with `success: false` and an `error` key and no `data`. Controllers
set the status code themselves — `render(status: 404, text: apiResponseService.error(...))`.

## Controllers stay thin

The pattern, from `BookController.groovy`:

```groovy
def index() {
    render apiResponseService.success(bookService.list(params), "Books retrieved")
}

def save() {
    try {
        def book = bookService.create(request.JSON as Map)
        render(status: 201, text: apiResponseService.success(bookService.toDto(book), "Book created successfully"))
    } catch (IllegalArgumentException e) {
        render(status: 400, text: apiResponseService.error(e.message))
    }
}
```

Parse, call one service, render. `IllegalArgumentException` from a service is the signal for
400 — services throw it for every business-rule rejection. No GORM queries in a controller;
`validate.sh` warns if one appears.

## Services hold everything

| Service | Owns |
|---|---|
| `BookService` | list/search/paging, create/update/delete, `toDto`, low-stock, the delete-blocked-by-orders guard |
| `OrderService` | placing an order (stock lock + decrement), status transitions, cancellation (restores stock) |
| `AuthorService` | author CRUD |
| `ApiResponseService` | the envelope; no business logic |

`BookService` and `OrderService` are `@Transactional(readOnly = true)` at class level with
`@Transactional` on the mutating methods.

Two conventions worth knowing:

- **Page size is capped at 100** in every list method: `Math.min((params.int('size') ?: 10), 100)`.
- **`params.int(...)` needs a real `GrailsParameterMap`.** A plain `Map` has no `int()` method,
  which is why `BookServiceSpec` mixes in `ControllerUnitTestMixin` to get one.

## The API surface

All routes are in `UrlMappings.groovy` — one file, auditable in one read.

```
GET  /health, /api/v1/health          the deploy gate (HealthController)
     /api/v1/books                    resources: index/show/save/update/delete
GET  /api/v1/books/search             ?title= &categoryId= &minPrice= &maxPrice= &inStock=
GET  /api/v1/books/isbn/$isbn
GET  /api/v1/books/low-stock
     /api/v1/authors, /api/v1/categories    full resources
     /api/v1/orders                   resources minus delete
POST /api/v1/orders/$id/cancel
GET  /api/v1/orders/customer/$customerId, /api/v1/orders/status/$status
PATCH /api/v1/orders/$id/status
```

**`GET /api/v1/orders` returns 405** — the `resources` mapping generates the route but
`OrderController` defines no `index()` action. Pre-existing; orders are listed by customer or
by status instead.

## Health

`HealthController` is not a business endpoint on purpose. It probes the database with
`SELECT 1`, returns 200/`UP` or 503/`DOWN`, and reports `version`, `commit`, `build` and
`environment` from `grails.util.Metadata`. The deploy pipeline gates on it, and then compares
the commit it reports against the one being deployed — which is what catches a container that
failed to be replaced. See `.claude/context/cicd.md`.
