package nl.rhaydus.feature.settings

interface SettingsRepository {
    suspend fun getUserSettings(userId: Int): UserSettings

    suspend fun saveUserSettings(userId: Int, userSettings: UserSettings)
}