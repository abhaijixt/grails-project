# Database

Answers: which database am I talking to, what is in it, and why does the app return no data?
This is a summary — `grails-app/domain/` and the live schema are authoritative.

## Two databases, two accounts

| Environment | Database | dbCreate | Account | Grants |
|---|---|---|---|---|
| development | `bookstore_db_dev` | `update` | `bookstore_dev` | ALL on that database |
| test | H2 in-memory | `create-drop` | `sa` | — |
| production | `bookstore_db` | `none` | `bookstore_app` | SELECT, INSERT, UPDATE, DELETE only |

Evidence: `DataSource.groovy:26-53`.

They shared `bookstore_db` until 2026-08-29. Development owns its schema — `dbCreate = "update"`
lets Hibernate rewrite it on every boot — so sharing meant a domain-class edit in dev silently
reshaped production. Splitting them is what stopped that.

**Production cannot run DDL.** `dbCreate = "none"` is the first defence and the account grants
are the second: `bookstore_app` has no `CREATE`, `ALTER` or `DROP`, so even setting `dbCreate`
to `update` by accident cannot reshape production. Verified:

```
mysql -u bookstore_app ... -e "create table probe (id int)"
ERROR 1142 (42000): CREATE command denied to user 'bookstore_app'@'localhost'
```

Passwords live at `~/.config/bookstore/{dev,prod}-db-password` (mode 600) on the maintainer's
machine. The dev one is wired into Grails through `~/.grails/bookstore-local.groovy`
(`Config.groovy:4-6`); the production one is a GitHub `production` environment secret injected
into the container. Neither is in this repository, and `.claude/settings.json` denies reading
either file.

## The thing that will confuse you first

`bookstore_db` contains **two parallel schemas**. The application maps to the singular names
and they are empty; the data is in plural tables the application has never mapped.

| Grails table (live, empty) | Legacy table (has data) | Created |
|---|---|---|
| `book` | `books` — 62 rows | legacy: 2026-03-15 |
| `author` | `authors` — 59 rows | Grails: 2026-05-11 |
| `category` | `categories` — 23 rows | `book` specifically: 2026-08-29 |
| `customer` | `customers` — 130 rows | |
| `book_order` | `orders` — 8 rows | |
| `order_item` | `order_items` — 13 rows | |
| `author_books` | `book_authors` — 60 rows | |

So `GET /api/v1/books` returning `totalElements: 0` is **correct behaviour**, not a bug. The
deployed app is healthy and serving an empty dataset.

The legacy tables predate the Grails application: they were created 2026-03-15, and neither the
Grails 6 domain classes (`git show 42961d5^:grails-app/domain/.../Book.groovy`) nor the current
2.5.6 ones have ever declared a `table` mapping. The singular tables were auto-created later by
development's `dbCreate = "update"`.

The two schemas are not interchangeable — different column conventions (`created_at` vs
`date_created`), no `version` column on the legacy side, no `publisher`, and `decimal(10,2)`
vs `decimal(6,2)` for price. Reconciling them is a data-migration decision that has not been
made. See `CICD.md` §5.

## Migrations: there are none

No `database-migration` plugin, no `grails-app/migrations/`, no ledger of what has been applied.

The consequence is specific: **add a field to a domain class and production breaks at runtime,
not at deploy.** The WAR builds, the deploy passes its health check (`/health` probes
`SELECT 1`, not your new column), and the first request touching that field throws.

Until migration tooling exists, a schema change is a manual two-step:

1. Apply the DDL by hand with an account that has DDL rights — `bookstore_app` does not.
2. Then merge the domain change.

Write down what you ran and where. Nothing else records it.

## Domain-to-table map

Default Grails naming throughout — no `table` mapping is declared anywhere except
`Book.description type: "text"` (`Book.groovy:20-22`).

| Class | Table | Notes |
|---|---|---|
| `Book` | `book` | `belongsTo` Category; `hasMany` authors, orderItems |
| `Author` | `author` | `belongsTo = Book` — Book owns the many-to-many |
| `Category` | `category` | `hasMany` books |
| `Customer` | `customer` | `hasMany` orders |
| `BookOrder` | `book_order` | named `BookOrder` because `Order` is a SQL reserved word |
| `OrderItem` | `order_item` | `belongsTo` BookOrder |
| `Author` ↔ `Book` | `author_books` | join table |

`OrderStatus` and `CustomerStatus` are enums, persisted as strings.

## Concurrency

There is no optimistic locking configuration and no `version false` — GORM's default `version`
column applies. The oversell guard is a **pessimistic** one: `Book.lock(id)` in
`OrderService.place`, which issues `SELECT ... FOR UPDATE`. A write path that loads a book with
`Book.get(id)` instead bypasses it.

In unit tests `lock()` has no equivalent in the mock datastore; `OrderServiceSpec` stubs it via
`Book.metaClass.static.lock` and restores it in `cleanup()`.
