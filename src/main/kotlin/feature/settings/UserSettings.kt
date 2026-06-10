package nl.rhaydus.feature.settings

import kotlinx.serialization.Serializable

@Serializable
data class UserSettings(
    val theme: AppTheme = AppTheme.SYSTEM,
    val updatedAt: Long = 0L,
)