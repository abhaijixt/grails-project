package com.learning.bookstore

import grails.transaction.Transactional

@Transactional(readOnly = true)
class AuthorService {

    List<Map> list() {
        Author.list(sort: "lastName", order: "asc").collect { toDto(it) }
    }

    Author getById(Long id) {
        Author.get(id)
    }

    @Transactional
    Author create(Map payload) {
        if (Author.findByEmail(payload.email as String)) {
            throw new IllegalArgumentException("An author with email '${payload.email}' already exists")
        }
        Author author = new Author(payload)
        if (!author.validate()) {
            throw new IllegalArgumentException(author.errors.allErrors*.defaultMessage.join(", "))
        }
        author.save(flush: true, failOnError: true)
        author
    }

    @Transactional
    Author update(Long id, Map payload) {
        Author author = Author.get(id)
        if (!author) return null

        if (payload.email && payload.email != author.email && Author.findByEmail(payload.email as String)) {
            throw new IllegalArgumentException("Email '${payload.email}' is already in use")
        }
        author.properties = payload
        if (!author.validate()) {
            throw new IllegalArgumentException(author.errors.allErrors*.defaultMessage.join(", "))
        }
        author.save(flush: true, failOnError: true)
        author
    }

    @Transactional
    void delete(Long id) {
        Author author = Author.get(id)
        if (author) {
            author.delete(flush: true)
        }
    }

    Map toDto(Author author) {
        [
            id       : author.id,
            fullName : author.fullName,
            firstName: author.firstName,
            lastName : author.lastName,
            email    : author.email,
            birthDate: author.birthDate,
            bio      : author.bio,
            createdAt: author.dateCreated
        ]
    }
}
