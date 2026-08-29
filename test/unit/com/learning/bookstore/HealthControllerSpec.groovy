package com.learning.bookstore

import grails.converters.JSON
import grails.test.mixin.TestFor
import org.h2.jdbcx.JdbcDataSource
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.SQLException

@TestFor(HealthController)
class HealthControllerSpec extends Specification {

    void "a reachable database reports UP with 200"() {
        given:
        JdbcDataSource h2 = new JdbcDataSource()
        h2.setURL("jdbc:h2:mem:healthSpec;DB_CLOSE_DELAY=-1")
        h2.user = "sa"
        h2.password = ""
        controller.dataSource = h2

        when:
        controller.index()
        Map body = JSON.parse(response.text) as Map

        then:
        response.status == 200
        body.status == "UP"
        body.checks.database.status == "UP"
        body.checks.database.latencyMs >= 0
    }

    void "an unreachable database reports DOWN with 503"() {
        given:
        controller.dataSource = { throw new SQLException("connection refused") } as DataSource

        when:
        controller.index()
        Map body = JSON.parse(response.text) as Map

        then:
        response.status == 503
        body.status == "DOWN"
        body.checks.database.error.contains("connection refused")
    }

    void "the probe reports the build metadata the pipeline stamps in"() {
        given:
        JdbcDataSource h2 = new JdbcDataSource()
        h2.setURL("jdbc:h2:mem:healthSpec;DB_CLOSE_DELAY=-1")
        h2.user = "sa"
        controller.dataSource = h2

        when:
        controller.index()
        Map body = JSON.parse(response.text) as Map

        then:
        body.containsKey("version")
        body.containsKey("commit")
        body.environment == "test"
    }
}
