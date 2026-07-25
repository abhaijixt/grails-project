# Open questions

Gaps and discrepancies the wiki knows about. Candidates for future raw material or fixes.

1. ~~**Groovy version discrepancy (doc vs. repo).**~~ **Resolved 2026-07-19:** `gradle.properties` pins `grailsVersion=6.1.2` and `build.gradle` declares no explicit Groovy — the framework-managed Groovy 4.x from the HLD is correct. The `groovy-5.0.0` jars in `lib/` are IDE-attached libraries (`.idea/libraries/groovy_5_0_0.xml`), not application dependencies.
2. **No tests — confirmed.** `src/main/groovy` and `src/integration-test/groovy` exist as empty directory skeletons; zero `.groovy` files anywhere under `src/`. The test dataSource (H2, create-drop) and `grails-gorm-testing-support` dependency are configured but unused. Coverage is genuinely zero — the biggest gap in the project.
3. **No authentication or authorization.** Every endpoint is open. Deliberate for a learning project, but undocumented — the HLD doesn't state it as a decision.
4. **CustomerController missing — confirmed.** The controllers directory holds exactly four controllers (Author, Book, Category, Order); there is no `CustomerController` and no `/api/v1/customers` route in UrlMappings. Orders require an existing ACTIVE customer, so there is currently **no API path to create the customer an order depends on**. Either a controller is missing or customers are seeded elsewhere (BootStrap not captured — check `grails-app/init/`).
5. **Production migrations unspecified.** `dbCreate: none` in production says migrations run explicitly, but no migration tool (e.g. Liquibase/Flyway or grails-database-migration) was captured. What is the intended mechanism?
6. **Dev credentials in plaintext** — see [conventions-and-config.md](conventions-and-config.md); acceptable for learning, worth an explicit note in the HLD.
