package com.learning.bookstore

enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    REFUNDED

    boolean canTransitionTo(OrderStatus target) {
        switch (this) {
            case PENDING: return target in [CONFIRMED, CANCELLED]
            case CONFIRMED: return target in [PROCESSING, CANCELLED]
            case PROCESSING: return target == SHIPPED
            case SHIPPED: return target == DELIVERED
            case DELIVERED: return target == REFUNDED
            default: return false
        }
    }
}

