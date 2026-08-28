/* Grails Bookstore documentation — React (JSX via Babel standalone).
   Components mirror the borrowed editorial style: sticky topbar, hero with
   tilted code card, numbered feature sections, checklist split-card, FAQ. */

const { useState } = React;

/* ---------------- Topbar ---------------- */

function Topbar() {
  return (
    <div className="topbar">
      <div className="topbar-inner">
        <a className="wordmark" href="#top">
          bookstore<span className="tick">.</span>api
        </a>
        <div className="topbar-meta">
          <span className="micro">Grails 6.1.2 · JDK 17 · MariaDB</span>
          <a className="btn primary" href="#endpoints">
            API reference
          </a>
        </div>
      </div>
    </div>
  );
}

/* ---------------- Hero ---------------- */

function Hero() {
  return (
    <div className="section soft" id="top">
      <div className="container">
        <div className="hero-content">
          <div className="hero-copy">
            <a className="crumb" href="#architecture">← Project docs</a>
            <h1>Grails Bookstore</h1>
            <p className="lede">A RESTful back-end API for a bookstore. No front-end — every response is JSON.</p>
            <p className="sub">
              Books, authors, categories and orders over HTTP on port 8080. Layered the way
              Grails intends: thin controllers, transactional services, GORM domains with
              real constraints and a guarded order state machine.
            </p>
            <div>
              <a className="btn secondary" href="#quickstart">Get started ↓</a>
            </div>
          </div>
          <div className="hero-card">
            <pre>{`grails-app/
├── conf/         `}<span className="c-dim"># application.yml, UrlMappings</span>{`
├── controllers/  `}<span className="c-dim"># Book, Author, Category, Order</span>{`
├── domain/       `}<span className="c-dim"># 6 entities + 2 enums</span>{`
├── services/     `}<span className="c-dim"># business rules + transactions</span>{`
└── init/

`}<span className="c-accent">$ ./gradlew bootRun</span>{`
Grails application running at http://localhost:8080`}</pre>
          </div>
        </div>
      </div>
    </div>
  );
}

/* ---------------- Numbered feature section ---------------- */

function Feature({ num, title, media, children }) {
  return (
    <div className="feature">
      <div className="feature-media">
        <div className="inner">{media}</div>
      </div>
      <div className="feature-head">
        <div className="feature-title">
          <h3>{title}</h3>
          <p className="feature-num">
            0<b>{num}</b>
          </p>
        </div>
        <div className="feature-body">{children}</div>
      </div>
    </div>
  );
}

/* ---------------- Endpoint table ---------------- */

const ROUTES = [
  ["GET",    "/api/v1/books",                    "List books (standard resource)"],
  ["POST",   "/api/v1/books",                    "Create a book"],
  ["GET",    "/api/v1/books/search",             "Search books"],
  ["GET",    "/api/v1/books/isbn/{isbn}",        "Find by ISBN"],
  ["GET",    "/api/v1/books/low-stock",          "Books running low on stock"],
  ["GET",    "/api/v1/authors",                  "Authors resource (full CRUD)"],
  ["GET",    "/api/v1/categories",               "Categories resource — delete guarded when books exist"],
  ["GET",    "/api/v1/orders",                   "Orders resource — DELETE deliberately excluded"],
  ["POST",   "/api/v1/orders/{id}/cancel",       "Cancel an order (restocks items)"],
  ["GET",    "/api/v1/orders/customer/{id}",     "Orders for a customer (paginated)"],
  ["GET",    "/api/v1/orders/status/{status}",   "Orders by status (paginated)"],
  ["PATCH",  "/api/v1/orders/{id}/status",       "Move order through the state machine"],
];

function EndpointsTable() {
  return (
    <table>
      <thead>
        <tr><th>Method</th><th>Route</th><th>Purpose</th></tr>
      </thead>
      <tbody>
        {ROUTES.map(([m, path, desc]) => (
          <tr key={m + path}>
            <td><span className={"method " + m.toLowerCase()}>{m}</span></td>
            <td><code>{path}</code></td>
            <td>{desc}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/* ---------------- Checklist split card ---------------- */

const REQUIREMENTS = [
  "JDK 17 and the bundled Gradle wrapper (7.6.4) — no global installs needed",
  "MariaDB running locally for dev (the schema auto-creates via dbCreate: update)",
  "Nothing else — H2 in-memory covers the test environment",
];

const HIGHLIGHTS = [
  "Pessimistic locking on order placement — concurrent orders can't oversell a book",
  "Guarded state machine: status only moves through canTransitionTo()",
  "priceAtPurchase snapshots — history survives later price changes",
  "One JSON envelope everywhere via ApiResponseService, pagination capped at 100",
];

function SplitCard() {
  return (
    <div className="split-card">
      <div className="split-col">
        <h3>All you need to run it:</h3>
        {REQUIREMENTS.map((text) => (
          <div className="check-item" key={text}>
            <span className="dot" />
            <p>{text}</p>
          </div>
        ))}
      </div>
      <div className="split-col">
        <h3>What the code gets right:</h3>
        {HIGHLIGHTS.map((text) => (
          <div className="check-item" key={text}>
            <span className="dot green" />
            <p>{text}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

/* ---------------- FAQ accordion ---------------- */

const FAQS = [
  {
    q: "How do I run it locally?",
    a: (
      <p>
        Start MariaDB, then run <code>./gradlew bootRun</code> from the project root. The dev
        dataSource uses <code>dbCreate: update</code> with <code>createDatabaseIfNotExist</code>,
        so the <code>bookstore_db</code> schema appears on first boot. The API listens on port 8080.
      </p>
    ),
  },
  {
    q: "Why is there no DELETE route for orders?",
    a: (
      <p>
        Cancellation is a business action, not a row deletion. The mapping excludes DELETE and
        exposes <code>POST /api/v1/orders/{"{id}"}/cancel</code> instead — it validates the state
        transition, restocks every item, and appends the cancellation reason to the order notes.
      </p>
    ),
  },
  {
    q: "How is stock protected from race conditions?",
    a: (
      <p>
        <code>OrderService.place()</code> takes a pessimistic lock per book
        (<code>Book.lock(bookId)</code>) inside a single transaction. Insufficient stock rejects
        the whole order, and any failure rolls back both the stock decrement and the order row.
        Any new code path that touches <code>stockQuantity</code> must follow the same pattern.
      </p>
    ),
  },
  {
    q: "What are the environments?",
    a: (
      <p>
        Development: MariaDB with <code>dbCreate: update</code>. Test: H2 in-memory with
        <code> create-drop</code>. Production: <code>dbCreate: none</code> — migrations must run
        explicitly, with credentials injected via <code>DB_URL</code>, <code>DB_USER</code> and
        <code> DB_PASSWORD</code> environment variables.
      </p>
    ),
  },
  {
    q: "What's known to be missing?",
    a: (
      <div>
        <p>
          Verified gaps, in priority order: zero tests (the H2 test environment is configured but
          <code> src/</code> holds only empty skeletons), no CustomerController — so there's no API
          path to create the customer an order depends on — no authentication, and no migration
          tooling despite production requiring it.
        </p>
        <p>
          The suggested first fix is a CustomerController following the existing conventions,
          which unblocks the full order flow end to end.
        </p>
      </div>
    ),
  },
];

function FaqItem({ q, a }) {
  const [open, setOpen] = useState(false);
  return (
    <div className={"faq-item" + (open ? " open" : "")} onClick={() => setOpen(!open)}>
      <div className="faq-q">
        <p>{q}</p>
        <span className="faq-toggle" />
      </div>
      <div className="faq-a">{a}</div>
    </div>
  );
}

function Faq() {
  return (
    <div className="faq">
      {FAQS.map((item) => (
        <FaqItem key={item.q} q={item.q} a={item.a} />
      ))}
    </div>
  );
}

/* ---------------- Footer ---------------- */

function Footer() {
  return (
    <footer>
      <div className="footer-inner">
        <div className="footer-cols">
          <div className="footer-col">
            <span className="micro">Docs</span>
            <a href="#architecture">Architecture</a>
            <a href="#domain">Domain model</a>
            <a href="#endpoints">API reference</a>
            <a href="#lifecycle">Order lifecycle</a>
            <a href="#conventions">Conventions</a>
          </div>
          <div className="footer-col">
            <span className="micro">Source</span>
            <a href="#top">grails-app/controllers</a>
            <a href="#top">grails-app/services</a>
            <a href="#top">grails-app/domain</a>
            <a href="#top">docs/ (HLD · LLD · walkthrough)</a>
          </div>
        </div>
        <div>
          <div className="footer-brand">
            bookstore<span className="tick">.</span>api
          </div>
        </div>
        <div className="footer-bottom">
          <span>A project-based-learning build — Grails 6.1.2 · Spring Boot 2.7 · GORM</span>
          <span>Docs generated from the project knowledge base</span>
        </div>
      </div>
    </footer>
  );
}

/* ---------------- Page ---------------- */

function App() {
  return (
    <React.Fragment>
      <Topbar />
      <Hero />

      <div className="section card-bg" id="architecture">
        <div className="container">
          <div className="section-head">
            <span className="micro">The system</span>
            <h2>What's inside the bookstore</h2>
            <p>
              Six sections, one per layer of the system — each with the code-level detail
              and the rule worth remembering when you extend it.
            </p>
          </div>

          <Feature
            num={1}
            title="Layered architecture, actually enforced"
            media={
              <pre>{`Request → Controller → Service → Domain (GORM) → MariaDB / H2

Controller   parses HTTP, calls a service, renders JSON. No logic.
Service      owns all business rules + transactions.
Domain       shape, constraints, relationships, state machine.`}</pre>
            }
          >
            <p>
              Services default to <code>@Transactional(readOnly = true)</code> at class level with
              <code> @Transactional</code> only on mutating methods — copy <code>OrderService</code>{" "}
              when adding a new one. To change a route, edit UrlMappings; a rule, the service;
              validation, the domain class.
            </p>
          </Feature>

          <Feature
            num={2}
            title="The domain model"
            media={
              <pre>{`Category ──< Book >── Author      (join table: book_author)
              │
              ▼ (via OrderItem)
Customer ──< BookOrder ──< OrderItem ──> Book

Book      isbn unique · price 0.01–9999.99 · stock min 0
Customer  email unique · status defaults ACTIVE
BookOrder status defaults PENDING · belongsTo Customer`}</pre>
            }
          >
            <p>
              Six entities and two enums, all with GORM-managed <code>dateCreated</code> /
              <code> lastUpdated</code>. Category deletion is guarded: the controller returns
              HTTP 400 if any book still references it. <code>OrderItem</code> records
              <code> priceAtPurchase</code> so order history survives price changes.
            </p>
          </Feature>

          <div id="endpoints">
            <Feature num={3} title="API reference" media={<EndpointsTable />}>
              <p>
                Every response uses one envelope:{" "}
                <code>{"{ success, message, data, timestamp }"}</code> on success and{" "}
                <code>{"{ success, message, error, timestamp }"}</code> on failure, rendered
                through <code>ApiResponseService</code>. List endpoints return{" "}
                <code>{"[content, totalElements, page, size]"}</code> with size capped at 100,
                sorted newest first.
              </p>
            </Feature>
          </div>

          <div id="lifecycle">
            <Feature
              num={4}
              title="Order lifecycle"
              media={
                <pre>{`place()        customer must exist + be ACTIVE
               Book.lock(id)  ← pessimistic lock per item
               stock check → decrement → priceAtPurchase snapshot
               recalculateTotal() → flush (one transaction)

updateStatus() guarded by canTransitionTo()
               → SHIPPED sets shippedAt · DELIVERED sets deliveredAt

cancel()       only where canTransitionTo(CANCELLED)
               restocks every item · appends reason to notes`}</pre>
              }
            >
              <p>
                Two invariants to protect when extending: stock is only touched under lock inside
                a transaction, and status only moves through <code>canTransitionTo</code> — never
                assign <code>order.status</code> directly in new code.
              </p>
            </Feature>
          </div>

          <div id="conventions">
            <Feature
              num={5}
              title="Configuration & conventions"
              media={
                <pre>{`grails.gorm.failOnError: true   # save() throws on bad data

development   MariaDB · dbCreate: update (auto-creates schema)
test          H2 in-memory · create-drop
production    dbCreate: none · DB_URL / DB_USER / DB_PASSWORD

pool: initialSize 5 · maxActive 50 · MariaDB 10.3 dialect`}</pre>
              }
            >
              <p>
                Because of <code>failOnError</code>, don't write <code>if (!obj.save())</code>{" "}
                checks — catch exceptions at the controller boundary. DTO mapping lives in
                services, computed display fields follow the <code>getFullName()</code> pattern,
                and destructive operations get guards, not trust.
              </p>
            </Feature>
          </div>

          <Feature
            num={6}
            title="Roadmap — verified gaps"
            media={
              <pre>{`1. CustomerController + /api/v1/customers   ← unblocks order flow
2. First integration test: place → assert stock decrements,
   cancel → assert restock (pins both invariants)
3. Migration tool before any production deploy
4. Document (or add) authentication`}</pre>
            }
          >
            <p>
              These aren't guesses — each was verified against the code: <code>src/</code> holds
              zero test files, exactly four controllers exist, and no migration plugin is
              declared in <code>build.gradle</code>.
            </p>
          </Feature>
        </div>
      </div>

      <div className="section soft" id="quickstart">
        <div className="container">
          <div className="section-head">
            <span className="micro">Get started</span>
            <h2>Run it in two commands</h2>
          </div>
          <SplitCard />
        </div>
      </div>

      <div className="section">
        <div className="container">
          <div className="section-head">
            <span className="micro">Questions</span>
            <h2>FAQs</h2>
          </div>
          <Faq />
        </div>
      </div>

      <Footer />
    </React.Fragment>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
