package com.learning.bookstore

import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import spock.lang.Specification
import spock.lang.Unroll

@TestFor(Book)
@Mock([Category, Author])
class BookSpec extends Specification {

    Category category

    def setup() {
        // One shared category: Category.name is unique, so building a fresh one
        // per book would fail on the category rather than on the book.
        category = new Category(name: "Programming").save(flush: true, failOnError: true)
    }

    private Book validBook(Map overrides = [:]) {
        new Book([
            title        : "Groovy in Action",
            isbn         : "9781935182443",
            price        : new BigDecimal("42.50"),
            stockQuantity: 7,
            category     : category
        ] + overrides)
    }

    void "a fully populated book validates"() {
        expect:
        validBook().validate()
    }

    @Unroll
    void "price #price is #outcome"() {
        expect:
        validBook(price: price).validate() == valid

        where:
        price                      | valid || outcome
        new BigDecimal("0.01")     | true  || "the lowest accepted value"
        new BigDecimal("9999.99")  | true  || "the highest accepted value"
        new BigDecimal("0.00")     | false || "below the minimum"
        new BigDecimal("10000.00") | false || "above the maximum"
    }

    void "stock quantity cannot go negative"() {
        expect:
        !validBook(stockQuantity: -1).validate()
    }

    void "isbn must be unique"() {
        given:
        validBook().save(flush: true, failOnError: true)

        expect:
        !validBook(title: "A different book").validate()
    }

    void "category is required"() {
        expect:
        !validBook(category: null).validate()
    }

    @Unroll
    void "title of length #length is #outcome"() {
        expect:
        validBook(title: "x" * length).validate() == valid

        where:
        length | valid || outcome
        1      | true  || "accepted"
        300    | true  || "accepted"
        301    | false || "too long"
        0      | false || "blank"
    }

    void "optional fields may be left unset"() {
        expect:
        validBook(publicationDate: null, description: null, coverImageUrl: null, publisher: null).validate()
    }
}
