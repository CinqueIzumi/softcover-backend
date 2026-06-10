package nl.rhaydus.hardcover

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.api.ApolloResponse
import com.apollographql.apollo.api.Mutation
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.Query
import com.apollographql.apollo.exception.ApolloException
import com.apollographql.apollo.exception.ApolloHttpException
import nl.rhaydus.core.model.HardcoverException

class HardcoverClient(
    private val apollo: ApolloClient,
) {
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