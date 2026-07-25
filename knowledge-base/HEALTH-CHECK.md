# Health check — monthly audit

Run this once a month, or whenever asked. Read `writing-rules.md`, `changelog.md`, the full `wiki/`, and everything in `raw/` and `outputs/` added since the last health check. Then run the seven-stage audit:

1. **Contradictions** — flag articles that disagree with each other; name both and quote the conflicting claims.
2. **Broken links & orphans** — wiki links that point nowhere, and articles no other article links to.
3. **Source provenance** — claims in the wiki not traceable to a file in `raw/`.
4. **Coverage** — raw files not yet reflected anywhere in the wiki.
5. **Stale articles** — content contradicted by newer raw material.
6. **Out of date** — anything older than 90 days that no longer holds (version numbers, pricing, feature availability).
7. **New article candidates** — three suggestions, each justified by gaps found above.

## Report

File the report as `outputs/YYYY-MM-DD-health-check.md` using this shape:

```
# Health check — <date>
## Verdict (one paragraph)
## Findings (grouped by the seven stages, only non-empty stages)
## Action menu (numbered list of concrete fixes, smallest first)
```

Log the run in `changelog.md`.

## Actioning

- **Interactive run**: after filing the report, ask the human which action-menu items to execute (multiple choice), then execute the chosen ones and log each.
- **Unattended run**: file the report only. Never edit the wiki during an unattended health check.
