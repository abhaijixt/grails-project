# Index

The map of this knowledge base. One line per article; start here for any question.

| Article | What it covers |
|---------|----------------|
| [architecture.md](architecture.md) | Stack, layered pattern, request flow, and where each responsibility lives |
| [domain-model.md](domain-model.md) | The 6 entities + 2 enums, relationships, constraints, and the order state machine |
| [api-endpoints.md](api-endpoints.md) | The full REST surface from UrlMappings, plus the response envelope |
| [order-lifecycle.md](order-lifecycle.md) | Placing, shipping, delivering, cancelling — stock locking and restock rules |
| [conventions-and-config.md](conventions-and-config.md) | GORM settings, environment dataSources, pagination and DTO conventions |
| [questions.md](questions.md) | Open questions, gaps, and one logged doc/code discrepancy |

## Sources in raw/

- `hld.md` — high-level design (stack, context, architecture diagrams)
- `lld.md` — low-level design (per-entity field specs, constraints, relationships)
- `code-walkthrough.md` — narrative walkthrough of the code
- `codebase-snapshot-2026-07-19.md` — verbatim captures: layout, UrlMappings, config, OrderService behaviors
