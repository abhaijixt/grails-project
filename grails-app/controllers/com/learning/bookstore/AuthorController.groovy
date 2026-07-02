package com.learning.bookstore

class AuthorController {
    static responseFormats = ['json']

    AuthorService authorService
    ApiResponseService apiResponseService

    def index() {
        render apiResponseService.success(authorService.list(), "Authors retrieved")
    }

    def show(Long id) {
        def author = authorService.getById(id)
        if (!author) {
            render(status: 404, text: apiResponseService.error("Author not found"))
            return
        }
        render apiResponseService.success(authorService.toDto(author))
    }

    def save() {
        try {
            def author = authorService.create(request.JSON as Map)
            render(status: 201, text: apiResponseService.success(authorService.toDto(author), "Author created"))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def update(Long id) {
        try {
            def author = authorService.update(id, request.JSON as Map)
            if (!author) {
                render(status: 404, text: apiResponseService.error("Author not found"))
                return
            }
            render apiResponseService.success(authorService.toDto(author), "Author updated successfully")
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def delete(Long id) {
        def author = authorService.getById(id)
        if (!author) {
            render(status: 404, text: apiResponseService.error("Author not found"))
            return
        }
        authorService.delete(id)
        render(status: 204)
    }
}
