package nl.rhaydus.feature.user

import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import nl.rhaydus.graphql.MeQuery
import nl.rhaydus.hardcover.HardcoverClient
import java.time.Duration

class UserDataSourceImpl(
    private val client: HardcoverClient,
) : UserDataSource {
    private val cache: AsyncCache<String, HardcoverUser?> = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(10_000)
        .buildAsync()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override suspend fun resolveUser(token: String): HardcoverUser? {
        return cache.get(token) { key, _ ->
            scope.future { fetchUser(key) }
        }.await()
    }

    private suspend fun fetchUser(token: String): HardcoverUser? {
        return try {
            client.query(token, MeQuery()).me.firstOrNull()?.let { HardcoverUser(it.id, it.username) }
        } catch (_: Exception) {
            null
        }
    }
}