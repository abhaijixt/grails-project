package com.learning.bookstore

import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import spock.lang.Specification
import spock.lang.Unroll

@TestFor(Customer)
@Mock([BookOrder])
class CustomerSpec extends Specification {

    private Customer validCustomer(Map overrides = [:]) {
        new Customer([firstName: "Ada", lastName: "Lovelace", email: "ada@example.com"] + overrides)
    }

    void "a customer defaults to ACTIVE"() {
        expect:
        validCustomer().status == CustomerStatus.ACTIVE
    }

    @Unroll
    void "phone '#phone' is #outcome"() {
        expect:
        validCustomer(phone: phone).validate() == valid

        where:
        phone             | valid || outcome
        null              | true  || "optional"
        "1234567890"      | true  || "ten digits"
        "+441234567890"   | true  || "internationally prefixed"
        "123456789"       | false || "too short"
        "12345678901234567" | false || "too long"
        "555-1234"        | false || "punctuated"
    }

    void "fullName joins the two name parts"() {
        expect:
        validCustomer().fullName == "Ada Lovelace"
    }
}
