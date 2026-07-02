package com.learning.bookstore

class Book {
    String title
    String isbn
    BigDecimal price
    Integer stockQuantity
    Date publicationDate
    String description
    String coverImageUrl
    Date dateCreated
    Date lastUpdated

    Category category

    static belongsTo = [category: Category]
    static hasMany = [authors: Author, orderItems: OrderItem]

    static mapping = {
        description type: "text"
    }

    static constraints = {
        title blank: false, size: 1..300
        isbn blank: false, unique: true, maxSize: 20
        price nullable: false, min: new BigDecimal("0.01"), max: new BigDecimal("9999.99")
        stockQuantity min: 0
        publicationDate nullable: true
        description nullable: true, maxSize: 2000
        coverImageUrl nullable: true, maxSize: 500
        category nullable: false
    }
}

