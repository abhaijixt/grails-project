package com.learning.bookstore

import grails.test.mixin.Mock
import grails.test.mixin.TestFor
import spock.lang.Specification
import spock.lang.Unroll

@TestFor(OrderService)
@Mock([BookOrder, OrderItem, Book, Author, Category, Customer])
class OrderServiceSpec extends Specification {

    Customer customer
    Book book

    def setup() {
        // Book.lock() is a database-level pessimistic lock with no equivalent in
        // the unit-test datastore; place() only needs it to resolve the row.
        Book.metaClass.static.lock = { Serializable id -> Book.get(id) }

        customer = new Customer(firstName: "Ada", lastName: "Lovelace", email: "ada@example.com").save(failOnError: true)
        Category category = new Category(name: "Programming").save(failOnError: true)
        book = new Book(title: "Groovy in Action", isbn: "9781935182443",
                        price: new BigDecimal("42.50"), stockQuantity: 10,
                        category: category).save(failOnError: true)
    }

    def cleanup() {
        Book.metaClass = null
    }

    void "placing an order decrements stock and totals the line items"() {
        when:
        BookOrder order = service.place([customerId: customer.id, shippingAddress: "1 Main St",
                                         items: [[bookId: book.id, quantity: 3]]])

        then:
        order.status == OrderStatus.PENDING
        order.totalAmount == new BigDecimal("127.50")
        book.stockQuantity == 7
    }

    void "an order for more copies than are in stock is rejected"() {
        when:
        service.place([customerId: customer.id, items: [[bookId: book.id, quantity: 11]]])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("Insufficient stock")
    }

    void "an unknown customer is rejected"() {
        when:
        service.place([customerId: 9999L, items: []])

        then:
        IllegalArgumentException e = thrown()
        e.message == "Customer not found"
    }

    void "an inactive customer is rejected"() {
        given:
        customer.status = CustomerStatus.INACTIVE
        customer.save(flush: true, failOnError: true)

        when:
        service.place([customerId: customer.id, items: []])

        then:
        IllegalArgumentException e = thrown()
        e.message == "Customer is not active"
    }

    @Unroll
    void "the #from -> #to transition is allowed"() {
        given:
        BookOrder order = new BookOrder(customer: customer, status: from).save(failOnError: true)

        expect:
        service.updateStatus(order.id, to).status == to

        where:
        from                   | to
        OrderStatus.PENDING    | OrderStatus.CONFIRMED
        OrderStatus.PENDING    | OrderStatus.CANCELLED
        OrderStatus.CONFIRMED  | OrderStatus.PROCESSING
        OrderStatus.PROCESSING | OrderStatus.SHIPPED
        OrderStatus.SHIPPED    | OrderStatus.DELIVERED
        OrderStatus.DELIVERED  | OrderStatus.REFUNDED
    }

    @Unroll
    void "the #from -> #to transition is refused"() {
        given:
        BookOrder order = new BookOrder(customer: customer, status: from).save(failOnError: true)

        when:
        service.updateStatus(order.id, to)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("Invalid status transition")

        where:
        from                   | to
        OrderStatus.PENDING    | OrderStatus.SHIPPED
        OrderStatus.PROCESSING | OrderStatus.DELIVERED
        OrderStatus.DELIVERED  | OrderStatus.PENDING
        OrderStatus.CANCELLED  | OrderStatus.CONFIRMED
        OrderStatus.REFUNDED   | OrderStatus.PENDING
    }

    void "shipping an order stamps shippedAt"() {
        given:
        BookOrder order = new BookOrder(customer: customer, status: OrderStatus.PROCESSING).save(failOnError: true)

        when:
        BookOrder updated = service.updateStatus(order.id, OrderStatus.SHIPPED)

        then:
        updated.shippedAt != null
    }

    void "updateStatus returns null for an unknown id"() {
        expect:
        service.updateStatus(9999L, OrderStatus.CONFIRMED) == null
    }

    void "cancelling an order puts the stock back and records the reason"() {
        given:
        BookOrder order = service.place([customerId: customer.id, items: [[bookId: book.id, quantity: 4]]])

        when:
        service.cancel(order.id, "Changed my mind")

        then:
        order.status == OrderStatus.CANCELLED
        book.stockQuantity == 10
        order.notes.contains("Changed my mind")
    }

    void "an order that has already shipped cannot be cancelled"() {
        given:
        BookOrder order = new BookOrder(customer: customer, status: OrderStatus.SHIPPED).save(failOnError: true)

        when:
        service.cancel(order.id, null)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains("Cannot cancel")
    }
}
