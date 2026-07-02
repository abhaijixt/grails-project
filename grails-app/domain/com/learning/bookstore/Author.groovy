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
