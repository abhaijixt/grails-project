# Code Walkthrough — Grails Bookstore

This document walks through every source file in the project in reading order — from the bottom of the stack (domain) upward to the top (controllers). For each file, the full code is explained line by line where it matters.

---

## Reading Order

```
1. gradle.properties          ← JVM and Grails version pins
2. build.gradle               ← Dependencies and plugins
3. application.yml            ← Database configuration
4. UrlMappings.groovy         ← All REST routes in one place
5. Domain classes             ← Data model and rules
   5a. OrderStatus.groovy     ← State machine enum
   5b. CustomerStatus.groovy
   5c. Category.groovy
   5d. Author.groovy
   5e. Customer.groovy
   5f. Book.groovy
   5g. OrderItem.groovy
   5h. BookOrder.groovy
6. Services                   ← Business logic
   6a. ApiResponseService.groovy
   6b. AuthorService.groovy
   6c. BookService.groovy
   6d. OrderService.groovy
7. Controllers                ← HTTP layer
   7a. AuthorController.groovy
   7b. CategoryController.groovy
   7c. BookController.groovy
   7d. OrderController.groovy
8. Application.groovy         ← Entry point
```

---

## 1. `gradle.properties`

```properties
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk   # Force Gradle to use Java 17
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8  # 2 GB heap for the build daemon

grailsVersion=6.1.2           # Tells the Grails Gradle plugin which Grails BOM to use
gormVersion=8.1.1             # GORM version — controls hibernate plugin version resolution
grailsGradlePluginVersion=6.1.2
```

**Why Java 17 specifically?** Gradle 7.6.4 (which this project uses) officially supports Java 8–19. The system JDK is Java 26, which Gradle 7.x cannot run on. Pinning to Java 17 here makes `./gradlew` always use the right JDK regardless of the shell's JAVA_HOME.

---

## 2. `build.gradle`

```groovy
buildscript {
    repositories {
        mavenCentral()
        maven { url "https://repo.grails.org/grails/core" }   // Grails artifacts live here
    }
    dependencies {
        // These three plugins wire up the entire Grails build pipeline
        classpath "org.grails:grails-gradle-plugin:6.1.2"
        classpath "com.bertramlabs.plugins:asset-pipeline-gradle:4.3.0"
        classpath "org.springframework.boot:spring-boot-gradle-plugin:2.7.18"
    }
}
```

The `buildscript` block is evaluated before the rest of `build.gradle`. The classpath entries here are the Gradle plugins themselves — not the application's runtime dependencies.

```groovy
configurations {
    profile    // Grails profile — declares this is a 'web' application
    console    // Grails console (REPL) classpath
    // Note: 'developmentOnly' and 'runtimeClasspath' are intentionally NOT declared here.
    // Spring Boot's Gradle plugin creates them automatically.
    // Declaring them here again causes a "configuration already exists" build error.
}
```

```groovy
apply plugin: "org.grails.grails-web"   // Adds Groovy compilation, GORM, Spring MVC wiring
apply plugin: "com.bertramlabs.asset-pipeline"  // Static asset compilation (CSS/JS)
apply plugin: "war"                     // Packages as WAR file for servlet container deployment
```

```groovy
dependencies {
    profile "org.grails.profiles:web"                         // Grails web profile BOM

    implementation "org.grails:grails-core"                   // Core Grails runtime
    implementation "org.grails:grails-plugin-rest"            // REST response rendering
    implementation "org.grails.plugins:hibernate5"            // GORM-Hibernate5 bridge
    implementation "org.hibernate:hibernate-core:5.6.15.Final"// JPA provider

    runtimeOnly "org.mariadb.jdbc:mariadb-java-client:3.3.3"  // MariaDB JDBC driver
    runtimeOnly "com.h2database:h2"                           // H2 for test environment
    runtimeOnly "org.apache.tomcat:tomcat-jdbc"               // Connection pool

    testImplementation "org.grails:grails-gorm-testing-support"
    testImplementation "org.grails:grails-web-testing-support"
}
```

```groovy
bootRun {
    jvmArgs("-Dspring.output.ansi.enabled=always", "-noverify", "-XX:TieredStopAtLevel=1")
    // -noverify and TieredStopAtLevel=1 make startup faster during development
    sourceResources sourceSets.main  // Allows hot-reload of resources without recompile
}
```

---

## 3. `application.yml`

```yaml
grails:
  gorm:
    failOnError: true   # GORM throws exception on save failure instead of returning false
                        # Without this, book.save() silently does nothing on validation failure
```

```yaml
environments:
  development:
    dataSource:
      dbCreate: update   # Hibernate auto-alters schema to match domain classes
                         # Safe for dev; NEVER use in production (data loss risk)
      url: jdbc:mariadb://localhost:3306/bookstore_db?createDatabaseIfNotExist=true&...
      username: developer
      password: dev_password_123
  test:
    dataSource:
      dbCreate: create-drop   # Creates schema before tests, drops after — clean slate
      url: jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE
      driverClassName: org.h2.Driver
      username: sa
      password: ""
      dialect: org.hibernate.dialect.H2Dialect
  production:
    dataSource:
      dbCreate: none          # Schema is NOT touched — migrations run separately
      url: ${DB_URL:jdbc:mariadb://localhost:3306/bookstore_db?...}
      username: ${DB_USER:developer}
      password: ${DB_PASSWORD}    # No default — must be set in environment

dataSource:               # Shared defaults applied to all environments
  pooled: true
  jmxExport: true
  driverClassName: org.mariadb.jdbc.Driver
  dialect: org.hibernate.dialect.MariaDB103Dialect
  properties:
    initialSize: 5        # Connection pool opens 5 connections on startup
    maxActive: 50         # Maximum concurrent connections
```

**How `${DB_URL:default}` works:** This is Spring's `@Value`-style property substitution. If the environment variable `DB_URL` is set, it uses that. If not, it falls back to the default after the colon. In production, `DB_PASSWORD` has no default — the application will fail to start if the environment variable is missing, which is intentional (fail fast, not silently).

---

## 4. `UrlMappings.groovy`

```groovy
class UrlMappings {
    static mappings = {

        // resources: 'book' generates 7 routes in total:
        //   GET    /api/v1/books           → index
        //   POST   /api/v1/books           → save
        //   GET    /api/v1/books/{id}      → show
        //   PUT    /api/v1/books/{id}      → update  (PATCH also maps here)
        //   DELETE /api/v1/books/{id}      → delete
        //   GET    /api/v1/books/create    → create  (browser-form scaffold, unused here)
        //   GET    /api/v1/books/{id}/edit → edit    (browser-form scaffold, unused here)
        // The create and edit routes exist but are irrelevant in a JSON-only API
        // because static responseFormats = ['json'] on the controller rejects HTML requests.
        "/api/v1/books"(resources: 'book')

        // Custom routes that the 'resources' shorthand doesn't cover
        "/api/v1/books/search"(controller: "book", action: "search", method: "GET")
        "/api/v1/books/isbn/$isbn"(controller: "book", action: "findByIsbn", method: "GET")
        // $isbn becomes params.isbn inside the controller action
        "/api/v1/books/low-stock"(controller: "book", action: "lowStock", method: "GET")

        "/api/v1/authors"(resources: 'author')     // full CRUD
        "/api/v1/categories"(resources: 'category') // full CRUD

        // Orders: excludes: ['delete'] means DELETE /api/v1/orders/{id} is not routed.
        // Cancellation is an explicit business action, not a generic HTTP DELETE.
        "/api/v1/orders"(resources: 'order', excludes: ['delete'])
        "/api/v1/orders/$id/cancel"(controller: "order", action: "cancel", method: "POST")
        "/api/v1/orders/customer/$customerId"(controller: "order", action: "byCustomer", method: "GET")
        "/api/v1/orders/status/$status"(controller: "order", action: "byStatus", method: "GET")
        "/api/v1/orders/$id/status"(controller: "order", action: "updateStatus", method: "PATCH")

        "500"(view: '/error')     // Grails renders error.gsp for 500s
        "404"(view: '/notFound')  // Grails renders notFound.gsp for 404s
    }
}
```

**Why centralised routing matters:** In Spring Boot, every controller method has its own `@GetMapping`/`@PostMapping` annotation. In Grails, all routes are in this one file. This makes it trivial to audit every endpoint the application exposes.

---

## 5. Domain Classes

### 5a. `OrderStatus.groovy` — State Machine Enum

```groovy
enum OrderStatus {
    PENDING, CONFIRMED, PROCESSING, SHIPPED, DELIVERED, CANCELLED, REFUNDED

    boolean canTransitionTo(OrderStatus target) {
        switch (this) {
            case PENDING:    return target in [CONFIRMED, CANCELLED]
            case CONFIRMED:  return target in [PROCESSING, CANCELLED]
            case PROCESSING: return target == SHIPPED
            case SHIPPED:    return target == DELIVERED
            case DELIVERED:  return target == REFUNDED
            default:         return false  // CANCELLED and REFUNDED are terminal
        }
    }
}
```

`target in [CONFIRMED, CANCELLED]` is Groovy's `in` operator — equivalent to `Arrays.asList(CONFIRMED, CANCELLED).contains(target)` in Java. It reads naturally.

The `default: return false` means CANCELLED and REFUNDED can never transition to anything — they are terminal states.

This method is called in two places:
1. `OrderService.updateStatus()` — for normal status advancement
2. `OrderService.cancel()` — checks if CANCELLED is reachable from current state

---

### 5b. `CustomerStatus.groovy`

```groovy
enum CustomerStatus { ACTIVE, INACTIVE, SUSPENDED }
```

No state machine here — status changes are administrative and not enforced by the application. Only the `ACTIVE` value is checked in the business logic (`OrderService.place()`).

---

### 5c. `Category.groovy`

```groovy
class Category {
    String name
    String description
    Date dateCreated    // GORM sets this on insert automatically
    Date lastUpdated    // GORM sets this on every update automatically

    static hasMany = [books: Book]
    // hasMany creates a Set<Book> property. GORM adds a books collection
    // and generates the FK on the book side (book.category_id).

    static constraints = {
        name blank: false, unique: true, size: 2..100
        // blank: false  → rejects empty strings (not the same as nullable: false)
        // unique: true  → GORM adds a UNIQUE constraint to the column AND validates at app level
        // size: 2..100  → Groovy range; min 2, max 100 characters
        description nullable: true, maxSize: 500
        // nullable: true is the default, but explicit here for clarity
    }
}
```

`dateCreated` and `lastUpdated` are a Grails naming convention — any field with exactly these names is managed automatically by GORM. You never call `setDateCreated()` yourself.

---

### 5d. `Author.groovy`

```groovy
class Author {
    String firstName
    String lastName
    String email
    Date birthDate
    String bio
    Date dateCreated
    Date lastUpdated

    static hasMany = [books: Book]
    // This is the inverse side of the Book<->Author many-to-many.
    // The join table (book_author) is owned by Book because Book declares belongsTo.

    static constraints = {
        firstName blank: false, maxSize: 100
        lastName  blank: false, maxSize: 100
        email     blank: false, email: true, unique: true
        // email: true  → validates format using a regex (not just presence)
        birthDate nullable: true
        bio       nullable: true, maxSize: 1000
    }

    String getFullName() {
        "${firstName} ${lastName}"
        // Groovy implicit return — last expression is returned
        // Callable as author.fullName (Groovy property access)
    }
}
```

---

### 5e. `Customer.groovy`

```groovy
class Customer {
    String firstName
    String lastName
    String email
    String phone
    String address
    CustomerStatus status = CustomerStatus.ACTIVE  // Default value on new instances
    Date dateCreated
    Date lastUpdated

    static hasMany = [orders: BookOrder]

    static constraints = {
        firstName blank: false, maxSize: 100
        lastName  blank: false, maxSize: 100
        email     blank: false, unique: true, email: true
        phone     nullable: true, matches: /^\+?[0-9]{10,15}$/
        // matches: uses a Groovy regex. The anchors (^ and $) ensure the entire
        // string must match, not just a substring.
        // Without anchors, "abc+1234567890xyz" would pass.
        address   nullable: true, maxSize: 500
        status    nullable: false
    }

    String getFullName() { "${firstName} ${lastName}" }
}
```

---

### 5f. `Book.groovy`

```groovy
class Book {
    String title
    String isbn
    BigDecimal price          // BigDecimal, not Double — avoids floating-point rounding errors on money
    Integer stockQuantity
    Date publicationDate
    String description
    String coverImageUrl
    Date dateCreated
    Date lastUpdated

    Category category         // Explicit field for the FK — required by belongsTo

    static belongsTo = [category: Category]
    // belongsTo has two effects:
    // 1. Cascades: when a Category is deleted, its Books are deleted too
    // 2. Ownership: the FK column (category_id) lives on the book table

    static hasMany = [authors: Author, orderItems: OrderItem]
    // authors: join table book_author
    //   Book is the owning side of this ManyToMany because Book declares hasMany[authors]
    //   and Author's hasMany[books] is the inverse side. GORM names the join table
    //   book_author from both class names (alphabetical order). Note: Book's belongsTo
    //   is for the Category relationship only — it has no effect on Author ownership.
    // orderItems: FK order_item.book_id

    static mapping = {
        description type: "text"
        // Without this, GORM would map description to VARCHAR(2000) on most databases.
        // "text" maps to MySQL/MariaDB TEXT column — up to 65,535 bytes.
    }

    static constraints = {
        title          blank: false, size: 1..300
        isbn           blank: false, unique: true, maxSize: 20
        price          nullable: false, min: new BigDecimal("0.01"), max: new BigDecimal("9999.99")
        stockQuantity  min: 0          // Stock cannot go below zero (via validation)
        publicationDate nullable: true
        description    nullable: true, maxSize: 2000
        coverImageUrl  nullable: true, maxSize: 500
        category       nullable: false
    }
}
```

---

### 5g. `OrderItem.groovy`

```groovy
class OrderItem {
    BookOrder order
    Book book
    Integer quantity
    BigDecimal priceAtPurchase   // Snapshot — never changes after creation
    Date dateCreated

    static belongsTo = [order: BookOrder]
    // Cascade: when a BookOrder is deleted, its OrderItems are deleted

    static constraints = {
        order          nullable: false
        book           nullable: false
        quantity       min: 1           // Must buy at least one
        priceAtPurchase nullable: false, min: BigDecimal.ZERO
    }

    BigDecimal getSubtotal() {
        (priceAtPurchase ?: BigDecimal.ZERO) * (quantity ?: 0)
        // ?: is the Elvis operator — returns left side if non-null, otherwise right side
        // Defensive but quantity and priceAtPurchase are never null in practice
    }
}
```

**Why `priceAtPurchase`?** If a book's price changes after an order is placed, the order should still show the original price. `priceAtPurchase` is set to `book.price` at the moment the `OrderItem` is created and never updated afterwards.

---

### 5h. `BookOrder.groovy`

```groovy
class BookOrder {
    Customer customer
    OrderStatus status = OrderStatus.PENDING   // Every new order starts as PENDING
    BigDecimal totalAmount = BigDecimal.ZERO   // Recalculated after items are added
    String shippingAddress
    String notes
    Date shippedAt      // Null until status transitions to SHIPPED
    Date deliveredAt    // Null until status transitions to DELIVERED
    Date dateCreated
    Date lastUpdated

    static belongsTo = [customer: Customer]
    // If a Customer is deleted, their BookOrders are cascaded deleted too

    static hasMany = [orderItems: OrderItem]

    static constraints = {
        customer        nullable: false
        status          nullable: false
        totalAmount     nullable: false, min: BigDecimal.ZERO
        shippingAddress nullable: true, maxSize: 500
        notes           nullable: true, maxSize: 1000
        shippedAt       nullable: true
        deliveredAt     nullable: true
    }

    void recalculateTotal() {
        totalAmount = orderItems?.collect { it.subtotal }?.sum() ?: BigDecimal.ZERO
        // orderItems?.   → safe navigation: returns null if orderItems is null
        // .collect { }   → Groovy equivalent of Java Stream.map()
        // .sum()         → Groovy extension method on Collection<Number>
        // ?: BigDecimal.ZERO → Elvis: if sum() returns null (empty collection), use zero
    }
}
```

---

## 6. Services

### 6a. `ApiResponseService.groovy`

```groovy
import grails.converters.JSON

class ApiResponseService {
    JSON success(Object data, String message = "Success") {
        // Default parameter value: message = "Success" means callers can omit it
        [
            success  : true,
            message  : message,
            data     : data,
            timestamp: new Date()
        ] as JSON
        // Groovy map literal cast to Grails JSON object
        // 'as JSON' triggers Grails' JSON converter — the map becomes a JSON string
    }

    JSON error(String error, String message = "Error") {
        [
            success  : false,
            message  : message,
            error    : error,
            timestamp: new Date()
        ] as JSON
    }
}
```

This service is a utility wrapper — it ensures every response, successful or not, has the same JSON envelope shape. Every controller injects it and routes all rendering through it.

---

### 6b. `AuthorService.groovy`

```groovy
import grails.gorm.transactions.Transactional

@Transactional(readOnly = true)
// Class-level: every method is read-only by default.
// Read-only transactions are faster — Hibernate skips dirty checking on flush.
class AuthorService {

    List<Map> list() {
        Author.list(sort: "lastName", order: "asc").collect { toDto(it) }
        // Author.list() — GORM static method, returns all Author records
        // .collect { toDto(it) } — transforms each Author to a Map
        // 'it' is Groovy's implicit single-argument closure parameter
    }

    Author getById(Long id) {
        Author.get(id)   // Returns null if not found — controllers check for null
    }

    @Transactional   // Overrides readOnly=true for this write method
    Author create(Map payload) {
        if (Author.findByEmail(payload.email as String)) {
            throw new IllegalArgumentException("An author with email '${payload.email}' already exists")
        }
        // GString interpolation: ${payload.email} embeds the value in the string
        Author author = new Author(payload)
        // Named-parameter constructor: Groovy passes the Map as property assignments
        // Equivalent to: author.firstName = payload.firstName; author.email = payload.email; ...
        if (!author.validate()) {
            throw new IllegalArgumentException(author.errors.allErrors*.defaultMessage.join(", "))
            // .allErrors   → list of ObjectError objects from Spring Validation
            // *. spread operator → calls .defaultMessage on every element in the list
            // .join(", ")  → concatenates with comma separator
        }
        author.save(flush: true, failOnError: true)
        // flush: true → immediately writes to DB (not deferred to transaction commit)
        // failOnError: true → throws instead of returning false on validation failure
        author
    }

    @Transactional
    Author update(Long id, Map payload) {
        Author author = Author.get(id)
        if (!author) return null   // Controller will render 404

        if (payload.email && payload.email != author.email
                          && Author.findByEmail(payload.email as String)) {
            throw new IllegalArgumentException("Email '${payload.email}' is already in use")
            // Three-part check:
            // 1. payload.email — only check if email is being changed
            // 2. payload.email != author.email — exclude the current author's own email
            // 3. Author.findByEmail(...) — check for actual duplicates
        }
        author.properties = payload   // Mass-assignment: updates all matching fields
        if (!author.validate()) {
            throw new IllegalArgumentException(author.errors.allErrors*.defaultMessage.join(", "))
        }
        author.save(flush: true, failOnError: true)
        author
    }

    @Transactional
    void delete(Long id) {
        Author author = Author.get(id)
        if (author) {
            author.delete(flush: true)
        }
        // No exception if not found — controller checks existence before calling
    }

    Map toDto(Author author) {
        [
            id       : author.id,
            fullName : author.fullName,   // calls getFullName()
            firstName: author.firstName,
            lastName : author.lastName,
            email    : author.email,
            birthDate: author.birthDate,
            bio      : author.bio,
            createdAt: author.dateCreated
        ]
    }
}
```

---

### 6c. `BookService.groovy`

```groovy
@Transactional(readOnly = true)
class BookService {

    Map list(Map params) {
        Integer max = Math.min((params.int('size') ?: 10), 100)
        // params.int('size') → safe coercion of the 'size' query param to Integer
        // ?: 10 → default to 10 if size not provided
        // Math.min(..., 100) → cap at 100 to prevent large queries
        Integer offset = (params.int('page') ?: 0) * max
        String sortBy  = params.sortBy ?: "title"
        String sortDir = params.sortDir ?: "asc"

        def books = Book.createCriteria().list(max: max, offset: offset, sort: sortBy, order: sortDir) { }
        // createCriteria().list() — Grails criteria API (Hibernate Criteria under the hood)
        // The closure {} is where filter conditions go (empty here = no filter)
        // Returns a PagedResultList with totalCount property

        [content: books.collect { toDto(it) }, totalElements: books.totalCount,
         page: (params.int('page') ?: 0), size: max]
    }

    Map search(Map params) {
        Integer max    = Math.min((params.int('size') ?: 10), 100)
        Integer offset = (params.int('page') ?: 0) * max

        def books = Book.createCriteria().list(max: max, offset: offset, sort: "title", order: "asc") {
            if (params.title) {
                ilike("title", "%${params.title}%")
                // ilike = case-insensitive LIKE — GORM criteria method
                // Equivalent SQL: WHERE LOWER(title) LIKE LOWER('%groovy%')
            }
            if (params.categoryId) {
                eq("category", Category.get(params.long('categoryId')))
                // eq = equality — matches exact category object
            }
            if (params.minPrice) {
                ge("price", params.bigDecimal('minPrice'))
                // ge = greater than or equal (>=)
            }
            if (params.maxPrice) {
                le("price", params.bigDecimal('maxPrice'))
                // le = less than or equal (<=)
            }
            if (params.inStock == 'true') {
                gt("stockQuantity", 0)
                // gt = greater than (>) — only books with stock > 0
            }
        }
        [content: books.collect { toDto(it) }, totalElements: books.totalCount,
         page: (params.int('page') ?: 0), size: max]
    }

    @Transactional
    Book create(Map payload) {
        if (Book.findByIsbn(payload.isbn)) {
            throw new IllegalArgumentException("Book with ISBN ${payload.isbn} already exists")
        }
        Category category = Category.get(payload.categoryId as Long)
        // 'as Long' — Groovy cast; payload values are strings from JSON, cast to Long
        if (!category) throw new IllegalArgumentException("Category not found")

        Book book = new Book(payload)
        // Named-parameter constructor — GORM mass-assigns all matching fields from the Map
        // 'authorIds' and 'categoryId' are in the payload but not Book fields — they're ignored
        book.category = category
        book.save(failOnError: true)   // Save first to get the book's ID

        List<Long> authorIds = (payload.authorIds ?: []) as List<Long>
        authorIds.each { id ->
            def author = Author.get(id)
            if (author) {
                book.addToAuthors(author)
                // addToAuthors() — GORM-generated method from 'hasMany = [authors: Author]'
                // Inserts a row in the book_author join table
            }
        }
        book.save(flush: true, failOnError: true)
        book   // implicit return
    }

    @Transactional
    Book update(Long id, Map payload) {
        Book book = Book.get(id)
        if (!book) return null

        // ISBN uniqueness check that excludes the current book's own ISBN
        if (payload.isbn && payload.isbn != book.isbn && Book.findByIsbn(payload.isbn)) {
            throw new IllegalArgumentException("ISBN ${payload.isbn} is already in use")
        }
        if (payload.categoryId) {
            def category = Category.get(payload.categoryId as Long)
            if (!category) throw new IllegalArgumentException("Category not found")
            book.category = category
        }

        book.properties = payload.findAll { k, _ -> k != "authorIds" && k != "categoryId" }
        // payload.findAll { k, _ -> } — filters the map, excluding keys we handle separately
        // book.properties = map — GORM mass-assignment; updates all matching fields

        if (payload.containsKey('authorIds')) {
            // Only rebuild authors if the client explicitly sent 'authorIds'.
            // A PATCH that only updates 'price' should not clear the author list.
            book.authors?.clear()
            (payload.authorIds ?: []).each { authorId ->
                def author = Author.get(authorId as Long)
                if (author) book.addToAuthors(author)
            }
        }
        book.save(flush: true, failOnError: true)
        book
    }

    @Transactional
    void delete(Long id) {
        Book book = Book.get(id)
        if (!book) return
        if (OrderItem.countByBook(book) > 0) {
            throw new IllegalArgumentException(
                "Cannot delete book '${book.title}' because it has associated orders")
            // Business rule: books referenced in orders cannot be deleted.
            // The order history must be preserved.
        }
        book.delete(flush: true)
    }

    List<Map> lowStock(Integer threshold = 5) {
        Book.findAllByStockQuantityLessThanEquals(threshold ?: 5).collect { toDto(it) }
        // GORM dynamic finder: findAllBy + FieldName + Comparator
        // LessThanEquals → WHERE stock_quantity <= threshold
    }

    Map toDto(Book book) {
        [
            id             : book.id,
            title          : book.title,
            isbn           : book.isbn,
            price          : book.price,
            stockQuantity  : book.stockQuantity,
            publicationDate: book.publicationDate,
            description    : book.description,
            coverImageUrl  : book.coverImageUrl,
            categoryId     : book.category?.id,       // ?. safe navigation — null if no category
            categoryName   : book.category?.name,
            authors        : (book.authors ?: []).collect {
                [id: it.id, fullName: it.fullName, email: it.email]
            },
            createdAt      : book.dateCreated
        ]
    }
}
```

---

### 6d. `OrderService.groovy`

```groovy
@Transactional(readOnly = true)
class OrderService {

    @Transactional
    BookOrder place(Map payload) {
        Customer customer = Customer.get(payload.customerId as Long)
        if (!customer) throw new IllegalArgumentException("Customer not found")
        if (customer.status != CustomerStatus.ACTIVE) {
            throw new IllegalArgumentException("Customer is not active")
        }

        BookOrder order = new BookOrder(
            customer       : customer,
            shippingAddress: payload.shippingAddress,
            notes          : payload.notes
        )
        // Named constructor with explicit fields (not mass-assignment) because
        // the payload contains 'items' which is not a BookOrder field
        order.save(failOnError: true)
        // Save the order first to get its ID, which OrderItems need as their FK

        (payload.items ?: []).each { item ->
            Book book = Book.lock(item.bookId as Long)
            // Book.lock(id) → issues SELECT * FROM book WHERE id = ? FOR UPDATE
            // The FOR UPDATE clause locks the row in the database.
            // Any other transaction trying to lock the same row will BLOCK here
            // until this transaction commits or rolls back.
            // This prevents two simultaneous orders from both reading stock=5,
            // both passing the check, and both decrementing — resulting in stock=-5.

            if (!book) throw new IllegalArgumentException("Book not found: ${item.bookId}")
            Integer qty = item.quantity as Integer
            if (book.stockQuantity < qty) {
                throw new IllegalArgumentException("Insufficient stock for '${book.title}'")
                // Exception thrown mid-transaction → @Transactional rolls back everything:
                // the order save, any stock decrements already done in this loop
            }
            book.stockQuantity = book.stockQuantity - qty
            book.save(failOnError: true)

            order.addToOrderItems(new OrderItem(
                order          : order,
                book           : book,
                quantity       : qty,
                priceAtPurchase: book.price   // Snapshot — frozen at this moment
            ))
        }
        order.recalculateTotal()   // Sum all orderItem subtotals
        order.save(flush: true, failOnError: true)
        order
    }

    Map customerOrders(Long customerId, Integer page = 0, Integer size = 10) {
        Integer max    = Math.min(size ?: 10, 100)
        Integer offset = (page ?: 0) * max
        def orders = BookOrder.findAllByCustomer(
            Customer.get(customerId),
            [max: max, offset: offset, sort: "dateCreated", order: "desc"]
        )
        // findAllByCustomer — GORM dynamic finder
        // The second argument is a map of query options (pagination, sorting)
        Integer total = BookOrder.countByCustomer(Customer.get(customerId))
        [content: orders.collect { toDto(it) }, totalElements: total, page: page, size: max]
    }

    Map byStatus(OrderStatus status, Integer page = 0, Integer size = 10) {
        Integer max    = Math.min(size ?: 10, 100)
        Integer offset = (page ?: 0) * max
        def orders = BookOrder.findAllByStatus(
            status,
            [max: max, offset: offset, sort: "dateCreated", order: "desc"]
        )
        Integer total = BookOrder.countByStatus(status)
        [content: orders.collect { toDto(it) }, totalElements: total, page: page, size: max]
    }

    @Transactional
    BookOrder updateStatus(Long id, OrderStatus newStatus) {
        BookOrder order = BookOrder.get(id)
        if (!order) return null
        if (!order.status.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException(
                "Invalid status transition: ${order.status} -> ${newStatus}")
        }
        order.status = newStatus
        if (newStatus == OrderStatus.SHIPPED)    order.shippedAt   = new Date()
        if (newStatus == OrderStatus.DELIVERED)  order.deliveredAt = new Date()
        order.save(flush: true, failOnError: true)
        order
    }

    @Transactional
    void cancel(Long id, String reason) {
        BookOrder order = BookOrder.get(id)
        if (!order) throw new IllegalArgumentException("Order not found")
        if (!order.status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new IllegalArgumentException("Cannot cancel order in status ${order.status}")
        }
        order.orderItems.each { item ->
            item.book.stockQuantity = item.book.stockQuantity + item.quantity
            item.book.save(failOnError: true)
            // Restoring stock: reverse the decrement done during place()
        }
        order.status = OrderStatus.CANCELLED
        order.notes = [order.notes, "Cancellation reason: ${reason ?: 'Customer requested cancellation'}"]
            .findAll { it }   // Remove nulls (order.notes may be null)
            .join(" | ")      // Join with separator
        order.save(flush: true, failOnError: true)
    }

    Map toDto(BookOrder order) {
        [
            id             : order.id,
            customerId     : order.customer?.id,
            customerName   : order.customer?.fullName,
            status         : order.status?.name(),    // .name() returns the enum constant name as String
            totalAmount    : order.totalAmount,
            shippingAddress: order.shippingAddress,
            notes          : order.notes,
            items          : (order.orderItems ?: []).collect {
                [
                    id             : it.id,
                    bookId         : it.book?.id,
                    bookTitle      : it.book?.title,
                    bookIsbn       : it.book?.isbn,
                    quantity       : it.quantity,
                    priceAtPurchase: it.priceAtPurchase,
                    subtotal       : it.subtotal   // calls getSubtotal()
                ]
            },
            createdAt  : order.dateCreated,
            shippedAt  : order.shippedAt,
            deliveredAt: order.deliveredAt
        ]
    }
}
```

---

## 7. Controllers

### 7a. `AuthorController.groovy`

```groovy
class AuthorController {
    static responseFormats = ['json']
    // Tells Grails: only serve JSON. Rejects requests with Accept: text/html.

    AuthorService authorService        // Spring injects by type — no @Autowired needed
    ApiResponseService apiResponseService

    def index() {
        render apiResponseService.success(authorService.list(), "Authors retrieved")
        // render — Grails method that writes to the HTTP response
        // No return statement needed — render() ends the action
    }

    def show(Long id) {
        // Grails binds the {id} path variable to the method parameter automatically
        def author = authorService.getById(id)
        if (!author) {
            render(status: 404, text: apiResponseService.error("Author not found"))
            return   // Explicit return needed here to stop execution after render
        }
        render apiResponseService.success(authorService.toDto(author))
    }

    def save() {
        try {
            def author = authorService.create(request.JSON as Map)
            // request.JSON — Grails parses the request body as JSON
            // 'as Map' — casts the parsed JSON to a Groovy Map
            render(status: 201, text: apiResponseService.success(authorService.toDto(author), "Author created"))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
            // e.message — Groovy property access, equivalent to e.getMessage()
        }
    }

    def update(Long id) {
        try {
            def author = authorService.update(id, request.JSON as Map)
            if (!author) {
                render(status: 404, text: apiResponseService.error("Author not found"))
                return
            }
            render apiResponseService.success(authorService.toDto(author), "Author updated successfully")
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def delete(Long id) {
        def author = authorService.getById(id)
        if (!author) {
            render(status: 404, text: apiResponseService.error("Author not found"))
            return
        }
        authorService.delete(id)
        render(status: 204)   // 204 No Content — success with no response body
    }
}
```

---

### 7b. `CategoryController.groovy`

```groovy
class CategoryController {
    static responseFormats = ['json']
    ApiResponseService apiResponseService
    // Note: no CategoryService. Simple CRUD with light validation can live in the controller.

    def index() {
        def categories = Category.list(sort: "name", order: "asc").collect {
            [id: it.id, name: it.name, description: it.description,
             bookCount: it.books?.size() ?: 0]
            // it.books?.size() — safe navigation; returns null if books set is null
            // ?: 0 — Elvis; default to 0
        }
        render apiResponseService.success(categories)
    }

    def save() {
        Map payload = request.JSON as Map
        if (Category.findByName(payload.name as String)) {
            render(status: 409, text: apiResponseService.error("Category '${payload.name}' already exists"))
            // 409 Conflict — the resource already exists
            return
        }
        Category category = new Category(payload)
        if (!category.validate()) {
            render(status: 400, text: apiResponseService.error(
                category.errors.allErrors*.defaultMessage.join(", ")))
            return
        }
        category.save(flush: true, failOnError: true)
        render(status: 201, text: apiResponseService.success(
            [id: category.id, name: category.name, description: category.description, bookCount: 0],
            "Category created"))
    }

    def update(Long id) {
        Category category = Category.get(id)
        if (!category) {
            render(status: 404, text: apiResponseService.error("Category not found"))
            return
        }
        Map payload = request.JSON as Map
        def duplicate = Category.findByName(payload.name as String)
        if (duplicate && duplicate.id != category.id) {
            // Self-exclusion: the category can keep its own name (not a duplicate)
            render(status: 409, text: apiResponseService.error("Name '${payload.name}' is taken"))
            return
        }
        category.properties = payload
        category.save(flush: true, failOnError: true)
        render apiResponseService.success(
            [id: category.id, name: category.name, description: category.description,
             bookCount: category.books?.size() ?: 0], "Category updated")
    }

    def delete(Long id) {
        Category category = Category.get(id)
        if (!category) {
            render(status: 404, text: apiResponseService.error("Category not found"))
            return
        }
        int bookCount = Book.countByCategory(category)
        if (bookCount > 0) {
            render(status: 400, text: apiResponseService.error(
                "Cannot delete category '${category.name}' because it has ${bookCount} books"))
            // Referential integrity check: a category with books cannot be deleted.
            // If we deleted the category, what category would those books belong to?
            return
        }
        category.delete(flush: true)
        render(status: 204)
    }
}
```

---

### 7c. `BookController.groovy`

```groovy
class BookController {
    static responseFormats = ['json']

    BookService bookService
    ApiResponseService apiResponseService

    def index() {
        // params — Grails magic object containing all request parameters (query string + path vars)
        render apiResponseService.success(bookService.list(params), "Books retrieved")
    }

    def search() {
        render apiResponseService.success(bookService.search(params), "Books retrieved")
    }

    def show(Long id) {
        def book = bookService.getById(id)
        if (!book) {
            render(status: 404, text: apiResponseService.error("Book not found"))
            return
        }
        render apiResponseService.success(bookService.toDto(book))
    }

    def findByIsbn(String isbn) {
        // isbn comes from the URL: /api/v1/books/isbn/$isbn → params.isbn → bound to parameter
        def book = bookService.findByIsbn(isbn)
        if (!book) {
            render(status: 404, text: apiResponseService.error("Book with ISBN ${isbn} not found"))
            return
        }
        render apiResponseService.success(bookService.toDto(book))
    }

    def save() {
        try {
            def book = bookService.create(request.JSON as Map)
            render(status: 201, text: apiResponseService.success(
                bookService.toDto(book), "Book created successfully"))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def update(Long id) {
        try {
            def book = bookService.update(id, request.JSON as Map)
            if (!book) {
                render(status: 404, text: apiResponseService.error("Book not found"))
                return
            }
            render apiResponseService.success(bookService.toDto(book), "Book updated successfully")
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def delete(Long id) {
        try {
            bookService.delete(id)
            render(status: 204)
        } catch (IllegalArgumentException e) {
            // Thrown when the book has associated orders
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def lowStock() {
        Integer threshold = params.int('threshold') ?: 5
        // params.int('threshold') — safe parse: returns null if not a valid integer
        def books = bookService.lowStock(threshold)
        render apiResponseService.success(books, "Found ${books.size()} books with low stock")
    }
}
```

---

### 7d. `OrderController.groovy`

```groovy
class OrderController {
    static responseFormats = ['json']

    OrderService orderService
    ApiResponseService apiResponseService

    def save() {
        try {
            BookOrder order = orderService.place(request.JSON as Map)
            render(status: 201, text: apiResponseService.success(
                orderService.toDto(order), "Order placed successfully"))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def byCustomer(Long customerId) {
        Integer page = params.int('page') ?: 0
        Integer size = params.int('size') ?: 10
        render apiResponseService.success(orderService.customerOrders(customerId, page, size))
    }

    def byStatus(String status) {
        OrderStatus enumStatus
        try {
            enumStatus = OrderStatus.valueOf(status?.toUpperCase())
            // .valueOf() throws IllegalArgumentException for unknown enum values
            // status?.toUpperCase() — safe navigation: handles null status gracefully
        } catch (Exception ignored) {
            render(status: 400, text: apiResponseService.error("Invalid status ${status}"))
            return
        }
        Integer page = params.int('page') ?: 0
        Integer size = params.int('size') ?: 10
        render apiResponseService.success(orderService.byStatus(enumStatus, page, size))
    }

    def updateStatus(Long id) {
        try {
            OrderStatus newStatus = OrderStatus.valueOf(params.newStatus?.toUpperCase())
            // params.newStatus → from query string: PATCH /api/v1/orders/5/status?newStatus=CONFIRMED
            BookOrder order = orderService.updateStatus(id, newStatus)
            if (!order) {
                render(status: 404, text: apiResponseService.error("Order not found"))
                return
            }
            render apiResponseService.success(orderService.toDto(order), "Order status updated")
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def cancel(Long id) {
        BookOrder order = orderService.getById(id)
        if (!order) {
            render(status: 404, text: apiResponseService.error("Order not found"))
            return
        }
        try {
            orderService.cancel(id, params.reason)
            // params.reason — optional query param: POST /api/v1/orders/5/cancel?reason=Wrong+item
            render apiResponseService.success(null, "Order cancelled successfully")
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }
}
```

---

## 8. `Application.groovy` — Entry Point

```groovy
package com.learning.bookstore

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
        // GrailsApp.run() is the Grails equivalent of SpringApplication.run()
        // It bootstraps the Spring application context, wires GORM, and starts Tomcat
    }
}
```

This is the smallest possible entry point. Grails' convention-over-configuration does the rest: it scans for domain classes, services, and controllers automatically based on their location in `grails-app/`.

---

## 9. Key Patterns Used

| Pattern | Where | What It Does |
|---|---|---|
| Active Record | Domain classes | Each domain class has `save()`, `delete()`, `get()`, `findBy*()` built in — no separate repository |
| Service Layer | `*Service.groovy` | All business logic and transactions isolated from HTTP handling |
| DTO via Map | `toDto()` in services | Converts domain objects to plain Maps for JSON serialisation |
| State Machine | `OrderStatus.canTransitionTo()` | Enforces valid order status transitions at the enum level |
| Pessimistic Lock | `Book.lock(id)` in `OrderService.place()` | Prevents concurrent oversell of the same book |
| Price Snapshot | `OrderItem.priceAtPurchase` | Preserves order totals when book prices change later |
| Elvis Operator | Throughout | `value ?: default` for null-safe defaults |
| Safe Navigation | Throughout | `object?.field` returns null instead of NullPointerException |
| Spread Operator | `errors.allErrors*.defaultMessage` | Applies method to every element in a collection |
| Implicit Return | All service/domain methods | Last expression is returned without `return` keyword |
