package nl.rhaydus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BookSeries(
    val id: Int,
    val name: String,
    val amountOfBooks: Int,
)