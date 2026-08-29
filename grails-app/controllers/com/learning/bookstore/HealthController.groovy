package com.learning.bookstore

import grails.converters.JSON
import grails.util.Environment
import grails.util.Metadata
import groovy.sql.Sql

/**
 * Liveness and readiness in one probe: the process answers, and it can still
 * reach the database. The deploy pipeline gates on this rather than on a
 * business endpoint, so "healthy" stays independent of whether any books exist.
 */
class HealthController {

    def dataSource

    def index() {
        Map database = checkDatabase()
        boolean up = (database.status == 'UP')
        Metadata metadata = Metadata.current

        response.status = up ? 200 : 503
        render([
            status     : up ? 'UP' : 'DOWN',
            application: metadata.getApplicationName(),
            version    : metadata.getApplicationVersion(),
            commit     : metadata.getProperty('app.commit') ?: 'unknown',
            build      : metadata.getProperty('app.build') ?: 'unknown',
            environment: Environment.current.name,
            checks     : [database: database],
            timestamp  : new Date()
        ] as JSON)
    }

    private Map checkDatabase() {
        long started = System.currentTimeMillis()
        try {
            new Sql(dataSource).firstRow('SELECT 1')
            [status: 'UP', latencyMs: System.currentTimeMillis() - started]
        } catch (Exception e) {
            log.error("Health probe could not reach the database", e)
            [status: 'DOWN', latencyMs: System.currentTimeMillis() - started, error: e.message]
        }
    }
}
