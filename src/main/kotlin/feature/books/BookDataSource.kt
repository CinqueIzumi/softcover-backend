package nl.rhaydus.feature.books

import nl.rhaydus.core.model.Book

interface BookDataSource {
    suspend fun getBookById(id: Int, token: String): Book
}