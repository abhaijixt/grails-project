package com.learning.bookstore

class BookOrder {
    Customer customer
    OrderStatus status = OrderStatus.PENDING
    BigDecimal totalAmount = BigDecimal.ZERO
    String shippingAddress
    String notes
    Date shippedAt
    Date deliveredAt
    Date dateCreated
    Date lastUpdated

    static belongsTo = [customer: Customer]
    static hasMany = [orderItems: OrderItem]

    static constraints = {
        customer nullable: false
        status nullable: false
        totalAmount nullable: false, min: BigDecimal.ZERO
        shippingAddress nullable: true, maxSize: 500
        notes nullable: true, maxSize: 1000
        shippedAt nullable: true
        deliveredAt nullable: true
    }

    void recalculateTotal() {
        totalAmount = orderItems?.collect { it.subtotal }?.sum() ?: BigDecimal.ZERO
    }
}

