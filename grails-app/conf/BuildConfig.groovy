grails.servlet.version = "3.0"
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.work.dir = "target/work"

// Grails 2.5.6 runs on JDK 7/8 only. Groovy 2.4.x cannot emit or run
// bytecode above 1.8, so these levels must not be raised.
grails.project.target.level = 1.7
grails.project.source.level = 1.7

grails.project.fork = [
    test   : [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, daemon: true],
    run    : [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve: false],
    war    : [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256, forkReserve: false],
    console: [maxMemory: 768, minMemory: 64, debug: false, maxPerm: 256]
]

grails.project.dependency.resolver = "maven"
grails.project.dependency.resolution = {

    inherits("global") {
    }
    log "warn"

    repositories {
        inherits true

        grailsCentral()
        mavenLocal()
        mavenCentral()
        mavenRepo "https://repo.grails.org/grails/core"
        mavenRepo "https://repo.grails.org/grails/plugins"
    }

    dependencies {
        // Legacy JDBC drivers: the 1.x MariaDB client is the last line that
        // targets the JDBC 4.1 surface Hibernate 3.6 expects.
        runtime "org.mariadb.jdbc:mariadb-java-client:1.5.9"
        runtime "com.h2database:h2:1.3.176"
    }

    plugins {
        build ":tomcat:8.0.50"

        compile ":cache:1.1.8"

        // Hibernate 3.6.10 via the Grails 2 hibernate plugin (GORM 3.x).
        runtime ":hibernate:3.6.10.19"
    }
}
