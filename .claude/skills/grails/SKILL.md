---
name: grails
description: Use when adding or changing any Grails artefact in grails-bookstore — a controller action, a service, a domain class, a URL mapping, or a Spring bean. Trigger on "add an endpoint", "expose X over the API", "add a field to the book", "create a service for Y", "my domain change isn't persisting", or any compile or wiring problem specific to Grails 2.5.6. Not for schema changes (use the database skill) or test authoring (use the testing skill).
---

# Working with Grails 2.5.6 in this repo

Read `.claude/context/architecture.md` for the map. This is the procedure.

## Before you start

Run the version gate. Grails 2.5.6 is EOL and most of what you know about Grails is about
Grails 3+. Check `.claude/context/grails-version-compat.md` before using any API you have not
seen in this repo, and use the 2.5.6 docs URL — never `/latest/`.

With 4 services and 5 controllers, there is almost always an existing example. Copy it rather
than reasoning from memory.

## Add an API endpoint

1. **Route** — add it to `UrlMappings.groovy`. Every route is public; there is no auth layer
   below you (`.claude/context/security.md`). Ask who can reach it before you add it.
2. **Controller action** — thin. Parse `params` or `request.JSON`, call one service, render
   through `apiResponseService`. Copy `BookController.index`/`save`.
3. **Service method** — all logic goes here. Throw `IllegalArgumentException` for a business
   rejection; the controller turns it into a 400.
4. **Status codes** — set them explicitly:
   `render(status: 404, text: apiResponseService.error("Book not found"))`.
5. **Spec** — a controller spec with a mocked service, and a service spec for the logic. See
   the testing skill.

Do not add an `index()` to `OrderController` casually — `GET /api/v1/orders` currently 405s by
omission, and something may rely on that.

## Add or change a service

- Class-level `@Transactional(readOnly = true)`, method-level `@Transactional` on mutators.
  Follow `BookService`.
- Cap page size: `Math.min((params.int('size') ?: 10), 100)`.
- `params.int(...)` only exists on a real `GrailsParameterMap`. If your method takes `params`,
  its spec needs `ControllerUnitTestMixin` to supply one.
- Money is `BigDecimal`. `Book.price` is `decimal(6,2)` and constrained `0.01 .. 9999.99` —
  the column and the constraint must stay in agreement.
- Return DTOs as plain `Map`s from a `toDto(...)` method. Do not render domain objects directly.

## Add a field to a domain class

**This is a schema change with no migration path.** Read `.claude/context/database.md` first,
and the database skill. In short:

1. Add the field and its constraint to the domain class.
2. Development picks it up automatically — `dbCreate = "update"` on `bookstore_db_dev`.
3. **Production will not.** `dbCreate = "none"`, and `bookstore_app` has no DDL rights. Apply
   the DDL by hand against `bookstore_db` with an account that can, before merging.
4. Update `toDto(...)` in the owning service, or the field will not appear in any response.
5. Update the domain spec.

Skipping step 3 gives you a build that passes, a deploy whose health check passes, and a
runtime failure on the first request that touches the column.

## GORM pitfalls specific to this codebase

- `Author.belongsTo = Book` — `Book` owns the many-to-many. Drive it with
  `book.addToAuthors(author)`, never from the `Author` side.
- `Book.lock(id)` in `OrderService.place` is the only oversell guard. Any new write path that
  loads a book must use it, or must have a stated reason not to.
- `grails.gorm.failOnError = true` — an invalid `save()` throws. Every save is a throw site.
- `dateCreated` / `lastUpdated` are GORM-managed. Never set them.
- `BookOrder` is not `Order` because `order` is a SQL reserved word. Do not "fix" the name.

## When it will not compile

Grails 2 caches aggressively and reports phantom "unable to resolve class" after a dependency
or config change. `grails --non-interactive clean` before believing the error.

If it still fails, check you are on JDK 8 — `java -version` must print `1.8`. The system
default here is JDK 26 and Groovy 2.4 will not start on it.
