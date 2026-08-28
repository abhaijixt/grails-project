/* Grails Bookstore documentation — React (JSX via Babel standalone).
   Components mirror the borrowed editorial style: sticky topbar, hero with
   tilted code card, numbered feature sections, checklist split-card, FAQ. */

const {
  useState
} = React;

/* ---------------- Topbar ---------------- */

function Topbar() {
  return /*#__PURE__*/React.createElement("div", {
    className: "topbar"
  }, /*#__PURE__*/React.createElement("div", {
    className: "topbar-inner"
  }, /*#__PURE__*/React.createElement("a", {
    className: "wordmark",
    href: "#top"
  }, "bookstore", /*#__PURE__*/React.createElement("span", {
    className: "tick"
  }, "."), "api"), /*#__PURE__*/React.createElement("div", {
    className: "topbar-meta"
  }, /*#__PURE__*/React.createElement("span", {
    className: "micro"
  }, "Grails 6.1.2 · JDK 17 · MariaDB"), /*#__PURE__*/React.createElement("a", {
    className: "btn primary",
    href: "#endpoints"
  }, "API reference"))));
}

/* ---------------- Hero ---------------- */

function Hero() {
  return /*#__PURE__*/React.createElement("div", {
    className: "section soft",
    id: "top"
  }, /*#__PURE__*/React.createElement("div", {
    className: "container"
  }, /*#__PURE__*/React.createElement("div", {
    className: "hero-content"
  }, /*#__PURE__*/React.createElement("div", {
    className: "hero-copy"
  }, /*#__PURE__*/React.createElement("a", {
    className: "crumb",
    href: "#architecture"
  }, "← Project docs"), /*#__PURE__*/React.createElement("h1", null, "Grails Bookstore"), /*#__PURE__*/React.createElement("p", {
    className: "lede"
  }, "A RESTful back-end API for a bookstore. No front-end — every response is JSON."), /*#__PURE__*/React.createElement("p", {
    className: "sub"
  }, "Books, authors, categories and orders over HTTP on port 8080. Layered the way Grails intends: thin controllers, transactional services, GORM domains with real constraints and a guarded order state machine."), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("a", {
    className: "btn secondary",
    href: "#quickstart"
  }, "Get started ↓"))), /*#__PURE__*/React.createElement("div", {
    className: "hero-card"
  }, /*#__PURE__*/React.createElement("pre", null, `grails-app/
├── conf/         `, /*#__PURE__*/React.createElement("span", {
    className: "c-dim"
  }, "# application.yml, UrlMappings"), `
├── controllers/  `, /*#__PURE__*/React.createElement("span", {
    className: "c-dim"
  }, "# Book, Author, Category, Order"), `
├── domain/       `, /*#__PURE__*/React.createElement("span", {
    className: "c-dim"
  }, "# 6 entities + 2 enums"), `
├── services/     `, /*#__PURE__*/React.createElement("span", {
    className: "c-dim"
  }, "# business rules + transactions"), `
└── init/

`, /*#__PURE__*/React.createElement("span", {
    className: "c-accent"
  }, "$ ./gradlew bootRun"), `
Grails application running at http://localhost:8080`)))));
}

/* ---------------- Numbered feature section ---------------- */

function Feature({
  num,
  title,
  media,
  children
}) {
  return /*#__PURE__*/React.createElement("div", {
    className: "feature"
  }, /*#__PURE__*/React.createElement("div", {
    className: "feature-media"
  }, /*#__PURE__*/React.createElement("div", {
    className: "inner"
  }, media)), /*#__PURE__*/React.createElement("div", {
    className: "feature-head"
  }, /*#__PURE__*/React.createElement("div", {
    className: "feature-title"
  }, /*#__PURE__*/React.createElement("h3", null, title), /*#__PURE__*/React.createElement("p", {
    className: "feature-num"
  }, "0", /*#__PURE__*/React.createElement("b", null, num))), /*#__PURE__*/React.createElement("div", {
    className: "feature-body"
  }, children)));
}

/* ---------------- Endpoint table ---------------- */

const ROUTES = [["GET", "/api/v1/books", "List books (standard resource)"], ["POST", "/api/v1/books", "Create a book"], ["GET", "/api/v1/books/search", "Search books"], ["GET", "/api/v1/books/isbn/{isbn}", "Find by ISBN"], ["GET", "/api/v1/books/low-stock", "Books running low on stock"], ["GET", "/api/v1/authors", "Authors resource (full CRUD)"], ["GET", "/api/v1/categories", "Categories resource — delete guarded when books exist"], ["GET", "/api/v1/orders", "Orders resource — DELETE deliberately excluded"], ["POST", "/api/v1/orders/{id}/cancel", "Cancel an order (restocks items)"], ["GET", "/api/v1/orders/customer/{id}", "Orders for a customer (paginated)"], ["GET", "/api/v1/orders/status/{status}", "Orders by status (paginated)"], ["PATCH", "/api/v1/orders/{id}/status", "Move order through the state machine"]];
function EndpointsTable() {
  return /*#__PURE__*/React.createElement("table", null, /*#__PURE__*/React.createElement("thead", null, /*#__PURE__*/React.createElement("tr", null, /*#__PURE__*/React.createElement("th", null, "Method"), /*#__PURE__*/React.createElement("th", null, "Route"), /*#__PURE__*/React.createElement("th", null, "Purpose"))), /*#__PURE__*/React.createElement("tbody", null, ROUTES.map(([m, path, desc]) => /*#__PURE__*/React.createElement("tr", {
    key: m + path
  }, /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("span", {
    className: "method " + m.toLowerCase()
  }, m)), /*#__PURE__*/React.createElement("td", null, /*#__PURE__*/React.createElement("code", null, path)), /*#__PURE__*/React.createElement("td", null, desc)))));
}

/* ---------------- Checklist split card ---------------- */

const REQUIREMENTS = ["JDK 17 and the bundled Gradle wrapper (7.6.4) — no global installs needed", "MariaDB running locally for dev (the schema auto-creates via dbCreate: update)", "Nothing else — H2 in-memory covers the test environment"];
const HIGHLIGHTS = ["Pessimistic locking on order placement — concurrent orders can't oversell a book", "Guarded state machine: status only moves through canTransitionTo()", "priceAtPurchase snapshots — history survives later price changes", "One JSON envelope everywhere via ApiResponseService, pagination capped at 100"];
function SplitCard() {
  return /*#__PURE__*/React.createElement("div", {
    className: "split-card"
  }, /*#__PURE__*/React.createElement("div", {
    className: "split-col"
  }, /*#__PURE__*/React.createElement("h3", null, "All you need to run it:"), REQUIREMENTS.map(text => /*#__PURE__*/React.createElement("div", {
    className: "check-item",
    key: text
  }, /*#__PURE__*/React.createElement("span", {
    className: "dot"
  }), /*#__PURE__*/React.createElement("p", null, text)))), /*#__PURE__*/React.createElement("div", {
    className: "split-col"
  }, /*#__PURE__*/React.createElement("h3", null, "What the code gets right:"), HIGHLIGHTS.map(text => /*#__PURE__*/React.createElement("div", {
    className: "check-item",
    key: text
  }, /*#__PURE__*/React.createElement("span", {
    className: "dot green"
  }), /*#__PURE__*/React.createElement("p", null, text)))));
}

/* ---------------- FAQ accordion ---------------- */

const FAQS = [{
  q: "How do I run it locally?",
  a: /*#__PURE__*/React.createElement("p", null, "Start MariaDB, then run ", /*#__PURE__*/React.createElement("code", null, "./gradlew bootRun"), " from the project root. The dev dataSource uses ", /*#__PURE__*/React.createElement("code", null, "dbCreate: update"), " with ", /*#__PURE__*/React.createElement("code", null, "createDatabaseIfNotExist"), ", so the ", /*#__PURE__*/React.createElement("code", null, "bookstore_db"), " schema appears on first boot. The API listens on port 8080.")
}, {
  q: "Why is there no DELETE route for orders?",
  a: /*#__PURE__*/React.createElement("p", null, "Cancellation is a business action, not a row deletion. The mapping excludes DELETE and exposes ", /*#__PURE__*/React.createElement("code", null, "POST /api/v1/orders/", "{id}", "/cancel"), " instead — it validates the state transition, restocks every item, and appends the cancellation reason to the order notes.")
}, {
  q: "How is stock protected from race conditions?",
  a: /*#__PURE__*/React.createElement("p", null, /*#__PURE__*/React.createElement("code", null, "OrderService.place()"), " takes a pessimistic lock per book (", /*#__PURE__*/React.createElement("code", null, "Book.lock(bookId)"), ") inside a single transaction. Insufficient stock rejects the whole order, and any failure rolls back both the stock decrement and the order row. Any new code path that touches ", /*#__PURE__*/React.createElement("code", null, "stockQuantity"), " must follow the same pattern.")
}, {
  q: "What are the environments?",
  a: /*#__PURE__*/React.createElement("p", null, "Development: MariaDB with ", /*#__PURE__*/React.createElement("code", null, "dbCreate: update"), ". Test: H2 in-memory with", /*#__PURE__*/React.createElement("code", null, " create-drop"), ". Production: ", /*#__PURE__*/React.createElement("code", null, "dbCreate: none"), " — migrations must run explicitly, with credentials injected via ", /*#__PURE__*/React.createElement("code", null, "DB_URL"), ", ", /*#__PURE__*/React.createElement("code", null, "DB_USER"), " and", /*#__PURE__*/React.createElement("code", null, " DB_PASSWORD"), " environment variables.")
}, {
  q: "What's known to be missing?",
  a: /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("p", null, "Verified gaps, in priority order: zero tests (the H2 test environment is configured but", /*#__PURE__*/React.createElement("code", null, " src/"), " holds only empty skeletons), no CustomerController — so there's no API path to create the customer an order depends on — no authentication, and no migration tooling despite production requiring it."), /*#__PURE__*/React.createElement("p", null, "The suggested first fix is a CustomerController following the existing conventions, which unblocks the full order flow end to end."))
}];
function FaqItem({
  q,
  a
}) {
  const [open, setOpen] = useState(false);
  return /*#__PURE__*/React.createElement("div", {
    className: "faq-item" + (open ? " open" : ""),
    onClick: () => setOpen(!open)
  }, /*#__PURE__*/React.createElement("div", {
    className: "faq-q"
  }, /*#__PURE__*/React.createElement("p", null, q), /*#__PURE__*/React.createElement("span", {
    className: "faq-toggle"
  })), /*#__PURE__*/React.createElement("div", {
    className: "faq-a"
  }, a));
}
function Faq() {
  return /*#__PURE__*/React.createElement("div", {
    className: "faq"
  }, FAQS.map(item => /*#__PURE__*/React.createElement(FaqItem, {
    key: item.q,
    q: item.q,
    a: item.a
  })));
}

/* ---------------- Footer ---------------- */

function Footer() {
  return /*#__PURE__*/React.createElement("footer", null, /*#__PURE__*/React.createElement("div", {
    className: "footer-inner"
  }, /*#__PURE__*/React.createElement("div", {
    className: "footer-cols"
  }, /*#__PURE__*/React.createElement("div", {
    className: "footer-col"
  }, /*#__PURE__*/React.createElement("span", {
    className: "micro"
  }, "Docs"), /*#__PURE__*/React.createElement("a", {
    href: "#architecture"
  }, "Architecture"), /*#__PURE__*/React.createElement("a", {
    href: "#domain"
  }, "Domain model"), /*#__PURE__*/React.createElement("a", {
    href: "#endpoints"
  }, "API reference"), /*#__PURE__*/React.createElement("a", {
    href: "#lifecycle"
  }, "Order lifecycle"), /*#__PURE__*/React.createElement("a", {
    href: "#conventions"
  }, "Conventions")), /*#__PURE__*/React.createElement("div", {
    className: "footer-col"
  }, /*#__PURE__*/React.createElement("span", {
    className: "micro"
  }, "Source"), /*#__PURE__*/React.createElement("a", {
    href: "#top"
  }, "grails-app/controllers"), /*#__PURE__*/React.createElement("a", {
    href: "#top"
  }, "grails-app/services"), /*#__PURE__*/React.createElement("a", {
    href: "#top"
  }, "grails-app/domain"), /*#__PURE__*/React.createElement("a", {
    href: "#top"
  }, "docs/ (HLD · LLD · walkthrough)"))), /*#__PURE__*/React.createElement("div", null, /*#__PURE__*/React.createElement("div", {
    className: "footer-brand"
  }, "bookstore", /*#__PURE__*/React.createElement("span", {
    className: "tick"
  }, "."), "api")), /*#__PURE__*/React.createElement("div", {
    className: "footer-bottom"
  }, /*#__PURE__*/React.createElement("span", null, "A project-based-learning build — Grails 6.1.2 · Spring Boot 2.7 · GORM"), /*#__PURE__*/React.createElement("span", null, "Docs generated from the project knowledge base"))));
}

/* ---------------- Page ---------------- */

function App() {
  return /*#__PURE__*/React.createElement(React.Fragment, null, /*#__PURE__*/React.createElement(Topbar, null), /*#__PURE__*/React.createElement(Hero, null), /*#__PURE__*/React.createElement("div", {
    className: "section card-bg",
    id: "architecture"
  }, /*#__PURE__*/React.createElement("div", {
    className: "container"
  }, /*#__PURE__*/React.createElement("div", {
    className: "section-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "micro"
  }, "The system"), /*#__PURE__*/React.createElement("h2", null, "What's inside the bookstore"), /*#__PURE__*/React.createElement("p", null, "Six sections, one per layer of the system — each with the code-level detail and the rule worth remembering when you extend it.")), /*#__PURE__*/React.createElement(Feature, {
    num: 1,
    title: "Layered architecture, actually enforced",
    media: /*#__PURE__*/React.createElement("pre", null, `Request → Controller → Service → Domain (GORM) → MariaDB / H2

Controller   parses HTTP, calls a service, renders JSON. No logic.
Service      owns all business rules + transactions.
Domain       shape, constraints, relationships, state machine.`)
  }, /*#__PURE__*/React.createElement("p", null, "Services default to ", /*#__PURE__*/React.createElement("code", null, "@Transactional(readOnly = true)"), " at class level with", /*#__PURE__*/React.createElement("code", null, " @Transactional"), " only on mutating methods — copy ", /*#__PURE__*/React.createElement("code", null, "OrderService"), " ", "when adding a new one. To change a route, edit UrlMappings; a rule, the service; validation, the domain class.")), /*#__PURE__*/React.createElement(Feature, {
    num: 2,
    title: "The domain model",
    media: /*#__PURE__*/React.createElement("pre", null, `Category ──< Book >── Author      (join table: book_author)
              │
              ▼ (via OrderItem)
Customer ──< BookOrder ──< OrderItem ──> Book

Book      isbn unique · price 0.01–9999.99 · stock min 0
Customer  email unique · status defaults ACTIVE
BookOrder status defaults PENDING · belongsTo Customer`)
  }, /*#__PURE__*/React.createElement("p", null, "Six entities and two enums, all with GORM-managed ", /*#__PURE__*/React.createElement("code", null, "dateCreated"), " /", /*#__PURE__*/React.createElement("code", null, " lastUpdated"), ". Category deletion is guarded: the controller returns HTTP 400 if any book still references it. ", /*#__PURE__*/React.createElement("code", null, "OrderItem"), " records", /*#__PURE__*/React.createElement("code", null, " priceAtPurchase"), " so order history survives price changes.")), /*#__PURE__*/React.createElement("div", {
    id: "endpoints"
  }, /*#__PURE__*/React.createElement(Feature, {
    num: 3,
    title: "API reference",
    media: /*#__PURE__*/React.createElement(EndpointsTable, null)
  }, /*#__PURE__*/React.createElement("p", null, "Every response uses one envelope:", " ", /*#__PURE__*/React.createElement("code", null, "{ success, message, data, timestamp }"), " on success and", " ", /*#__PURE__*/React.createElement("code", null, "{ success, message, error, timestamp }"), " on failure, rendered through ", /*#__PURE__*/React.createElement("code", null, "ApiResponseService"), ". List endpoints return", " ", /*#__PURE__*/React.createElement("code", null, "[content, totalElements, page, size]"), " with size capped at 100, sorted newest first."))), /*#__PURE__*/React.createElement("div", {
    id: "lifecycle"
  }, /*#__PURE__*/React.createElement(Feature, {
    num: 4,
    title: "Order lifecycle",
    media: /*#__PURE__*/React.createElement("pre", null, `place()        customer must exist + be ACTIVE
               Book.lock(id)  ← pessimistic lock per item
               stock check → decrement → priceAtPurchase snapshot
               recalculateTotal() → flush (one transaction)

updateStatus() guarded by canTransitionTo()
               → SHIPPED sets shippedAt · DELIVERED sets deliveredAt

cancel()       only where canTransitionTo(CANCELLED)
               restocks every item · appends reason to notes`)
  }, /*#__PURE__*/React.createElement("p", null, "Two invariants to protect when extending: stock is only touched under lock inside a transaction, and status only moves through ", /*#__PURE__*/React.createElement("code", null, "canTransitionTo"), " — never assign ", /*#__PURE__*/React.createElement("code", null, "order.status"), " directly in new code."))), /*#__PURE__*/React.createElement("div", {
    id: "conventions"
  }, /*#__PURE__*/React.createElement(Feature, {
    num: 5,
    title: "Configuration & conventions",
    media: /*#__PURE__*/React.createElement("pre", null, `grails.gorm.failOnError: true   # save() throws on bad data

development   MariaDB · dbCreate: update (auto-creates schema)
test          H2 in-memory · create-drop
production    dbCreate: none · DB_URL / DB_USER / DB_PASSWORD

pool: initialSize 5 · maxActive 50 · MariaDB 10.3 dialect`)
  }, /*#__PURE__*/React.createElement("p", null, "Because of ", /*#__PURE__*/React.createElement("code", null, "failOnError"), ", don't write ", /*#__PURE__*/React.createElement("code", null, "if (!obj.save())"), " ", "checks — catch exceptions at the controller boundary. DTO mapping lives in services, computed display fields follow the ", /*#__PURE__*/React.createElement("code", null, "getFullName()"), " pattern, and destructive operations get guards, not trust."))), /*#__PURE__*/React.createElement(Feature, {
    num: 6,
    title: "Roadmap — verified gaps",
    media: /*#__PURE__*/React.createElement("pre", null, `1. CustomerController + /api/v1/customers   ← unblocks order flow
2. First integration test: place → assert stock decrements,
   cancel → assert restock (pins both invariants)
3. Migration tool before any production deploy
4. Document (or add) authentication`)
  }, /*#__PURE__*/React.createElement("p", null, "These aren't guesses — each was verified against the code: ", /*#__PURE__*/React.createElement("code", null, "src/"), " holds zero test files, exactly four controllers exist, and no migration plugin is declared in ", /*#__PURE__*/React.createElement("code", null, "build.gradle"), ".")))), /*#__PURE__*/React.createElement("div", {
    className: "section soft",
    id: "quickstart"
  }, /*#__PURE__*/React.createElement("div", {
    className: "container"
  }, /*#__PURE__*/React.createElement("div", {
    className: "section-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "micro"
  }, "Get started"), /*#__PURE__*/React.createElement("h2", null, "Run it in two commands")), /*#__PURE__*/React.createElement(SplitCard, null))), /*#__PURE__*/React.createElement("div", {
    className: "section"
  }, /*#__PURE__*/React.createElement("div", {
    className: "container"
  }, /*#__PURE__*/React.createElement("div", {
    className: "section-head"
  }, /*#__PURE__*/React.createElement("span", {
    className: "micro"
  }, "Questions"), /*#__PURE__*/React.createElement("h2", null, "FAQs")), /*#__PURE__*/React.createElement(Faq, null))), /*#__PURE__*/React.createElement(Footer, null));
}
ReactDOM.createRoot(document.getElementById("root")).render(/*#__PURE__*/React.createElement(App, null));