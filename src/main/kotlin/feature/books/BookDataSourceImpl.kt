package nl.rhaydus.feature.books

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import nl.rhaydus.core.mapping.toBook
import nl.rhaydus.core.model.Book
import nl.rhaydus.core.model.orNotFound
import nl.rhaydus.graphql.GetBookByIdQuery
import nl.rhaydus.hardcover.HardcoverClient
import java.time.Duration
import java.util.concurrent.CompletableFuture

class BookDataSourceImpl(
    private val client: HardcoverClient,
) : BookDataSource {
    private val cache: AsyncCache<Int, Book> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofDays(1))
        .maximumSize(10_000)
        .buildAsync()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun getBookById(
        id: Int,
        token: String,
    ): Book {
        val book = cache.get(id) { key, _ ->
            scope.future { resolveBook(id = key, token = token) }
        }.await()

        if (book.id != id) {
            cache.asMap().putIfAbsent(book.id, CompletableFuture.completedFuture(book))
        }

        return book
    }

    private suspend fun resolveBook(
        id: Int,
        token: String,
    ): Book {
        val initialBook = fetchBookById(id = id, token = token)

        return if (initialBook.canonicalId != null && initialBook.canonicalId != id) {
            fetchBookById(id = initialBook.canonicalId, token = token)
        } else {
            initialBook
        }
    }

    private suspend fun fetchBookById(
        id: Int,
        token: String,
    ): Book {
        val book = client.query(
            token = token,
            query = GetBookByIdQuery(id = id),
        )
            .books
            .firstOrNull()
            ?.bookDetailFragment
            .orNotFound(message = "No book found for id $id")
            .toBook()

        return book
    }
}