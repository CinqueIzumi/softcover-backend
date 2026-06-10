package nl.rhaydus.hardcover

import com.apollographql.apollo.ApolloClient
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import nl.rhaydus.feature.user.HardcoverUser
import nl.rhaydus.graphql.MeQuery
import java.time.Duration

class HardcoverClient(
    private val apollo: ApolloClient,
) {
    private val cache: AsyncCache<String, HardcoverUser?> = Caffeine
        .newBuilder()
        .expireAfterWrite(Duration.ofMinutes(5))
        .maximumSize(10_000)
        .buildAsync()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    suspend fun resolveUser(token: String): HardcoverUser? {
        return cache.get(token) { key, _ ->
            scope.future { fetchUser(token = key) }
        }.await()
    }

    private suspend fun fetchUser(token: String): HardcoverUser? {
        val response = apollo
            .query(MeQuery())
            .addHttpHeader("Authorization", "Bearer $token")
            .execute()

        if (response.hasErrors()) return null

        val me = response.data?.me?.firstOrNull() ?: return null

        return HardcoverUser(
            id = me.id,
            username = me.username,
        )
    }
}