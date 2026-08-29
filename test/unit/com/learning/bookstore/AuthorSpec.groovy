package com.learning.bookstore

import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import spock.lang.Specification
import spock.lang.Unroll

@TestFor(Author)
@Mock([Book, Category])
class AuthorSpec extends Specification {

    private Author validAuthor(Map overrides = [:]) {
        new Author([firstName: "Dierk", lastName: "Koenig", email: "dierk@example.com"] + overrides)
    }

    void "a fully populated author validates"() {
        expect:
        validAuthor().validate()
    }

    @Unroll
    void "email '#email' is #outcome"() {
        expect:
        validAuthor(email: email).validate() == valid

        where:
        email               | valid || outcome
        "a@example.com"     | true  || "well formed"
        "not-an-email"      | false || "missing a domain"
        ""                  | false || "blank"
    }

    void "email must be unique"() {
        given:
        validAuthor().save(flush: true, failOnError: true)

        expect:
        !validAuthor(firstName: "Someone", lastName: "Else").validate()
    }

    void "fullName joins the two name parts"() {
        expect:
        validAuthor().fullName == "Dierk Koenig"
    }

    void "bio is optional but bounded"() {
        expect:
        validAuthor(bio: null).validate()
        validAuthor(bio: "x" * 1000).validate()
        !validAuthor(bio: "x" * 1001).validate()
    }
}
