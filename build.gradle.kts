plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.apollo)
}

group = "nl.rhaydus"
version = "1.0.0-SNAPSHOT"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

apollo {
    service("hardcover") {
        packageName.set("nl.rhaydus.graphql")

        // Hasura custom scalars → Kotlin types (add more as new queries use them, e.g. bigint/timestamptz).
        mapScalarToKotlinString("citext")

        // One-time schema fetch (introspection needs your personal token):
        //   HARDCOVER_TOKEN=<token> ./gradlew downloadHardcoverApolloSchemaFromIntrospection
        // The downloaded schema.graphqls is committed; the token is never stored in the build.
        introspection {
            endpointUrl.set("https://api.hardcover.app/v1/graphql")
            headers.put("Authorization", "Bearer " + (System.getenv("HARDCOVER_TOKEN") ?: ""))
            schemaFile.set(file("src/main/graphql/schema.graphqls"))
        }
    }
}
dependencies {
    implementation(ktorLibs.serialization.kotlinx.json)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.resources)
    implementation(ktorLibs.server.statusPages)
    implementation(libs.logback.classic)

    // Database storage
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)
    implementation(libs.h2)
    implementation(libs.hikari)

    // GraphQL (Hardcover API)
    implementation(libs.apollo.runtime)

    // Authentication
    implementation(ktorLibs.server.auth)

    // Caching
    implementation(libs.caffeine)

    // Testing
    testImplementation(kotlin("test"))
    testImplementation(ktorLibs.server.testHost)
}
