# Domain model

Answers: what are the entities, how do they relate, and which constraints will reject a save.
A summary — `grails-app/domain/com/learning/bookstore/` is authoritative and is 8 short files.

## The graph

```
Category 1──* Book *──* Author        (join table author_books; Book owns it)
                │
                └──* OrderItem *──1 BookOrder *──1 Customer
```

`BookOrder` is called that, not `Order`, because `order` is a SQL reserved word.

## Constraints that will reject a save

`grails.gorm.failOnError = true` (`Config.groovy:53`), so an invalid `save()` **throws** rather
than returning null. Assume every save can throw.

| Class | Constraint | Why it matters |
|---|---|---|
| `Book` | `price` min `0.01`, max `9999.99` | the column is `decimal(6,2)` — the constraint and the column agree, do not raise one alone |
| `Book` | `isbn` unique, maxSize 20 | |
| `Book` | `title` size 1..300, `stockQuantity` min 0 | negative stock is rejected at the domain, not just in the service |
| `Book` | `category` not nullable | `belongsTo = [category: Category]` |
| `Author` | `email` unique + format | |
| `Customer` | `phone` matches `/^\+?[0-9]{10,15}$/` | punctuated numbers are rejected — `555-1234` fails |
| `Customer` | `email` unique + format | |
| `Category` | `name` unique, size 2..100 | a spec that builds two categories with the same name fails on the *category*, not the thing under test |
| `OrderItem` | `quantity` min 1, `priceAtPurchase` min 0 | |

## Load-bearing behaviour

**`OrderStatus.canTransitionTo`** is the whole order state machine, in one enum method:

```
PENDING    → CONFIRMED, CANCELLED
CONFIRMED  → PROCESSING, CANCELLED
PROCESSING → SHIPPED
SHIPPED    → DELIVERED
DELIVERED  → REFUNDED
CANCELLED, REFUNDED → nothing
```

`OrderServiceSpec` covers all six legal transitions and five illegal ones. Widening the machine
means widening the spec first.

**`Author.belongsTo = Book`** makes `Book` the owner of the many-to-many. GORM 3.x requires an
explicit owner on a bidirectional many-to-many; `BookService` drives the association through
`book.addToAuthors(...)`. Many-to-many caps at save-update cascade, so deleting a `Book` never
deletes `Author`s.

**`OrderItem.priceAtPurchase`** is a price snapshot. It is written when the order is placed and
never recalculated, so changing a book's price does not rewrite order history.
`OrderItem.getSubtotal()` multiplies it by quantity; `BookOrder.recalculateTotal()` sums them.

**Derived getters**: `Author.getFullName()`, `Customer.getFullName()`, `OrderItem.getSubtotal()`.
They are not persisted and appear in DTOs only because the services put them there.

## Auditing

Every class has `dateCreated` / `lastUpdated`, which GORM populates. Never set them by hand.
There is no `createdBy` equivalent — the application has no concept of a user
(`.claude/context/security.md`).

## GORM quirks that bite in tests

- **`lock()` does not exist in the unit-test datastore.** `OrderServiceSpec` stubs
  `Book.metaClass.static.lock` in `setup()` and clears `Book.metaClass` in `cleanup()`.
- **Unique constraints and queries only see flushed state.** `save(failOnError: true)` alone is
  not enough in a spec — use `save(flush: true, failOnError: true)` or the second instance
  validates happily and your `findAllBy...` returns nothing.
- **Enums default at the field**, not in `constraints`: `OrderStatus status = OrderStatus.PENDING`.
