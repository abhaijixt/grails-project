package com.learning.bookstore

class CategoryController {
    static responseFormats = ['json']

    ApiResponseService apiResponseService

    def index() {
        def categories = Category.list(sort: "name", order: "asc").collect {
            [id: it.id, name: it.name, description: it.description, bookCount: it.books?.size() ?: 0]
        }
        render apiResponseService.success(categories)
    }

    def show(Long id) {
        def category = Category.get(id)
        if (!category) {
            render(status: 404, text: apiResponseService.error("Category not found"))
            return
        }
        render apiResponseService.success([id: category.id, name: category.name, description: category.description, bookCount: category.books?.size() ?: 0])
    }

    def save() {
        Map payload = request.JSON as Map
        if (Category.findByName(payload.name as String)) {
            render(status: 409, text: apiResponseService.error("Category '${payload.name}' already exists"))
            return
        }
        Category category = new Category(payload)
        if (!category.validate()) {
            render(status: 400, text: apiResponseService.error(category.errors.allErrors*.defaultMessage.join(", ")))
            return
        }
        category.save(flush: true, failOnError: true)
        render(status: 201, text: apiResponseService.success([id: category.id, name: category.name, description: category.description, bookCount: 0], "Category created"))
    }

    def update(Long id) {
        Category category = Category.get(id)
        if (!category) {
            render(status: 404, text: apiResponseService.error("Category not found"))
            return
        }
        Map payload = request.JSON as Map
        def duplicate = Category.findByName(payload.name as String)
        if (duplicate && duplicate.id != category.id) {
            render(status: 409, text: apiResponseService.error("Name '${payload.name}' is taken"))
            return
        }
        category.properties = payload
        category.save(flush: true, failOnError: true)
        render apiResponseService.success([id: category.id, name: category.name, description: category.description, bookCount: category.books?.size() ?: 0], "Category updated")
    }

    def delete(Long id) {
        Category category = Category.get(id)
        if (!category) {
            render(status: 404, text: apiResponseService.error("Category not found"))
            return
        }
        int bookCount = Book.countByCategory(category)
        if (bookCount > 0) {
            render(status: 400, text: apiResponseService.error("Cannot delete category '${category.name}' because it has ${bookCount} books"))
            return
        }
        category.delete(flush: true)
        render(status: 204)
    }
}

