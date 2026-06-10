package nl.rhaydus

import nl.rhaydus.model.AppTheme
import nl.rhaydus.model.UserSettings
import nl.rhaydus.table.SettingsTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.upsert

interface SettingsRepository {
    suspend fun getUserSettings(userId: Int): UserSettings

    suspend fun saveUserSettings(userId: Int, userSettings: UserSettings)
}

class PersistentSettingsRepositoryImpl(private val db: Database) : SettingsRepository {
    override suspend fun getUserSettings(userId: Int): UserSettings {
        return suspendTransaction(db = db) {
            SettingsTable
                .selectAll()
                .where { SettingsTable.userId eq userId }
                .map { row ->
                    UserSettings(
                        theme = AppTheme.valueOf(row[SettingsTable.theme]),
                        updatedAt = row[SettingsTable.updatedAt],
                    )
                }
                .singleOrNull()
                ?: UserSettings()
        }
    }

    override suspend fun saveUserSettings(
        userId: Int,
        userSettings: UserSettings
    ) {
        suspendTransaction(db = db) {
            SettingsTable.upsert {
                it[SettingsTable.userId] = userId
                it[theme] = userSettings.theme.name
                it[updatedAt] = userSettings.updatedAt
            }
        }
    }
}