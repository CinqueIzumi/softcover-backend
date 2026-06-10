package nl.rhaydus.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import nl.rhaydus.core.model.UserPrincipal
import nl.rhaydus.feature.user.userDataSource

fun Application.configureAuthentication() {
    val userDataSource = userDataSource

    install(Authentication) {
        bearer("external") {
            authenticate { credential ->
                userDataSource.resolveUser(token = credential.token)?.let { user ->
                    UserPrincipal(
                        userId = user.id,
                        token = credential.token,
                    )
                }
            }
        }
    }
}