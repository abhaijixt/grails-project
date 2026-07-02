package com.learning.bookstore

class Category {
    String name
    String description
    Date dateCreated
    Date lastUpdated

    static hasMany = [books: Book]

    static constraints = {
        name blank: false, unique: true, size: 2..100
        description nullable: true, maxSize: 500
    }
}

