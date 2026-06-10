package nl.rhaydus.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import nl.rhaydus.core.model.UserPrincipal
import nl.rhaydus.hardcover.hardcoverClient

fun Application.configureAuthentication() {
    val hardcoverClient = hardcoverClient

    install(Authentication) {
        bearer("external") {
            authenticate { credential ->
                hardcoverClient.resolveUser(token = credential.token)?.let { user ->
                    UserPrincipal(
                        userId = user.id,
                        token = credential.token,
                    )
                }
            }
        }
    }
}