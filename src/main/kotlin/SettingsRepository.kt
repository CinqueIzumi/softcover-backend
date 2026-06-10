package nl.rhaydus

import nl.rhaydus.model.UserSettings
import java.util.concurrent.ConcurrentHashMap

interface SettingsRepository {
    fun getUserSettings(userId: Int): UserSettings

    fun saveUserSettings(userId: Int, userSettings: UserSettings)
}

class MemorySettingsRepositoryImpl : SettingsRepository {
    val settingsMap = ConcurrentHashMap<Int, UserSettings>()

    override fun getUserSettings(userId: Int): UserSettings {
        return settingsMap.getOrDefault(userId, UserSettings())
    }

    override fun saveUserSettings(userId: Int, userSettings: UserSettings) {
        settingsMap[userId] = userSettings
    }
}