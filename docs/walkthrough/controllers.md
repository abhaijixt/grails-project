# Controller Layer

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
