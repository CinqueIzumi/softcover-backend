package nl.rhaydus.hardcover

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import io.ktor.utils.io.*
import nl.rhaydus.core.model.HardcoverException

class HardcoverClient(
    private val apollo: ApolloClient,
) {
    suspend fun <D : Query.Data> queryCatching(
        token: String,
        query: Query<D>,
    ): Result<D> = runCancellable { query(token, query) }

    suspend fun <D : Mutation.Data> mutateCatching(
        token: String,
        mutation: Mutation<D>,
    ): Result<D> = runCancellable { mutate(token, mutation) }

    suspend fun <D : Query.Data> query(
        token: String,
        query: Query<D>,
    ): D {
        val response = try {
            apollo.query(query).addHttpHeader("Authorization", "Bearer $token").execute()
        } catch (e: ApolloException) {
            throw e.toHardcoverException()
        }

        return response.dataOrThrowCustom()
    }

    suspend fun <D : Mutation.Data> mutate(
        token: String,
        mutation: Mutation<D>,
    ): D {
        val response = try {
            apollo.mutation(mutation).addHttpHeader("Authorization", "Bearer $token").execute()
        } catch (e: ApolloException) {
            throw e.toHardcoverException()
        }

        return response.dataOrThrowCustom()
    }

    private suspend fun <T> runCancellable(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun <D : Operation.Data> ApolloResponse<D>.dataOrThrowCustom(): D {
        if (hasErrors()) {
            val errorMessage = errors?.joinToString { it.message } ?: "No errors were found"

            throw HardcoverException.GraphQLError(message = errorMessage)
        }

        return data ?: throw HardcoverException.EmptyResponse()
    }

    private fun ApolloException.toHardcoverException(): HardcoverException {
        return when {
            this is ApolloHttpException && statusCode == 401 -> HardcoverException.Unauthorized()
            else -> HardcoverException.Unavailable(this)
        }
    }
}