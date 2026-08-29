package com.learning.bookstore

import grails.converters.JSON
import grails.test.mixin.TestFor
import grails.test.mixin.TestMixin
import grails.test.mixin.web.ControllerUnitTestMixin
import spock.lang.Specification

@TestFor(ApiResponseService)
// ControllerUnitTestMixin registers the JSON object marshallers; without it the
// converter cannot serialise a plain Map.
@TestMixin(ControllerUnitTestMixin)
class ApiResponseServiceSpec extends Specification {

    void "success wraps the payload in the standard envelope"() {
        when:
        Map parsed = parse(service.success([id: 1], "Books retrieved"))

        then:
        parsed.success == true
        parsed.message == "Books retrieved"
        parsed.data.id == 1
        parsed.timestamp
    }

    void "success has a default message"() {
        expect:
        parse(service.success([])).message == "Success"
    }

    void "error reports the failure and carries no data"() {
        when:
        Map parsed = parse(service.error("Book not found"))

        then:
        parsed.success == false
        parsed.error == "Book not found"
        !parsed.containsKey("data")
    }

    private static Map parse(JSON json) {
        JSON.parse(json.toString()) as Map
    }
}
