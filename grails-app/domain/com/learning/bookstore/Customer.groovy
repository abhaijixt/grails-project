package com.learning.bookstore

class Customer {
    String firstName
    String lastName
    String email
    String phone
    String address
    CustomerStatus status = CustomerStatus.ACTIVE
    Date dateCreated
    Date lastUpdated

    static hasMany = [orders: BookOrder]

    static constraints = {
        firstName blank: false, maxSize: 100
        lastName blank: false, maxSize: 100
        email blank: false, unique: true, email: true
        phone nullable: true, matches: /^\+?[0-9]{10,15}$/
        address nullable: true, maxSize: 500
        status nullable: false
    }

    String getFullName() {
        "${firstName} ${lastName}"
    }
}

