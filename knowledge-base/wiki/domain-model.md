# Domain model

Sources: `raw/lld.md`, `raw/hld.md`. Files live in `grails-app/domain/com/learning/bookstore/`.

## Entity map

```
Category ──< Book >── Author        (Book belongsTo Category; Book–Author via join table book_author)
              │
              ▼ (via OrderItem)
Customer ──< BookOrder ──< OrderItem ──> Book
```

Six domain classes (`Book`, `Author`, `Category`, `Customer`, `BookOrder`, `OrderItem`) and two enums (`CustomerStatus`, `OrderStatus`). All entities carry GORM-managed `dateCreated` / `lastUpdated`.

## Constraints worth remembering

| Entity | The rules that bite |
|--------|---------------------|
| Book | `isbn` unique (max 20); `price` 0.01–9999.99; `stockQuantity` min 0; `description` mapped as SQL TEXT |
| Author | `email` unique + format-validated; `getFullName()` computed property |
| Category | `name` unique (2–100 chars); **deletion guard**: CategoryController rejects delete with HTTP 400 if `Book.countByCategory > 0` |
| Customer | `email` unique; `phone` regex `/^\+?[0-9]{10,15}$/`; `status` defaults to ACTIVE; has `getFullName()` |
| BookOrder | `status` defaults to PENDING; `totalAmount` min 0; `shippedAt`/`deliveredAt` set by status transitions; belongsTo Customer (cascade delete) |
| OrderItem | Records `priceAtPurchase` at order time — historical prices survive later price changes |

## The order state machine

`OrderStatus` implements `canTransitionTo(...)`; services refuse invalid transitions (see `raw/codebase-snapshot-2026-07-19.md`). Cancellation is only reachable from states where `canTransitionTo(CANCELLED)` holds — details in [order-lifecycle.md](order-lifecycle.md).

## Related

- [order-lifecycle.md](order-lifecycle.md) — how these rules behave at runtime
- [conventions-and-config.md](conventions-and-config.md) — `failOnError: true` makes constraint violations throw
