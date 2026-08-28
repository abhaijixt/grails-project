package com.learning.bookstore

import grails.transaction.Transactional

@Transactional(readOnly = true)
class BookService {

    Map list(Map params) {
        Integer max = Math.min((params.int('size') ?: 10), 100)
        Integer offset = (params.int('page') ?: 0) * max
        String sortBy = params.sortBy ?: "title"
        String sortDir = params.sortDir ?: "asc"

        def books = Book.createCriteria().list(max: max, offset: offset, sort: sortBy, order: sortDir) { }
        [content: books.collect { toDto(it) }, totalElements: books.totalCount, page: (params.int('page') ?: 0), size: max]
    }

    Map search(Map params) {
        Integer max = Math.min((params.int('size') ?: 10), 100)
        Integer offset = (params.int('page') ?: 0) * max
        BigDecimal minPrice = toBigDecimal(params.minPrice)
        BigDecimal maxPrice = toBigDecimal(params.maxPrice)

        def books = Book.createCriteria().list(max: max, offset: offset, sort: "title", order: "asc") {
            if (params.title) {
                ilike("title", "%${params.title}%")
            }
            if (params.categoryId) {
                eq("category", Category.get(params.long('categoryId')))
            }
            if (minPrice != null) {
                ge("price", minPrice)
            }
            if (maxPrice != null) {
                le("price", maxPrice)
            }
            if (params.inStock == 'true') {
                gt("stockQuantity", 0)
            }
        }
        [content: books.collect { toDto(it) }, totalElements: books.totalCount, page: (params.int('page') ?: 0), size: max]
    }

    // Grails 2.5 params has no bigDecimal() converter, so parse by hand and
    // treat an unparseable value as "filter not supplied".
    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null
        try {
            return new BigDecimal(value.toString().trim())
        } catch (NumberFormatException ignored) {
            return null
        }
    }

    Book getById(Long id) {
        Book.get(id)
    }

    Book findByIsbn(String isbn) {
        Book.findByIsbn(isbn)
    }

    @Transactional
    Book create(Map payload) {
        if (Book.findByIsbn(payload.isbn)) {
            throw new IllegalArgumentException("Book with ISBN ${payload.isbn} already exists")
        }
        Category category = Category.get(payload.categoryId as Long)
        if (!category) throw new IllegalArgumentException("Category not found")

        Book book = new Book(payload)
        book.category = category
        book.save(failOnError: true)

        List<Long> authorIds = (payload.authorIds ?: []) as List<Long>
        authorIds.each { id ->
            def author = Author.get(id)
            if (author) {
                book.addToAuthors(author)
            }
        }
        book.save(flush: true, failOnError: true)
        book
    }

    @Transactional
    Book update(Long id, Map payload) {
        Book book = Book.get(id)
        if (!book) return null

        if (payload.isbn && payload.isbn != book.isbn && Book.findByIsbn(payload.isbn)) {
            throw new IllegalArgumentException("ISBN ${payload.isbn} is already in use")
        }
        if (payload.categoryId) {
            def category = Category.get(payload.categoryId as Long)
            if (!category) throw new IllegalArgumentException("Category not found")
            book.category = category
        }

        book.properties = payload.findAll { k, _ -> k != "authorIds" && k != "categoryId" }
        if (payload.containsKey('authorIds')) {
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
            throw new IllegalArgumentException("Cannot delete book '${book.title}' because it has associated orders")
        }
        book.delete(flush: true)
    }

    List<Map> lowStock(Integer threshold = 5) {
        Book.findAllByStockQuantityLessThanEquals(threshold ?: 5).collect { toDto(it) }
    }

    Map toDto(Book book) {
        [
            id: book.id,
            title: book.title,
            isbn: book.isbn,
            price: book.price,
            stockQuantity: book.stockQuantity,
            publicationDate: book.publicationDate,
            description: book.description,
            coverImageUrl: book.coverImageUrl,
            publisher: book.publisher,
            categoryId: book.category?.id,
            categoryName: book.category?.name,
            authors: (book.authors ?: []).collect { [id: it.id, fullName: it.fullName, email: it.email] },
            createdAt: book.dateCreated
        ]
    }
}

