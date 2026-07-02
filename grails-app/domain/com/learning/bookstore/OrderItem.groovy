package com.learning.bookstore

class OrderItem {
    BookOrder order
    Book book
    Integer quantity
    BigDecimal priceAtPurchase
    Date dateCreated

    static belongsTo = [order: BookOrder]

    static constraints = {
        order nullable: false
        book nullable: false
        quantity min: 1
        priceAtPurchase nullable: false, min: BigDecimal.ZERO
    }

    BigDecimal getSubtotal() {
        (priceAtPurchase ?: BigDecimal.ZERO) * (quantity ?: 0)
    }
}

