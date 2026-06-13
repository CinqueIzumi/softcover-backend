package nl.rhaydus.feature.editions

import kotlinx.serialization.Serializable

@Serializable
data class IsbnEditionMatchResponse(
    val bookId: Int,
    val editionId: Int,
)