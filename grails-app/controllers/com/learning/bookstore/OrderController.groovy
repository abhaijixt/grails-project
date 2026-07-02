package com.learning.bookstore

class OrderController {
    static responseFormats = ['json']

    OrderService orderService
    ApiResponseService apiResponseService

    def save() {
        try {
            BookOrder order = orderService.place(request.JSON as Map)
            render(status: 201, text: apiResponseService.success(orderService.toDto(order), "Order placed successfully"))
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def show(Long id) {
        BookOrder order = orderService.getById(id)
        if (!order) {
            render(status: 404, text: apiResponseService.error("Order not found"))
            return
        }
        render apiResponseService.success(orderService.toDto(order))
    }

    def byCustomer(Long customerId) {
        Integer page = params.int('page') ?: 0
        Integer size = params.int('size') ?: 10
        render apiResponseService.success(orderService.customerOrders(customerId, page, size))
    }

    def byStatus(String status) {
        OrderStatus enumStatus
        try {
            enumStatus = OrderStatus.valueOf(status?.toUpperCase())
        } catch (Exception ignored) {
            render(status: 400, text: apiResponseService.error("Invalid status ${status}"))
            return
        }
        Integer page = params.int('page') ?: 0
        Integer size = params.int('size') ?: 10
        render apiResponseService.success(orderService.byStatus(enumStatus, page, size))
    }

    def updateStatus(Long id) {
        try {
            OrderStatus newStatus = OrderStatus.valueOf(params.newStatus?.toUpperCase())
            BookOrder order = orderService.updateStatus(id, newStatus)
            if (!order) {
                render(status: 404, text: apiResponseService.error("Order not found"))
                return
            }
            render apiResponseService.success(orderService.toDto(order), "Order status updated")
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }

    def cancel(Long id) {
        BookOrder order = orderService.getById(id)
        if (!order) {
            render(status: 404, text: apiResponseService.error("Order not found"))
            return
        }
        try {
            orderService.cancel(id, params.reason)
            render apiResponseService.success(null, "Order cancelled successfully")
        } catch (IllegalArgumentException e) {
            render(status: 400, text: apiResponseService.error(e.message))
        }
    }
}

