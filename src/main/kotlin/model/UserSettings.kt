package nl.rhaydus.model

import kotlinx.serialization.Serializable

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

@Serializable
data class UserSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val updatedAt: Long = 0L,
)