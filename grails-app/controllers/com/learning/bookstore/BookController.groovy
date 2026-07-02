package com.learning.bookstore

class BookController {
    static responseFormats = ['json']

    BookService bookService
    ApiResponseService apiResponseService

    def index() {
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
            render(status: 201, text: apiResponseService.success(bookService.toDto(book), "Book created successfully"))
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
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def lowStock() {
        Integer threshold = params.int('threshold') ?: 5
        def books = bookService.lowStock(threshold)
        render apiResponseService.success(books, "Found ${books.size()} books with low stock")
    }
}

