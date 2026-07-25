# Conventions and configuration

Source: `raw/codebase-snapshot-2026-07-19.md`, `raw/hld.md`.

## GORM settings

- `grails.gorm.failOnError: true` — every `save()` throws on validation failure instead of returning false. Don't write `if (!obj.save())` checks in this codebase; catch exceptions at the controller boundary instead.
- Services default to `@Transactional(readOnly = true)` at class level, with `@Transactional` on mutating methods only (`OrderService` is the model to copy).

## Environments

| Env | dbCreate | Database |
|-----|----------|----------|
| development | `update` | MariaDB `bookstore_db` (auto-created; credentials in application.yml) |
| test | `create-drop` | H2 in-memory |
| production | `none` — migrations must be run explicitly | MariaDB via `DB_URL`/`DB_USER`/`DB_PASSWORD` env vars |

Connection pool: initialSize 5, maxActive 50, MariaDB dialect 10.3.

## Project conventions to follow when extending

1. New endpoints go through UrlMappings, respond via `ApiResponseService`, and follow the `[content, totalElements, page, size]` pagination shape with `size` capped at 100 ([api-endpoints.md](api-endpoints.md)).
2. DTO mapping happens in services (`OrderService.toDto`), not in controllers or domains.
3. Computed display fields follow the `getFullName()` pattern on the domain class.
4. Destructive operations get guards, not trust: category deletion checks for books; order deletion doesn't exist at all.

## Security note

Dev database credentials sit in plaintext in `application.yml` (`developer` / `dev_password_123`). Fine for a learning project; flagged in [questions.md](questions.md) for anything beyond that.

## Related

- [architecture.md](architecture.md), [order-lifecycle.md](order-lifecycle.md)
