package nl.rhaydus.feature.settings

import org.jetbrains.exposed.v1.core.Table

object SettingsTable : Table("settings") {
    val userId = integer("user_id")
    val theme = varchar("theme", 32)
    val updatedAt = long("updated_at")

    override val primaryKey: PrimaryKey = PrimaryKey(userId)
}