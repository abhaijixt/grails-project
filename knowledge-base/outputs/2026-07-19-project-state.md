# Output — State of the project: what's solid, what's missing

*Demo question, answered from this knowledge base plus code verification. Generated 2026-07-19. Sources: [architecture.md](../wiki/architecture.md), [domain-model.md](../wiki/domain-model.md), [order-lifecycle.md](../wiki/order-lifecycle.md), [api-endpoints.md](../wiki/api-endpoints.md), [conventions-and-config.md](../wiki/conventions-and-config.md), [questions.md](../wiki/questions.md).*

## What's solid

The core design is better than a typical CRUD learning project:

- **Clean layering, actually enforced.** Controllers stay thin, services own transactions with the right annotations (`readOnly = true` class default, `@Transactional` on mutations), domains own constraints ([architecture.md](../wiki/architecture.md)).
- **Concurrency handled correctly.** Order placement takes a pessimistic lock per book (`Book.lock`) inside one transaction, so concurrent orders can't oversell — and cancellation restocks atomically ([order-lifecycle.md](../wiki/order-lifecycle.md)).
- **Real business modeling.** A guarded order state machine (`canTransitionTo`), `priceAtPurchase` preserving price history, cancellation as a POST action rather than a DELETE, and a category deletion guard ([domain-model.md](../wiki/domain-model.md), [api-endpoints.md](../wiki/api-endpoints.md)).
- **Consistent API conventions.** One response envelope via `ApiResponseService`, capped pagination with a fixed shape, DTO mapping kept in services ([conventions-and-config.md](../wiki/conventions-and-config.md)).

## What's missing (verified against the code, not just the docs)

1. **Zero tests.** `src/` contains only empty directory skeletons — no unit or integration tests despite the H2 test environment and testing dependencies being configured. This is the highest-value gap to close ([questions.md](../wiki/questions.md) #2).
2. **No way to create a customer.** Four controllers exist (Book, Author, Category, Order) but no CustomerController and no `/api/v1/customers` route — yet placing an order requires an existing ACTIVE customer. The API can't currently exercise its own order flow end-to-end ([questions.md](../wiki/questions.md) #4).
3. **No auth.** Every endpoint is open; not stated as a decision anywhere ([questions.md](../wiki/questions.md) #3).
4. **No migration tooling** despite production's `dbCreate: none` requiring explicit migrations ([questions.md](../wiki/questions.md) #5).

## Suggested next steps, smallest first

1. Add `CustomerController` + `/api/v1/customers` routes following the existing controller/service/envelope conventions — unblocks the full order flow.
2. Write the first integration test: place an order, assert stock decrements; cancel it, assert restock. This pins the two invariants [order-lifecycle.md](../wiki/order-lifecycle.md) says must be protected.
3. Pick a migration tool (grails-database-migration is the idiomatic choice) before any production deployment.
4. Document the no-auth decision in the HLD, or add it.
