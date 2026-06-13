package nl.rhaydus.feature.editions

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import nl.rhaydus.core.mapping.toBookEdition
import nl.rhaydus.core.model.BookEdition
import nl.rhaydus.core.model.orNotFound
import nl.rhaydus.graphql.GetBookIdByEditionIdQuery
import nl.rhaydus.graphql.GetEditionByIsbnQuery
import nl.rhaydus.graphql.GetEditionsByIdsQuery
import nl.rhaydus.hardcover.HardcoverClient
import java.time.Duration

class EditionDataSourceImpl(
    private val hardcoverClient: HardcoverClient,
) : EditionDataSource {
    private val bookIdEditionIdCache: AsyncCache<Int, Int> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays(1))
        .maximumSize(10_000)
        .buildAsync()

    private val editionCache: AsyncCache<Int, BookEdition> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofDays(1))
        .maximumSize(10_000)
        .buildAsync()

    private val isbnMatchCache: AsyncCache<String, IsbnEditionMatchResponse> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays(1))
        .maximumSize(10_000)
        .buildAsync()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun getBookIdByEditionId(
        editionId: Int,
        token: String,
    ): Int {
        val bookId = bookIdEditionIdCache.get(editionId) { key, _ ->
            scope.future { fetchBookIdByEditionId(key, token) }
        }.await()

        return bookId
    }

    override suspend fun getEditionsByIds(
        ids: List<Int>,
        token: String,
    ): List<BookEdition> {
        // TODO: Maybe throw an error here instead, as an empty list of ids should fail?
        if (ids.isEmpty()) return emptyList()

        val byId: Map<Int, BookEdition> = editionCache.getAll(ids.toSet()) { missing, _ ->
            scope.future {
                fetchEditionsByIds(missing.toList(), token).associateBy { it.id }
            }
        }.await()

        return ids.mapNotNull { byId[it] }
    }

    override suspend fun getEditionByIsbn(
        isbn: String,
        token: String,
    ): IsbnEditionMatchResponse {
        return isbnMatchCache.get(isbn) { key, _ ->
            scope.future { fetchEditionByIsbn(key, token) }
        }.await()
    }

    // region Network fetching
    private suspend fun fetchEditionByIsbn(
        isbn: String,
        token: String,
    ): IsbnEditionMatchResponse {
        val edition = hardcoverClient.query(
            token = token,
            query = GetEditionByIsbnQuery(isbn = isbn),
        )
            .editions
            .firstOrNull()
            .orNotFound(message = "No edition was found for isbn10/13 = $isbn")

        return IsbnEditionMatchResponse(
            bookId = edition.book_id,
            editionId = edition.id,
        )
    }

    private suspend fun fetchEditionsByIds(
        ids: List<Int>,
        token: String,
    ): List<BookEdition> {
        return ids.chunked(200).flatMap { chunk ->
            hardcoverClient
                .query(token = token, query = GetEditionsByIdsQuery(ids = chunk))
                .orNotFound(message = "No editions were found")
                .editions
                .map { it.editionDetailFragment.toBookEdition() }
        }
    }

    private suspend fun fetchBookIdByEditionId(
        editionId: Int,
        token: String,
    ): Int {
        return hardcoverClient.query(
            token = token,
            query = GetBookIdByEditionIdQuery(editionId = editionId),
        )
            .editions
            .firstOrNull()
            ?.book_id
            .orNotFound(message = "No book matching edition with id=$editionId was found")
    }
    // endregion
}