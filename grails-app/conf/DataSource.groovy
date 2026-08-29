dataSource {
    pooled = true
    jmxExport = true
    driverClassName = "org.mariadb.jdbc.Driver"
    // Hibernate 3.6.10 ships no MariaDB dialect; MySQL5InnoDBDialect is the
    // supported way to talk to MariaDB from this Hibernate line.
    dialect = "org.hibernate.dialect.MySQL5InnoDBDialect"
    properties {
        jmxEnabled = true
        initialSize = 5
        maxActive = 50
    }
}

hibernate {
    cache.use_second_level_cache = false
    cache.use_query_cache = false
}

// No password appears in this file. Development reads it from DEV_DB_PASSWORD or
// from the external config at ~/.grails/bookstore-local.groovy (wired up in
// Config.groovy); production reads DB_PASSWORD from the container environment.
environments {
    development {
        dataSource {
            dbCreate = "update"
            // A database of its own: development owns its schema (dbCreate
            // rewrites it), and production must never be on the other end of
            // that. They shared bookstore_db until 2026-08-29.
            url = System.getenv("DEV_DB_URL") ?: "jdbc:mariadb://localhost:3306/bookstore_db_dev?createDatabaseIfNotExist=true&useSSL=false"
            username = System.getenv("DEV_DB_USER") ?: "bookstore_dev"
            password = System.getenv("DEV_DB_PASSWORD")
        }
    }
    test {
        dataSource {
            dbCreate = "create-drop"
            url = "jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE"
            driverClassName = "org.h2.Driver"
            dialect = "org.hibernate.dialect.H2Dialect"
            username = "sa"
            password = ""
        }
    }
    production {
        dataSource {
            // dbCreate must be 'none' in production; run migrations explicitly.
            // bookstore_app is granted DML only, so a stray dbCreate cannot
            // reshape the schema even if this is changed by accident.
            dbCreate = "none"
            url = System.getenv("DB_URL") ?: "jdbc:mariadb://localhost:3306/bookstore_db?useSSL=false"
            username = System.getenv("DB_USER") ?: "bookstore_app"
            password = System.getenv("DB_PASSWORD")
        }
    }
}
