package nl.rhaydus.feature.editions

import nl.rhaydus.core.model.BookEdition

interface EditionDataSource {
    suspend fun getBookIdByEditionId(
        editionId: Int,
        token: String,
    ): Int

    suspend fun getEditionsByIds(
        ids: List<Int>,
        token: String,
    ): List<BookEdition>

    suspend fun getEditionByIsbn(
        isbn: String,
        token: String,
    ): IsbnEditionMatchResponse
}