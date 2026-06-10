package nl.rhaydus.feature.user

import kotlinx.serialization.Serializable

@Serializable
data class HardcoverUser(
    val id: Int,
    val username: String?,
)