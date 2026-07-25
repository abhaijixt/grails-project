# Service Layer

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
            publisher      : book.publisher,
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
