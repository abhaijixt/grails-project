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

environments {
    development {
        dataSource {
            dbCreate = "update"
            url = "jdbc:mariadb://localhost:3306/bookstore_db?createDatabaseIfNotExist=true&useSSL=false"
            username = "developer"
            password = "dev_password_123"
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
            // dbCreate must be 'none' in production; run migrations explicitly
            dbCreate = "none"
            url = System.getenv("DB_URL") ?: "jdbc:mariadb://localhost:3306/bookstore_db?useSSL=false"
            username = System.getenv("DB_USER") ?: "developer"
            password = System.getenv("DB_PASSWORD")
        }
    }
}
