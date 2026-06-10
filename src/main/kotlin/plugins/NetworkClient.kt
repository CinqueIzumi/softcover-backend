package nl.rhaydus.plugins

import com.apollographql.apollo.ApolloClient
import io.ktor.server.application.*
import nl.rhaydus.feature.user.UserDataSourceImpl
import nl.rhaydus.feature.user.setUserDataSource
import nl.rhaydus.hardcover.HardcoverClient
import nl.rhaydus.hardcover.setHardcoverClient

fun Application.configureGraphQLClient() {
    val graphqlUrl = environment.config.property("hardcover.graphqlUrl").getString()

    val apollo = ApolloClient.Builder().serverUrl(graphqlUrl).build()

    val client = HardcoverClient(apollo)

    setHardcoverClient(client)
    setUserDataSource(UserDataSourceImpl(client))

    // Tie the client's lifetime to the application's — one instance, closed on shutdown.
    monitor.subscribe(ApplicationStopping) {
        apollo.close()
    }
}
