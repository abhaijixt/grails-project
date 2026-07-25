# Grails/Groovy Equivalent - Bookstore CRUD

This directory contains a Grails 6 + Groovy implementation equivalent to the existing Spring Boot CRUD API.

## Implemented API Surface

- `GET/POST/PUT/DELETE /api/v1/books`
- `GET /api/v1/books/search`
- `GET /api/v1/books/isbn/{isbn}`
- `GET /api/v1/books/low-stock`
- `GET/POST /api/v1/authors`
- `GET/POST/PUT/DELETE /api/v1/categories`
- `POST/GET/DELETE /api/v1/orders`
- `GET /api/v1/orders/customer/{customerId}`
- `GET /api/v1/orders/status/{status}`
- `PATCH /api/v1/orders/{id}/status?newStatus=...`

## Key Structure

- `grails-app/domain/com/learning/bookstore/`  
  GORM entities (`Book`, `Author`, `Category`, `Customer`, `BookOrder`, `OrderItem`) and enums.
- `grails-app/services/com/learning/bookstore/`  
  Business logic (`BookService`, `OrderService`) and response wrapper helper (`ApiResponseService`).
- `grails-app/controllers/com/learning/bookstore/`  
  REST controllers wired to `/api/v1/...` endpoints.
- `grails-app/conf/UrlMappings.groovy`  
  Route mapping for custom actions (`search`, `lowStock`, `byCustomer`, `updateStatus`, etc.).

## Run (when Grails/Gradle is available)

```bash
cd grails-bookstore
./gradlew bootRun
```

## Documentation

Design documentation lives in `docs/` and is published as a MkDocs site using the
Material theme. The Markdown files are readable as-is on GitHub; the MkDocs site
adds search, syntax highlighting, and rendered Mermaid diagrams.

| Document | Covers |
|---|---|
| `docs/HLD.md` | High-level design — stack, architecture, API surface, business rules |
| `docs/LLD.md` | Low-level design — domain specs, schema, method and action specs, response shapes |
| `docs/walkthrough/` | Code walkthrough — file-by-file, split by layer (config, domain, services, controllers) |

### Serving the docs locally

Requires Python 3.9+. Install the pinned toolchain into a virtualenv:

```bash
python3 -m venv .venv-docs
source .venv-docs/bin/activate
pip install -r requirements-docs.txt
```

Then start the live-reloading dev server from the repository root:

```bash
mkdocs serve
```

Open <http://127.0.0.1:8000/>. Edits to anything under `docs/` reload the browser
automatically.

To produce the static site (output goes to `site/`, which is git-ignored):

```bash
mkdocs build --strict
```

`--strict` turns broken links, bad anchors, and pages missing from the nav into
build failures — worth keeping on in CI.

> **Note:** Mermaid diagrams are rendered client-side by a script the Material
> theme loads from a CDN, so they need network access on first view. The ASCII
> versions of the same diagrams are kept in the documents alongside them and
> render offline.

# grails-project
