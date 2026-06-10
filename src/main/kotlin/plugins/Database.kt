package nl.rhaydus.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.util.*
import nl.rhaydus.feature.settings.SettingsTable
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

fun Application.configureDatabases() {
    val dataSource = hikari()

    val db = Database.connect(dataSource)

    setDatabase(db)

    // TODO: This is supposed to be dev only. Alternatives suggested were Flyway/Liquibase, investigate these?
    transaction {
        SchemaUtils.create(SettingsTable)
    }
}

private fun Application.hikari(): HikariDataSource {
    val config = HikariConfig().apply {
        jdbcUrl = environment.config.property("storage.jdbcUrl").getString()
        driverClassName = environment.config.property("storage.driverClassName").getString()
        username = environment.config.property("storage.user").getString()
        password = environment.config.property("storage.password").getString()
        maximumPoolSize = 10
    }

    return HikariDataSource(config)
}

private val DatabaseKey = AttributeKey<Database>("Database")

val Application.database: Database
    get() = attributes[DatabaseKey]

fun Application.setDatabase(db: Database) = attributes.put(DatabaseKey, db)
