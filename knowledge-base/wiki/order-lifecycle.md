# Order lifecycle

Source: `raw/codebase-snapshot-2026-07-19.md` (OrderService behaviors), `raw/lld.md`. Code: `grails-app/services/com/learning/bookstore/OrderService.groovy`.

## Placing an order (`place`)

1. Customer must exist and be `ACTIVE` — otherwise `IllegalArgumentException`.
2. For each item: `Book.lock(bookId)` takes a **pessimistic lock** so concurrent orders can't oversell the same book.
3. Insufficient stock rejects the whole order.
4. Stock decrements; the item records `priceAtPurchase` (price history survives later changes).
5. `recalculateTotal()` then a flushing save.

The whole method is one transaction: any failure rolls back stock decrements and the order row together.

## Status transitions (`updateStatus`)

Guarded by `OrderStatus.canTransitionTo(...)` — invalid transitions throw. Side effects:

- → `SHIPPED` sets `shippedAt`
- → `DELIVERED` sets `deliveredAt`

## Cancellation (`cancel`)

- Only from states allowed by `canTransitionTo(CANCELLED)`.
- **Restocks every item** (`stockQuantity + quantity`).
- Appends the cancellation reason to `notes` (defaults to "Customer requested cancellation").
- Exposed as `POST /api/v1/orders/$id/cancel` — deliberately not an HTTP DELETE ([api-endpoints.md](api-endpoints.md)).

## The two invariants to protect when extending

1. **Stock is only touched under lock inside a transaction** — any new code path that changes `stockQuantity` must follow the `Book.lock` pattern.
2. **Status only moves through `canTransitionTo`** — never assign `order.status` directly in new code.

## Related

- [domain-model.md](domain-model.md) — the state machine and `priceAtPurchase`
- [conventions-and-config.md](conventions-and-config.md) — transaction annotations
