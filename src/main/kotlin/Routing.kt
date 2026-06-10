package nl.rhaydus

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.receive
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.model.Book
import nl.rhaydus.model.UserSettings

fun Application.configureRouting() {
    val repo = MemorySettingsRepositoryImpl()

    routing {
        bookRoutes()

        settingsRoutes(repo = repo)
    }
}

fun Route.bookRoutes() {
    get("/books") {
        val books = listOf(
            Book(title = "Salem's lot", author = "Stephen King"),
            Book(title = "Salem's lot", author = "Stephen King"),
        )

        call.respond(books)
    }
}

fun Route.settingsRoutes(repo: SettingsRepository) {
    route("/settings") {
        get {
            val userId = call.parameters["userId"]?.toIntOrNull()

            if (userId == null || userId == -1) {
                call.respond(HttpStatusCode.BadRequest, "User ID is required to fetch user settings")
                return@get
            }

            val settings = repo.getUserSettings(userId = userId)

            call.respond(settings)
        }

        put {
            val userId = call.parameters["userId"]?.toIntOrNull()

            if (userId == null || userId == -1) {
                call.respond(HttpStatusCode.BadRequest, "User ID is required to fetch user settings")
                return@put
            }

            val settings = call.receive<UserSettings>()

            repo.saveUserSettings(userId = userId, settings)

            call.respond(HttpStatusCode.OK)
        }
    }
}