package nl.rhaydus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Tag(
    val id: Int,
    val name: String,
    val category: String,
    val count: Int,
)