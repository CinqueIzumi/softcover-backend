package nl.rhaydus.core.model

import kotlinx.serialization.Serializable

@Serializable
data class BookEdition(
    val id: Int,
    val canonicalId: Int?,
    val bookId: Int,
    val title: String?,
    val url: String?, // TODO: Falls back to first of images[]
    val publisher: String?,
    val isbn10: String?,
    val isbn13: String?,
    val pages: Int?,
    val audioSeconds: Int?,
    val authors: List<Author>,
    val releaseYear: Int,
    val releaseDate: String?,
    val format: String,
    val readingFormatId: Int?,
)