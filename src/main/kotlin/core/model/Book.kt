package nl.rhaydus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Book(
    val id: Int,
    val canonicalId: Int?,
    val title: String,
    val headline: String,
    val description: String,
    val rating: Double,
    val releaseYear: Int,
    val releaseDate: String?,
    val coverUrl: String,
    val usersCount: Int,
    val ratingsCount: Int,
    val isCompilation: Boolean,
    val authors: List<Author>,
    val bookSeries: BookSeries?,
    val positionsInSeries: List<Double>,
    val tags: List<Tag>,
    val editions: List<BookEdition>,
    val defaultEdition: BookEdition?,
)
