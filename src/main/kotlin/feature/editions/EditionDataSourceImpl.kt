package nl.rhaydus.feature.editions

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import nl.rhaydus.core.model.orNotFound
import nl.rhaydus.graphql.GetBookIdByEditionIdQuery
import nl.rhaydus.hardcover.HardcoverClient
import java.time.Duration

class EditionDataSourceImpl(
    private val hardcoverClient: HardcoverClient,
) : EditionDataSource {
    private val cache: AsyncCache<Int, Int> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays(1))
        .maximumSize(10_000)
        .buildAsync()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun getBookIdByEditionId(
        editionId: Int,
        token: String,
    ): Int {
        val bookId = cache.get(editionId) { key, _ ->
            scope.future { fetchBookIdByEditionId(key, token) }
        }.await()

        return bookId
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
}