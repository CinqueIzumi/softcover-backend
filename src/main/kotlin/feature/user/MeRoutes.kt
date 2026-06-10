package nl.rhaydus.feature.user

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.core.model.UserPrincipal

fun Route.meRoutes(dataSource: UserDataSource) {
    get("/me") {
        val token: String = call.principal<UserPrincipal>()?.token
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val user: HardcoverUser = dataSource.resolveUser(token = token)
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        call.respond(user)
    }
}