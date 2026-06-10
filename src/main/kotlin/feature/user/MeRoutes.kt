package nl.rhaydus.feature.user

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.core.model.UserPrincipal
import nl.rhaydus.hardcover.HardcoverClient

fun Route.meRoutes(client: HardcoverClient) {
    get("/me") {
        val token: String = call.principal<UserPrincipal>()?.token
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val user: HardcoverUser = client.resolveUser(token = token)
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        call.respond(user)
    }
}