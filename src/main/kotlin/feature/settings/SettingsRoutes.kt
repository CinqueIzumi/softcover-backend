package nl.rhaydus.feature.settings

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.core.model.UserPrincipal

fun Route.settingsRoutes(repo: SettingsRepository) {
    route("/settings") {
        get {
            val userId = call.principal<UserPrincipal>()?.userId
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val settings = repo.getUserSettings(userId = userId)
            call.respond(settings)
        }

        put {
            val userId = call.principal<UserPrincipal>()?.userId
                ?: return@put call.respond(HttpStatusCode.Unauthorized)

            val settings = call.receive<UserSettings>().copy(updatedAt = System.currentTimeMillis())

            repo.saveUserSettings(userId = userId, userSettings = settings)

            call.respond(HttpStatusCode.OK)
        }
    }
}
