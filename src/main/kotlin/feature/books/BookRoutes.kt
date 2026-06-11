package nl.rhaydus.feature.books

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import nl.rhaydus.core.model.UserPrincipal

fun Route.bookRoutes(bookDataSource: BookDataSource) {
    get("books/{id}") {
        val id = call.parameters["id"]?.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest)

        val token = call.principal<UserPrincipal>()?.token ?: return@get call.respond(HttpStatusCode.Unauthorized)

        val book = bookDataSource.getBookById(id = id, token = token)

        return@get call.respond(book)
    }
}