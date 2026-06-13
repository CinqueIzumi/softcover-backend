package nl.rhaydus.feature.editions

interface EditionDataSource {
    suspend fun getBookIdByEditionId(
        editionId: Int,
        token: String,
    ): Int
}