package com.learning.bookstore

import grails.converters.JSON
import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import spock.lang.Specification

@TestFor(BookController)
@Mock([Book, Category, Author])
class BookControllerSpec extends Specification {

    def setup() {
        controller.apiResponseService = new ApiResponseService()
    }

    void "index renders the paged envelope from the service"() {
        given:
        controller.bookService = Mock(BookService)

        when:
        controller.index()

        then:
        1 * controller.bookService.list(_) >> [content: [], totalElements: 0, page: 0, size: 10]
        (JSON.parse(response.text) as Map).success == true
    }

    void "show returns 404 when the book does not exist"() {
        given:
        controller.bookService = Mock(BookService)

        when:
        controller.show(9999L)

        then:
        1 * controller.bookService.getById(9999L) >> null
        response.status == 404
    }

    void "save returns 201 with the created book"() {
        given:
        Category category = new Category(name: "Programming").save(failOnError: true)
        Book book = new Book(title: "Groovy in Action", isbn: "9781935182443",
                             price: new BigDecimal("42.50"), stockQuantity: 1, category: category)
        controller.bookService = Mock(BookService)
        request.json = '{"title":"Groovy in Action"}'

        when:
        controller.save()

        then:
        1 * controller.bookService.create(_) >> book
        1 * controller.bookService.toDto(book) >> [title: book.title]
        response.status == 201
    }

    void "save turns a rejected payload into a 400"() {
        given:
        controller.bookService = Mock(BookService)
        request.json = '{"isbn":"duplicate"}'

        when:
        controller.save()

        then:
        1 * controller.bookService.create(_) >> { throw new IllegalArgumentException("Book with ISBN duplicate already exists") }
        response.status == 400
        (JSON.parse(response.text) as Map).success == false
    }
}
