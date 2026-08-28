package com.learning.bookstore

class Author {
    String firstName
    String lastName
    String email
    Date birthDate
    String bio
    Date dateCreated
    Date lastUpdated

    static hasMany = [books: Book]
    // GORM 3.x requires an explicit owner on a bidirectional many-to-many.
    // Book owns it: BookService drives the association via book.addToAuthors(...).
    // Many-to-many caps at save-update cascade, so deleting a Book never deletes Authors.
    static belongsTo = Book

    static constraints = {
        firstName blank: false, maxSize: 100
        lastName blank: false, maxSize: 100
        email blank: false, email: true, unique: true
        birthDate nullable: true
        bio nullable: true, maxSize: 1000
    }

    String getFullName() {
        "${firstName} ${lastName}"
    }
}
