package nl.rhaydus.feature.user

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.plugins.userPrincipal

fun Route.meRoutes(dataSource: UserDataSource) {
    get("/me") {
        val user: HardcoverUser = dataSource.resolveUser(token = call.userPrincipal.token)
            ?: return@get call.respond(HttpStatusCode.Unauthorized)

        call.respond(user)
    }
}