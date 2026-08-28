package com.learning.bookstore

import grails.transaction.Transactional

@Transactional(readOnly = true)
class OrderService {

    @Transactional
    BookOrder place(Map payload) {
        Customer customer = Customer.get(payload.customerId as Long)
        if (!customer) throw new IllegalArgumentException("Customer not found")
        if (customer.status != CustomerStatus.ACTIVE) throw new IllegalArgumentException("Customer is not active")

        BookOrder order = new BookOrder(
            customer: customer,
            shippingAddress: payload.shippingAddress,
            notes: payload.notes
        )
        order.save(failOnError: true)

        (payload.items ?: []).each { item ->
            // Pessimistic lock prevents concurrent orders from overselling the same book
            Book book = Book.lock(item.bookId as Long)
            if (!book) throw new IllegalArgumentException("Book not found: ${item.bookId}")
            Integer qty = item.quantity as Integer
            if (book.stockQuantity < qty) {
                throw new IllegalArgumentException("Insufficient stock for '${book.title}'")
            }
            book.stockQuantity = book.stockQuantity - qty
            book.save(failOnError: true)

            order.addToOrderItems(new OrderItem(
                order: order,
                book: book,
                quantity: qty,
                priceAtPurchase: book.price
            ))
        }
        order.recalculateTotal()
        order.save(flush: true, failOnError: true)
        order
    }

    BookOrder getById(Long id) {
        BookOrder.get(id)
    }

    Map customerOrders(Long customerId, Integer page = 0, Integer size = 10) {
        Integer max = Math.min(size ?: 10, 100)
        Integer offset = (page ?: 0) * max
        def orders = BookOrder.findAllByCustomer(Customer.get(customerId), [max: max, offset: offset, sort: "dateCreated", order: "desc"])
        Integer total = BookOrder.countByCustomer(Customer.get(customerId))
        [content: orders.collect { toDto(it) }, totalElements: total, page: page, size: max]
    }

    Map byStatus(OrderStatus status, Integer page = 0, Integer size = 10) {
        Integer max = Math.min(size ?: 10, 100)
        Integer offset = (page ?: 0) * max
        def orders = BookOrder.findAllByStatus(status, [max: max, offset: offset, sort: "dateCreated", order: "desc"])
        Integer total = BookOrder.countByStatus(status)
        [content: orders.collect { toDto(it) }, totalElements: total, page: page, size: max]
    }

    @Transactional
    BookOrder updateStatus(Long id, OrderStatus newStatus) {
        BookOrder order = BookOrder.get(id)
        if (!order) return null
        if (!order.status.canTransitionTo(newStatus)) {
            throw new IllegalArgumentException("Invalid status transition: ${order.status} -> ${newStatus}")
        }
        order.status = newStatus
        if (newStatus == OrderStatus.SHIPPED) order.shippedAt = new Date()
        if (newStatus == OrderStatus.DELIVERED) order.deliveredAt = new Date()
        order.save(flush: true, failOnError: true)
        order
    }

    @Transactional
    void cancel(Long id, String reason) {
        BookOrder order = BookOrder.get(id)
        if (!order) throw new IllegalArgumentException("Order not found")
        if (!order.status.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new IllegalArgumentException("Cannot cancel order in status ${order.status}")
        }
        order.orderItems.each { item ->
            item.book.stockQuantity = item.book.stockQuantity + item.quantity
            item.book.save(failOnError: true)
        }
        order.status = OrderStatus.CANCELLED
        order.notes = [order.notes, "Cancellation reason: ${reason ?: 'Customer requested cancellation'}"].findAll { it }.join(" | ")
        order.save(flush: true, failOnError: true)
    }

    Map toDto(BookOrder order) {
        [
            id: order.id,
            customerId: order.customer?.id,
            customerName: order.customer?.fullName,
            status: order.status?.name(),
            totalAmount: order.totalAmount,
            shippingAddress: order.shippingAddress,
            notes: order.notes,
            items: (order.orderItems ?: []).collect {
                [
                    id: it.id,
                    bookId: it.book?.id,
                    bookTitle: it.book?.title,
                    bookIsbn: it.book?.isbn,
                    quantity: it.quantity,
                    priceAtPurchase: it.priceAtPurchase,
                    subtotal: it.subtotal
                ]
            },
            createdAt: order.dateCreated,
            shippedAt: order.shippedAt,
            deliveredAt: order.deliveredAt
        ]
    }
}

