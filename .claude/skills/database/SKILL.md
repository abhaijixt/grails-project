---
name: database
description: Use for any schema or data change against the bookstore databases — adding a column, adding an index, writing a one-off data fix, or reconciling the legacy plural tables. Trigger on "add a column", "migration", "DDL", "alter table", "new index", "data fix", "why is the API returning nothing", or when a domain-class change needs a matching schema change. Also use when asked whether something is safe to run.
---

# Schema and data work

Read `.claude/context/database.md` first for the two databases, the accounts, and the legacy
table situation. This skill is the procedure.

## Know which database you are about to touch

| Database | Who owns it | What happens if you break it |
|---|---|---|
| `bookstore_db_dev` | development, `dbCreate = "update"` | drop it; the next `grails run-app` rebuilds it |
| `bookstore_db` | production, `dbCreate = "none"` | **no backups, no migration ledger** — a mistake is permanent |

The names are one word apart. If you are not certain which one a command targets, you are not
ready to run it.

## There is no migration tool

No `database-migration` plugin, no `grails-app/migrations/`, no record anywhere of what has
been applied. Every production schema change is a human running DDL by hand.

The failure this produces is quiet and specific: add a field to a domain class, and the build
passes, CI passes, the deploy's health check passes — it probes `SELECT 1`, not your column —
and the first request that touches the field throws. **Nothing in the pipeline catches it.**

## The procedure for a schema change

1. Make the domain-class change and let development pick it up (`dbCreate = "update"`).
2. Read the DDL Hibernate actually produced, so you know what you are about to run:
   `SHOW CREATE TABLE book` against `bookstore_db_dev`.
3. Write the equivalent statement for production by hand. Additive only — a new column must be
   **nullable**, or existing rows cannot satisfy it.
4. Apply it to `bookstore_db` with an account that has DDL rights. `bookstore_app` does not,
   and that is deliberate.
5. Record what you ran, where, and when — in the PR description. Nothing else records it.
6. Then merge.

Doing step 6 before step 4 is the mistake this skill exists to prevent.

## Data fixes

- `SELECT` the affected rows first and note the count. If the write reports a different count,
  stop and find out why before continuing.
- Every write needs a `WHERE`. `validate.sh` blocks the unbounded ones when a client is invoked.
- There is no soft delete anywhere in this schema. Removal is removal.
- `BookService.delete` refuses a book that has order items; raw SQL does not. If you are
  bypassing that guard, say why in the PR.

## The legacy tables

`bookstore_db` holds two parallel schemas — the table is in `.claude/context/database.md`.
Before proposing to reconcile them, know that:

- The plural tables predate the application. Neither the Grails 6 nor the 2.5.6 domain classes
  ever mapped to them.
- The shapes differ: `created_at` vs `date_created`, no `version` column, no `publisher`,
  `decimal(10,2)` vs `decimal(6,2)`.
- There are two defensible answers — backfill the Grails tables, or map the domains onto the
  legacy names — and the decision has not been made. It is written up in `CICD.md` §5.

Do not pick one silently as part of an unrelated change.

## Refuse to do these without an explicit, confirmed instruction

- Dropping or truncating anything in `bookstore_db`. No backups exist.
- Changing production `dbCreate` away from `none`.
- Granting `bookstore_app` any DDL privilege. Its absence is a control, not an oversight.
- Rewriting or deleting rows in the legacy plural tables. They are the only copy of that data.
- Running anything at all against `bookstore_db` when the intent was development.

Note that `.claude/settings.json` denies the `mysql` and `mariadb` clients outright. That is
not an obstacle to work around — it is the boundary that keeps schema work on the reviewed
path. If a task genuinely needs a client, say so and let the maintainer run it.
