package nl.rhaydus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Author(
    val id: Int,
    val name: String,
)