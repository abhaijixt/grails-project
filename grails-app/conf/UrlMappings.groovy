class UrlMappings {
    static mappings = {
        // Probed by the deploy pipeline and by anything watching the app.
        "/health"(controller: "health", action: "index", method: "GET")
        "/api/v1/health"(controller: "health", action: "index", method: "GET")

        "/api/v1/books"(resources: 'book')
        "/api/v1/books/search"(controller: "book", action: "search", method: "GET")
        "/api/v1/books/isbn/$isbn"(controller: "book", action: "findByIsbn", method: "GET")
        "/api/v1/books/low-stock"(controller: "book", action: "lowStock", method: "GET")

        "/api/v1/authors"(resources: 'author')

        "/api/v1/categories"(resources: 'category')

        // Order resources without delete (cancellation is an explicit business action)
        "/api/v1/orders"(resources: 'order', excludes: ['delete'])
        "/api/v1/orders/$id/cancel"(controller: "order", action: "cancel", method: "POST")
        "/api/v1/orders/customer/$customerId"(controller: "order", action: "byCustomer", method: "GET")
        "/api/v1/orders/status/$status"(controller: "order", action: "byStatus", method: "GET")
        "/api/v1/orders/$id/status"(controller: "order", action: "updateStatus", method: "PATCH")

        "500"(view: '/error')
        "404"(view: '/notFound')
    }
}
