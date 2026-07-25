# Configuration & Build

## 1. `gradle.properties`

```properties
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk   # Force Gradle to use Java 17
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8  # 2 GB heap for the build daemon

grailsVersion=6.1.2           # Tells the Grails Gradle plugin which Grails BOM to use
gormVersion=8.1.1             # GORM version — controls hibernate plugin version resolution
grailsGradlePluginVersion=6.1.2
```

**Why Java 17 specifically?** Gradle 7.6.4 (which this project uses) officially supports Java 8–19. The system JDK is Java 26, which Gradle 7.x cannot run on. Pinning to Java 17 here makes `./gradlew` always use the right JDK regardless of the shell's JAVA_HOME.

---

## 2. `build.gradle`

```groovy
buildscript {
    repositories {
        mavenCentral()
        maven { url "https://repo.grails.org/grails/core" }   // Grails artifacts live here
    }
    dependencies {
        // These three plugins wire up the entire Grails build pipeline
        classpath "org.grails:grails-gradle-plugin:6.1.2"
        classpath "com.bertramlabs.plugins:asset-pipeline-gradle:4.3.0"
        classpath "org.springframework.boot:spring-boot-gradle-plugin:2.7.18"
    }
}
```

The `buildscript` block is evaluated before the rest of `build.gradle`. The classpath entries here are the Gradle plugins themselves — not the application's runtime dependencies.

```groovy
configurations {
    profile    // Grails profile — declares this is a 'web' application
    console    // Grails console (REPL) classpath
    // Note: 'developmentOnly' and 'runtimeClasspath' are intentionally NOT declared here.
    // Spring Boot's Gradle plugin creates them automatically.
    // Declaring them here again causes a "configuration already exists" build error.
}
```

```groovy
apply plugin: "org.grails.grails-web"   // Adds Groovy compilation, GORM, Spring MVC wiring
apply plugin: "com.bertramlabs.asset-pipeline"  // Static asset compilation (CSS/JS)
apply plugin: "war"                     // Packages as WAR file for servlet container deployment
```

```groovy
dependencies {
    profile "org.grails.profiles:web"                         // Grails web profile BOM

    implementation "org.grails:grails-core"                   // Core Grails runtime
    implementation "org.grails:grails-plugin-rest"            // REST response rendering
    implementation "org.grails.plugins:hibernate5"            // GORM-Hibernate5 bridge
    implementation "org.hibernate:hibernate-core:5.6.15.Final"// JPA provider

    runtimeOnly "org.mariadb.jdbc:mariadb-java-client:3.3.3"  // MariaDB JDBC driver
    runtimeOnly "com.h2database:h2"                           // H2 for test environment
    runtimeOnly "org.apache.tomcat:tomcat-jdbc"               // Connection pool

    testImplementation "org.grails:grails-gorm-testing-support"
    testImplementation "org.grails:grails-web-testing-support"
}
```

```groovy
bootRun {
    jvmArgs("-Dspring.output.ansi.enabled=always", "-noverify", "-XX:TieredStopAtLevel=1")
    // -noverify and TieredStopAtLevel=1 make startup faster during development
    sourceResources sourceSets.main  // Allows hot-reload of resources without recompile
}
```

---

## 3. `application.yml`

```yaml
grails:
  gorm:
    failOnError: true   # GORM throws exception on save failure instead of returning false
                        # Without this, book.save() silently does nothing on validation failure
```

```yaml
environments:
  development:
    dataSource:
      dbCreate: update   # Hibernate auto-alters schema to match domain classes
                         # Safe for dev; NEVER use in production (data loss risk)
      url: jdbc:mariadb://localhost:3306/bookstore_db?createDatabaseIfNotExist=true&...
      username: developer
      password: dev_password_123
  test:
    dataSource:
      dbCreate: create-drop   # Creates schema before tests, drops after — clean slate
      url: jdbc:h2:mem:testDb;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE
      driverClassName: org.h2.Driver
      username: sa
      password: ""
      dialect: org.hibernate.dialect.H2Dialect
  production:
    dataSource:
      dbCreate: none          # Schema is NOT touched — migrations run separately
      url: ${DB_URL:jdbc:mariadb://localhost:3306/bookstore_db?...}
      username: ${DB_USER:developer}
      password: ${DB_PASSWORD}    # No default — must be set in environment

dataSource:               # Shared defaults applied to all environments
  pooled: true
  jmxExport: true
  driverClassName: org.mariadb.jdbc.Driver
  dialect: org.hibernate.dialect.MariaDB103Dialect
  properties:
    initialSize: 5        # Connection pool opens 5 connections on startup
    maxActive: 50         # Maximum concurrent connections
```

**How `${DB_URL:default}` works:** This is Spring's `@Value`-style property substitution. If the environment variable `DB_URL` is set, it uses that. If not, it falls back to the default after the colon. In production, `DB_PASSWORD` has no default — the application will fail to start if the environment variable is missing, which is intentional (fail fast, not silently).

---

## 4. `UrlMappings.groovy`

```groovy
class UrlMappings {
    static mappings = {

        // resources: 'book' generates 7 routes in total:
        //   GET    /api/v1/books           → index
        //   POST   /api/v1/books           → save
        //   GET    /api/v1/books/{id}      → show
        //   PUT    /api/v1/books/{id}      → update  (PATCH also maps here)
        //   DELETE /api/v1/books/{id}      → delete
        //   GET    /api/v1/books/create    → create  (browser-form scaffold, unused here)
        //   GET    /api/v1/books/{id}/edit → edit    (browser-form scaffold, unused here)
        // The create and edit routes exist but are irrelevant in a JSON-only API
        // because static responseFormats = ['json'] on the controller rejects HTML requests.
        "/api/v1/books"(resources: 'book')

        // Custom routes that the 'resources' shorthand doesn't cover
        "/api/v1/books/search"(controller: "book", action: "search", method: "GET")
        "/api/v1/books/isbn/$isbn"(controller: "book", action: "findByIsbn", method: "GET")
        // $isbn becomes params.isbn inside the controller action
        "/api/v1/books/low-stock"(controller: "book", action: "lowStock", method: "GET")

        "/api/v1/authors"(resources: 'author')     // full CRUD
        "/api/v1/categories"(resources: 'category') // full CRUD

        // Orders: excludes: ['delete'] means DELETE /api/v1/orders/{id} is not routed.
        // Cancellation is an explicit business action, not a generic HTTP DELETE.
        "/api/v1/orders"(resources: 'order', excludes: ['delete'])
        "/api/v1/orders/$id/cancel"(controller: "order", action: "cancel", method: "POST")
        "/api/v1/orders/customer/$customerId"(controller: "order", action: "byCustomer", method: "GET")
        "/api/v1/orders/status/$status"(controller: "order", action: "byStatus", method: "GET")
        "/api/v1/orders/$id/status"(controller: "order", action: "updateStatus", method: "PATCH")

        "500"(view: '/error')     // Grails renders error.gsp for 500s
        "404"(view: '/notFound')  // Grails renders notFound.gsp for 404s
    }
}
```

**Why centralised routing matters:** In Spring Boot, every controller method has its own `@GetMapping`/`@PostMapping` annotation. In Grails, all routes are in this one file. This makes it trivial to audit every endpoint the application exposes.
