# API endpoints

Source: `raw/codebase-snapshot-2026-07-19.md` (verbatim UrlMappings), `raw/code-walkthrough.md`.

## Route table

RESTful resources expand to the standard Grails CRUD actions; extras are listed explicitly.

| Resource | Routes |
|----------|--------|
| Books | `resources` at `/api/v1/books`, plus `GET /search`, `GET /isbn/$isbn`, `GET /low-stock` |
| Authors | `resources` at `/api/v1/authors` |
| Categories | `resources` at `/api/v1/categories` (delete guarded — see [domain-model.md](domain-model.md)) |
| Orders | `resources` at `/api/v1/orders` **excluding delete**, plus `POST /$id/cancel`, `GET /customer/$customerId`, `GET /status/$status`, `PATCH /$id/status` |

The design choice on orders is explicit in the mapping's comment: cancellation is a business action, not a row deletion — hence no DELETE route.

## Response envelope

Every controller renders through `ApiResponseService`:

```json
{ "success": true,  "message": "Success", "data": …, "timestamp": … }
{ "success": false, "message": "Error",   "error": …, "timestamp": … }
```

## Pagination shape

List endpoints in `OrderService` return `[content, totalElements, page, size]` with `size` capped at 100 and default sort `dateCreated desc`. Treat this as the project convention for any new list endpoint.

## Error views

`500` → `/error`, `404` → `/notFound` (mapped at the bottom of UrlMappings).

## Related

- [order-lifecycle.md](order-lifecycle.md) — what the order routes actually do
- [architecture.md](architecture.md)
