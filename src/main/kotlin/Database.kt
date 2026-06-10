package nl.rhaydus

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.util.AttributeKey
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.math.cbrt

val DatabaseKey = AttributeKey<Database>("Database")

object SettingsTable : Table("settings") {
    val userId = integer("user_id")
    val theme = varchar("theme", 32)
    val updatedAt = long("updated_at")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}

fun Application.configureDatabases() {
    val dataSource = hikari()

    val db = Database.connect(dataSource)

    attributes.put(DatabaseKey, db)

    // TODO: This is supposed to be dev only. Alternatives suggested were Flyway/Liquibase, investigate these?
    transaction { SchemaUtils.create(SettingsTable) }
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