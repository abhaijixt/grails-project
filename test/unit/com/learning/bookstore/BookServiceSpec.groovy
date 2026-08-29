package com.learning.bookstore

import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import spock.lang.Specification

@TestFor(BookService)
@Mock([Book, Category, Author, OrderItem, BookOrder, Customer])
// BookService.list() calls params.int(), which only a real GrailsParameterMap has.
@TestMixin(ControllerUnitTestMixin)
class BookServiceSpec extends Specification {

    Category category

    def setup() {
        category = new Category(name: "Programming").save(flush: true, failOnError: true)
    }

    private Book existingBook(Map overrides = [:]) {
        new Book([
            title        : "Groovy in Action",
            isbn         : "9781935182443",
            price        : new BigDecimal("42.50"),
            stockQuantity: 7,
            category     : category
        ] + overrides).save(flush: true, failOnError: true)
    }

    void "create persists a book and links its category"() {
        when:
        Book book = service.create([
            title: "Programming Groovy", isbn: "9781937785307",
            price: new BigDecimal("35.00"), stockQuantity: 3, categoryId: category.id
        ])

        then:
        book.id
        book.category == category
        Book.count() == 1
    }

    void "create attaches the authors it is given and skips unknown ids"() {
        given:
        Author author = new Author(firstName: "Dierk", lastName: "Koenig", email: "dierk@example.com").save(flush: true, failOnError: true)

        when:
        Book book = service.create([
            title: "Groovy in Action", isbn: "9781935182443",
            price: new BigDecimal("42.50"), stockQuantity: 1,
            categoryId: category.id, authorIds: [author.id, 9999L]
        ])

        then:
        book.authors*.id == [author.id]
    }

    void "create rejects a duplicate isbn"() {
        given:
        existingBook()

        when:
        service.create([title: "Другая", isbn: "9781935182443", price: new BigDecimal("1.00"),
                        stockQuantity: 1, categoryId: category.id])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("already exists")
    }

    void "create rejects an unknown category"() {
        when:
        service.create([title: "Orphan", isbn: "9780000000001", price: new BigDecimal("1.00"),
                        stockQuantity: 1, categoryId: 9999L])

        then:
        IllegalArgumentException e = thrown()
        e.message == "Category not found"
    }

    void "update returns null for an unknown id"() {
        expect:
        service.update(9999L, [title: "Nope"]) == null
    }

    void "update rejects an isbn already held by another book"() {
        given:
        existingBook()
        Book other = existingBook(isbn: "9780000000002", title: "Other")

        when:
        service.update(other.id, [isbn: "9781935182443"])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("already in use")
    }

    void "update leaves the isbn alone when it is unchanged"() {
        given:
        Book book = existingBook()

        when:
        Book updated = service.update(book.id, [isbn: "9781935182443", title: "Retitled"])

        then:
        updated.title == "Retitled"
    }

    void "delete removes a book that no order refers to"() {
        given:
        Book book = existingBook()

        when:
        service.delete(book.id)

        then:
        Book.count() == 0
    }

    void "delete refuses a book that an order refers to"() {
        given:
        Book book = existingBook()
        Customer customer = new Customer(firstName: "Ada", lastName: "Lovelace", email: "ada@example.com").save(failOnError: true)
        BookOrder order = new BookOrder(customer: customer).save(failOnError: true)
        new OrderItem(order: order, book: book, quantity: 1, priceAtPurchase: book.price).save(failOnError: true)

        when:
        service.delete(book.id)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("associated orders")
        Book.count() == 1
    }

    void "delete of an unknown id is a no-op"() {
        when:
        service.delete(9999L)

        then:
        notThrown(Exception)
    }

    void "lowStock returns only books at or under the threshold"() {
        given:
        existingBook(isbn: "9780000000010", stockQuantity: 2)
        existingBook(isbn: "9780000000011", stockQuantity: 5)
        existingBook(isbn: "9780000000012", stockQuantity: 6)

        expect:
        service.lowStock(5).size() == 2
    }

    void "list caps the page size at 100"() {
        given:
        params.size = "500"

        expect:
        service.list(params).size == 100
    }

    void "toDto exposes the category and author summary"() {
        given:
        Author author = new Author(firstName: "Dierk", lastName: "Koenig", email: "dierk@example.com").save(flush: true, failOnError: true)
        Book book = existingBook()
        book.addToAuthors(author)
        book.save(flush: true, failOnError: true)

        when:
        Map dto = service.toDto(book)

        then:
        dto.title == "Groovy in Action"
        dto.categoryName == "Programming"
        dto.authors*.fullName == ["Dierk Koenig"]
    }
}
