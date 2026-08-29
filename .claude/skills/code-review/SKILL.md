---
name: code-review
description: Use when reviewing a diff, a pull request, or freshly written code in grails-bookstore, and as a self-check before proposing changes. Trigger on "review this", "check my changes", "is this correct", "before I open the PR", "look at this diff", or after generating a non-trivial change to grails-app/, scripts/, or .github/. Applies this repo's real conventions, not generic Grails advice.
---

# Code review checklist — grails-bookstore

Derived from the conventions actually present in this codebase. Ordered by blast radius.

## 1 · The self-hosted runner boundary

- [ ] Does any workflow other than `deploy-local.yml` mention `self-hosted`? The repository is
      **public**. That is remote code execution on the maintainer's laptop. `validate.sh`
      blocks it; if it reached review, ask how.
- [ ] Has `deploy-local.yml` gained a `pull_request` trigger? Same problem, via forks.
- [ ] Are all actions pinned to a commit SHA? `sha_pinning_required` is on — a tag reference is
      rejected at run time, so the PR looks fine and the run never starts.
- [ ] Does a workflow change rename the CI job? `Compile, test, package` is the required status
      check on `main`; renaming it blocks every PR until branch protection is updated.

## 2 · Schema, and the absence of migrations

- [ ] Does the diff add, rename or retype a **domain class field**? Production is
      `dbCreate = "none"` with a DML-only account. The DDL must be applied by hand *before*
      this merges, or the first request touching the column throws in production while CI, the
      build and the health check all pass.
- [ ] Does it change `dbCreate` anywhere? Production must stay `none`.
- [ ] Does a new constraint disagree with the column? `Book.price` is `decimal(6,2)` and
      constrained `0.01 .. 9999.99`. Raising one without the other is a runtime truncation.
- [ ] Is the change reaching for the legacy plural tables (`books`, `authors`, …)? Those are
      unmapped and unowned — read `.claude/context/database.md` before touching them.

## 3 · Correctness against the domain

- [ ] Order status set by assignment? It must go through `OrderService.updateStatus`, which
      consults `OrderStatus.canTransitionTo`.
- [ ] New transition added to the enum without widening the spec first?
- [ ] A write path that loads a `Book` **without** `Book.lock(id)`? That silently bypasses the
      only oversell guard.
- [ ] Does cancellation still restore stock, and still append rather than overwrite `notes`?
- [ ] Money still `BigDecimal` end to end — no `double`, no `float`, no `Math.round`.
- [ ] `OrderItem.priceAtPurchase` still a snapshot, never recalculated from the current price.

## 4 · Layering

- [ ] Any GORM query, `new Sql(...)` or `createCriteria()` in a controller? Controllers parse,
      call one service, and render. `validate.sh` warns; the warning is not a suggestion.
- [ ] Does the service throw `IllegalArgumentException` for business rejections, so the
      controller's existing `catch` turns it into a 400?
- [ ] Does every response go through `apiResponseService`? A bare `render` breaks the envelope
      every client depends on.
- [ ] New list endpoint — is the page size capped at 100?
- [ ] New field — is it in the owning service's `toDto(...)`? Otherwise it silently never
      appears in any response.

## 5 · Security

- [ ] New route in `UrlMappings.groovy`? **There is no authentication anywhere.** Whoever can
      reach the port can call it, including the mutating actions.
- [ ] Any literal credential, token or hostname? `validate.sh` blocks the obvious shapes, but
      only literals. Passwords come from the environment or `~/.grails/bookstore-local.groovy`.
- [ ] Anything logged that would carry a request body into the log volume? There is no
      redaction anywhere.
- [ ] Does the diff widen `.claude/settings.json` `allow`? `deny` beats `allow`, and `deny` is
      the real boundary.

## 6 · The version ceiling

- [ ] Any Grails 3+ idiom? `grails.gorm.transactions.Transactional`, `application.yml`,
      `logback.groovy`, `Application.groovy`, Gradle syntax. See
      `.claude/context/grails-version-compat.md` — the git history contains all of them,
      because this repo was downgraded from Grails 6.
- [ ] Anything raising `source.level` / `target.level` above 1.7, or bumping the MariaDB driver
      past 1.x? Both are deliberate pins that look stale.

## 7 · Tests

- [ ] New service logic covered on the happy path **and** every exception it throws?
- [ ] New constraint tested on both sides of the boundary?
- [ ] Any `save(failOnError: true)` in a spec followed by a query or a uniqueness assertion? It
      needs `flush: true` or it silently proves nothing.
- [ ] Does the spec pass for the right reason? A green spec that never flushed is a green spec
      that tested nothing.

## 8 · Deploy mechanics

- [ ] Does the change touch `HealthController`? `/health` is the deploy gate — if it stops
      returning 200, or stops reporting `commit`, every deploy fails and rolls back.
- [ ] Does it touch `scripts/deploy-local.sh`? That is also the only rollback path.
- [ ] Is `application.properties` in the diff carrying `app.commit` / `app.build`? That is the
      local build stamp and should not be committed.

## Repo-specific smells to flag

- **A test asserting `totalElements > 0` against production data.** The app serves an empty
  dataset by design — the rows are in unmapped legacy tables.
- **"Fixing" `BookOrder` to `Order`.** `order` is a SQL reserved word.
- **Removing a JDK-version assertion from a workflow** because "it always passes". It passes
  because it is there; the system JDK is 26.
- **Adding an `index()` to `OrderController`** without checking who relies on the current 405.
- **A second `@Transactional` on a class that already declares `@Transactional(readOnly = true)`**
  — read the existing annotation before adding one.
- **Widening a `validate.sh` guard because it fired.** Sometimes correct — the SQL guards were
  narrowed for exactly that reason. But the fix is a narrower pattern, never deleting the rule.
