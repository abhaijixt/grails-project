# Domain Layer

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
    String publisher
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
        publisher      nullable: true, maxSize: 200
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
