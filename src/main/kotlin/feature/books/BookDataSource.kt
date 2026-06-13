package nl.rhaydus.feature.books

import nl.rhaydus.core.model.Book
import nl.rhaydus.core.model.BookEdition

interface BookDataSource {
    suspend fun getBookById(id: Int, token: String): Book

    suspend fun getBooksByIds(ids: List<Int>, token: String): List<Book>

    suspend fun getEditionsByBookId(bookId: Int, token: String): List<BookEdition>
}