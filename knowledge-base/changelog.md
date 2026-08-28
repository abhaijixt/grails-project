# Changelog

Append-only. Newest entries at the bottom.

- **2026-07-19** — Knowledge base created inside the grails-bookstore repository. Schema (CLAUDE.md), writing-rules.md, and HEALTH-CHECK.md set up.
- **2026-07-19** — Ingested 4 raw files: `hld.md` (copy of docs/HLD.md), `lld.md` (copy of docs/LLD.md), `code-walkthrough.md` (copy of docs/CODE_WALKTHROUGH.md), `codebase-snapshot-2026-07-19.md` (verbatim captures: layout, UrlMappings, config, OrderService behaviors).
- **2026-07-19** — First wiki build: index + 5 topic articles + questions.md. Discrepancy logged in questions.md: HLD states Groovy 4.x while `lib/` contains Groovy 5.0.0 jars.
- **2026-07-19** — Code verification pass on open questions: #1 resolved (Groovy 4.x confirmed via gradle.properties/build.gradle; lib/ Groovy 5 jars are IDE artifacts), #2 confirmed (zero test files under src/), #4 confirmed (no CustomerController, no /api/v1/customers route). questions.md updated.
- **2026-07-19** — First output generated: `outputs/2026-07-19-project-state.md` (state of the project: what's solid, what's missing, next steps).
