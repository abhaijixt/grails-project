package com.learning.bookstore

import grails.converters.JSON

class ApiResponseService {
    JSON success(Object data, String message = "Success") {
        [
            success: true,
            message: message,
            data: data,
            timestamp: new Date()
        ] as JSON
    }

    JSON error(String error, String message = "Error") {
        [
            success: false,
            message: message,
            error: error,
            timestamp: new Date()
        ] as JSON
    }
}

