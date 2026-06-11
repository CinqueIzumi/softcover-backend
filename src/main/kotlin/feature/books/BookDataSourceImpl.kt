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
import nl.rhaydus.graphql.GetBooksByIdsQuery
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
            cacheBookUnderOwnId(book = book)
        }

        return book
    }

    override suspend fun getBooksByIds(
        ids: List<Int>,
        token: String,
    ): List<Book> {
        // TODO: Maybe throw an error here instead, as an empty list of ids should fail?
        if (ids.isEmpty()) return emptyList()

        val byId: Map<Int, Book> = cache.getAll(ids.toSet()) { missing, _ ->
            scope.future {
                val fetched = fetchBooksByIds(missing.toList(), token)
                val resolved = resolveCanonicalBooks(fetched) { canonicalIds ->
                    fetchBooksByIds(canonicalIds, token)
                }

                fetched.indices.associate { i -> fetched[i].id to resolved[i] }
            }
        }.await()

        byId.values.forEach(::cacheBookUnderOwnId)

        return ids.mapNotNull { byId[it] }
    }

    private fun cacheBookUnderOwnId(book: Book) {
        cache.asMap().putIfAbsent(book.id, CompletableFuture.completedFuture(book))
    }

    private suspend fun fetchBooksByIds(
        ids: List<Int>,
        token: String,
    ): List<Book> {
        return ids.chunked(200).flatMap { chunk ->
            client
                .query(token = token, query = GetBooksByIdsQuery(ids = chunk))
                .books
                .map { it.bookDetailFragment.toBook() }
        }
    }

    private suspend fun resolveBook(
        id: Int,
        token: String,
    ): Book {
        val book = fetchBookById(id = id, token = token)

        return resolveCanonicalBooks(books = listOf(book)) { ids ->
            ids.map { fetchBookById(id = id, token = token) }
        }.first()
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

    private suspend fun resolveCanonicalBooks(
        books: List<Book>,
        fetchByIds: suspend (List<Int>) -> List<Book>,
    ): List<Book> {
        val redirects = books.filter { it.isCanonicalRedirect() }
        if (redirects.isEmpty()) return books

        val present = books.associateBy { it.id }

        // Ensure canonical ids, which are already in the redirects list, are skipped to prevent fetching duplicates
        val canonicalIds = redirects
            .mapNotNull { it.canonicalId }
            .distinct()
            .filter { it !in present }

        val survivors = (present + fetchByIds(canonicalIds).associateBy { it.id })

        return books.map { book ->
            if (book.isCanonicalRedirect()) survivors[book.canonicalId] ?: book else book
        }
    }

    private fun Book.isCanonicalRedirect(): Boolean = canonicalId != null && canonicalId != id
}